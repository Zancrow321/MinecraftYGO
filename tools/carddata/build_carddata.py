#!/usr/bin/env python3
"""Builds the card data and Lua scripts the mod bundles for its card pool.

Reads EDOPro's card database (BabelCDB cards.cdb) and card scripts (ProjectIgnis CardScripts) and writes,
for just the cards in the pool:

  engine/src/main/resources/minecraftygo/cards.json    stats + text for each card
  engine/src/main/resources/minecraftygo/scripts/      base scripts and c<code>.lua for each card
  engine/src/main/resources/minecraftygo/system_strings.json   EDOPro's system strings (prompt texts)

The pool is pool.json (written by tools/models/import_models.py), every card in the deck lists under
engine/src/main/resources/minecraftygo/decks/*.ydk, and every card those cards' scripts mention by passcode
(tokens, fusion materials), so the engine never asks for a card it doesn't know.

Usage:
  git clone --depth 1 https://github.com/ProjectIgnis/BabelCDB
  git clone --depth 1 https://github.com/ProjectIgnis/CardScripts
  git clone --depth 1 https://github.com/ProjectIgnis/Distribution
  python3 tools/carddata/build_carddata.py --cdb BabelCDB/cards.cdb --scripts CardScripts \
      --strings Distribution/config/strings.conf
"""
import argparse
import json
import re
import shutil
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "engine/src/main/resources/minecraftygo"

# Sub-folders of CardScripts searched for c<code>.lua, in priority order.
SCRIPT_DIRS = ["official", "pre-errata", "goat", "pre-release"]
TYPE_NORMAL = 0x10
TYPE_TOKEN = 0x4000
TYPE_LINK = 0x4000000


def find_script(scripts, code):
    name = f"c{code}.lua"
    return next((scripts / d / name for d in SCRIPT_DIRS if (scripts / d / name).exists()), None)


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
    # Keep positions: scripts refer to strings by index (aux.Stringid(code, i)). Trim only trailing blanks.
    strings = [s or "" for s in text[2:]]
    while strings and not strings[-1]:
        strings.pop()
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
    parser.add_argument("--strings", required=True, type=Path, help="path to Distribution/config/strings.conf")
    args = parser.parse_args()

    pool = set()
    pool_file = RESOURCES / "pool.json"
    if pool_file.exists():
        data = json.loads(pool_file.read_text(encoding="utf-8"))
        pool.update(data["monsters"], data["spellsTraps"])
    for deck in sorted((RESOURCES / "decks").glob("*.ydk")):
        pool.update(read_ydk(deck))

    db = sqlite3.connect(args.cdb)
    known = {code for (code,) in db.execute("select id from datas")}
    missing = sorted(pool - known)
    if missing:
        sys.exit(f"cards missing from the database: {missing}")

    # Pull in cards the scripts refer to, until nothing new turns up.
    sources = {}
    todo = set(pool)
    while todo:
        found = set()
        for code in todo:
            source = find_script(args.scripts, code)
            sources[code] = source
            if source is not None:
                text = source.read_text(encoding="utf-8", errors="replace")
                found.update(int(m) for m in re.findall(r"(?<![\w.])(\d{4,9})(?![\w.])", text))
        todo = (found & known) - pool
        pool |= todo

    cards = []
    for code in sorted(pool):
        row = db.execute("select id, alias, setcode, type, atk, def, level, race, attribute from datas where id=?",
                         (code,)).fetchone()
        text = db.execute("select name, desc, " + ", ".join(f"str{i}" for i in range(1, 17))
                          + " from texts where id=?", (code,)).fetchone()
        cards.append(card_row(row, text))

    (RESOURCES / "cards.json").write_text(json.dumps(cards, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")

    out = RESOURCES / "scripts"
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    for base in sorted(args.scripts.glob("*.lua")):
        shutil.copy(base, out / base.name)
    unscripted = []
    for card in cards:
        source = sources[card["code"]]
        if source is None:
            # Normal monsters and tokens have no script; the core falls back to built-in behaviour.
            if card["type"] & (TYPE_NORMAL | TYPE_TOKEN):
                continue
            unscripted.append(f"{card['code']} {card['name']}")
            continue
        shutil.copy(source, out / source.name)
    if unscripted:
        sys.exit(f"no script for {unscripted}")

    system = {}
    for line in args.strings.read_text(encoding="utf-8").splitlines():
        if line.startswith("!system "):
            _, number, text = line.split(" ", 2)
            system[int(number)] = text
    (RESOURCES / "system_strings.json").write_text(
        json.dumps({str(k): v for k, v in sorted(system.items())}, indent=1, ensure_ascii=False) + "\n",
        encoding="utf-8")

    print(f"{len(system)} system strings, {len(cards)} cards, {len(list(out.iterdir()))} scripts written to {RESOURCES}")


if __name__ == "__main__":
    main()
