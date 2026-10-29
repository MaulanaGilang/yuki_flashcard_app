"""CLI: python -m yuki_pipeline <stage> [--recipe config/zh.yaml]

Stages: fetch, normalize, enrich, media, evaluate, package, all.
`media` takes --only audio|images and --image-budget N (the daily FLUX batch).
"""

from __future__ import annotations

import argparse
import sys

from .config import load_recipe
from .util import setup_logging

STAGES = ["fetch", "normalize", "enrich", "media", "evaluate", "package"]


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="yuki_pipeline")
    p.add_argument("stage", choices=STAGES + ["all", "images"])
    p.add_argument("--recipe", default="config/zh.yaml")
    p.add_argument("--refresh", action="store_true", help="fetch: re-download sources")
    p.add_argument("--source", action="append", help="fetch: only these source names")
    p.add_argument("--only", choices=["audio", "images"], help="media: run one half")
    p.add_argument("--image-budget", type=int, default=None,
                   help="images: max FLUX generations this run (default: recipe daily budget)")
    p.add_argument("--suggest-fluent", action="store_true", help="images: write Fluent Emoji match candidates")
    p.add_argument("--no-asr", action="store_true", help="evaluate: skip the Whisper ASR check")
    p.add_argument("--force", action="store_true", help="package: build even without a passing evaluate report")
    p.add_argument("--limit", type=int, default=None, help="debug: process only N words")
    p.add_argument("-v", "--verbose", action="store_true")
    a = p.parse_args(argv)
    setup_logging(a.verbose)
    recipe = load_recipe(a.recipe)

    stages = STAGES if a.stage == "all" else [a.stage]
    for s in stages:
        if s == "fetch":
            from . import fetch
            fetch.run(recipe, refresh=a.refresh, only=a.source)
        elif s == "normalize":
            from . import normalize
            normalize.run(recipe)
        elif s == "enrich":
            from . import enrich
            enrich.run(recipe)
        elif s == "media":
            from . import media
            media.run(recipe, only=a.only, image_budget=a.image_budget, limit=a.limit)
        elif s == "images":
            from . import images
            images.run(recipe, budget=a.image_budget, limit=a.limit, suggest=a.suggest_fluent)
        elif s == "evaluate":
            from . import evaluate
            ok = evaluate.run(recipe, asr=not a.no_asr)
            if not ok:
                return 1
        elif s == "package":
            from . import package
            package.run(recipe, force=a.force)
    return 0


if __name__ == "__main__":
    sys.exit(main())
