"""Importe le Katameros (base SQLite de pierresaid/katameros-api, MIT) vers les JSON embarqués.

Usage :
    python -I -X utf8 scripts/import_katameros.py [--db .import/katameros-api/KatamerosDatabase.db]

Sorties dans app/src/main/assets/katameros/ :
    lectionary.json        références des lectures, indépendantes de la langue
                           (annual MM-JJ, sundays MM-N, greatLent S-J, pentecost S-J, feasts)
    <lang>/bible.json      uniquement les versets cités par lectionary.json
    <lang>/texts.json      titres, introductions et conclusions, noms des livres, fêtes, jeûnes
    <lang>/synaxarium.json commémorations par jour copte (fr, en, ar)

Grammaire des références (identique à katameros-api) :
    "@"     sépare des lectures distinctes (ex. plusieurs prophéties)
    "*@+"   enchaîne plusieurs passages dans une même lecture
    passage "Livre.Chapitre:Versets" ; versets "1,3,5-9", "26-end", ou "26-2:7" (sur deux chapitres)
"""
from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "katameros"
FEASTS_FILE = ROOT / "scripts" / "data" / "feasts.json"

# code de langue de l'app -> (LanguageId de la base, BibleId)
LANGUAGES = {
    "fr": (1, 1),    # Louis Segond 1910 (domaine public)
    "en": (2, 2),    # NKJV (sous copyright, choix assumé : voir docs/SOURCES.md)
    "ar": (3, 11),   # Van Dyck avec diacritiques (domaine public)
    "cop": (5, 6),   # Bible copte bohaïrique (partielle)
}
SYNAXARIUM_LANGS = ("fr", "en", "ar")

OFFICE_COLUMNS = {
    "vespers": {"psalm": "V_Psalm_Ref", "gospel": "V_Gospel_Ref"},
    "matins": {"psalm": "M_Psalm_Ref", "gospel": "M_Gospel_Ref", "prophecy": "Prophecy"},
    "liturgy": {
        "pauline": "P_Gospel_Ref", "catholic": "C_Gospel_Ref", "acts": "X_Gospel_Ref",
        "psalm": "L_Psalm_Ref", "gospel": "L_Gospel_Ref",
    },
}

PASSAGE = re.compile(r"^(\d+)\.(\d+):(.+)$")
# « @ » sépare les lectures, sauf dans le séparateur de passages « *@+ ».
READING_SEPARATOR = re.compile(r"(?<!\*)@(?!\+)")


def parse_reference(ref: str) -> list[list[tuple[int, int, str]]]:
    """Découpe une référence en lectures, chacune étant une liste de passages (livre, chapitre, versets)."""
    readings = []
    for reading in READING_SEPARATOR.split(ref):
        passages = []
        for part in reading.split("*@+"):
            part = part.strip()
            if not part:
                continue
            m = PASSAGE.match(part)
            if not m:
                raise ValueError(f"Référence illisible : {part!r} dans {ref!r}")
            passages.append((int(m.group(1)), int(m.group(2)), m.group(3)))
        readings.append(passages)
    return readings


def expand_verses(book: int, chapter: int, spec: str, last_verse) -> list[tuple[int, int]]:
    """Liste des (chapitre, verset) couverts par un passage. last_verse(livre, chapitre) -> int."""
    out: list[tuple[int, int]] = []
    for item in spec.split(","):
        item = item.strip()
        if "-" not in item:
            out.append((chapter, int(item)))
            continue
        start, end = item.split("-", 1)
        start_v = int(start)
        if end == "end":
            out += [(chapter, v) for v in range(start_v, last_verse(book, chapter) + 1)]
        elif ":" in end:  # 26-2:7 : jusqu'au chapitre suivant (ou plus loin)
            end_ch, end_v = (int(x) for x in end.split(":"))
            for ch in range(chapter, end_ch + 1):
                first = start_v if ch == chapter else 1
                last = end_v if ch == end_ch else last_verse(book, ch)
                out += [(ch, v) for v in range(first, last + 1)]
        else:
            out += [(chapter, v) for v in range(start_v, int(end) + 1)]
    return out


def clean(text: str | None) -> str | None:
    if text is None:
        return None
    text = text.replace("\r\n", "\n").strip()
    return text or None


def ref_or_none(value):
    return None if value in (None, "", "None") else value


def offices(row: sqlite3.Row) -> dict:
    keys = row.keys()
    result = {}
    for office, cols in OFFICE_COLUMNS.items():
        refs = {k: ref_or_none(row[c]) for k, c in cols.items() if c in keys}
        refs = {k: v for k, v in refs.items() if v}
        if refs:
            result[office] = refs
    return result


def build_lectionary(db: sqlite3.Connection) -> dict:
    # En cas de doublon (ex. Carême semaine 7 jour 4), on garde la première ligne, comme FirstAsync() côté C#.
    lect = {"annual": {}, "sundays": {}, "greatLent": {}, "pentecost": {}}
    for r in db.execute("select * from AnnualReadings order by Month_Number, Day, Id"):
        lect["annual"].setdefault(f"{r['Month_Number']:02d}-{r['Day']:02d}", offices(r))
    for r in db.execute("select * from SundayReadings order by Month_Number, Day, Id"):
        lect["sundays"].setdefault(f"{r['Month_Number']:02d}-{r['Day']}", offices(r))
    for r in db.execute("select * from GreatLentReadings order by Week, DayOfWeek, Id"):
        lect["greatLent"].setdefault(f"{r['Week']}-{r['DayOfWeek']}", offices(r))
    for r in db.execute("select * from PentecostReadings order by Week, DayOfWeek, Id"):
        lect["pentecost"].setdefault(f"{r['Week']}-{r['DayOfWeek']}", offices(r))
    if FEASTS_FILE.exists():
        lect["feasts"] = json.loads(FEASTS_FILE.read_text(encoding="utf-8"))
    else:
        print(f"! {FEASTS_FILE.relative_to(ROOT)} absent : fêtes spéciales non incluses", file=sys.stderr)
        lect["feasts"] = {}
    return lect


