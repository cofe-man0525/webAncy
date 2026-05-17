import hashlib
import json
import math
import re
import sqlite3
from datetime import datetime
from pathlib import Path
from threading import Lock
from typing import Any

from app.config import get_settings


class UserVectorStore:
    def __init__(self):
        self.settings = get_settings()
        self.db_path = Path(self.settings.vector_db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = Lock()
        self._init_db()

    def add_text(self, user_id: int, source_type: str, content: str, metadata: dict[str, Any] | None = None) -> None:
        clean_content = (content or "").strip()
        if not clean_content:
            return
        vector = embed_text(clean_content)
        now = datetime.now().isoformat(timespec="seconds")
        with self._lock, sqlite3.connect(self.db_path) as connection:
            connection.execute(
                """
                INSERT INTO user_vector_memory
                (user_id, source_type, content, metadata_json, vector_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    user_id,
                    source_type,
                    clean_content[:4000],
                    json.dumps(metadata or {}, ensure_ascii=False),
                    json.dumps(vector),
                    now,
                ),
            )

    def search(self, user_id: int, query: str, top_k: int = 4, source_types: tuple[str, ...] | None = None) -> list[str]:
        clean_query = (query or "").strip()
        if not clean_query:
            return []
        query_vector = embed_text(clean_query)
        sql = "SELECT source_type, content, metadata_json, vector_json FROM user_vector_memory WHERE user_id = ?"
        params: list[Any] = [user_id]
        if source_types:
            placeholders = ",".join("?" for _ in source_types)
            sql += f" AND source_type IN ({placeholders})"
            params.extend(source_types)
        sql += " ORDER BY id DESC LIMIT 300"

        with self._lock, sqlite3.connect(self.db_path) as connection:
            rows = connection.execute(sql, params).fetchall()

        scored: list[tuple[float, str]] = []
        for source_type, content, metadata_json, vector_json in rows:
            try:
                vector = json.loads(vector_json)
            except Exception:
                continue
            score = cosine(query_vector, vector)
            if score <= 0:
                continue
            title = ""
            try:
                metadata = json.loads(metadata_json or "{}")
                title = metadata.get("title") or metadata.get("imageName") or ""
            except Exception:
                pass
            prefix = f"[{source_type}]"
            if title:
                prefix += f" {title}:"
            scored.append((score, f"{prefix} {content[:420]}"))
        scored.sort(key=lambda item: item[0], reverse=True)
        return [content for _, content in scored[:top_k]]

    def _init_db(self) -> None:
        with self._lock, sqlite3.connect(self.db_path) as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS user_vector_memory (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id INTEGER NOT NULL,
                  source_type TEXT NOT NULL,
                  content TEXT NOT NULL,
                  metadata_json TEXT,
                  vector_json TEXT NOT NULL,
                  created_at TEXT NOT NULL
                )
                """
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS idx_user_vector_memory_user_source ON user_vector_memory(user_id, source_type)"
            )


def embed_text(text: str, dimension: int = 192) -> list[float]:
    vector = [0.0] * dimension
    lowered = (text or "").lower()
    tokens = re.findall(r"[a-zA-Z0-9]+", lowered)
    tokens.extend(char for char in lowered if "\u4e00" <= char <= "\u9fff")
    tokens.extend(
        lowered[index:index + 2]
        for index in range(max(0, len(lowered) - 1))
        if "\u4e00" <= lowered[index] <= "\u9fff"
        and "\u4e00" <= lowered[index + 1] <= "\u9fff"
    )
    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % dimension
        vector[index] += 1.0 if digest[4] % 2 == 0 else -1.0
    length = math.sqrt(sum(value * value for value in vector))
    return [value / length for value in vector] if length else vector


def cosine(left: list[float], right: list[float]) -> float:
    return sum(left[index] * right[index] for index in range(min(len(left), len(right))))
