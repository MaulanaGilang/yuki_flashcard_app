"""Run: .venv/Scripts/python.exe -m pytest tests"""

import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from yuki_pipeline.zh import (  # noqa: E402
    normalize_search, numeric_to_marked, search_key, segment_marked, syllables_from,
)


def _dev_normalize():
    spec = importlib.util.spec_from_file_location("build_dev_pack", ROOT / "tools" / "build_dev_pack.py")
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)
    except ImportError as e:  # edge_tts / PIL missing
        pytest.skip(str(e))
    return mod.normalize


@pytest.mark.parametrize("text", ["Píngguǒ", "nǚ'ér lǜsè", "молоко́ ёлка й", "la canción ñandú", "تُفَّاحَة", "苹果 APPLE"])
def test_normalize_matches_app_contract(text):
    assert normalize_search(text) == _dev_normalize()(text)


def test_search_key_layout():
    assert search_key("苹果", ["píng", "guǒ"], [2, 3], ["apple"]) == "苹果 pingguo ping guo ping2guo3 apple"
    assert search_key("女儿", ["nǚ", "ér"], [3, 2], ["daughter"]) == "女儿 nuer nu er nu3er2 daughter"


@pytest.mark.parametrize("num,marked", [("guo3", "guǒ"), ("lv4", "lǜ"), ("nu:3", "nǚ"), ("ma5", "ma"), ("r5", "r"),
                                        ("liu2", "liú"), ("gui4", "guì"), ("Zhong1", "Zhōng")])
def test_numeric_to_marked(num, marked):
    assert numeric_to_marked(num) == marked


def test_syllables_from_cedict_candidate():
    assert syllables_from("苹果", [["ping2", "guo3"]], "píngguǒ") == (["píng", "guǒ"], [2, 3])


@pytest.mark.parametrize("word,pinyin,expected", [
    ("见过", "jiànguo", ["jiàn", "guo"]),
    ("能不能", "néng bu néng", ["néng", "bu", "néng"]),
    ("女儿", "nǚ'ér", ["nǚ", "ér"]),
    ("一会儿", "yīhuìr", ["yī", "huì", "r"]),
    ("西安", "Xī'ān", ["xī", "ān"]),
])
def test_segment_marked(word, pinyin, expected):
    syl, tones = segment_marked(word, pinyin)
    assert syl == expected
    assert len(tones) == len(word)
