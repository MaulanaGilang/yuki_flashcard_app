"""Turn the official word lists that only exist as PDFs or spreadsheets into clean TSV tables.

Reads `work/prefetch/` (see config/sources.md) and writes `work/prefetch/wordlists/`:
  de_goethe.tsv   headword, article, plural, forms, level (A1/A2/B1), source
  ru_trki_b2.tsv  headword (stress as U+0301), plain, aspect, pair, level (B2 = "at most B2"), source
  ru_kelly.tsv    headword, pos, level, ipm, source
  ar_kelly.tsv    headword (unvowelled), pos, level, ipm, source

The level rule when lists overlap is the engine's job (ТРКИ beats KELLY; a lower level wins).

Needs pdfplumber and xlrd:
    python -m pip install pdfplumber==0.11.4 xlrd==2.0.1
    python -X utf8 tools/extract_wordlists.py
"""

from __future__ import annotations

import csv
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREFETCH = ROOT / "work" / "prefetch"
OUT = PREFETCH / "wordlists"

ARTICLES = ("der", "die", "das")


# ---------- German: Goethe-Zertifikat A1 / A2 / B1 ----------

# (level, file, entry columns per page): the A1 list is one column, A2 and B1 are two.
GOETHE = [
    ("A1", "goethe/A1_SD1_Wortliste_02.pdf", 1),
    ("A2", "goethe/Goethe-Zertifikat_A2_Wortliste.pdf", 2),
    ("B1", "goethe/Goethe-Zertifikat_B1_Wortliste.pdf", 2),
]
# Headwords sit in the left band of each column; example sentences start further right.
HEAD_BAND = 0.36


def goethe_lines(path: Path, columns: int):
    """Yield the headword-band text of every line in the alphabetical part, column by column."""
    import pdfplumber

    with pdfplumber.open(path) as pdf:
        started = False
        for page in pdf.pages:
            text = page.extract_text() or ""
            if started and re.search(r"LITERATUR|LITerATur|Literaturverzeichnis", text[:300]):
                break  # bibliography after the list
            if not started:
                # The alphabetical list starts after the thematic word groups.
                if re.search(r"alphabetische|Alphabetische|ALPHABETISCHE", text) or re.search(r"^ab\b", text, re.M):
                    started = True
                else:
                    continue
            w = page.width
            spans = ((0, w),) if columns == 1 else ((0, w / 2), (w / 2, w))
            for x0, x1 in spans:
                col = page.crop((x0, 0, x1, page.height))
                words = col.extract_words(keep_blank_chars=False, use_text_flow=True)
                rows: dict[int, list] = {}
                for word in words:
                    rows.setdefault(round(word["top"] / 3), []).append(word)
                for key in sorted(rows):
                    line = sorted(rows[key], key=lambda t: t["x0"])
                    band = [t["text"] for t in line if t["x0"] < x0 + (x1 - x0) * HEAD_BAND]
                    if band:
                        yield " ".join(band)


NOUN = re.compile(r"^(der|die|das)(?:/(?:der|die|das))?\s+([A-ZÄÖÜ][\wäöüß\-]*)(?:,\s*(-\S*|\(Pl\.\)|\(Sg\.\)|=|¨\S*))?")


def head_tokens(line: str) -> str:
    """Leading lowercase tokens: the headword, before the example sentence (which starts with a capital)."""
    out = []
    for tok in line.split():
        if out and (tok[0].isupper() or tok[0] in "–-—\"„"):
            break
        out.append(tok)
        if tok.endswith(","):
            break
    return " ".join(out)


def parse_goethe(level: str, path: Path, columns: int) -> list[dict]:
    entries: list[dict] = []
    current: dict | None = None
    for raw in goethe_lines(path, columns):
        line = raw.strip()
        if not line or re.fullmatch(r"[\dA-ZÄÖÜ\W]{1,3}", line) or "WORTLISTE" in line or re.search(r"\d{6}", line):
            continue
        continuing = current is not None and current["_open"]
        if continuing:
            current["forms"].append(line.rstrip(","))
            current["_open"] = line.endswith(",")
            continue
        m = NOUN.match(line)
        if m:
            current = {"headword": f"{m.group(1)} {m.group(2)}", "article": m.group(1), "plural": m.group(3) or "", "forms": [], "_open": False}
        else:
            first = head_tokens(line)
            head = first.split(",")[0].strip()
            if not re.match(r"^[a-zäöüß(][\wäöüß()\s/\-]*$", head) or len(head) > 30 or len(head) < 2:
                continue
            current = {"headword": head, "article": "", "plural": "", "forms": [], "_open": first.endswith(",")}
        entries.append(current)
    out, seen = [], set()
    for e in entries:
        if e["headword"] in seen:
            continue
        seen.add(e["headword"])
        out.append({"headword": e["headword"], "article": e["article"], "plural": e["plural"],
                    "forms": "; ".join(f for f in e["forms"] if f), "level": level, "source": "goethe"})
    return out


