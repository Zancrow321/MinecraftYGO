"""Draws the 16x16 item and block textures for the card collection items and the Card Trader's clothes.

Run from the repository root: python3 tools/textures/make_collection_textures.py
"""
from pathlib import Path

from PIL import Image

ASSETS = Path("neoforge/src/main/resources/assets/minecraftygo/textures")


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


def main():
    save(from_rows(PACK_BODY, GREYS), "item/booster_pack.png")
    save(from_rows(PACK_TRIM, TRIM), "item/booster_pack_trim.png")
    save(from_rows(BINDER, BINDER_P), "item/binder.png")
    save(from_rows(DECK_BOX, DECK_BOX_P), "item/deck_box.png")
    save(card_shop_top(), "block/card_shop_top.png")
    save(card_shop_side(), "block/card_shop_side.png")
    save(planks((120, 85, 50)), "block/card_shop_bottom.png")
    save(trader_clothes(), "entity/villager/profession/card_trader.png")


if __name__ == "__main__":
    main()
