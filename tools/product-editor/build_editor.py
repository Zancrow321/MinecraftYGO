#!/usr/bin/env python3
"""Builds the custom product editor: editor.html with the mod's card list and booster list put in.

    python3 tools/product-editor/build_editor.py

writes tools/product-editor/jadm-product-editor.html, one file that works offline in any browser. Run it again
after the card data changes (tools/carddata), so the editor knows the new cards. With a folder as argument it also
writes page.html and cards.json there, for hosting the editor as a web page.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "engine/src/main/resources/jadm"
HERE = pathlib.Path(__file__).resolve().parent

TYPE_MONSTER, TYPE_SPELL, TYPE_TRAP = 0x1, 0x2, 0x4
TYPE_FUSION, TYPE_SYNCHRO, TYPE_XYZ, TYPE_LINK = 0x40, 0x2000, 0x800000, 0x4000000
TYPE_TOKEN = 0x4000


def category(t):
    if t & TYPE_MONSTER:
        return "x" if t & (TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK) else "m"
    return "s" if t & TYPE_SPELL else "t"


def main():
    cards = json.load(open(RES / "cards.json", encoding="utf-8"))
    by_code = {c["code"]: c for c in cards}
    out = []
    for c in sorted(cards, key=lambda c: c["name"]):
        if c["type"] & TYPE_TOKEN:
            continue
        # An alternate artwork has its original's name; the editor lists the original only.
        alias = by_code.get(c["alias"])
        if alias is not None and alias["name"] == c["name"]:
            continue
        out.append([c["code"], c["name"], category(c["type"])])
    products = json.load(open(RES / "products.json", encoding="utf-8"))["products"]
    boosters = [[p["id"], p["code"], p["name"], p["date"]] for p in products
                if p["kind"] == "booster" or p["kind"] in ("special", "tin") and len(p["cards"]) >= 20]
    template = (HERE / "editor.html").read_text(encoding="utf-8")
    cards_json = json.dumps(out, ensure_ascii=False, separators=(",", ":"))
    boosters_json = json.dumps(boosters, ensure_ascii=False, separators=(",", ":"))
    page = template.replace("/*BOOSTERS*/[]", boosters_json)
    # One file that works offline: the card list inside, in a document of its own.
    html = ("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
            "</head>\n<body>\n" + page.replace("/*CARDS*/[]", cards_json) + "\n</body>\n</html>\n")
    target = HERE / "jadm-product-editor.html"
    target.write_text(html, encoding="utf-8")
    print(f"{target}: {len(out)} cards, {len(boosters)} boosters, {len(html) // 1024} KB")
    if len(sys.argv) > 1:
        # A web page with the card list beside it, e.g. to host it: page.html + cards.json.
        web = pathlib.Path(sys.argv[1])
        web.mkdir(parents=True, exist_ok=True)
        (web / "page.html").write_text(page, encoding="utf-8")
        (web / "cards.json").write_text(cards_json, encoding="utf-8")
        print(f"{web}: page.html + cards.json")

if __name__ == "__main__":
    main()
