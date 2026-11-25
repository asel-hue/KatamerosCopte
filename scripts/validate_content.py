"""Valide le contenu Katameros embarqué et tient à jour son manifest (sha256 et tailles).

    python -I -X utf8 scripts/validate_content.py           # vérifie seulement (CI) ; code 1 si erreur ou manifest périmé
    python -I -X utf8 scripts/validate_content.py --write   # vérifie, puis réécrit manifest.json (contentVersion + 1)

Erreurs bloquantes : JSON invalide ou avec BOM, jour copte manquant, référence illisible, fête mal formée,
synaxaire incomplet, manifest périmé. Avertissements : versets absents de la Bible source (lacunes connues,
comme Daniel 14 ou des différences de versification), écarts de nombre de commémorations entre langues.
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from import_katameros import ASSETS, LANGUAGES, SYNAXARIUM_LANGS, all_references, expand_verses, parse_reference  # noqa: E402

MANIFEST = ASSETS / "manifest.json"
SCHEMA_VERSION = 1
LANGUAGE_INFO = {
    "fr": {"name": "Français", "rtl": False},
    "ar": {"name": "العربية", "rtl": True},
    "en": {"name": "English", "rtl": False},
    "cop": {"name": "ⲘⲉⲧⲢⲉⲙⲛ̀ⲭⲏⲙⲓ", "rtl": False},
}

errors: list[str] = []
warnings: list[str] = []


def load(path: Path):
    raw = path.read_bytes()
    if raw.startswith(b"\xef\xbb\xbf"):
        errors.append(f"{path.relative_to(ASSETS)} : BOM UTF-8 interdit")
    try:
        return json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        errors.append(f"{path.relative_to(ASSETS)} : JSON invalide ({e})")
        return None


def coptic_keys() -> list[str]:
    return [f"{m:02d}-{d:02d}" for m in range(1, 14) for d in range(1, (6 if m == 13 else 30) + 1)]


def check_lectionary(lect: dict) -> set[str]:
    for part in ("annual", "sundays", "greatLent", "pentecost", "feasts"):
        if part not in lect:
            errors.append(f"lectionary.json : partie « {part} » absente")
    missing = [k for k in coptic_keys() if k not in lect.get("annual", {})]
    if missing:
        errors.append(f"lectionary.json : jours annuels manquants {missing[:10]}")
    for key, feast in lect.get("feasts", {}).items():
        if not isinstance(feast, dict) or not isinstance(feast.get("sections"), list) or not feast["sections"]:
            errors.append(f"lectionary.json : fête « {key} » sans sections")
    refs = all_references(lect)
    for ref in sorted(refs):
        try:
            parse_reference(ref)
        except ValueError as e:
            errors.append(f"lectionary.json : {e}")
    return refs


def check_bible(code: str, bible: dict, refs: set[str]) -> None:
    def last_verse(book, ch):
        verses = bible.get(str(book), {}).get(str(ch), {})
        return max((int(v) for v in verses), default=0)

    absent = 0
    for ref in refs:
        try:
            readings = parse_reference(ref)
        except ValueError:
            continue
        for reading in readings:
            for book, ch, spec in reading:
                for c, v in expand_verses(book, ch, spec, last_verse):
                    if str(v) not in bible.get(str(book), {}).get(str(c), {}):
                        absent += 1
    if absent:
        warnings.append(f"{code}/bible.json : {absent} versets cités absents de la Bible source")


def check_synaxarium(code: str, syn: dict) -> dict[str, int]:
    missing = [k for k in coptic_keys() if k not in syn]
    if missing:
        errors.append(f"{code}/synaxarium.json : jours manquants {missing[:10]}")
    for key, items in syn.items():
        for item in items:
            if not item.get("title") or not item.get("html"):
                errors.append(f"{code}/synaxarium.json : commémoration incomplète le {key}")
    return {k: len(v) for k, v in syn.items()}


def file_entries() -> dict:
    files = {}
    for path in sorted(ASSETS.rglob("*.json")):
        if path == MANIFEST:
            continue
        data = path.read_bytes()
        files[path.relative_to(ASSETS).as_posix()] = {"sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}
    return files


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="réécrire manifest.json")
    args = parser.parse_args()

    lect = load(ASSETS / "lectionary.json")
    refs = check_lectionary(lect) if lect else set()
    counts = {}
    for code in LANGUAGES:
        for name in ("bible.json", "texts.json"):
            if not (ASSETS / code / name).exists():
                errors.append(f"{code}/{name} manquant")
        bible = load(ASSETS / code / "bible.json") if (ASSETS / code / "bible.json").exists() else None
        if bible is not None:
            check_bible(code, bible, refs)
        if code in SYNAXARIUM_LANGS and (ASSETS / code / "synaxarium.json").exists():
            syn = load(ASSETS / code / "synaxarium.json")
            if syn is not None:
                counts[code] = check_synaxarium(code, syn)
    if "fr" in counts:
        for code, per_day in counts.items():
            diff = [k for k in per_day if per_day[k] != counts["fr"].get(k)]
            if diff:
                warnings.append(f"{code}/synaxarium.json : {len(diff)} jours avec un nombre de commémorations différent du français")

    files = file_entries()
    current = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {}
    stale = current.get("files") != files

    for w in warnings:
        print(f"avertissement : {w}")
    for e in errors:
        print(f"ERREUR : {e}")
    if errors:
        return 1

    if args.write:
        manifest = {
            "schemaVersion": SCHEMA_VERSION,
            "contentVersion": current.get("contentVersion", 0) + (1 if stale else 0),
            "updatedAt": datetime.date.today().isoformat() if stale else current.get("updatedAt"),
            "languages": [{"code": c, **LANGUAGE_INFO[c]} for c in LANGUAGES],
            "files": files,
        }
        MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"manifest.json écrit (contentVersion {manifest['contentVersion']}, {len(files)} fichiers)")
        return 0
    if stale:
        print("ERREUR : manifest.json périmé, lancer scripts/validate_content.py --write")
        return 1
    print(f"Contenu valide ({len(files)} fichiers).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
