"""Draws the mod's logo in a simple Minecraft pixel style: two cards with the classic brown card back and its swirl,
facing off with a spark between them. It is drawn on a 32x32 grid and scaled up without smoothing, so every pixel stays a crisp block.

Writes the icon (shown in the mod list and used as the Modrinth/CurseForge icon), the same icon without its
background, and a 1280x400 banner with the mod's name for the top of the store descriptions.

Run from the repository root: python3 tools/textures/make_logo.py
"""
import math
import random
from pathlib import Path

from PIL import Image

ICON = Path("neoforge/src/main/resources/minecraftygo_logo.png")
RELEASE = Path("docs/release")
N = 32
OUTLINE = 0x14101C

STONE = [0x2B2F45, 0x262A3E, 0x30354D, 0x23263A, 0x2E3249]

# A small blocky font for the banner, 7 rows high.
FONT = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "G": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "J": ["....#", "....#", "....#", "....#", "#...#", "#...#", ".###."],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    " ": ["...", "...", "...", "...", "...", "...", "..."],
}


def rgba(c):
    return (c >> 16) & 255, (c >> 8) & 255, c & 255, 255


def card(x0, y0, w=15, h=21):
    """A card lying face down with its top-left corner at (x0, y0), as a {pixel: color} layer: a brown back with a
    black oval in the middle and a fiery swirl inside it."""
    cx, cy = x0 + w // 2, y0 + h // 2
    ax, ay = 4.6, 6.6  # half axes of the oval
    layer = {}
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            u, v = x - x0, y - y0
            ring = min(u, v, w - 1 - u, h - 1 - v)
            dx, dy = (x - cx) / ax, (y - cy) / ay
            e = dx * dx + dy * dy
            turn = math.atan2(dy, dx)
            if ring == 0:  # outer edge, lit from the top left
                c = 0xC07A3A if (u == 0 or v == 0) and u != w - 1 and v != h - 1 else 0x6A3412
            elif ring == 1:
                c = 0x8E4E1E
            elif e <= 1.0:
                if e > 0.62:
                    c = 0x120806  # the oval's black rim
                else:
                    arm = math.sin(2 * turn - math.sqrt(e) * 7.0)  # two arms winding into the middle
                    c = 0xFFB040 if arm > 0.5 else 0xE0501C if arm > -0.2 else 0x7A1408
                    if e < 0.05:
                        c = 0xFFD070
            else:  # dark brown with lighter streaks swirling around the oval
                c = 0x7A4219 if math.sin(2 * turn - math.sqrt(e) * 3.2) > 0.35 else 0x4E250D
                if ring == 2 and (u == 2 or v == 2):
                    c = 0x7E4620
            layer[(x, y)] = c
    return layer

def spark(cx, cy):
    layer = {}
    for d in range(4):
        for p in ((cx + d, cy), (cx - d, cy), (cx, cy + d), (cx, cy - d)):
            layer[p] = 0xFFFBE0 if d < 2 else 0xFFD23C
    layer[(cx, cy)] = 0xFFFFFF
    return layer


def with_outline(layer):
    out = dict(layer)
    for x, y in layer:
        for p in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if p not in layer and 0 <= p[0] < N and 0 <= p[1] < N:
                out[p] = OUTLINE
    return out


def stone(w, h, seed):
    """Dark stone-like noise, the background of the icon and the banner."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), rgba(rnd.choice(STONE)))
    return img


def icon(background=True):
    if background:
        img = stone(N, N, 7)
        for i in range(N):  # a block-like bevel: light top and left edge, dark bottom and right
            img.putpixel((i, 0), rgba(0x40466A))
            img.putpixel((0, i), rgba(0x40466A))
            img.putpixel((i, N - 1), rgba(0x171927))
            img.putpixel((N - 1, i), rgba(0x171927))
    else:
        img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for layer in (card(3, 3), card(14, 8), spark(25, 4)):
        for p, c in with_outline(layer).items():
            img.putpixel(p, rgba(c))
    return img


def text(canvas, s, ox, oy, px, top, bottom, side):
    """Blocky two-tone letters with a drop shadow, each font pixel px screen pixels wide."""
    pts, x = [], 0
    for ch in s:
        glyph = FONT[ch]
        pts += [(x + i, y) for y, row in enumerate(glyph) for i, c in enumerate(row) if c == "#"]
        x += len(glyph[0]) + 1
    for off, color in ((px // 2 + 3, None), (px // 2, side), (0, "face")):
        for gx, gy in pts:
            c = OUTLINE if color is None else (top if gy <= 3 else bottom) if color == "face" else side
            left, up = ox + gx * px + off, oy + gy * px + off
            canvas.paste(rgba(c), (left, up, left + px, up + px))


def banner():
    w, h, px = 1280, 400, 8
    img = stone(w // px, h // px, 3).resize((w, h), Image.NEAREST)
    img.alpha_composite(icon(background=False).resize((N * 10, N * 10), Image.NEAREST), (40, 40))
    text(img, "JUST ANOTHER", 400, 92, 9, 0xFFFFFF, 0xB8C2D2, 0x5A6378)
    text(img, "DUELING MOD", 400, 198, 13, 0xFFF3B0, 0xFFC21F, 0x9A5A08)
    return img


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print("wrote", path)


def main():
    save(icon().resize((512, 512), Image.NEAREST), ICON)
    save(icon(background=False).resize((512, 512), Image.NEAREST), RELEASE / "icon_transparent.png")
    save(banner(), RELEASE / "banner.png")


if __name__ == "__main__":
    main()
