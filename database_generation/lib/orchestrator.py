import json
import os
import sqlite3
import importlib.util
import logging
import sys
import shutil
import tempfile
from datetime import datetime
from typing import List, Dict, Any, Set, Iterator, Tuple, Optional
from .base_provider import Provider, ProviderType
from .utils import merge_json_strings
from .u8_utils import load_u8_words, extract_bracketed_pinyins
from .conf import CEDICT_FILE, DEFINITION_AI_LOCALE

class Orchestrator:
    def __init__(self, schema_path: str, db_path: str):
        self.schema_path = schema_path
        self.db_path = db_path
        self.providers: List[Provider] = []
        self.schema_data = self._load_schema()
        self.table_definitions = self._parse_room_entities()
        self.column_defaults: Dict[str, Dict[str, Any]] = {}
        self.previous_database_path: Optional[str] = None
        self._setup_logging()

    def _setup_logging(self):
        os.makedirs('logs', exist_ok=True)
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        log_file = f'logs/generation_{timestamp}.log'
        
        logging.basicConfig(
            level=logging.DEBUG,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler(log_file, encoding='utf-8'),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger(__name__)
        self.logger.info(f"Logging initialized. Log file: {log_file}")

    def _load_schema(self):
        with open(self.schema_path, 'r') as f:
            return json.load(f)

    def _parse_room_entities(self) -> Dict[str, Dict[str, Any]]:
        entities = {}
        for entity in self.schema_data['database']['entities']:
            table_name = entity['tableName']
            fields = {field['columnName']: field for field in entity['fields']}
            entities[table_name] = {
                'fields': fields,
                'primaryKey': entity['primaryKey']['columnNames']
            }
        return entities

    def create_database(self):
        self.logger.info(f"Initializing database: {self.db_path}")
        if os.path.exists(self.db_path):
            os.remove(self.db_path)
        
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        # sqlite3 leaves foreign-key enforcement disabled by default.  Keep generation
        # subject to the same relational guarantees as the Room database it produces.
        cursor.execute("PRAGMA foreign_keys = ON")
        
        for entity in self.schema_data['database']['entities']:
            create_sql = entity['createSql'].replace('${TABLE_NAME}', entity['tableName'])
            cursor.execute(create_sql)
            
            for index in entity.get('indices', []):
                index_sql = index['createSql'].replace('${TABLE_NAME}', entity['tableName'])
                cursor.execute(index_sql)

            for trigger in entity.get('contentSyncTriggers', []):
                cursor.execute(trigger)
        
        for query in self.schema_data['database']['setupQueries']:
            cursor.execute(query)

        version = self.schema_data['database']['version']
        cursor.execute(f"PRAGMA user_version = {version}")
    
        conn.commit()
        conn.close()
        self.logger.info("Database schema created.")

    def discover_providers(self, inputs_dir: str):
        self.logger.info(f"Discovering providers in {inputs_dir}...")
        for item in sorted(os.listdir(inputs_dir)):
            item_path = os.path.join(inputs_dir, item)
            provider_file = os.path.join(item_path, "provider.py")
            if os.path.isdir(item_path) and os.path.exists(provider_file):
                spec = importlib.util.spec_from_file_location(f"provider_{item}", provider_file)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                
                for attr_name in dir(module):
                    attr = getattr(module, attr_name)
                    if isinstance(attr, type) and issubclass(attr, Provider) and attr is not Provider:
                        self.providers.append(attr())
                        self.logger.info(f"Discovered provider: {attr_name} in {item}")

    def validate_providers(self):
        self.logger.info("Validating providers...")
        errors = []
        table_owners = {}
        
        for provider in self.providers:
            p_name = provider.__class__.__name__
            p_schema = provider.schema()
            
            for table, info in p_schema.items():
                if table not in self.table_definitions:
                    errors.append(f"Provider {p_name} targets unknown table '{table}'")
                    continue
                
                provider_cols = set(info['columns'])
                if info['type'] == ProviderType.TABLE:
                    if table in table_owners:
                        owner_name, owner_cols = table_owners[table]
                        if provider_cols != owner_cols:
                            errors.append(f"Conflict: Table '{table}' has multiple primary providers with DIFFERENT columns: {p_name} and {owner_name}")
                        else:
                            self.logger.warning(f"Table '{table}' has multiple primary providers ({p_name}, {owner_name}), but their schema matches. Allowing.")
                    else:
                        table_owners[table] = (p_name, provider_cols)

                valid_cols = self.table_definitions[table]['fields'].keys()
                for col in info['columns']:
                    if col not in valid_cols:
                        errors.append(f"Provider {p_name} targets unknown column '{col}' in table '{table}'")

                # Collect defaults from provider schema
                if 'defaults' in info:
                    if table not in self.column_defaults:
                        self.column_defaults[table] = {}
                    self.column_defaults[table].update(info['defaults'])
        
        if errors:
            for err in errors:
                self.logger.error(err)
            logging.shutdown()
            sys.exit(1)
        self.logger.info("Validation successful.")

    def _assemble_data(self, conn: sqlite3.Connection):
        cursor = conn.cursor()
        
        prev_conn = None
        if self.previous_database_path:
            prev_conn = sqlite3.connect(self.previous_database_path)
            prev_conn.row_factory = sqlite3.Row

        def normalize(v):
            """Treats NULL, empty strings, and 'N/A' as equivalent."""
            if v is None or v == '' or v == 'N/A':
                return ''
            return str(v).strip()

        table_providers = [p for p in self.providers if any(s['type'] == ProviderType.TABLE for s in p.schema().values())]
        column_providers = [p for p in self.providers if p not in table_providers]

        assembly_errors = []

        for provider in table_providers + column_providers:
            p_name = provider.__class__.__name__
            p_schema = provider.schema()
            self.logger.info(f"Assembling data from {p_name}...")
            
            for table_name, record in provider.data():
                if table_name not in p_schema:
                    continue
                
                info = p_schema[table_name]
                table_def = self.table_definitions[table_name]
                
                # Global Cleanup: Trim simplified and filter out LLM artifacts/garbage
                is_garbage = False
                for key in ['simplified', 'a_simplified']:
                    if key in record and isinstance(record[key], str):
                        val = record[key].strip()
                        record[key] = val
                        # Explicit filter for LLM tags and artifacts
                        if not val or any(tag in val for tag in ['tool_call', '<|', '|>', '</']):
                            msg = f"Garbage record detected in {table_name} from {p_name}: '{val}'"
                            self.logger.error(msg)
                            assembly_errors.append(msg)
                            is_garbage = True
                            break
                if is_garbage:
                    continue

                # Foreign Key Safeguard: Ensure parent word exists for child tables
                if table_name in ['word_definition', 'chinese_word_annotation']:
                    pk_val = record.get('simplified') or record.get('a_simplified')
                    cursor.execute("SELECT 1 FROM chinese_word WHERE simplified = ?", (pk_val,))
                    if not cursor.fetchone():
                        msg = f"FK violation: {table_name} refers to '{pk_val}' which is missing in chinese_word (Provider: {p_name})"
                        self.logger.error(msg)
                        assembly_errors.append(msg)
                        continue

                if info['type'] == ProviderType.TABLE:
                    data_to_insert = record.copy()
                    table_fields = table_def['fields']
                    for col_name, field_info in table_fields.items():
                        if col_name not in data_to_insert:
                            default_val = field_info.get('defaultValue')
                            if default_val is not None:
                                if isinstance(default_val, str) and default_val.startswith("'") and default_val.endswith("'"):
                                    data_to_insert[col_name] = default_val[1:-1]
                                else:
                                    try:
                                        data_to_insert[col_name] = int(default_val)
                                    except (ValueError, TypeError):
                                        data_to_insert[col_name] = default_val
                            elif table_name in self.column_defaults and col_name in self.column_defaults[table_name]:
                                data_to_insert[col_name] = self.column_defaults[table_name][col_name]
                            elif not field_info.get('notNull', False):
                                data_to_insert[col_name] = None

                    cols = list(data_to_insert.keys())
                    placeholders = ', '.join(['?'] * len(cols))
                    sql = f"INSERT OR REPLACE INTO {table_name} ({', '.join(cols)}) VALUES ({placeholders})"
                    
                    try:
                        cursor.execute(sql, list(data_to_insert.values()))
                    except Exception as e:
                        print(f"\033[91mError inserting into {table_name} from provider {p_name}: {e}\033[0m")
                        print(f"SQL:\n{sql}")
                        print(f"Data: {data_to_insert}")
                
                elif info['type'] == ProviderType.COLUMN:
                    index_col = info['index']
                    if index_col not in record:
                        continue
                    
                    cols = list(record.keys())
                    update_cols = [c for c in cols if c != index_col]
                    
                    if 'definition' in update_cols:
                        cursor.execute(f"SELECT definition FROM {table_name} WHERE {index_col} = ?", (record[index_col],))
                        row = cursor.fetchone()
                        if row:
                            current_def = row[0]
                            record['definition'] = merge_json_strings(current_def, record['definition'])
                    
                    # Finalize columns for update
                    final_update_cols = [c for c in record.keys() if c != index_col]
                    set_clause = ', '.join([f"{c} = ?" for c in final_update_cols])
                    sql = f"UPDATE {table_name} SET {set_clause} WHERE {index_col} = ?"
                    
                    values = [record[c] for c in final_update_cols]
                    values.append(record[index_col])
                    cursor.execute(sql, values)
            
            conn.commit()
        
        if prev_conn:
            prev_conn.close()

        if assembly_errors:
            self.logger.critical(f"Database assembly failed with {len(assembly_errors)} errors.")
            for err in assembly_errors[:10]: # Log first 10
                self.logger.error(f"  - {err}")
            if len(assembly_errors) > 10:
                self.logger.error(f"  ... and {len(assembly_errors) - 10} more.")
            
            logging.shutdown()
            sys.exit(1)

    def run(self, run_update: bool, report_only: bool = False):
        if report_only:
            if not os.path.exists(self.db_path):
                self.logger.error(f"Database file not found for report: {self.db_path}")
                return
            conn = sqlite3.connect(self.db_path)
            try:
                self._generate_report(conn)
            finally:
                conn.close()
            return

        if run_update:
            for provider in self.providers:
                p_name = provider.__class__.__name__
                self.logger.info(f"Updating {p_name}...")
                try:
                    provider.update()
                except Exception as e:
                    self.logger.critical(f"FATAL: Update failed for {p_name}: {e}", exc_info=True)
                    logging.shutdown()
                    sys.exit(1)

        # Stage 0 needs the previous generated database before create_database replaces it.
        # Keep the snapshot outside the output directory and remove it after assembly.
        if os.path.exists(self.db_path):
            fd, snapshot_path = tempfile.mkstemp(prefix="mandarin_assistant_previous_", suffix=".db")
            os.close(fd)
            shutil.copy2(self.db_path, snapshot_path)
            self.previous_database_path = snapshot_path
            for provider in self.providers:
                provider.previous_database_path = snapshot_path
            self.logger.info(f"Preserved previous dictionary snapshot: {snapshot_path}")

        self.create_database()
        conn = sqlite3.connect(self.db_path)
        try:
            # PRAGMA settings are connection-local, so enable this again for provider inserts.
            conn.execute("PRAGMA foreign_keys = ON")
            self._assemble_data(conn)
            self._post_process(conn)
            self._generate_report(conn)
        finally:
            conn.close()
            if self.previous_database_path and os.path.exists(self.previous_database_path):
                os.remove(self.previous_database_path)
                self.previous_database_path = None
        self.logger.info("Database generation complete.")

    def _post_process(self, conn: sqlite3.Connection):
        from unidecode import unidecode
        self.logger.info("Post-processing: generating searchable_text...")
        cursor = conn.cursor()
        
        # Update chinese_word
        cursor.execute("SELECT simplified, traditional, pinyins, examples, collocations, synonyms, antonym, searchable_text FROM chinese_word")
        words = cursor.fetchall()
        for row in words:
            simplified, traditional, pinyins, examples, collocations, synonyms, antonym, old_searchable_text = row
            
            # Index every reading: the display pinyins plus any [pinyin]
            # prefixes stored in formatted multi-reading definitions.
            cursor.execute("SELECT definition FROM word_definition WHERE simplified = ?", (simplified,))
            definition_rows = [r[0] for r in cursor.fetchall()]
            
            variants = []
            if pinyins and pinyins not in variants:
                variants.append(pinyins)
            for definition_row in definition_rows:
                for reading in extract_bracketed_pinyins(definition_row):
                    if reading not in variants:
                        variants.append(reading)
            toneless_parts = [unidecode(variant) for variant in variants]
            toneless = " ".join(toneless_parts)
            concatenated = " ".join([part.replace(" ", "") for part in toneless_parts])
            hanzi_split = " ".join(list(simplified))
            
            parts = [simplified, traditional, hanzi_split, toneless, concatenated, examples, collocations, synonyms, antonym]
            new_searchable_text = " ".join([str(p) for p in parts if p]).lower()
            
            if new_searchable_text != old_searchable_text:
                cursor.execute("UPDATE chinese_word SET searchable_text = ? WHERE simplified = ?", (new_searchable_text, simplified))
        
        # Update chinese_word_annotation
        cursor.execute("SELECT a_simplified, a_pinyins, notes, themes, a_searchable_text FROM chinese_word_annotation")
        annotations = cursor.fetchall()
        for row in annotations:
            a_simplified, a_pinyins, notes, themes, old_searchable_text = row
            
            toneless = unidecode(a_pinyins or "")
            concatenated = toneless.replace(" ", "")
            hanzi_split = " ".join(list(a_simplified))
            
            parts = [a_simplified, hanzi_split, toneless, concatenated, notes, themes]
            new_searchable_text = " ".join([str(p) for p in parts if p]).lower()
            
            if new_searchable_text != old_searchable_text:
                cursor.execute("UPDATE chinese_word_annotation SET a_searchable_text = ? WHERE a_simplified = ?", (new_searchable_text, a_simplified))
            
        # Rebuild FTS indexes to ensure they are in sync and not corrupted by REPLACE operations during assembly
        self.logger.info("Rebuilding FTS5 indexes...")
        cursor.execute("INSERT INTO chinese_word_fts(chinese_word_fts) VALUES('rebuild')")
        cursor.execute("INSERT INTO word_definition_fts(word_definition_fts) VALUES('rebuild')")
        cursor.execute("INSERT INTO chinese_word_annotation_fts(chinese_word_annotation_fts) VALUES('rebuild')")

        conn.commit()

    def _generate_report(self, conn: sqlite3.Connection):
        self.logger.info("Generating summary report...")

        cedict_words = set(load_u8_words(CEDICT_FILE))
        cedict_count = len(cedict_words)

        cursor = conn.cursor()

        # 1. Total words in chinese_word
        cursor.execute("SELECT simplified FROM chinese_word")
        db_words = {row[0] for row in cursor.fetchall()}
        db_count = len(db_words)

        in_cedict_count = len(db_words.intersection(cedict_words))
        out_cedict_count = db_count - in_cedict_count

        def pct_in(count, total_or_ref):
            return f"{count} ({count / total_or_ref * 100:.1f}%)" if total_or_ref > 0 else f"{count} (0.0%)"

        report = []
        report.append("=" * 95)
        report.append(f"{'DICTIONARY GENERATION SUMMARY REPORT':^95}")
        report.append("=" * 95)
        report.append(f"{'Category':<35} | {'Total':<15} | {'In CEDict (% of Ref)':<20} | {'Out of CEDict':<15}")
        report.append("-" * 95)

        report.append(f"{'CEDict Reference':<35} | {cedict_count:<15} | {'-':<20} | {'-':<15}")
        report.append(
            f"{'Database (chinese_word)':<35} | {db_count:<15} | {pct_in(in_cedict_count, cedict_count):<20} | {str(out_cedict_count):<15}")
        report.append("-" * 95)

        # Languages
        langs = [
            ("English", "en"),
            ("French", "fr"),
            ("Spanish", "es"),
            ("HSK3", DEFINITION_AI_LOCALE)
        ]

        for name, code in langs:
            cursor.execute("SELECT simplified FROM word_definition WHERE language = ?", (code,))
            lang_words = {row[0] for row in cursor.fetchall()}
            total = len(lang_words)
            in_c = len(lang_words.intersection(cedict_words))
            out_c = total - in_c
            report.append(
                f"{f'Lang: {name}':<35} | {total:<15} | {pct_in(in_c, cedict_count):<20} | {str(out_c):<15}")

        report.append("-" * 95)

        # Columns
        cols = ["examples", "modality", "type", "synonyms", "antonym", "collocations"]
        for col in cols:
            cursor.execute(
                f"SELECT simplified FROM chinese_word WHERE {col} IS NOT NULL AND {col} != '' AND {col} != 'N/A'")
            col_words = {row[0] for row in cursor.fetchall()}
            total = len(col_words)
            in_c = len(col_words.intersection(cedict_words))
            out_c = total - in_c
            report.append(
                f"{f'Field: {col}':<35} | {total:<15} | {pct_in(in_c, cedict_count):<20} | {str(out_c):<15}")

        report.append("=" * 95 + "\n")

        for line in report:
            self.logger.info(line)
