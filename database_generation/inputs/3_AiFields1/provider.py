import os
import sqlite3
import json
import sys
import time
import re
import logging
from typing import Dict, Any, Iterator, Tuple, List, Optional

from lib.utils_ai import call_llm_api
from lib import Provider, ProviderType, BATCH_SIZE, API_ENDPOINT, MODEL_NAME, DEFINITION_AI_LOCALE, HSK_FILES, load_u8_words, ensure_cache_table
from lib.conf import CEDICT_FILE

ALLOWED_MODALITIES = ("ORAL", "WRITTEN", "ORAL_WRITTEN", "N/A")
ALLOWED_TYPES = ("NOUN", "VERB", "ADJECTIVE", "ADVERB", "CONJUNCTION", "PREPOSITION", "INTERJECTION", "IDIOM", "N/A")
# Columns that must hold a meaningful value for the word to count as "done".
# This mirrors the summary report predicate (orchestrator._generate_report):
#   col IS NOT NULL AND col != '' AND col != 'N/A'
# synonyms/antonym are legitimately sparse, so they are NOT required.
REQUIRED_COLUMNS = ("examples", "modality", "type")
OPTIONAL_COLUMNS = ("synonyms", "antonym")
ALL_COLUMNS = REQUIRED_COLUMNS + OPTIONAL_COLUMNS


def is_complete(value: Any) -> bool:
    """Completeness check for a cached field value.

    A field counts as done when it is NOT NULL and not blank. 'N/A' is
    accepted as an answered value (per user decision) even though the
    summary report still lists N/A rows as missing.
    """
    return value is not None and str(value).strip() != ""


def normalize_modality(value: Any) -> Optional[str]:
    if value is None:
        return None
    v = str(value).strip().upper().replace(" ", "_")
    if v == "WRITTEN_ORAL":
        v = "ORAL_WRITTEN"
    return v if v in ALLOWED_MODALITIES else None


def normalize_pos_type(value: Any) -> Optional[str]:
    if value is None:
        return None
    v = str(value).strip().upper()
    if v == "N/A":
        return "N/A"
    # The model sometimes returns several tags ("VERB|ADJECTIVE|NOUN") or
    # non-listed ones ("PROPER NOUN"). Keep the first translatable token
    # instead of caching garbage that the report would count as present.
    for token in re.split(r"[^A-Z]+", v):
        if token in ALLOWED_TYPES:
            return token
    return None


def normalize_text(value: Any) -> Optional[str]:
    if value is None:
        return None
    v = str(value).strip()
    return v if v else None


def contains_foreign_latin(text: str, word: str) -> bool:
    """True if text holds Latin letters outside the headword itself.

    CEDict contains Latin/digit headwords (e.g. 'MC', '3D打印'); their own
    characters must not trip the simplified-Chinese-only validation.
    """
    probe = text.replace(word, "") if word else text
    return bool(re.search(r"[a-zA-Z]", probe))


def normalize_record(raw: Dict[str, Any]) -> Dict[str, Any]:
    """Normalizes one cached row so completeness checks, assembly output and
    stored values all agree (no garbage enums counted as present)."""
    return {
        "examples": normalize_text(raw.get("examples")),
        "modality": normalize_modality(raw.get("modality")),
        "type": normalize_pos_type(raw.get("type")),
        "synonyms": normalize_text(raw.get("synonyms")),
        "antonym": normalize_text(raw.get("antonym")),
    }


def generate_prompt(words: List[str]) -> str:
    return f"""<|system|>
You are a precise simplified Chinese language assistant helping learners learn using mostly HSK3 simplified chinese vocabulary. You MUST return a valid JSON array of objects.
<|user|>
Analyze the following Chinese words. For each word, return an object with these fields:

1. "word": The original simplified chinese word FROM THE LIST BELOW. Do NOT change it to traditional characters or a different word.
2. "examples": One example sentence per definition, separated with \\n. Use HSK3 simplificed chinese vocabulary.
3. "modality": EXACTLY ONE of ["ORAL", "WRITTEN", "ORAL_WRITTEN", "N/A"].
4. "type": EXACTLY ONE of ["NOUN", "VERB", "ADJECTIVE", "ADVERB", "CONJUNCTION", "PREPOSITION", "INTERJECTION", "IDIOM", "N/A"]. If a word has multiple types, choose the most common one.
5. "synonyms": Comma-separated simplified Chinese words (or empty string).
6. "antonym": Closest antonym in simplified Chinese (or empty string).

CRITICAL: 
- Output MUST be a valid JSON array. 
- Use ONLY SIMPLIFIED CHINESE. Do NOT return traditional characters.
- No markdown formatting (no ```json).
- Fields "modality" and "type" must be a single string from the allowed list, NOT a list or multiple strings.

Words to analyze: {', '.join(words)}

Expected format:
[
  {{
    "word": "example",
    "examples": "ex1\\nex2",
    "modality": "ORAL_WRITTEN",
    "type": "NOUN",
    "synonyms": "syn1, syn2",
    "antonym": "ant1"
  }}
]
"""

