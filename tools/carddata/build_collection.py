#!/usr/bin/env python3
"""Builds the booster sets, the TCG product list and the era banlist.

Reads pool.json (from tools/models/import_models.py), cards.json (from build_carddata.py) and YGOPRODeck's card
dump and set list, and writes:

  engine/src/main/resources/jadm/sets.json      booster sets of the modeled pool: their pool cards and rarities
  engine/src/main/resources/jadm/products.json  every TCG product in release order (see below)
  engine/src/main/resources/jadm/banlist.json   how many copies of each restricted card a deck may hold

A set becomes a booster when it is an original booster (a three-letter code without a region marker such as
"-EN", and not a starter deck or tournament pack), at least MIN_COVERAGE of its cards are in the pool and it has at least MIN_CARDS of them. As new models
join the pool, later sets (Pharaoh's Servant, ...) start to qualify on their own. Packs only ever contain pool
cards.

The banlist is the OCG list in force at the era cutoff as best known (May 2000): only the restricted cards that
are in the pool are written. Server owners can replace it with config/jadm/banlist.json.

products.json lists every TCG product YGOPRODeck knows that has a release date and at least one bundled card,
oldest first: its id, set code, name, date, a rough kind (booster, deck, tin, promo, special), every card with
its rarity as printed, and the cards it is the first TCG product for ("new"). Reprint-only products have an
empty "new". Cards never printed in the TCG are listed under "ocgOnly" with their OCG release date.

    curl -o ygoprodeck.json "https://db.ygoprodeck.com/api/v7/cardinfo.php?misc=yes"
    curl -o cardsets.json https://db.ygoprodeck.com/api/v7/cardsets.php
    python3 tools/carddata/build_collection.py --ygoprodeck ygoprodeck.json --cardsets cardsets.json
"""
import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "engine/src/main/resources/jadm"
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


def kind(name, code, size):
    """A rough product kind from its name; pack formats per kind come later (M11d)."""
    lower = name.lower()
    words = lower.split()
    if "tin" in words or "tins" in words or "mega-tin" in lower or "mega-tins" in lower:
        return "tin"
    if "deck" in lower and "booster" not in lower:
        return "deck"
    if any(w in lower for w in ("promo", "participation", "shonen jump", "prize", "sneak peek", "championship",
                                "premiere", "sample", "giveaway", "magazine", "issue", "game")):
        return "promo"
    if lower.startswith(("tournament pack", "ots tournament pack")) or "battle pack" in lower:
        return "booster"
    if len(code) in (3, 4) and size >= 50:
        return "booster"
    return "special"


def write_products(cards, cardsets):
    bundled = {}
    for line in (RESOURCES / "cards.json").read_text(encoding="utf-8").splitlines():
        line = line.rstrip(",")
        if line.startswith("{"):
            card = json.loads(line)
            bundled[card["code"]] = card
    dates = {s["set_name"]: s.get("tcg_date") for s in cardsets}
    contents = defaultdict(dict)  # set name -> code -> rarity as printed (the first listed one)
    codes = {}
    ocg_dates = {}
    for card in cards:
        code = card["id"]
        if code not in bundled:
            continue
        misc = (card.get("misc_info") or [{}])[0]
        if misc.get("ocg_date"):
            ocg_dates[code] = misc["ocg_date"]
        for printing in card.get("card_sets") or []:
            name = printing["set_name"]
            if dates.get(name):
                contents[name].setdefault(code, printing["set_rarity"])
                codes.setdefault(name, printing["set_code"].split("-")[0])
    order = sorted(contents, key=lambda n: (dates[n], n))
    seen = set()
    ids = set()
    products = []
    for name in order:
        new = sorted(c for c in contents[name] if c not in seen)
        seen.update(new)
        pid = slug(name)
        while pid in ids:
            pid += "_"
        ids.add(pid)
        products.append({"id": pid, "code": codes[name], "name": name, "date": dates[name],
                         "kind": kind(name, codes[name], len(contents[name])),
                         "cards": [[c, r] for c, r in sorted(contents[name].items())], "new": new})
    # Cards that never came out in the TCG unlock with their OCG date; alternate artworks follow their original.
    ocg_only = sorted([c, ocg_dates[c]] for c in bundled if c not in seen and c in ocg_dates)
    undated = [c for c, card in bundled.items() if c not in seen and c not in ocg_dates
               and not (card["alias"] and abs(card["alias"] - c) < 10) and not card["type"] & 0x4000]
    with (RESOURCES / "products.json").open("w", encoding="utf-8") as out:
        out.write('{"products":[\n')
        out.write(",\n".join(json.dumps(p, ensure_ascii=False, separators=(",", ":")) for p in products))
        out.write('\n],\n"ocgOnly":' + json.dumps(ocg_only, separators=(",", ":")) + "}\n")
    kinds = defaultdict(int)
    for p in products:
        kinds[p["kind"]] += 1
    print(f"{len(products)} products ({sum(1 for p in products if p['new'])} with new cards) {dict(kinds)}, "
          f"{len(seen)} TCG cards, {len(ocg_only)} OCG-only, {len(undated)} undated non-token cards: {undated[:10]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ygoprodeck", type=Path, required=True, help="cardinfo.php?misc=yes dump")
    parser.add_argument("--cardsets", type=Path, required=True, help="cardsets.php dump")
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
    write_products(cards, json.loads(args.cardsets.read_text(encoding="utf-8")))
    for s in sets:
        counts = {r: sum(1 for c in s["cards"] if c["rarity"] == r) for r in ORDER}
        print(f"{s['code']} {s['name']}: {len(s['cards'])} cards {counts}")
    print(f"banlist {BANLIST_NAME}: {len(limits)} restricted cards in the pool")


if __name__ == "__main__":
    main()
