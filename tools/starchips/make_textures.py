"""Draws the Star Chip textures for the glove on the screen: a chip and an empty socket, 12x12 pixel art each (drawn
big, then shrunk with hard edges so no half-transparent pixels are left).

Run from the repository root: python3 tools/starchips/make_textures.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path("neoforge/src/main/resources/assets/jadm/textures/gui")
SIZE = 12
SCALE = 16  # drawn big, then shrunk


def star(cx, cy, outer, inner, points=5, turn=-math.pi / 2):
    out = []
    for i in range(points * 2):
        r = outer if i % 2 == 0 else inner
        a = turn + i * math.pi / points
        out.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return out


def chip():
    big = SIZE * SCALE
    u = SCALE  # one pixel of the texture
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = big / 2
    # Dark outline, then the gold star, then a lighter upper face and a glint for a cut-gem look.
    d.polygon(star(c, c + 0.4 * u, c, c * 0.48), fill=(96, 54, 10, 255))
    d.polygon(star(c, c + 0.4 * u, c - 1.1 * u, c * 0.40), fill=(232, 160, 24, 255))
    d.polygon(star(c, c - 0.2 * u, c - 2.2 * u, c * 0.32), fill=(255, 214, 72, 255))
    d.rectangle((c - 1.5 * u, c - 2.5 * u, c - 0.5 * u, c - 1.5 * u), fill=(255, 250, 210, 255))
    return shrink(img)


def socket():
    big = SIZE * SCALE
    u = SCALE
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0.2 * u, 0.2 * u, big - 0.2 * u, big - 0.2 * u), fill=(120, 92, 52, 255))
    d.ellipse((1.2 * u, 1.2 * u, big - 1.2 * u, big - 1.2 * u), fill=(176, 146, 92, 255))
    d.ellipse((2.2 * u, 2.2 * u, big - 2.2 * u, big - 2.2 * u), fill=(30, 20, 12, 255))
    d.ellipse((3.2 * u, 3.6 * u, big - 3.2 * u, big - 2.4 * u), fill=(46, 32, 20, 255))
    return shrink(img)


def shrink(img):
    """Averages each SCALE x SCALE block (weighted by alpha) into one pixel that is either solid or empty."""
    big = img.load()
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = out.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r = g = b = a = 0
            for dy in range(SCALE):
                for dx in range(SCALE):
                    pr, pg, pb, pa = big[x * SCALE + dx, y * SCALE + dy]
                    r += pr * pa
                    g += pg * pa
                    b += pb * pa
                    a += pa
            if a / (SCALE * SCALE) >= 110:
                px[x, y] = (r // a, g // a, b // a, 255)
    return out


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    chip().save(OUT / "star_chip.png")
    socket().save(OUT / "star_chip_socket.png")
    print("Wrote", OUT / "star_chip.png", "and", OUT / "star_chip_socket.png")
