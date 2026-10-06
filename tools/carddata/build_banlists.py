#!/usr/bin/env python3
"""Builds the history of TCG Forbidden & Limited Lists for progression worlds.

Reads the TCG lists of DawnbrandBots' yaml-yugi-limit-regulation (one file per list, keyed by Konami's card id) and
YGOPRODeck's card dump (which maps Konami ids to passcodes), and writes

  engine/src/main/resources/jadm/banlists.json   [{"date": "2005-03-01", "name": "TCG, March 2005",
                                                           "limits": {"<passcode>": <copies>, ...}}, ...]

oldest first. A progression world plays with the newest list whose date is not after its newest unlocked product.

    git clone --depth 1 https://github.com/DawnbrandBots/yaml-yugi-limit-regulation
    curl -o ygoprodeck.json "https://db.ygoprodeck.com/api/v7/cardinfo.php?misc=yes"
    python3 tools/carddata/build_banlists.py --lists yaml-yugi-limit-regulation --ygoprodeck ygoprodeck.json
"""
import argparse
import datetime
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "engine/src/main/resources/jadm/banlists.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lists", required=True, type=Path, help="a clone of yaml-yugi-limit-regulation")
    parser.add_argument("--ygoprodeck", required=True, type=Path, help="cardinfo.php?misc=yes dump")
    args = parser.parse_args()

    passcodes = {}
    for card in json.loads(args.ygoprodeck.read_text(encoding="utf-8"))["data"]:
        for misc in card.get("misc_info", []):
            if "konami_id" in misc:
                passcodes[misc["konami_id"]] = card["id"]

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
        lists.append({"date": data["date"], "name": f"TCG, {date:%B} {date.year}",
                      "limits": dict(sorted(limits.items(), key=lambda kv: int(kv[0])))})
    OUT.write_text(json.dumps(lists, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{len(lists)} lists, {lists[0]['name']} to {lists[-1]['name']}; {unknown} cards without a passcode")


if __name__ == "__main__":
    main()
