#!/usr/bin/env python3
"""Builds the card data and Lua scripts the mod bundles for its card pool.

Reads EDOPro's card database (BabelCDB cards.cdb) and card scripts (ProjectIgnis CardScripts) and writes,
for just the cards in the pool:

  engine/src/main/resources/minecraftygo/cards.json    stats + text for each card
  engine/src/main/resources/minecraftygo/scripts/      base scripts and c<code>.lua for each card

The pool is every card in the deck lists under engine/src/main/resources/minecraftygo/decks/*.ydk.

Usage:
  git clone --depth 1 https://github.com/ProjectIgnis/BabelCDB
  git clone --depth 1 https://github.com/ProjectIgnis/CardScripts
  python3 tools/carddata/build_carddata.py --cdb BabelCDB/cards.cdb --scripts CardScripts
"""
import argparse
import json
import shutil
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "engine/src/main/resources/minecraftygo"

# Sub-folders of CardScripts searched for c<code>.lua, in priority order.
SCRIPT_DIRS = ["official", "pre-errata", "goat", "pre-release"]
TYPE_LINK = 0x4000000


def read_ydk(path):
    """Returns the passcodes in a .ydk deck list (main, extra and side)."""
    codes = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith(("#", "!")):
            codes.append(int(line))
    return codes


def card_row(row, text):
    code, alias, setcode, ctype, atk, defense, level, race, attribute = row
    setcodes = [(setcode >> (16 * i)) & 0xFFFF for i in range(4)]
    is_link = bool(ctype & TYPE_LINK)
    strings = [s for s in text[2:] if s]
    return {
        "code": code,
        "alias": alias,
        "setcodes": [s for s in setcodes if s],
        "type": ctype,
        "level": level & 0xFF,
        "lscale": (level >> 24) & 0xFF,
        "rscale": (level >> 16) & 0xFF,
        "attribute": attribute,
        "race": race,
        "attack": atk,
        # Link monsters store their arrows in the DEF column.
        "defense": 0 if is_link else defense,
        "linkMarker": defense if is_link else 0,
        "name": text[0],
        "desc": text[1],
        "strings": strings,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cdb", required=True, type=Path, help="path to BabelCDB cards.cdb")
    parser.add_argument("--scripts", required=True, type=Path, help="path to a CardScripts checkout")
    args = parser.parse_args()

    pool = set()
    for deck in sorted((RESOURCES / "decks").glob("*.ydk")):
        pool.update(read_ydk(deck))

    db = sqlite3.connect(args.cdb)
    cards = []
    missing = []
    for code in sorted(pool):
        row = db.execute("select id, alias, setcode, type, atk, def, level, race, attribute from datas where id=?",
                         (code,)).fetchone()
        text = db.execute("select name, desc, " + ", ".join(f"str{i}" for i in range(1, 17))
                          + " from texts where id=?", (code,)).fetchone()
        if row is None or text is None:
            missing.append(code)
            continue
        cards.append(card_row(row, text))
    if missing:
        sys.exit(f"cards missing from the database: {missing}")

    (RESOURCES / "cards.json").write_text(json.dumps(cards, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")

    out = RESOURCES / "scripts"
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    for base in sorted(args.scripts.glob("*.lua")):
        shutil.copy(base, out / base.name)
    for card in cards:
        name = f"c{card['code']}.lua"
        source = next((args.scripts / d / name for d in SCRIPT_DIRS if (args.scripts / d / name).exists()), None)
        if source is None:
            # Normal monsters have no script; the core falls back to built-in behaviour.
            if card["type"] & 0x10:
                continue
            sys.exit(f"no script for {card['code']} {card['name']}")
        shutil.copy(source, out / name)

    print(f"{len(cards)} cards, {len(list(out.iterdir()))} scripts written to {RESOURCES}")


if __name__ == "__main__":
    main()
