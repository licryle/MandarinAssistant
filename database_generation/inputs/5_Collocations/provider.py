import os
import re
import sqlite3
import sys
import time
import logging
from typing import Dict, Any, Iterator, Tuple, List

from lib.utils_ai import call_llm_api
from lib import Provider, ProviderType, BATCH_SIZE, API_ENDPOINT, MODEL_NAME, HSK_FILES, COLLOCATIONS_CACHE_DB, load_u8_words, ensure_cache_table
from lib.conf import CEDICT_FILE


def is_complete(value: Any) -> bool:
    """Completeness check: NOT NULL and not blank.

    'N/A' is accepted as an answered value (per user decision); NULL and ''
    must never be written to the cache.
    """
    return value is not None and str(value).strip() != ""


def contains_foreign_latin(text: str, word: str) -> bool:
    """True if text holds Latin letters outside the headword itself.

    CEDict contains Latin/digit headwords (e.g. 'MC', '3D打印'); their own
    characters must not trip the Chinese-only validation.
    """
    probe = text.replace(word, "") if word else text
    return bool(re.search(r"[a-zA-Z]", probe))


def generate_prompt(words: List[str]) -> str:
    return f"""<|system|>
You are a precise Chinese language assistant. You MUST return a valid JSON array of objects.
<|user|>
For each of the following Chinese words, provide a list of up to 5 most common collocations (habitual co-occurrence of words).
The collocations should be ordered by popularity/frequency descending.

CRITICAL: 
- Output MUST be a valid JSON array. 
- No trailing commas in objects.
- No markdown formatting (no ```json).
- The field "collocations" must be a SINGLE STRING, each value separated with \\n.

Words to analyze: {', '.join(words)}

Expected format:
[
  {{
    "word": "做",
    "collocations": "做决定\\n做饭\\n做功课\\n做生意\\n做运动"
  }}
]
"""

class CollocationsProvider(Provider):
    def __init__(self):
        self.logger = logging.getLogger(__name__)

    def _get_cache_conn(self):
        os.makedirs(os.path.dirname(COLLOCATIONS_CACHE_DB), exist_ok=True)
        conn = sqlite3.connect(COLLOCATIONS_CACHE_DB)
        ensure_cache_table(
            conn,
            table="chinese_word",
            columns_sql=("`simplified` TEXT NOT NULL, `collocations` TEXT, "
                         "PRIMARY KEY(`simplified`)"),
            # PRIMARY KEY already indexes `simplified`; no extra index needed.
        )
        return conn

    def update(self):
        """Fetches missing collocations from the LLM and stores them in the local cache DB."""
        words_to_process = load_u8_words(CEDICT_FILE)

        conn = self._get_cache_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT simplified, collocations FROM chinese_word")
        cache = {(row[0] or "").strip(): row[1] for row in cursor.fetchall() if row[0]}

        # A word is missing unless its collocations hold a usable value
        # (NOT NULL, not blank). The old IS NOT NULL check treated '' rows
        # as complete, so thousands of words were never (re)tried.
        # 'N/A' counts as answered; NULL and '' must never be written.
        missing_words = [w for w in words_to_process if not is_complete(cache.get(w))]

        if not missing_words:
            self.logger.info("CollocationsProvider: All words already cached.")
            conn.close()
            return

        self.logger.info(f"CollocationsProvider: Found {len(missing_words)} words missing from cache. Starting LLM updates...")

        cached_count = 0
        discarded_count = 0
        failed_batches = 0

        for i in range(0, len(missing_words), BATCH_SIZE):
            batch = missing_words[i:i + BATCH_SIZE]
            prompt = generate_prompt(batch)
            required_fields = ['word', 'collocations']

            ai_results = call_llm_api(API_ENDPOINT, MODEL_NAME, prompt, required_fields)
            if ai_results:
                for res in ai_results:
                    raw_word = res.get('word')
                    collocations_raw = res.get('collocations')
                    word = str(raw_word).strip() if raw_word else ""

                    # Strict Validation: the word MUST be in our current batch.
                    # The model sometimes echoes a traditional variant or a
                    # different word; caching that key would silently lose the
                    # requested word (UPDATE matches 0 rows at assembly).
                    if not word or word not in batch:
                        self.logger.warning(f"CollocationsProvider: Discarding AI result for '{raw_word}' - not in requested batch.")
                        discarded_count += 1
                        continue
                    if not collocations_raw or not str(collocations_raw).strip():
                        discarded_count += 1
                        continue

                    # 1. Trim strings on each line
                    lines = [line.strip() for line in str(collocations_raw).split('\n') if line.strip()]

                    # 2. Drop lines with Latin letters, unless the Latin comes
                    # from the headword itself (e.g. 'MC', '3D打印').
                    lines = [line for line in lines if not contains_foreign_latin(line, word)]

                    # 3. Remove any string that doesn't contain the original word
                    lines = [line for line in lines if word in line]

                    # 4. Remove duplicates (preserving order)
                    seen = set()
                    unique_lines = []
                    for line in lines:
                        if line not in seen:
                            unique_lines.append(line)
                            seen.add(line)

                    collocations = '\n'.join(unique_lines)
                    if not collocations:
                        # Never write ''/whitespace: the word would look
                        # cached while holding nothing usable.
                        self.logger.warning(f"CollocationsProvider: No usable collocations for '{word}' - leaving uncached for retry.")
                        discarded_count += 1
                        continue

                    cursor.execute("INSERT OR REPLACE INTO chinese_word (simplified, collocations) VALUES (?, ?)", (word, collocations))
                    cached_count += 1
                conn.commit()
                self.logger.info(f"CollocationsProvider: Progress {i + len(batch)}/{len(missing_words)}")
            else:
                failed_batches += 1
                self.logger.warning(f"CollocationsProvider: Failed to get results for batch starting with {batch[0]}. Skipping.")

            time.sleep(1)

        self.logger.info(f"CollocationsProvider: done. cached={cached_count} discarded={discarded_count} failed_batches={failed_batches}")
        conn.close()

    def schema(self) -> Dict[str, Dict[str, Any]]:
        return {
            "chinese_word": {
                "type": ProviderType.COLUMN,
                "columns": ["collocations"],
                "index": "simplified"
            }
        }

    def data(self) -> Iterator[Tuple[str, Dict[str, Any]]]:
        if not os.path.exists(COLLOCATIONS_CACHE_DB):
            return

        words_to_process = load_u8_words(CEDICT_FILE)
        allowed_words = set(words_to_process)

        conn = sqlite3.connect(COLLOCATIONS_CACHE_DB)
        cursor = conn.cursor()
        cursor.execute("SELECT simplified, collocations FROM chinese_word")
        for row in cursor.fetchall():
            word = (row[0] or "").strip()
            # Only yield usable rows (never NULL/'') for base-vocabulary
            # words, so stale keys (e.g. traditional variants) and blank
            # rows never reach assembly.
            if word in allowed_words and is_complete(row[1]):
                yield ("chinese_word", {"simplified": word, "collocations": row[1]})
        conn.close()
