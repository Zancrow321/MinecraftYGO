#!/usr/bin/env python3
"""Builds the card data and Lua scripts the mod bundles for its card pool.

Reads EDOPro's card database (BabelCDB cards.cdb) and card scripts (ProjectIgnis CardScripts) and writes,
for the cards it bundles:

  engine/src/main/resources/jadm/cards.json    stats + text for each card
  engine/src/main/resources/jadm/scripts/      base scripts and c<code>.lua for each card
  engine/src/main/resources/jadm/system_strings.json   EDOPro's system strings (prompt texts)

With --all (what the mod ships since M11a) every official card in cards.cdb is written: all OCG and TCG cards, without
anime, Rush Duel or Speed Duel skill cards, which live in other BabelCDB files. Which of them a server lets players
use is decided at runtime by the pool mode. Without --all, only the modeled pool is written: pool.json (from
tools/models/import_models.py), every card in the deck lists under engine/src/main/resources/jadm/decks/*.ydk,
and every card those cards' scripts mention by passcode (tokens, fusion materials), so the engine never asks for a
card it doesn't know.

Scripts that fail to load are found by the engine test ScriptLoadTest; such cards go in broken.json, which keeps
them out of the pool until the script is fixed.

Usage:
  git clone --depth 1 https://github.com/ProjectIgnis/BabelCDB
  git clone --depth 1 https://github.com/ProjectIgnis/CardScripts
  git clone --depth 1 https://github.com/ProjectIgnis/Distribution
  python3 tools/carddata/build_carddata.py --cdb BabelCDB/cards.cdb --scripts CardScripts \
      --strings Distribution/config/strings.conf --all $(for f in BabelCDB/release-*.cdb; do echo --release-cdb $f; done)

A set that just came out can sit in its own BabelCDB/release-<set>.cdb until ProjectIgnis merges it into cards.cdb
(Beyond the Brave did in October 2026); --release-cdb merges those in. Of their cards, only the ones with a script
(or that need none) are written, so a card waiting for its script never stops the build.
"""
import argparse
import json
import re
import shutil
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "engine/src/main/resources/jadm"

# Sub-folders of CardScripts searched for c<code>.lua, in priority order.
SCRIPT_DIRS = ["official", "pre-errata", "goat", "pre-release"]
TYPE_NORMAL = 0x10
TYPE_TOKEN = 0x4000
TYPE_LINK = 0x4000000
TYPE_PENDULUM = 0x1000000


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


def merged_db(cdb, releases):
    """cards.cdb plus the release-<set>.cdb files: ProjectIgnis keeps the cards of a just-released set in their own
    database until it merges them into cards.cdb. Rows from those are flagged "released"; cards.cdb wins on a clash."""
    db = sqlite3.connect(":memory:")
    for i, path in enumerate([cdb, *releases]):
        db.execute(f"attach database ? as src{i}", (str(path),))
        if i == 0:
            db.execute("create table datas as select *, 0 as released from src0.datas")
            db.execute("create table texts as select * from src0.texts")
            db.execute("create unique index datas_id on datas(id)")
            db.execute("create unique index texts_id on texts(id)")
        else:
            db.execute(f"insert or ignore into datas select *, 1 from src{i}.datas")
            db.execute(f"insert or ignore into texts select * from src{i}.texts")
    return db


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cdb", required=True, type=Path, help="path to BabelCDB cards.cdb")
    parser.add_argument("--scripts", required=True, type=Path, help="path to a CardScripts checkout")
    parser.add_argument("--strings", required=True, type=Path, help="path to Distribution/config/strings.conf")
    parser.add_argument("--all", action="store_true", help="write every official card, not only the modeled pool")
    parser.add_argument("--release-cdb", action="append", default=[], type=Path,
                        help="a BabelCDB release-<set>.cdb to merge in (repeatable)")
    args = parser.parse_args()

    db = merged_db(args.cdb, args.release_cdb)
    known = {code for (code,) in db.execute("select id from datas")}
    pool = set()
    pool_file = RESOURCES / "pool.json"
    if pool_file.exists():
        data = json.loads(pool_file.read_text(encoding="utf-8"))
        pool.update(data["monsters"], data["spellsTraps"])
    for deck in sorted((RESOURCES / "decks").glob("*.ydk")):
        pool.update(read_ydk(deck))
    if args.all:
        # OCG (0x1) and TCG (0x2) cards only; the anime, Rush and skill cards are in other databases anyway.
        pool.update(code for (code,) in db.execute("select id from datas where ot & 3 != 0 and not released"))
        # Cards of a fresh release whose script ProjectIgnis hasn't written yet wait for the next rebuild.
        for code, ctype in db.execute("select id, type from datas where ot & 3 != 0 and released"):
            if find_script(args.scripts, code) or ctype & (TYPE_NORMAL | TYPE_TOKEN) and not ctype & TYPE_PENDULUM:
                pool.add(code)

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

    # One card per line keeps the file small enough to bundle and still diffable.
    (RESOURCES / "cards.json").write_text(
        "[\n" + ",\n".join(json.dumps(c, ensure_ascii=False, separators=(",", ":")) for c in cards) + "\n]\n",
        encoding="utf-8")

    out = RESOURCES / "scripts"
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    for base in sorted(args.scripts.glob("*.lua")):
        shutil.copy(base, out / base.name)
    unscripted = []
    for card in cards:
        source = sources[card["code"]]
        alias = card["alias"]
        if source is None and alias and abs(alias - card["code"]) < 10:
            # Alternate artworks run their original card's script (ocgcore interpreter::register_card).
            if sources.get(alias) is None and not card["type"] & (TYPE_NORMAL | TYPE_TOKEN):
                unscripted.append(f"{card['code']} {card['name']}")
            continue
        if source is None:
            # Normal monsters and tokens have no script; the core falls back to built-in behaviour. Normal pendulum
            # monsters are the exception: their scales come from a script.
            if card["type"] & (TYPE_NORMAL | TYPE_TOKEN) and not card["type"] & TYPE_PENDULUM:
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
