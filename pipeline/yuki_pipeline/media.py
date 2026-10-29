"""Stage 4 — media: audio (always) and images (Fluent Emoji now, FLUX batch as quota allows)."""

from __future__ import annotations

from .config import Recipe


def run(recipe: Recipe, only: str | None = None, image_budget: int | None = None, limit: int | None = None) -> None:
    if only in (None, "audio"):
        from . import audio
        audio.run(recipe, limit=limit)
    if only in (None, "images"):
        from . import images
        images.run(recipe, budget=image_budget, limit=limit)
