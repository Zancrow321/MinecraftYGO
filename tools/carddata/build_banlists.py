#!/usr/bin/env python3
"""Builds the history of TCG Forbidden & Limited Lists for progression worlds, and the other official lists.

Reads the lists of DawnbrandBots' yaml-yugi-limit-regulation (one file per list, keyed by Konami's card id),
Project Ignis' LFLists (the current lists, keyed by passcode) and YGOPRODeck's card dump (which maps Konami ids to
passcodes), and writes

  engine/src/main/resources/jadm/banlists.json           [{"date": "2005-03-01", "name": "TCG, March 2005",
                                                                   "limits": {"<passcode>": <copies>, ...}}, ...]
  engine/src/main/resources/jadm/official_banlists.json  {"lists": [{"id": "ocg", "name": ..., "date": ...,
                                                                   "limits": {...}}, {"id": "genesys", ...,
                                                                   "points": {"<passcode>": <points>}}, ...]}
  engine/src/main/resources/jadm/konami_ids.json         {"<Konami id>": [<passcode>, ...]} for the bundled cards

banlists.json is oldest first; when Project Ignis already has a newer TCG list than the yaml-yugi one, it is added
at the end. A progression world plays with the newest list whose date is not after its newest unlocked product.
official_banlists.json holds the lists a server can pick by name (OCG, Traditional, Worlds, Master Duel and the
Genesys point lists); /jadm banlist update downloads newer ones from the same places at runtime, and the Konami ids
let it read the Genesys and Master Duel lists, which have no passcodes.

    git clone --depth 1 https://github.com/DawnbrandBots/yaml-yugi-limit-regulation
    git clone --depth 1 https://github.com/ProjectIgnis/LFLists
    curl -o ygoprodeck.json "https://db.ygoprodeck.com/api/v7/cardinfo.php?misc=yes"
    python3 tools/carddata/build_banlists.py --lists yaml-yugi-limit-regulation --lflists LFLists \
        --ygoprodeck ygoprodeck.json
"""
import argparse
import datetime
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "engine/src/main/resources/jadm"
OUT = RESOURCES / "banlists.json"
OFFICIAL = RESOURCES / "official_banlists.json"
KONAMI_IDS = RESOURCES / "konami_ids.json"
CARDS = RESOURCES / "cards.json"

# id, display name, Project Ignis file. Keep in step with OfficialBanlists.SOURCES in the mod.
LFLISTS = [("ocg", "OCG", "OCG.lflist.conf"), ("traditional", "Traditional", "Traditional.lflist.conf"),
           ("worlds", "Worlds", "World.lflist.conf")]
# id, display name, yaml-yugi directory, whether the numbers are Genesys points
VECTORS = [("master-duel", "Master Duel", "master-duel", False), ("genesys", "Genesys", "genesys", True),
           ("genesys-ocg", "Genesys (OCG)", "ocg-genesys", True)]


def sorted_codes(d):
    return dict(sorted(d.items(), key=lambda kv: int(kv[0])))


def month_name(prefix, date):
    return f"{prefix}, {date:%B} {date.year}"


def read_lflist(path):
    """The first list of a Project Ignis .lflist.conf: its date (from "!2026.09 TCG") and {passcode: copies}."""
    date = None
    limits = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("!"):
            if date is not None:
                break
            m = re.match(r"!(\d{4})\.(\d{1,2})(?:\.(\d{1,2}))?", line)
            date = datetime.date(int(m.group(1)), int(m.group(2)), int(m.group(3) or 1))
            continue
        m = re.match(r"^(\d+)\s+(-?\d+)", line)
        if m and date is not None:
            limits[m.group(1)] = int(m.group(2))
    return date, sorted_codes(limits)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lists", required=True, type=Path, help="a clone of yaml-yugi-limit-regulation")
    parser.add_argument("--lflists", required=True, type=Path, help="a clone of ProjectIgnis/LFLists")
    parser.add_argument("--ygoprodeck", required=True, type=Path, help="cardinfo.php?misc=yes dump")
    args = parser.parse_args()

    bundled = {c["code"] for c in json.loads(CARDS.read_text(encoding="utf-8"))}
    passcodes = {}
    konami_ids = {}
    for card in json.loads(args.ygoprodeck.read_text(encoding="utf-8"))["data"]:
        for misc in card.get("misc_info", []):
            if "konami_id" in misc:
                passcodes[misc["konami_id"]] = card["id"]
                # Alternate artworks share the Konami id.
                codes = sorted({card["id"], *(image["id"] for image in card.get("card_images", []))} & bundled)
                if codes:
                    konami_ids[str(misc["konami_id"])] = codes

    lists = []
    unknown = 0
    for path in sorted((args.lists / "data/tcg").glob("*.vector.json")):
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}\.vector\.json", path.name):
            continue  # current / upcoming symlinks
        data = json.loads(path.read_text(encoding="utf-8"))
        limits = {}
        for konami_id, copies in data["regulation"].items():
            code = passcodes.get(int(konami_id))
            if code is None:
                unknown += 1
                continue
            limits[str(code)] = copies
        date = datetime.date.fromisoformat(data["date"])
        lists.append({"date": data["date"], "name": month_name("TCG", date), "limits": sorted_codes(limits)})
    tcg_date, tcg_limits = read_lflist(args.lflists / "0TCG.lflist.conf")
    if tcg_date > datetime.date.fromisoformat(lists[-1]["date"]):
        lists.append({"date": tcg_date.isoformat(), "name": month_name("TCG", tcg_date), "limits": tcg_limits})
    OUT.write_text(json.dumps(lists, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{len(lists)} lists, {lists[0]['name']} to {lists[-1]['name']}; {unknown} cards without a passcode")

    official = []
    for list_id, name, file in LFLISTS:
        date, limits = read_lflist(args.lflists / file)
        official.append({"id": list_id, "name": month_name(name, date), "date": date.isoformat(), "limits": limits})
    for list_id, name, directory, points in VECTORS:
        data = json.loads((args.lists / "data" / directory / "current.vector.json").read_text(encoding="utf-8"))
        values = {}
        for konami_id, value in data["regulation"].items():
            for code in konami_ids.get(konami_id, []):
                values[str(code)] = value
        date = datetime.date.fromisoformat(data["date"])
        entry = {"id": list_id, "name": f"{name}, {date:%B} {date.day}, {date.year}" if points
                 else month_name(name, date), "date": data["date"]}
        entry["points" if points else "limits"] = sorted_codes(values)
        official.append(entry)
    OFFICIAL.write_text(json.dumps({"lists": official}, separators=(",", ":")) + "\n", encoding="utf-8")
    for entry in official:
        print(f"{entry['id']}: {entry['name']}, {len(entry.get('limits', entry.get('points')))} cards")
    KONAMI_IDS.write_text(json.dumps(dict(sorted(konami_ids.items(), key=lambda kv: int(kv[0]))),
                                     separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{len(konami_ids)} Konami ids")


if __name__ == "__main__":
    main()
