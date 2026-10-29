"""Check glyph coverage of the UI fonts and subset LXGW WenKai GB for the app.

DESIGN-SYSTEM §3 glyph checks: pinyin tone vowels, Russian stress mark, Spanish marks.
The hanzi face is subset to GB2312 level-1 (3,755 most common characters, which covers
HSK 1-6) plus any extra characters passed via --extra-chars-file (e.g. from built packs).

Usage: python subset_fonts.py <download_dir> <android_res_font_dir> [--extra-chars-file f]
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

PINYIN = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüĀÁǍÀĒÉĚÈŌÓǑÒ"
RUSSIAN = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ́"
SPANISH = "ñÑ¿¡áéíóúüÁÉÍÓÚÜ"


def missing(font: Path, chars: str) -> list[str]:
    cmap = TTFont(font).getBestCmap()
    return [c for c in chars if ord(c) not in cmap]


def gb2312_level1() -> str:
    out = []
    for hi in range(0xB0, 0xD8):
        for lo in range(0xA1, 0xFF):
            try:
                out.append(bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    return "".join(out)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("download_dir", type=Path)
    ap.add_argument("res_font_dir", type=Path)
    ap.add_argument("--extra-chars-file", type=Path)
    a = ap.parse_args()
    a.res_font_dir.mkdir(parents=True, exist_ok=True)

    for name, chars in [("Onest.ttf", PINYIN + RUSSIAN + SPANISH), ("Unbounded.ttf", RUSSIAN.replace("́", "") + SPANISH)]:
        gaps = missing(a.download_dir / name, chars)
        print(f"{name}: {'all glyphs present' if not gaps else 'MISSING ' + ''.join(gaps)}")

    shutil.copy(a.download_dir / "Onest.ttf", a.res_font_dir / "onest_variable.ttf")
    shutil.copy(a.download_dir / "Unbounded.ttf", a.res_font_dir / "unbounded_variable.ttf")

    hanzi = gb2312_level1()
    if a.extra_chars_file and a.extra_chars_file.exists():
        hanzi += a.extra_chars_file.read_text(encoding="utf-8")
    text = hanzi + "，。！？、；：“”‘’（）《》…—·0123456789" + PINYIN
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    font = TTFont(a.download_dir / "WenKaiGB.ttf")
    sub = subset.Subsetter(opts)
    sub.populate(text=text)
    sub.subset(font)
    out = a.res_font_dir / "lxgw_wenkai_gb.ttf"
    font.save(out)
    print(f"WenKai GB subset: {len(set(text))} chars -> {out.stat().st_size / 1e6:.2f} MB")


if __name__ == "__main__":
    main()