# ---------- Russian: ТРКИ-2 lexical minimum (cumulative up to B2) ----------

VOWELS = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"


def fix_stress(s: str) -> str:
    """The PDF's font stores the stress mark as a Latin 'k' after the vowel: веkсить -> ве́сить."""
    return re.sub(rf"([{VOWELS}])k", "\\1\u0301", s)


def plain(s: str) -> str:
    return unicodedata.normalize("NFC", s.replace("\u0301", ""))


TRKI_HEAD = re.compile(r"^([а-яё][а-яё\u0301\-]*(?:\s(?:себя|ся))?)(?=[,;\s(]|$)")


def parse_trki_b2(path: Path) -> list[dict]:
    import pdfplumber

    out, seen = [], set()
    with pdfplumber.open(path) as pdf:
        for page in pdf.pages[6:]:
            left = page.crop((0, 0, page.width * 0.62, page.height)).extract_text() or ""
            for raw in left.splitlines():
                line = fix_stress(raw.strip())
                if not line or line[0].isdigit() or line.startswith(("—", "(", "Лексический")):
                    continue
                m = TRKI_HEAD.match(line)
                if not m:
                    continue
                head = m.group(1)
                if len(plain(head)) < 2 or plain(head) in seen:
                    continue
                aspect = "impf." if " нсв" in line else ("pf." if " св" in line else "")
                pair = ""
                pm = re.search(r"—\s*([а-яё\u0301]+)[^—]*\bсв\b", line)
                if aspect == "impf." and pm:
                    pair = pm.group(1)
                seen.add(plain(head))
                out.append({"headword": head, "plain": plain(head), "aspect": aspect, "pair": pair, "level": "B2", "source": "trki2"})
    return out


# ---------- KELLY (Russian, Arabic) ----------

AR_POS = {"اسم": "n", "صفة": "adj", "فعل": "v", "ظرف": "adv", "حرف": "particle", "ضمير": "pron", "عدد": "num"}


def parse_kelly(path: Path, lang: str) -> list[dict]:
    import xlrd

    sheet = xlrd.open_workbook(str(path)).sheet_by_index(0)
    out = []
    for r in range(1, sheet.nrows):
        row = [str(sheet.cell_value(r, c)).strip() for c in range(sheet.ncols)]
        if lang == "ru":
            head, level, pos, ipm = row[0], row[1].upper(), row[2], row[4]
        else:
            head = row[3] or row[4]  # corrected word, else the original
            level, pos, ipm = row[5].upper(), AR_POS.get(row[2], row[2]), row[6]
        if head and level in {"A1", "A2", "B1", "B2", "C1", "C2"}:
            out.append({"headword": head, "pos": pos, "level": level, "ipm": ipm, "source": "kelly"})
    return out


def write(name: str, rows: list[dict]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]), delimiter="\t")
        w.writeheader()
        w.writerows(rows)
    counts: dict[str, int] = {}
    for r in rows:
        counts[r["level"]] = counts.get(r["level"], 0) + 1
    print(f"{name}: {len(rows)} rows {dict(sorted(counts.items()))}")


def main() -> None:
    only = set(sys.argv[1:])
    if not only or "de" in only:
        rows = []
        for level, rel, columns in GOETHE:
            rows += parse_goethe(level, PREFETCH / rel, columns)
        # A word keeps its lowest Goethe level.
        best: dict[str, dict] = {}
        for r in rows:
            best.setdefault(r["headword"], r)
        write("de_goethe.tsv", list(best.values()))
    if not only or "ru" in only:
        write("ru_trki_b2.tsv", parse_trki_b2(PREFETCH / "trki/trki_b2_leksicheskii_minimum.pdf"))
        write("ru_kelly.tsv", parse_kelly(PREFETCH / "kelly/ru_m3.xls", "ru"))
    if not only or "ar" in only:
        write("ar_kelly.tsv", parse_kelly(PREFETCH / "kelly/ar_m3.xls", "ar"))


if __name__ == "__main__":
    main()
