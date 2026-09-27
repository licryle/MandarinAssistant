"""Shared helpers for provider-local SQLite cache databases.

Every LLM-backed provider keeps a small `chinese_word` cache DB so repeated
runs only query the model for words that are still missing. Table creation
must be uniform and strictly non-destructive: this module only ever issues
CREATE TABLE / CREATE INDEX IF NOT EXISTS and never alters existing tables
or their data.
"""
import sqlite3
from typing import Sequence


def ensure_cache_table(conn: sqlite3.Connection, *, table: str, columns_sql: str,
                       indexes: Sequence[str] = ()) -> None:
    """Creates a cache table and its indexes, only if they are missing.

    Safe to call on every update(): existing tables, data and schemas are
    never modified. Table/column names come from hardcoded call sites
    (never user input).
    """
    assert table.isidentifier(), f"Invalid cache table name: {table!r}"
    cursor = conn.cursor()
    cursor.execute(f"CREATE TABLE IF NOT EXISTS {table} ({columns_sql})")
    for index_sql in indexes:
        cursor.execute(index_sql)
    conn.commit()
