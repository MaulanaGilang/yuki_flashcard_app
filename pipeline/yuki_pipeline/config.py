"""Recipe loading and the on-disk layout shared by every stage."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

import yaml

PIPELINE_DIR = Path(__file__).resolve().parents[1]
REPO_DIR = PIPELINE_DIR.parent
WORK_DIR = PIPELINE_DIR / "work"
RAW_DIR = WORK_DIR / "raw"
PACKS_DIR = REPO_DIR / "packs"


@dataclass
class Recipe:
    path: Path
    data: dict

    @property
    def pack(self) -> dict:
        return self.data["pack"]

    @property
    def pack_id(self) -> str:
        return self.pack["id"]

    @property
    def lang(self) -> str:
        return self.pack["lang"]

    @property
    def versioned_id(self) -> str:
        return f"{self.pack_id}-v{self.pack['version']}"

    @property
    def work(self) -> Path:
        p = WORK_DIR / self.pack_id
        p.mkdir(parents=True, exist_ok=True)
        return p

    @property
    def out_dir(self) -> Path:
        return PACKS_DIR / self.versioned_id

    def section(self, name: str) -> dict:
        return self.data.get(name, {}) or {}

    def resolve(self, rel: str) -> Path:
        """Resolve a recipe-relative path (relative to pipeline/)."""
        return (PIPELINE_DIR / rel).resolve()


def load_recipe(path: str | Path) -> Recipe:
    p = Path(path)
    if not p.is_absolute():
        p = (Path.cwd() / p) if (Path.cwd() / p).exists() else PIPELINE_DIR / p
    with open(p, encoding="utf-8") as f:
        return Recipe(p, yaml.safe_load(f))


def load_env() -> dict[str, str]:
    """Read pipeline/.env (git-ignored) without overriding the real environment."""
    env = dict(os.environ)
    f = PIPELINE_DIR / ".env"
    if f.exists():
        for line in f.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env.setdefault(k.strip(), v.strip().strip('"').strip("'"))
    return env
