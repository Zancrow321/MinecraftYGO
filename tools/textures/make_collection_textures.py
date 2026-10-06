"""Draws the 16x16 item and block textures for the card collection items, the Card Trader's clothes and the Duel
Dome blocks and the Card Vending Machine and the Shop Stand.

Run from the repository root: python3 tools/textures/make_collection_textures.py
"""
from pathlib import Path

from PIL import Image

ASSETS = Path("neoforge/src/main/resources/assets/jadm/textures")


def save(img, path):
    out = ASSETS / path
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print("wrote", out)


def from_rows(rows, palette):
    img = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    return img


# Booster pack: layer0 is the grey body that the item color tints per set, layer1 the silver crimp and gold emblem.
PACK_BODY = [
    "................",
    "................",
    "................",
    "....hhhhhhhh....",
    "....hmmmmmmd....",
    "....hmmmmmmd....",
    "....hmmmmmmd....",
    "....hmmmmmmd....",
    "....hmmmmmmd....",
    "....hmmmmmmd....",
    "....hmmmmmmd....",
    "....hmmmmmmd....",
    "....dddddddd....",
    "................",
    "................",
    "................",
]
PACK_TRIM = [
    "................",
    "....s.s.s.s.....",
    "....ssssssss....",
    "....SSSSSSSS....",
    "................",
    ".......g........",
    "......gyg.......",
    ".....gyYyg......",
    "......gyg.......",
    ".......g........",
    "................",
    "................",
    "....SSSSSSSS....",
    "....ssssssss....",
    ".....s.s.s.s....",
    "................",
]
GREYS = {"h": (235, 235, 235, 255), "m": (200, 200, 200, 255), "d": (150, 150, 150, 255)}
TRIM = {"s": (205, 210, 220, 255), "S": (150, 155, 170, 255), "g": (190, 140, 30, 255),
        "y": (240, 200, 60, 255), "Y": (255, 245, 180, 255)}

BINDER = [
    "................",
    "...kbbbbbbbbbk..",
    "...kBBBBBBBBBk..",
    "...kBwwwBwwwBk..",
    "..rkBwcwBwcwBk..",
    "..rkBwwwBwwwBk..",
    "...kBBBBBBBBBk..",
    "...kBwwwBwwwBk..",
    "..rkBwcwBwcwBk..",
    "..rkBwwwBwwwBk..",
    "...kBBBBBBBBBk..",
    "...kBBBBBBGBBk..",
    "..rkBBBBBGGGBk..",
    "..rkBBBBBBGBBk..",
    "...kkkkkkkkkkk..",
    "................",
]
BINDER_P = {"k": (30, 30, 45, 255), "b": (70, 60, 120, 255), "B": (50, 45, 95, 255),
            "w": (210, 180, 120, 255), "c": (150, 60, 40, 255), "r": (180, 180, 190, 255),
            "G": (220, 180, 60, 255)}

DECK_BOX = [
    "................",
    "................",
    "................",
    "....kkkkkkkk....",
    "...krrrrrrrrk...",
    "...kRRRRRRRRk...",
    "...kkkkkkkkkk...",
    "...kbbbbbbbbk...",
    "...kbbbggbbbk...",
    "...kbbgyygbbk...",
    "...kbbbggbbbk...",
    "...kbbbbbbbbk...",
    "...kbbbbbbbbk...",
    "...kddddddddk...",
    "....kkkkkkkk....",
    "................",
]
DECK_BOX_P = {"k": (25, 20, 20, 255), "r": (190, 50, 45, 255), "R": (140, 30, 30, 255),
              "b": (120, 30, 30, 255), "d": (85, 20, 20, 255), "g": (200, 150, 40, 255),
              "y": (250, 215, 90, 255)}

# Structure deck: layer0 is the box the item color tints per product, layer1 the window with the top card.
STRUCTURE_BODY = [
    "................",
    "................",
    "...hhhhhhhhhh...",
    "...hmmmmmmmmd...",
    "...hm......md...",
    "...hm......md...",
    "...hm......md...",
    "...hm......md...",
    "...hm......md...",
    "...hm......md...",
    "...hm......md...",
    "...hmmmmmmmmd...",
    "...hmmmmmmmmd...",
    "...dddddddddd...",
    "................",
    "................",
]
STRUCTURE_TRIM = [
    "................",
    "................",
    "................",
    "................",
    ".....bbbbbb.....",
    ".....bBBBBb.....",
    ".....bBgyBb.....",
    ".....bByYBb.....",
    ".....bBgyBb.....",
    ".....bBBBBb.....",
    ".....bbbbbb.....",
    "................",
    "....SSSSSSSS....",
    "................",
    "................",
    "................",
]
STRUCTURE_P = {"b": (60, 40, 30, 255), "B": (120, 80, 50, 255), "g": (190, 140, 30, 255),
               "y": (240, 200, 60, 255), "Y": (255, 245, 180, 255), "S": (205, 210, 220, 255)}

