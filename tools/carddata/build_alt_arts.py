#!/usr/bin/env python3
"""Builds the list of alternate artworks players can pull.

An alternate artwork is a bundled card whose alias is the original's passcode and lies within ten of its own (the
core plays the two as one card). The bundled data has more of them than there are pictures: only the artworks
YGOPRODeck has an image for are written, so a pulled alt art always shows its own picture.

    engine/src/main/resources/jadm/alt_arts.json   {"<original passcode>": [<alternate passcodes>], ...}

    curl -o ygoprodeck.json "https://db.ygoprodeck.com/api/v7/cardinfo.php?misc=yes"
    python3 tools/carddata/build_alt_arts.py --ygoprodeck ygoprodeck.json
"""
import argparse
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "engine/src/main/resources/jadm"
TOKEN = 0x4000


def bundled_cards():
    cards = {}
    for line in (RESOURCES / "cards.json").read_text(encoding="utf-8").splitlines():
        line = line.rstrip(",")
        if line.startswith("{"):
            card = json.loads(line)
            cards[card["code"]] = card
    return cards


def original(cards, code):
    """The original's passcode for an alternate artwork, else the card's own."""
    alias = cards[code]["alias"]
    return alias if alias and abs(alias - code) < 10 else code


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ygoprodeck", type=Path, required=True, help="cardinfo.php dump")
    args = parser.parse_args()

    images = set()
    for card in json.loads(args.ygoprodeck.read_text(encoding="utf-8"))["data"]:
        images.update(image["id"] for image in card.get("card_images") or [])
    cards = bundled_cards()
    broken = set(json.loads((RESOURCES / "broken.json").read_text(encoding="utf-8")))
    alts = defaultdict(list)
    skipped = 0
    for code, card in cards.items():
        first = original(cards, code)
        if first == code or card["type"] & TOKEN or code in broken or first in broken or first not in cards:
            continue
        if code in images:
            alts[first].append(code)
        else:
            skipped += 1
    out = {str(k): sorted(v) for k, v in sorted(alts.items())}
    with (RESOURCES / "alt_arts.json").open("w", encoding="utf-8") as f:
        f.write("{\n" + ",\n".join(f'"{k}":{json.dumps(v)}' for k, v in out.items()) + "\n}\n")
    print(f"{sum(len(v) for v in out.values())} alternate artworks of {len(out)} cards, "
          f"{skipped} left out without an image")


if __name__ == "__main__":
    main()