class AiFieldsProvider(Provider):
    def __init__(self):
        self.logger = logging.getLogger(__name__)

    def _get_cache_conn(self):
        cache_db = os.path.join(os.path.dirname(__file__), "ai_fields_cache.db")
        conn = sqlite3.connect(cache_db)
        ensure_cache_table(
            conn,
            table="chinese_word",
            columns_sql=("simplified TEXT, examples TEXT, modality TEXT, "
                         "type TEXT, synonyms TEXT, antonym TEXT"),
            indexes=("CREATE INDEX IF NOT EXISTS idx_chinese_word_simplified "
                     "ON chinese_word (simplified)",),
        )
        return conn

    def _upsert_row(self, cursor: sqlite3.Cursor, word: str, merged: Dict[str, Any]):
        """UPDATE-or-INSERT without requiring a PRIMARY KEY.

        INSERT OR REPLACE cannot replace without a unique constraint and
        would pile up duplicate rows, so update in place when the word
        already has row(s), insert only when it has none.
        """
        cursor.execute("SELECT COUNT(*) FROM chinese_word WHERE simplified = ?", (word,))
        if cursor.fetchone()[0]:
            cursor.execute(
                "UPDATE chinese_word SET examples = ?, modality = ?, type = ?, "
                "synonyms = ?, antonym = ? WHERE simplified = ?",
                (merged["examples"], merged["modality"], merged["type"],
                 merged["synonyms"], merged["antonym"], word),
            )
        else:
            cursor.execute(
                "INSERT INTO chinese_word (simplified, examples, modality, type, synonyms, antonym) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                (word, merged["examples"], merged["modality"], merged["type"],
                 merged["synonyms"], merged["antonym"]),
            )

    def _load_cache(self, cursor: sqlite3.Cursor) -> Dict[str, Dict[str, Any]]:
        # Dedupe in memory only (most complete, then most recent wins). The
        # legacy table has no PRIMARY KEY; the file itself is never rewritten.
        cursor.execute("SELECT rowid, simplified, examples, modality, type, synonyms, antonym FROM chinese_word")
        scored: Dict[str, Tuple[int, int, Dict[str, Any]]] = {}
        for rowid, simplified, examples, modality, pos_type, synonyms, antonym in cursor.fetchall():
            word = (simplified or "").strip()
            if not word:
                continue
            vals = normalize_record({"examples": examples, "modality": modality, "type": pos_type,
                                     "synonyms": synonyms, "antonym": antonym})
            score = sum(1 for c in REQUIRED_COLUMNS if is_complete(vals[c]))
            prev = scored.get(word)
            if prev is None or (score, rowid) > (prev[0], prev[1]):
                scored[word] = (score, rowid, vals)
        return {word: vals for word, (_, _, vals) in scored.items()}

    def update(self):
        """Fetches missing AI fields from the LLM and stores them in the local cache DB."""
        words_to_process = load_u8_words(CEDICT_FILE)

        conn = self._get_cache_conn()
        cursor = conn.cursor()

        cache = self._load_cache(cursor)
        # A word is missing unless every required column holds a usable
        # value (NOT NULL, not blank). Checking mere row existence hides
        # NULL/blank fields from every later run (the reported bug).
        # 'N/A' counts as answered; synonyms/antonym never gate completeness.
        missing_words = [w for w in words_to_process
                         if not all(is_complete(cache.get(w, {}).get(c)) for c in REQUIRED_COLUMNS)]

        if not missing_words:
            self.logger.info("AiFieldsProvider: All words already cached.")
            conn.close()
            return

        self.logger.info(f"AiFieldsProvider: Found {len(missing_words)} words missing from cache. Starting LLM updates...")

        cached_count = 0
        discarded_count = 0
        failed_batches = 0

        for i in range(0, len(missing_words), BATCH_SIZE):
            batch = missing_words[i:i + BATCH_SIZE]
            prompt = generate_prompt(batch)
            # synonyms/antonym are optional: a result missing them must still
            # be used, never discarded.
            required_fields = ['word', 'examples', 'modality', 'type']

            ai_results = call_llm_api(API_ENDPOINT, MODEL_NAME, prompt, required_fields)

            if ai_results:
                for res in ai_results:
                    raw_word = res.pop('word', None)
                    if not raw_word:
                        discarded_count += 1
                        continue

                    word = str(raw_word).strip()

                    # Strict Validation: The word MUST be in our current batch.
                    # This prevents traditional characters or garbage from being cached.
                    if word not in batch:
                        self.logger.warning(f"AiFieldsProvider: Discarding AI result for '{word}' - not in requested batch.")
                        discarded_count += 1
                        continue

                    # Normalize fields. Drop unknown keys: the model sometimes
                    # emits garbled ones (e.g. "ant" instead of "antonym")
                    # which would crash the INSERT.
                    normalized = {
                        "examples": normalize_text(res.get("examples")),
                        "modality": normalize_modality(res.get("modality")),
                        "type": normalize_pos_type(res.get("type")),
                        "synonyms": normalize_text(res.get("synonyms")),
                        "antonym": normalize_text(res.get("antonym")),
                    }

                    # Examples containing Latin outside the headword itself
                    # are rejected, but only the examples field is dropped:
                    # the remaining fields are still worth caching.
                    if normalized["examples"] and contains_foreign_latin(normalized["examples"], word):
                        self.logger.warning(f"AiFieldsProvider: Dropping examples for '{word}' - contain non-Chinese characters.")
                        normalized["examples"] = None

                    # Merge with any previously cached row: never overwrite a
                    # complete value with an incomplete one, so retries can
                    # only improve a word, never degrade it.
                    existing = cache.get(word, {})
                    merged = {}
                    for col in REQUIRED_COLUMNS:
                        if is_complete(normalized[col]):
                            merged[col] = normalized[col]
                        elif is_complete(existing.get(col)):
                            merged[col] = existing[col]
                        else:
                            merged[col] = normalized[col]
                    for col in OPTIONAL_COLUMNS:
                        merged[col] = normalized[col] if normalized[col] is not None else existing.get(col)

                    self._upsert_row(cursor, word, merged)
                    cache[word] = merged
                    cached_count += 1

                conn.commit()
                self.logger.info(f"AiFieldsProvider: Progress {i + len(batch)}/{len(missing_words)}")
            else:
                failed_batches += 1
                self.logger.warning(f"AiFieldsProvider: Failed to get results for batch starting with {batch[0]}. Skipping.")

            # Sleep to avoid rate limiting
            time.sleep(1)

        self.logger.info(f"AiFieldsProvider: done. cached={cached_count} discarded={discarded_count} failed_batches={failed_batches}")
        conn.close()

    def schema(self) -> Dict[str, Dict[str, Any]]:
        return {
            "chinese_word": {
                "type": ProviderType.COLUMN,
                "columns": ["examples", "modality", "type", "synonyms", "antonym"],
                "index": "simplified"
            }
        }

    def data(self) -> Iterator[Tuple[str, Dict[str, Any]]]:
        cache_db = os.path.join(os.path.dirname(__file__), "ai_fields_cache.db")
        if not os.path.exists(cache_db):
            return

        words_to_process = load_u8_words(CEDICT_FILE)
        allowed_words = set(words_to_process)

        conn = sqlite3.connect(cache_db)
        cursor = conn.cursor()

        # Dedupe in memory (most complete, then most recent wins) and
        # normalize, so duplicate legacy rows yield one clean record.
        # The cache file itself is never rewritten here.
        cursor.execute("SELECT rowid, * FROM chinese_word")
        columns = [desc[0] for desc in cursor.description]
        scored: Dict[str, Tuple[int, int, Dict[str, Any]]] = {}
        for row in cursor.fetchall():
            record = dict(zip(columns, row))
            rowid = record.pop("rowid")
            word = (record.get("simplified") or "").strip() if isinstance(record.get("simplified"), str) else ""
            if not word:
                continue
            record["simplified"] = word
            for col in ALL_COLUMNS:
                record[col] = normalize_record(record)[col]
            score = sum(1 for c in REQUIRED_COLUMNS if is_complete(record.get(c)))
            prev = scored.get(word)
            if prev is None or (score, rowid) > (prev[0], prev[1]):
                scored[word] = (score, rowid, record)
        for _, _, record in scored.values():
            # Final validation: only yield if word is in our base dictionary
            if record.get('simplified') in allowed_words:
                yield ("chinese_word", record)

        conn.close()
