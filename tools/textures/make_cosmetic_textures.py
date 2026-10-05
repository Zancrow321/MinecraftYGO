"""Recolors the duel disk and the card back into the disk skins and card sleeves.

Battle City (the disk as modeled) and Classic (the plain card back) are the originals; every other skin repaints the
light grey plastic and shifts the hue of the glowing zones, every other sleeve shifts the card back's hue.
Keep the ids in sync with Cosmetics.java.

Run from the repository root: python3 tools/textures/make_cosmetic_textures.py
"""
import colorsys
from pathlib import Path

from PIL import Image

ASSETS = Path("neoforge/src/main/resources/assets/minecraftygo/textures")

# id: (plastic colour, glow hue in degrees or None to keep, glow saturation factor)
SKINS = {
    "obelisk_blue": ((70, 110, 215), None, 1.0),
    "slifer_red": ((200, 45, 40), 25, 1.0),
    "ra_yellow": ((235, 195, 50), 50, 1.0),
    "shadow": ((70, 55, 95), 280, 1.0),
    "crimson": ((45, 42, 48), 355, 1.2),
    "gold": ((225, 175, 60), 45, 0.6),
}

# id: (hue in degrees or None, saturation factor, value factor)
SLEEVES = {
    "crimson": (355, 1.0, 1.0),
    "emerald": (140, 1.0, 1.0),
    "amethyst": (275, 1.0, 1.0),
    "onyx": (None, 0.0, 0.8),
    "gold": (45, 0.9, 1.15),
}


def recolor(img, fn):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = fn(r, g, b) + (a,)
    return out


def shift(r, g, b, hue, sat, val):
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    if hue is not None:
        h = hue / 360
    s = min(1.0, s * sat)
    v = min(1.0, v * val)
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v))


def plastic(colour):
    def fn(r, g, b):
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if s < 0.15 and v > 0.45:  # the light grey shell; dark trim and the red counter stay
            k = v / 0.65
            return tuple(min(255, round(c * k)) for c in colour)
        return r, g, b
    return fn


def main():
    disk = Image.open(ASSETS / "disk/duel_disk.png").convert("RGBA")
    glow = Image.open(ASSETS / "disk/duel_disk_e.png").convert("RGBA")
    for skin, (colour, hue, sat) in SKINS.items():
        recolor(disk, plastic(colour)).save(ASSETS / f"disk/duel_disk_{skin}.png")
        recolor(glow, lambda r, g, b: shift(r, g, b, hue, sat, 1)).save(ASSETS / f"disk/duel_disk_{skin}_e.png")
        print("wrote skin", skin)
    back = Image.open(ASSETS / "field/card_back.png").convert("RGBA")
    for sleeve, (hue, sat, val) in SLEEVES.items():
        recolor(back, lambda r, g, b: shift(r, g, b, hue, sat, val)).save(ASSETS / f"field/sleeve_{sleeve}.png")
        print("wrote sleeve", sleeve)


if __name__ == "__main__":
    main()
