"""Capture la structure des fêtes « construites » en dur dans katameros-api (Factories/Feasts/*.cs).

Ces fêtes (Semaine sainte, Croix, Ninive, Nativité…) ont une structure fixe : seul leur déclenchement dépend
de la date (porté en Kotlin dans core-liturgy). On appelle donc l'API publique une fois par fête, à une date
représentative, et on garde uniquement la structure et les références, sans aucun texte.

    python -I -X utf8 scripts/capture_feasts.py     # écrit scripts/data/feasts.json (à committer)

Les dates représentatives viennent de l'analyse du code C# (ordre de déclenchement compris).
"""
from __future__ import annotations

import json
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "scripts" / "data" / "feasts.json"
API = "https://api.katameros.app/readings/gregorian/{date}?languageId=1"

# clé stable de la fête -> date grégorienne (jj-mm-aaaa) qui la déclenche
FEASTS = {
    "christmas": "07-01-2026",
    "theophany_paramoun": "18-01-2026",
    "temple_entrance": "15-02-2025",
    "lent_preparation_saturday": "14-02-2026",
    "lent_preparation_sunday": "23-02-2025",
    "nineveh_1": "02-02-2026",
    "nineveh_2": "03-02-2026",
    "nineveh_3": "04-02-2026",
    "jonah": "05-02-2026",
    "annunciation": "07-04-2025",
    "month_29": "08-11-2026",
    "lazarus_saturday": "04-04-2026",
    "palm_sunday": "05-04-2026",
    "pascha_monday": "06-04-2026",
    "pascha_tuesday": "07-04-2026",
    "pascha_wednesday": "08-04-2026",
    "pascha_thursday": "09-04-2026",
    "pascha_friday": "10-04-2026",
    "pascha_saturday": "11-04-2026",
    "easter": "12-04-2026",
    "egypt_entrance": "01-06-2026",
    "cross_1": "27-09-2026",
    "cross_2": "28-09-2026",
    "cross_3": "29-09-2026",
}

SYNAXARIUM_SUBSECTION = 10


def fetch(date: str) -> dict:
    req = urllib.request.Request(API.format(date=date), headers={"User-Agent": "KatamerosCopte-import/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def passage_ref(p: dict) -> str:
    return f"{p['bookId']}.{p['ref']}"


def structure(day: dict) -> dict:
    sections = []
    for s in day.get("sections") or []:
        subs = []
        for sub in s.get("subSections") or []:
            if sub.get("id") == SYNAXARIUM_SUBSECTION:
                subs.append({"id": SYNAXARIUM_SUBSECTION, "synaxarium": True})
                continue
            readings = []
            for r in sub.get("readings") or []:
                passages = r.get("passages") or []
                if not passages:
                    continue
                readings.append({
                    "type": r["id"],
                    "ref": "*@+".join(passage_ref(p) for p in passages),
                    # Mode « simple » (sans introduction ni conclusion), ex. les 4 Évangiles du Vendredi saint.
                    "simple": not r.get("introduction") and not r.get("conclusion"),
                })
            entry = {"id": sub["id"], "readings": readings}
            if sub.get("introduction"):
                entry["introduction"] = True
            subs.append(entry)
        sections.append({"id": s["id"], "subSections": subs})
    return {"sections": sections}


def main() -> int:
    result = {}
    for key, date in FEASTS.items():
        day = fetch(date)
        result[key] = {"sampleDate": date, "title": day.get("title"), **structure(day)}
        print(f"{key:28s} {date}  {day.get('title')!s:40.40s} {len(result[key]['sections'])} sections")
        time.sleep(1)  # rester discret avec l'API publique
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    print(f"-> {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
