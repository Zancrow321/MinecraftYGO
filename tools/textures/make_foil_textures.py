"""Draws the foil textures that are added on top of foil cards (see FoilEffect.java).

They are drawn with additive blending, so black adds nothing and brighter colors shine more. Each one tiles, so
the game can scroll it across a card.

Run from the repository root: python3 tools/textures/make_foil_textures.py
"""
import colorsys
import math
import random
from pathlib import Path

from PIL import Image

OUT = Path("neoforge/src/main/resources/assets/jadm/textures/foil")


def save(img, name):
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / name)
    print("wrote", OUT / name)


def rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, s, max(0.0, min(1.0, v)))
    return int(r * 255), int(g * 255), int(b * 255), 255


def holo(size=128):
    """Super and Ultra Rare artwork: a rainbow that bends in soft waves, like a holofoil."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            wave = 0.18 * math.sin(2 * tau * y) + 0.12 * math.sin(3 * tau * x + 1.3 * math.sin(tau * y))
            hue = (x + y) / size + wave
            # Bright bands where two waves meet, dimmer in between.
            light = 0.55 + 0.45 * math.sin(4 * tau * (x - y) + 2 * math.sin(2 * tau * x))
            img.putpixel((x, y), rgb(hue, 0.75, 0.25 + 0.75 * light * light))
    return img


def secret(size=128, seed=7):
    """Secret Rare artwork: fine diagonal lines in rainbow colors, with a few sparkles."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            hue = (x - y) / size + 0.1 * math.sin(2 * tau * (x + y))
            line = (x + y) % 4
            v = 1.0 if line == 0 else 0.45 if line == 1 else 0.08
            img.putpixel((x, y), rgb(hue, 0.6, v))
    rng = random.Random(seed)
    for _ in range(40):
        cx, cy = rng.randrange(size), rng.randrange(size)
        for dx, dy, v in ((0, 0, 255), (1, 0, 160), (-1, 0, 160), (0, 1, 160), (0, -1, 160)):
            img.putpixel(((cx + dx) % size, (cy + dy) % size), (v, v, v, 255))
    return img


def sheen(width=512, height=4, band=20):
    """A soft white band; scrolled across a card it makes the glare that sweeps over foil names and artwork."""
    img = Image.new("RGBA", (width, height))
    for x in range(width):
        d = abs(x - width / 2) / band
        v = int(255 * max(0.0, math.cos(min(d, 1.0) * math.pi / 2)) ** 2) if d < 1 else 0
        for y in range(height):
            img.putpixel((x, y), (v, v, v, 255))
    return img


if __name__ == "__main__":
    save(holo(), "holo.png")
    save(secret(), "secret.png")
    save(sheen(), "sheen.png")