# Collector's tin: layer0 the tinted metal, layer1 the rim and the gold emblem.
TIN_BODY = [
    "................",
    "................",
    "................",
    "...hhhhhhhhhh...",
    "..hmmmmmmmmmmd..",
    "..hmmmmmmmmmmd..",
    "..hmmmmmmmmmmd..",
    "..hmmmmmmmmmmd..",
    "..hmmmmmmmmmmd..",
    "..hmmmmmmmmmmd..",
    "..hmmmmmmmmmmd..",
    "..hmmmmmmmmmmd..",
    "...dddddddddd...",
    "................",
    "................",
    "................",
]
TIN_TRIM = [
    "................",
    "................",
    "................",
    "...ssssssssss...",
    "..sS........Ss..",
    "..s....g.....s..",
    "..s...gyg....s..",
    "..s..gyYyg...s..",
    "..s...gyg....s..",
    "..s....g.....s..",
    "..sS........Ss..",
    "..s..........s..",
    "...SSSSSSSSSS...",
    "................",
    "................",
    "................",
]


def planks(base):
    img = Image.new("RGBA", (16, 16))
    r, g, b = base
    for y in range(16):
        for x in range(16):
            shade = 0 if y % 4 else -28
            if (x + (y // 4) * 5) % 8 == 0 and y % 4:
                shade = -14
            img.putpixel((x, y), (max(r + shade, 0), max(g + shade, 0), max(b + shade, 0), 255))
    return img


def card_shop_top():
    img = planks((150, 110, 70))
    # Three cards on display under a glass top.
    for i, color in enumerate([(200, 150, 60), (90, 60, 140), (60, 120, 170)]):
        x0 = 1 + i * 5
        for y in range(4, 12):
            for x in range(x0, x0 + 4):
                edge = x in (x0, x0 + 3) or y in (4, 11)
                img.putpixel((x, y), (40, 30, 25, 255) if edge else color + (255,))
        img.putpixel((x0 + 1, 6), (240, 230, 200, 255))
    return img


def card_shop_side():
    img = planks((150, 110, 70))
    for x in range(16):
        img.putpixel((x, 0), (90, 60, 35, 255))
        img.putpixel((x, 15), (90, 60, 35, 255))
    # A sign with a gold star.
    for y in range(4, 11):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (4, 10)
            img.putpixel((x, y), (60, 30, 20, 255) if edge else (30, 40, 90, 255))
    for x, y in [(7, 5), (8, 5), (6, 7), (7, 7), (8, 7), (9, 7), (7, 6), (8, 6), (7, 8), (8, 8), (7, 9), (8, 9)]:
        img.putpixel((x, y), (240, 200, 60, 255))
    return img


def trader_clothes():
    """A navy coat with a gold card emblem, painted over the villager's jacket UVs (64x64 layout)."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    navy, dark, gold = (35, 45, 95, 255), (25, 30, 65, 255), (220, 180, 60, 255)
    # Jacket box at (0,38), 8 wide, 20 tall, 6 deep.
    for x0, y0, w, h in [(6, 38, 8, 6), (14, 38, 8, 6), (0, 44, 6, 20), (6, 44, 8, 20), (14, 44, 6, 20),
                         (20, 44, 8, 20)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img.putpixel((x, y), dark if (y - y0) == h - 1 or (x - x0) in (0, w - 1) else navy)
    # Front: an open collar and a card emblem on the chest.
    for y in range(44, 47):
        for x in range(9, 11):
            img.putpixel((x, y), (0, 0, 0, 0))
    for y in range(48, 53):
        for x in range(8, 12):
            img.putpixel((x, y), gold if x in (8, 11) or y in (48, 52) else (200, 60, 40, 255))
    # Gold buttons down the back seam's twin on the front.
    for y in (55, 58, 61):
        img.putpixel((10, y), gold)
    return img


def metal_tile(base, glow, pattern):
    """A metal tile with a glowing pattern: '#' glow, '+' bright glow, '.' metal."""
    img = Image.new("RGBA", (16, 16))
    r, g, b = base
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            shade = -30 if edge else (8 if (x + y) % 7 == 0 else 0)
            img.putpixel((x, y), (max(min(r + shade, 255), 0), max(min(g + shade, 255), 0),
                                  max(min(b + shade, 255), 0), 255))
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x, y), glow + (255,))
            elif ch == "+":
                img.putpixel((x, y), (230, 250, 255, 255))
    return img


CORE_TOP = [
    "................",
    ".##############.",
    ".#............#.",
    ".#..########..#.",
    ".#..#......#..#.",
    ".#..#.####.#..#.",
    ".#..#.#++#.#..#.",
    ".####.#++#.####.",
    ".####.#++#.####.",
    ".#..#.#++#.#..#.",
    ".#..#.####.#..#.",
    ".#..#......#..#.",
    ".#..########..#.",
    ".#............#.",
    ".##############.",
    "................",
]
CORE_SIDE = [
    "................",
    "................",
    "..############..",
    "................",
    "....#......#....",
    "....#..++..#....",
    "....#.+##+.#....",
    "....#.+##+.#....",
    "....#..++..#....",
    "....#......#....",
    "................",
    "..############..",
    "................",
    "................",
    "................",
    "................",
]
PLATFORM_TOP = [
    "................",
    ".##############.",
    ".#............#.",
    ".#.##########.#.",
    ".#.#........#.#.",
    ".#.#..####..#.#.",
    ".#.#..#++#..#.#.",
    ".#.#..#++#..#.#.",
    ".#.#..#++#..#.#.",
    ".#.#..#++#..#.#.",
    ".#.#..####..#.#.",
    ".#.#........#.#.",
    ".#.##########.#.",
    ".#............#.",
    ".##############.",
    "................",
]
PLATFORM_SIDE = [
    "................",
    "################",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "################",
    "................",
]
KIT = [
    "................",
    "................",
    "..kkkkkkkkkkkk..",
    "..kqqqqqqqqqqk..",
    "..kqbbbbbbbbqk..",
    "..kqbqqqqqqbqk..",
    "..kqbqqppqqbqk..",
    "..kqbccccccbqk..",
    "..kqbqqccqqbqk..",
    "..kqbqqqqqqbqk..",
    "..kqbqqppqqbqk..",
    "..kqbbbbbbbbqk..",
    "..kqqqqqqqqqqk..",
    "..kkkkkkkkkkkk..",
    "................",
    "................",
]
KIT_P = {"k": (40, 45, 60, 255), "q": (225, 225, 220, 255), "b": (90, 180, 230, 255),
         "c": (40, 200, 210, 255), "p": (40, 70, 200, 255)}


# Card Vending Machine: a red machine with packs behind its glass, a coin slot and a drawer at the bottom.
MACHINE_P = {
    "o": (60, 15, 15, 255), "R": (185, 40, 40, 255), "r": (130, 25, 25, 255), "g": (40, 55, 75, 255),
    "G": (95, 125, 155, 255), "a": (225, 180, 60, 255), "b": (70, 110, 205, 255), "c": (160, 70, 190, 255),
    "d": (60, 170, 90, 255), "s": (20, 20, 20, 255), "w": (230, 235, 240, 255), "y": (245, 205, 60, 255),
    "m": (150, 152, 162, 255), "M": (115, 117, 128, 255),
}
MACHINE_FRONT = [
    "oooooooooooooooo",
    "oRRRRRRRRRRRRRRo",
    "oRgggggggggRRRRo",
    "oRgaagbbgccRwwRo",
    "oRgaagbbgccRwwRo",
    "oRgaagbbgccRRRRo",
    "oRGggGggGggRyRRo",
    "oRgddgaagbbRRRRo",
    "oRgddgaagbbRRsRo",
    "oRgddgaagbbRRsRo",
    "oRGggGggGggRRRRo",
    "oRRRRRRRRRRRRRRo",
    "oRrrrrrrrrrrrrRo",
    "oRrssssssssssrRo",
    "oRrrrrrrrrrrrrRo",
    "oooooooooooooooo",
]
MACHINE_SIDE = [
    "oooooooooooooooo",
    "oRRRRRRRRRRRRRRo",
    "oRrrrrrrrrrrrrRo",
    "oRrRRRRRRRRRRrRo",
    "oRrRRRRRRRRRRrRo",
    "oRrRRRRRRRRRRrRo",
    "oRrRRwwwwwwRRrRo",
    "oRrRRwyyyywRRrRo",
    "oRrRRwwwwwwRRrRo",
    "oRrRRRRRRRRRRrRo",
    "oRrRRRRRRRRRRrRo",
    "oRrRRRRRRRRRRrRo",
    "oRrrrrrrrrrrrrRo",
    "oRRRRRRRRRRRRRRo",
    "oRsRsRsRsRsRsRRo",
    "oooooooooooooooo",
]
MACHINE_TOP = [
    "oooooooooooooooo",
    "ommmmmmmmmmmmmmo",
    "omMMMMMMMMMMMMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMmmmmmmmmmmMmo",
    "omMMMMMMMMMMMMmo",
    "ommmmmmmmmmmmmmo",
    "oooooooooooooooo",
]


def card_machine():
    for rows in (MACHINE_FRONT, MACHINE_SIDE, MACHINE_TOP):
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    save(from_rows(MACHINE_FRONT, MACHINE_P), "block/card_machine_front.png")
    save(from_rows(MACHINE_SIDE, MACHINE_P), "block/card_machine_side.png")
    save(from_rows(MACHINE_TOP, MACHINE_P), "block/card_machine_top.png")


# Shop Stand: a market stall with a red and white awning on top and cards on show over a wooden counter.
STAND_P = {
    "o": (70, 45, 25, 255), "p": (150, 110, 70, 255), "P": (125, 90, 55, 255), "r": (200, 45, 45, 255),
    "w": (240, 235, 225, 255), "R": (150, 30, 30, 255), "a": (225, 180, 60, 255), "b": (70, 110, 205, 255),
    "c": (160, 70, 190, 255), "k": (40, 30, 25, 255), "W": (205, 200, 190, 255),
    "i": (95, 65, 40, 255),
}
STAND_TOP = [
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "RRRRWWWWRRRRWWWW",
]
STAND_SIDE = [
    "rrrrwwwwrrrrwwww",
    "rrrrwwwwrrrrwwww",
    "RrrRWwwWRrrRWwwW",
    "iRRiiWWiiRRiiWWi",
    "oiiiiiiiiiiiiiio",
    "oikkkkikkkkikkko",
    "oikaakikbbkikcco",
    "oikaakikbbkikcco",
    "oikaakikbbkikcco",
    "oikkkkikkkkikkko",
    "oooooooooooooooo",
    "opppppppppppppPo",
    "oPPPPPPPPPPPPPPo",
    "opppppppppppppPo",
    "oPPPPPPPPPPPPPPo",
    "oooooooooooooooo",
]


def shop_stand():
    for rows in (STAND_TOP, STAND_SIDE):
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    save(from_rows(STAND_TOP, STAND_P), "block/shop_stand_top.png")
    save(from_rows(STAND_SIDE, STAND_P), "block/shop_stand_side.png")


def main():
    save(from_rows(PACK_BODY, GREYS), "item/booster_pack.png")
    save(from_rows(PACK_TRIM, TRIM), "item/booster_pack_trim.png")
    save(from_rows(BINDER, BINDER_P), "item/binder.png")
    save(from_rows(DECK_BOX, DECK_BOX_P), "item/deck_box.png")
    save(from_rows(STRUCTURE_BODY, GREYS), "item/structure_deck.png")
    save(from_rows(STRUCTURE_TRIM, STRUCTURE_P), "item/structure_deck_trim.png")
    save(from_rows(TIN_BODY, GREYS), "item/tin.png")
    save(from_rows(TIN_TRIM, TRIM), "item/tin_trim.png")
    save(card_shop_top(), "block/card_shop_top.png")
    save(card_shop_side(), "block/card_shop_side.png")
    save(planks((120, 85, 50)), "block/card_shop_bottom.png")
    save(trader_clothes(), "entity/villager/profession/card_trader.png")
    save(metal_tile((70, 80, 100), (80, 220, 255), CORE_TOP), "block/duel_dome_core_top.png")
    save(metal_tile((70, 80, 100), (80, 220, 255), CORE_SIDE), "block/duel_dome_core_side.png")
    save(metal_tile((45, 60, 110), (90, 160, 255), PLATFORM_TOP), "block/duelist_platform_top.png")
    save(metal_tile((45, 60, 110), (90, 160, 255), PLATFORM_SIDE), "block/duelist_platform_side.png")
    save(from_rows([r.ljust(16, ".")[:16] for r in KIT], KIT_P), "item/duel_dome_kit.png")
    card_machine()
    shop_stand()


if __name__ == "__main__":
    main()
