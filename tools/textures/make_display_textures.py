"""Makes the textures of the collector displays (card frame, showcase, toploader stand, pack display).

Run from the repository root: python3 tools/textures/make_display_textures.py
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "neoforge/src/main/resources/assets/jadm/textures"
DISPLAY = ROOT / "display"
BLOCK = ROOT / "block"


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def glow():
    """A soft glowing rectangle for the halo behind a card; drawn additively, so black is nothing."""
    size = 64
    img = Image.new("RGB", (size, size))
    inner = 0.16  # where the card's edge is, as a share of the side
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size
            v = (y + 0.5) / size
            # Distance outside the inner rectangle, 0 inside.
            dx = max(inner - u, 0, u - (1 - inner))
            dy = max(inner - v, 0, v - (1 - inner))
            d = math.hypot(dx, dy) / inner
            a = (1 - smooth(d)) ** 1.6
            c = int(255 * a)
            img.putpixel((x, y), (c, c, c))
    img.save(DISPLAY / "glow.png")


def glass():
    """Reflections on glass, drawn additively: black is clear, the faint diagonal streaks catch the light."""
    size = 32
    img = Image.new("RGB", (size, size))
    for y in range(size):
        for x in range(size):
            s = (x + y) % size
            if s in (5, 6) or s in (22, 23, 24):
                c = 34
            elif s == 12:
                c = 22
            else:
                c = 4
            img.putpixel((x, y), (c, c + 2, c + 4))
    img.save(DISPLAY / "glass.png")


def toploader():
    """A clear plastic toploader: see-through, with a bright rim and a top notch."""
    w, h = 24, 32
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            edge = x == 0 or x == w - 1 or y == h - 1 or (y == 0 and not (8 <= x < 16))
            notch = y == 2 and 8 <= x < 16
            shine = (x - y // 2) in (3, 4) and y < h - 2
            if edge:
                c = (235, 245, 255, 200)
            elif notch:
                c = (235, 245, 255, 120)
            elif shine:
                c = (255, 255, 255, 60)
            else:
                c = (220, 235, 255, 26)
            img.putpixel((x, y), c)
    img.save(DISPLAY / "toploader.png")


def pack_box():
    """The pack display's cardboard: deep blue with a gold band and a darker inside."""
    rnd = random.Random(7)
    side = Image.new("RGB", (16, 16))
    inside = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-6, 6)
            if 5 <= y <= 6:
                c = (214 + n, 168 + n, 48 + n // 2)
            elif y == 4 or y == 7:
                c = (120 + n, 86 + n, 20)
            else:
                c = (26 + n, 44 + n, 110 + n)
            side.putpixel((x, y), c)
            m = rnd.randint(-4, 4)
            inside.putpixel((x, y), (14 + m, 20 + m, 52 + m))
    side.save(BLOCK / "pack_display_side.png")
    inside.save(BLOCK / "pack_display_inside.png")


def velvet():
    """Dark red velvet for behind the cards."""
    rnd = random.Random(3)
    img = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-7, 7)
            img.putpixel((x, y), (74 + n, 12 + n // 2, 22 + n // 2))
    img.save(BLOCK / "display_velvet.png")


def main():
    DISPLAY.mkdir(parents=True, exist_ok=True)
    glow()
    glass()
    toploader()
    pack_box()
    velvet()


if __name__ == "__main__":
    main()
