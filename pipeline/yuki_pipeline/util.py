"""Small helpers shared by the stages: hashing, JSON caches, logging, ffmpeg."""

from __future__ import annotations

import hashlib
import json
import logging
import os
import sys
import tempfile
from pathlib import Path
from typing import Any

log = logging.getLogger("yuki")


def setup_logging(verbose: bool = False) -> None:
    logging.basicConfig(
        level=logging.DEBUG if verbose else logging.INFO,
        format="%(asctime)s %(levelname)-5s %(message)s",
        datefmt="%H:%M:%S",
        stream=sys.stdout,
    )
    # Windows consoles default to a legacy code page; make hanzi printable.
    try:
        sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
    except Exception:
        pass


def sha256_file(path: Path, chunk: int = 1 << 20) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while b := f.read(chunk):
            h.update(b)
    return h.hexdigest()


def sha1_text(*parts: str) -> str:
    return hashlib.sha1("\x1f".join(parts).encode("utf-8")).hexdigest()


def read_json(path: Path, default: Any = None) -> Any:
    if not path.exists():
        return default
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path: Path, data: Any, indent: int | None = 1) -> None:
    """Atomic write so an interrupted run never leaves a half-written cache."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, suffix=".tmp")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
    os.replace(tmp, path)


class JsonCache:
    """A dict persisted to one JSON file, flushed every `every` updates."""

    def __init__(self, path: Path, every: int = 50):
        self.path = path
        self.every = every
        self.data: dict = read_json(path, {}) or {}
        self._dirty = 0

    def get(self, key: str, default: Any = None) -> Any:
        return self.data.get(key, default)

    def __contains__(self, key: str) -> bool:
        return key in self.data

    def set(self, key: str, value: Any) -> None:
        self.data[key] = value
        self._dirty += 1
        if self._dirty >= self.every:
            self.flush()

    def flush(self) -> None:
        if self._dirty:
            write_json(self.path, self.data)
            self._dirty = 0


def ffmpeg_exe() -> str:
    """ffmpeg from PATH if present, else the static build shipped by imageio-ffmpeg."""
    from shutil import which

    exe = which("ffmpeg")
    if exe:
        return exe
    import imageio_ffmpeg

    return imageio_ffmpeg.get_ffmpeg_exe()