def all_references(node) -> set[str]:
    """Toutes les chaînes de référence présentes dans lectionary.json (y compris les fêtes)."""
    found: set[str] = set()
    if isinstance(node, dict):
        for k, v in node.items():
            if isinstance(v, str) and k not in ("id", "kind", "type", "title"):
                if PASSAGE.match(READING_SEPARATOR.split(v)[0].split("*@+")[0]):
                    found.add(v)
            else:
                found |= all_references(v)
    elif isinstance(node, list):
        for v in node:
            found |= all_references(v)
    return found


def build_bible(db: sqlite3.Connection, bible_id: int, refs: set[str]) -> tuple[dict, int, int]:
    verses: dict[tuple[int, int, int], str] = {}
    for book, ch, num, text in db.execute(
        "select BookId, Chapter, Number, Text from Verses where BibleId = ?", (bible_id,)
    ):
        verses[(book, ch, num)] = " ".join(text.split())
    last = {}
    for (book, ch, num) in verses:
        last[(book, ch)] = max(last.get((book, ch), 0), num)

    def last_verse(book, ch):
        return last.get((book, ch), 0)

    wanted: set[tuple[int, int, int]] = set()
    for ref in refs:
        for reading in parse_reference(ref):
            for book, ch, spec in reading:
                for c, v in expand_verses(book, ch, spec, last_verse):
                    wanted.add((book, c, v))
    bible: dict = {}
    missing = 0
    for book, ch, v in sorted(wanted):
        text = verses.get((book, ch, v))
        if text is None:
            missing += 1
            continue
        bible.setdefault(str(book), {}).setdefault(str(ch), {})[str(v)] = text
    return bible, len(wanted), missing


def build_texts(db: sqlite3.Connection, lang_id: int) -> dict:
    one = lambda q, *a: {str(k): clean(v) for k, v in db.execute(q, a)}
    texts = {
        "books": one("select BookId, Text from BooksTranslations where LanguageId = ?", lang_id),
        "feasts": one("select f.Name, t.Text from FeastsTranslations t join Feasts f on f.Id = t.FeastId where t.LanguageId = ?", lang_id),
        "fasts": {},
        "readings": {},
        "sections": {},
        "subSections": {},
    }
    for name, text, desc in db.execute(
        "select f.Name, t.Text, t.Description from FastsTranslations t join Fasts f on f.Id = t.FastId where t.LanguageId = ?",
        (lang_id,),
    ):
        texts["fasts"][name] = {"title": clean(text), "description": clean(desc)}
    meta_names = {1: "title", 2: "introduction", 3: "conclusion"}
    for rid, mid, text in db.execute(
        "select ReadingId, ReadingsMetadatasId, Text from ReadingsMetadatasTranslations where LanguageId = ?", (lang_id,)
    ):
        texts["readings"].setdefault(str(rid), {})[meta_names.get(mid, str(mid))] = clean(text)
    for sid, text in db.execute(
        "select SectionsId, Text from SectionsMetadatasTranslations where LanguageId = ?", (lang_id,)
    ):
        texts["sections"][str(sid)] = clean(text)
    sub_names = {1: "title", 2: "introduction"}
    for sid, mid, text in db.execute(
        "select SubSectionsId, SubSectionsMetadatasId, Text from SubSectionsMetadatasTranslations where LanguageId = ?",
        (lang_id,),
    ):
        texts["subSections"].setdefault(str(sid), {})[sub_names.get(mid, str(mid))] = clean(text)
    return texts


def build_synaxarium(db: sqlite3.Connection, lang_id: int) -> dict:
    days: dict = {}
    for month, day, title, text, story in db.execute(
        'select Month, Day, Title, Text, StoryId from Synaxarium where LanguageId = ? order by Month, Day, "Order"',
        (lang_id,),
    ):
        days.setdefault(f"{month:02d}-{day:02d}", []).append(
            {"id": str(story) if story is not None else None, "title": clean(title), "html": clean(text)}
        )
    return days


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", default=str(ROOT / ".import" / "katameros-api" / "KatamerosDatabase.db"))
    args = parser.parse_args()
    db = sqlite3.connect(f"file:{Path(args.db).as_posix()}?mode=ro", uri=True)
    db.row_factory = sqlite3.Row

    lectionary = build_lectionary(db)
    write_json(ASSETS / "lectionary.json", lectionary)
    refs = all_references(lectionary)
    print(f"lectionary.json : {len(lectionary['annual'])} jours, {len(lectionary['sundays'])} dimanches, "
          f"{len(lectionary['greatLent'])} jours de Carême, {len(lectionary['pentecost'])} jours de Pentecôte, "
          f"{len(lectionary['feasts'])} fêtes ; {len(refs)} références")

    for code, (lang_id, bible_id) in LANGUAGES.items():
        bible, wanted, missing = build_bible(db, bible_id, refs)
        write_json(ASSETS / code / "bible.json", bible)
        write_json(ASSETS / code / "texts.json", build_texts(db, lang_id))
        line = f"{code}: {wanted - missing}/{wanted} versets"
        if code in SYNAXARIUM_LANGS:
            syn = build_synaxarium(db, lang_id)
            write_json(ASSETS / code / "synaxarium.json", syn)
            line += f", synaxaire {len(syn)} jours / {sum(len(v) for v in syn.values())} commémorations"
        print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
