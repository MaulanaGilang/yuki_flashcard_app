"""Generate FSRS-6 reference vectors from the official py-fsrs package.

The Kotlin port in android/core/scheduler replays every case and must reproduce
state, step, stability, difficulty and due exactly (floats within 1e-9 relative).

Usage:  pipeline/.venv/Scripts/python.exe pipeline/tools/fsrs_vectors.py
Output: android/core/scheduler/src/test/resources/fsrs_vectors.json
"""

from __future__ import annotations

import json
import random
from datetime import datetime, timedelta, timezone
from importlib.metadata import version
from pathlib import Path

from fsrs import Card, Rating, Scheduler

OUT = Path(__file__).resolve().parents[2] / "android/core/scheduler/src/test/resources/fsrs_vectors.json"
EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)

CONFIGS = [
    dict(desired_retention=0.9, learning_steps=[60, 600], relearning_steps=[600], maximum_interval=36500),
    dict(desired_retention=0.8, learning_steps=[60, 600], relearning_steps=[600], maximum_interval=36500),
    dict(desired_retention=0.95, learning_steps=[60], relearning_steps=[300, 1200], maximum_interval=36500),
    dict(desired_retention=0.9, learning_steps=[], relearning_steps=[], maximum_interval=36500),
    dict(desired_retention=0.9, learning_steps=[60, 600, 3600], relearning_steps=[600], maximum_interval=100),
]


def micros(dt: datetime) -> int:
    delta = dt - EPOCH
    return (delta.days * 86_400 + delta.seconds) * 1_000_000 + delta.microseconds


def main() -> None:
    rng = random.Random(20261029)
    cases = []
    for case_index in range(1200):
        cfg = CONFIGS[case_index % len(CONFIGS)]
        scheduler = Scheduler(
            desired_retention=cfg["desired_retention"],
            learning_steps=[timedelta(seconds=s) for s in cfg["learning_steps"]],
            relearning_steps=[timedelta(seconds=s) for s in cfg["relearning_steps"]],
            maximum_interval=cfg["maximum_interval"],
            enable_fuzzing=False,
        )
        now = datetime(2026, 1, 1, tzinfo=timezone.utc) + timedelta(
            seconds=rng.randrange(0, 300 * 86_400), microseconds=rng.randrange(0, 1_000_000)
        )
        card = Card(due=now)
        steps = []
        for _ in range(rng.randint(1, 14)):
            rating = rng.choices(
                [Rating.Again, Rating.Hard, Rating.Good, Rating.Easy], weights=[2, 2, 5, 1]
            )[0]
            card, _log = scheduler.review_card(card, rating, review_datetime=now)
            steps.append(
                {
                    "rating": int(rating),
                    "t": micros(now),
                    "state": int(card.state),
                    "step": card.step,
                    "stability": card.stability,
                    "difficulty": card.difficulty,
                    "due": micros(card.due),
                }
            )
            interval = card.due - now
            roll = rng.random()
            if roll < 0.15:  # early, same day
                now = now + timedelta(minutes=rng.randint(1, 180))
            elif roll < 0.35:  # early
                now = now + interval * rng.uniform(0.2, 0.95)
            elif roll < 0.85:  # on time-ish
                now = card.due + timedelta(minutes=rng.randint(0, 600))
            else:  # late
                now = now + interval * rng.uniform(1.2, 4.0)
            now = now.replace(microsecond=now.microsecond)  # keep microsecond precision
        cases.append({"config": cfg, "steps": steps})

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"py_fsrs": version("fsrs"), "cases": cases}, separators=(",", ":")), encoding="utf-8")
    print(f"wrote {len(cases)} cases to {OUT}")


if __name__ == "__main__":
    main()
