"""Stage 1 — fetch: download pinned sources into work/raw/.

Idempotent: a file listed in work/raw/fetch.lock.json is not downloaded again unless
`--refresh` is given. The lock records url, sha256 and size of every file fetched.
"""

from __future__ import annotations

import bz2
import shutil
import subprocess
import tarfile
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path

import requests

from .config import RAW_DIR, Recipe
from .util import log, read_json, sha256_file, write_json

LOCK = RAW_DIR / "fetch.lock.json"
UA = {"User-Agent": "yuki-pipeline/0.1 (personal offline flashcards)"}


def _download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    for attempt in range(4):
        try:
            with requests.get(url, headers=UA, stream=True, timeout=60) as r:
                r.raise_for_status()
                with open(tmp, "wb") as f:
                    for chunk in r.iter_content(1 << 20):
                        f.write(chunk)
            tmp.replace(dest)
            return
        except requests.RequestException as e:
            log.warning("download failed (%s), retry %d: %s", url, attempt + 1, e)
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"could not download {url}")


def _safe_members(tar: tarfile.TarFile, root: Path):
    for m in tar.getmembers():
        target = (root / m.name).resolve()
        if root.resolve() not in target.parents and target != root.resolve():
            raise RuntimeError(f"unsafe path in archive: {m.name}")
        if m.issym() or m.islnk():
            continue
        yield m


def _extract(path: Path, kind: str) -> None:
    out = path.parent
    if kind == "zip":
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                target = (out / n).resolve()
                if out.resolve() not in target.parents:
                    raise RuntimeError(f"unsafe path in zip: {n}")
            z.extractall(out)
    elif kind == "bz2":
        target = path.with_suffix("")
        with bz2.open(path, "rb") as src, open(target, "wb") as dst:
            shutil.copyfileobj(src, dst, 1 << 20)
    elif kind == "tar":
        with tarfile.open(path, "r:*") as t:
            t.extractall(out, members=list(_safe_members(t, out)))
    else:
        raise ValueError(kind)


def _git(*args: str, cwd: Path | None = None) -> str:
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8")
    if r.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def _git_sparse(src: dict, dest: Path) -> None:
    if not (dest / ".git").exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        _git("clone", "--filter=blob:none", "--no-checkout", "--sparse", src["repo"], str(dest))
    _git("sparse-checkout", "set", "--no-cone", *src["patterns"], cwd=dest)
    _git("-c", "advice.detachedHead=false", "checkout", src["commit"], cwd=dest)


def run(recipe: Recipe, refresh: bool = False, only: list[str] | None = None) -> None:
    lock = read_json(LOCK, {}) or {}
    for name, src in recipe.data["sources"].items():
        if only and name not in only:
            continue
        if src["kind"] == "http":
            for f in src["files"]:
                dest = RAW_DIR / f["dest"]
                key = f["dest"]
                if not refresh and key in lock and dest.exists():
                    log.info("fetch %-12s cached  %s", name, key)
                    continue
                log.info("fetch %-12s GET     %s", name, f["url"])
                _download(f["url"], dest)
                digest = sha256_file(dest)
                if f.get("sha256") and f["sha256"] != digest:
                    raise RuntimeError(f"sha256 mismatch for {key}: {digest}")
                if f.get("extract"):
                    _extract(dest, f["extract"])
                lock[key] = {
                    "source": name, "url": f["url"], "sha256": digest,
                    "bytes": dest.stat().st_size, "license": src.get("license"),
                    "fetched_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                }
                write_json(LOCK, lock)
        elif src["kind"] == "git_sparse":
            dest = RAW_DIR / src["dest"]
            key = src["dest"]
            prev = lock.get(key, {})
            if (not refresh and prev.get("commit") == src["commit"]
                    and prev.get("patterns") == src["patterns"] and dest.exists()):
                log.info("fetch %-12s cached  %s@%s", name, src["repo"], src["commit"][:8])
                continue
            log.info("fetch %-12s clone   %s@%s (sparse)", name, src["repo"], src["commit"][:8])
            _git_sparse(src, dest)
            lock[key] = {
                "source": name, "url": src["repo"], "commit": src["commit"],
                "patterns": src["patterns"], "license": src.get("license"),
                "fetched_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            }
            write_json(LOCK, lock)
        else:
            raise ValueError(f"unknown source kind {src['kind']}")
    log.info("fetch done: %d entries in %s", len(lock), LOCK)
