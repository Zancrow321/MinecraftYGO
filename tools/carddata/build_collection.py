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
its plainest rarity as printed (and the better ones it is also printed in, for the pack slots), and the cards it is
the first TCG product for ("new"). Cards are listed by their original passcode, never an alternate artwork's.
Reprint-only products have an
empty "new". Cards never printed in the TCG are listed under "ocgOnly" with their OCG release date.

    curl -o ygoprodeck.json "https://db.ygoprodeck.com/api/v7/cardinfo.php?misc=yes"
    curl -o cardsets.json https://db.ygoprodeck.com/api/v7/cardsets.php
    python3 tools/carddata/build_collection.py --ygoprodeck ygoprodeck.json --cardsets cardsets.json
"""
import argparse
import html
import json
import re
from collections import Counter, defaultdict
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


# Words every real rarity name has; YGOPRODeck's set lists also carry notes in the rarity field ("2" and "3" for
# copies in a deck, "New", "European debut", "force-SMW"...), and those are no rarity.
REAL_RARITY = re.compile(r"rare|common|short print", re.I)
RARITY_ALIASES = {"Cr": "Collector's Rare"}
# Printings YGOPRODeck's dump lacks (it has only Duelist's Advance's first 48 cards): product name -> its set code
# and, per card number and name, [passcode, rarity, better rarities...].
MISSING_PRINTINGS = Path(__file__).resolve().parent / "missing_printings.json"


def printed_rarity(rarity):
    """The rarity as printed, or None when the field holds a note instead."""
    rarity = RARITY_ALIASES.get(rarity, rarity)
    return rarity if REAL_RARITY.search(rarity) else None


def slot(rarity):
    """The pack slot a printed rarity falls into, 0 (common) to 4 (secret), as BoosterSets.Rarity.ofPrinted."""
    r = rarity.lower()
    if "short print" in r or "normal parallel" in r:
        return 0
    if any(w in r for w in ("secret", "starlight", "collector", "ghost", "grand master", "quarter century")):
        return 4
    if any(w in r for w in ("ultra", "ultimate", "gold", "platinum")):
        return 3
    if "super" in r:
        return 2
    return 1 if any(w in r for w in ("rare", "starfoil", "shatterfoil")) else 0


def kind(name, code, size):
    """A rough product kind from its name; pack formats per kind come later (M11d)."""
    lower = name.lower()
    words = lower.split()
    if "tin" in words or "tins" in words or "mega-tin" in lower or "mega-tins" in lower:
        return "tin"
    if "deck" in lower and "booster" not in lower:
        return "deck"
    if lower.startswith("champion pack"):  # "Champion Pack: Game One" is a pack, not a video game promo
        return "booster"
    if any(w in lower for w in ("promo", "participation", "shonen jump", "prize", "sneak peek", "championship",
                                "premiere", "sample", "giveaway", "magazine", "issue", "game")):
        return "promo"
    if (lower.startswith(("tournament pack", "ots tournament pack")) or "battle pack" in lower
            or "power-up pack" in lower):  # the packs that came inside a Super Starter or box set
        return "booster"
    if len(code) in (3, 4) and size >= 50:
        return "booster"
    return "special"


PLAIN = {"Common", "Rare", "Super Rare", "Ultra Rare", "Secret Rare"}


def plainness(rarity):
    """Sorts a card's rarities plainest first: by slot, the plain name of a slot before its finishes (Ultra Rare
    before Gold Rare or Ultimate Rare)."""
    return slot(rarity), rarity not in PLAIN


def listing(code, rarities):
    """A card of a product: [code, rarity], with [every other rarity it is also printed in] when there are any: a
    Phantom Nightmare ultra is also a Quarter Century Secret Rare and a Starlight Rare, an old core set's rare also
    an Ultimate Rare. Each one can be pulled."""
    better = [r for r in rarities[1:] if r != rarities[0]]
    return [code, rarities[0], better] if better else [code, rarities[0]]


def bundled_cards():
    bundled = {}
    for line in (RESOURCES / "cards.json").read_text(encoding="utf-8").splitlines():
        line = line.rstrip(",")
        if line.startswith("{"):
            card = json.loads(line)
            bundled[card["code"]] = card
    return bundled


def write_products(cards, cardsets, rarities_only=False):
    bundled = bundled_cards()
    dates = {s["set_name"]: s.get("tcg_date") for s in cardsets}
    contents = defaultdict(dict)  # set name -> code -> its rarities as printed (the real ones, in listed order)
    codes = {}
    ocg_dates = {}
    for card in cards:
        code = card["id"]
        if code not in bundled:
            continue
        # YGOPRODeck lists some cards under one of their alternate artworks (Dark Magician as 46986420); a product
        # holds the original, and alt arts come from packs at random (see build_alt_arts.py).
        alias = bundled[code]["alias"]
        if alias and abs(alias - code) < 10 and alias in bundled:
            code = alias
        misc = (card.get("misc_info") or [{}])[0]
        if misc.get("ocg_date"):
            ocg_dates[code] = misc["ocg_date"]
        for printing in card.get("card_sets") or []:
            name = printing["set_name"]
            if dates.get(name):
                rarity = printed_rarity(printing["set_rarity"])
                rarities = contents[name].setdefault(code, [])
                if rarity and rarity not in rarities:
                    rarities.append(rarity)
                codes.setdefault(name, printing["set_code"].split("-")[0])
    for name, missing in json.loads(MISSING_PRINTINGS.read_text(encoding="utf-8")).items():
        if dates.get(name):
            codes.setdefault(name, missing["code"])
            for code, *rarities in missing["cards"].values():
                if code in bundled:
                    contents[name].setdefault(code, rarities)
    # A card is listed with its plainest rarity, the one it is pulled as in its usual slot (Phantom Nightmare's ultras
    # are also Quarter Century Secret Rares, and listing that one first left the set without ultras); its better
    # printings follow as variants. A card listed with only a note gets the product's most usual rarity: the commons
    # of a deck, the foils of an all-foil set.
    for name, members in contents.items():
        usual = Counter(min(r, key=slot) for r in members.values() if r).most_common(1)
        for code, rarities in members.items():
            if not rarities:
                rarities.append(usual[0][0] if usual else "Common")
            rarities.sort(key=plainness)
    if rarities_only:
        update_rarities(contents)
        return
    order = sorted(contents, key=lambda n: (dates[n], n))
    seen = set()
    ids = set()
    products = []
    for name in order:
        new = sorted(c for c in contents[name] if c not in seen)
        seen.update(new)
        pid = slug(name)  # from the raw name, so ids made before the unescaping stay the same
        while pid in ids:
            pid += "_"
        ids.add(pid)
        products.append({"id": pid, "code": codes[name], "name": html.unescape(name), "date": dates[name],
                         "kind": kind(name, codes[name], len(contents[name])),
                         "cards": [listing(c, r) for c, r in sorted(contents[name].items())], "new": new})
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


def update_rarities(contents):
    """Rewrites only the rarities of the cards products.json already lists, keeping every list as it is: a fresh
    dump also changes card lists (a new set's temporary passcodes go away), which a rarity fix shouldn't touch."""
    path = RESOURCES / "products.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    by_name = {html.unescape(name): members for name, members in contents.items()}
    changed = 0
    for product in data["products"]:
        members = by_name.get(product["name"], {})
        for i, card in enumerate(product["cards"]):
            rarities = members.get(card[0])
            if rarities:
                updated = listing(card[0], rarities)
                changed += updated != card
                product["cards"][i] = updated
    with path.open("w", encoding="utf-8") as out:
        out.write('{"products":[\n')
        out.write(",\n".join(json.dumps(p, ensure_ascii=False, separators=(",", ":")) for p in data["products"]))
        out.write('\n],\n"ocgOnly":' + json.dumps(data["ocgOnly"], separators=(",", ":")) + "}\n")
    print(f"rarities of {changed} printings updated")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ygoprodeck", type=Path, required=True, help="cardinfo.php?misc=yes dump")
    parser.add_argument("--cardsets", type=Path, required=True, help="cardsets.php dump")
    parser.add_argument("--rarities-only", action="store_true",
                        help="only update the rarities of the cards products.json lists, nothing else")
    args = parser.parse_args()
    if args.rarities_only:
        cards = json.loads(args.ygoprodeck.read_text(encoding="utf-8"))["data"]
        write_products(cards, json.loads(args.cardsets.read_text(encoding="utf-8")), rarities_only=True)
        return

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
