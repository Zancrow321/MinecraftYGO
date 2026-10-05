#!/usr/bin/env python3
"""Builds the booster sets and the era banlist for the card pool.

Reads pool.json (from tools/models/import_models.py) and YGOPRODeck's card dump, and writes:

  engine/src/main/resources/minecraftygo/sets.json     booster sets: their pool cards and each card's rarity
  engine/src/main/resources/minecraftygo/banlist.json  how many copies of each restricted card a deck may hold

A set becomes a booster when it is an original booster (a three-letter code without a region marker such as
"-EN", and not a starter deck or tournament pack), at least MIN_COVERAGE of its cards are in the pool and it has at least MIN_CARDS of them. As new models
join the pool, later sets (Pharaoh's Servant, ...) start to qualify on their own. Packs only ever contain pool
cards.

The banlist is the OCG list in force at the era cutoff as best known (May 2000): only the restricted cards that
are in the pool are written. Server owners can replace it with config/minecraftygo/banlist.json.

    python3 tools/carddata/build_collection.py --ygoprodeck ygoprodeck.json
"""
import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "engine/src/main/resources/minecraftygo"
MIN_COVERAGE = 0.6
MIN_CARDS = 60
RARITIES = {
    "Common": "common", "Short Print": "common", "Super Short Print": "common",
    "Rare": "rare", "Super Rare": "super", "Ultra Rare": "ultra", "Secret Rare": "secret",
}
ORDER = ["common", "rare", "super", "ultra", "secret"]
# Later names of the same booster; the original printing is the one kept.
RENAMED = {"SRL"}
# OCG Forbidden/Limited list of May 2000, the one in force at the era cutoff.
BANLIST_NAME = "OCG, May 2000"
LIMITS = {
    "Raigeki": 1, "Dark Hole": 1, "Change of Heart": 1, "Pot of Greed": 1, "Last Will": 1, "Mirror Force": 1,
    "Exodia the Forbidden One": 1, "Left Arm of the Forbidden One": 1, "Left Leg of the Forbidden One": 1,
    "Right Arm of the Forbidden One": 1, "Right Leg of the Forbidden One": 1,
    "Monster Reborn": 2, "Graceful Charity": 2, "Snatch Steal": 2,
}


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ygoprodeck", type=Path, required=True, help="cardinfo.php?misc=yes dump")
    args = parser.parse_args()

    pool = json.loads((RESOURCES / "pool.json").read_text(encoding="utf-8"))
    codes = set(pool["monsters"]) | set(pool["spellsTraps"])
    cards = json.loads(args.ygoprodeck.read_text(encoding="utf-8"))["data"]

    totals = defaultdict(set)  # set name -> every card in it
    printings = defaultdict(dict)  # set name -> code -> best rarity
    prefixes = {}
    printing_codes = {}
    for card in cards:
        for printing in card.get("card_sets") or []:
            name = printing["set_name"]
            totals[name].add(card["id"])
            prefixes.setdefault(name, printing["set_code"].split("-")[0])
            printing_codes.setdefault(name, printing["set_code"])
            rarity = RARITIES.get(printing["set_rarity"])
            if card["id"] in codes and rarity:
                best = printings[name].get(card["id"])
                if best is None or ORDER.index(rarity) > ORDER.index(best):
                    printings[name][card["id"]] = rarity

    sets = []
    for name, members in printings.items():
        prefix = prefixes[name]
        original = len(prefix) == 3 and "(" not in name and not prefix.startswith(("SD", "TP"))
        original = original and prefix not in RENAMED and not any(
            "-EN" in p for p in [printing_codes[name]])
        if original and len(members) >= MIN_CARDS and len(members) / len(totals[name]) >= MIN_COVERAGE:
            sets.append({"id": slug(name), "code": prefix, "name": name,
                         "cards": [{"code": c, "rarity": r} for c, r in sorted(members.items())]})
    sets.sort(key=lambda s: s["code"] != "LOB")  # LOB first; the rest keep dump order
    (RESOURCES / "sets.json").write_text(json.dumps(sets, indent=1), encoding="utf-8")

    by_name = {c["name"]: c["id"] for c in cards}
    limits = {str(by_name[n]): k for n, k in LIMITS.items() if by_name.get(n) in codes}
    (RESOURCES / "banlist.json").write_text(json.dumps({"name": BANLIST_NAME, "limits": limits}, indent=1),
                                            encoding="utf-8")
    for s in sets:
        counts = {r: sum(1 for c in s["cards"] if c["rarity"] == r) for r in ORDER}
        print(f"{s['code']} {s['name']}: {len(s['cards'])} cards {counts}")
    print(f"banlist {BANLIST_NAME}: {len(limits)} restricted cards in the pool")


if __name__ == "__main__":
    main()
