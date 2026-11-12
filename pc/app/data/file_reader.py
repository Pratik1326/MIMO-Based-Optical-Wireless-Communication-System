"""Streaming file input with precomputed SHA-256 metadata."""

from dataclasses import dataclass
from pathlib import Path

from .checksum import sha256_file


@dataclass(frozen=True, slots=True)
class FileInfo:
    path: Path
    size: int
    sha256: bytes


def inspect_file(path: str | Path) -> FileInfo:
    source = Path(path)
    return FileInfo(source, source.stat().st_size, sha256_file(source))


def read_chunks(path: str | Path, chunk_size: int):
    if chunk_size <= 0:
        raise ValueError("chunk_size must be positive")
    with Path(path).open("rb") as source:
        yield from iter(lambda: source.read(chunk_size), b"")
