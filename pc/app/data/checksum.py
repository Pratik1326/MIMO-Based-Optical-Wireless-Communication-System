"""Integrity helpers for source files and byte streams."""

import hashlib
from pathlib import Path


def sha256_bytes(data: bytes) -> bytes:
    return hashlib.sha256(data).digest()


def sha256_file(path: str | Path, chunk_size: int = 1024 * 1024) -> bytes:
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(chunk_size), b""):
            digest.update(chunk)
    return digest.digest()
