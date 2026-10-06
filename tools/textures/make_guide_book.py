"""Draws the Duelist's Handbook: its 16x16 item texture and the open book its screen is drawn on (two pages on a
380x224 leather cover, in a 512x256 texture).

Run from the repository root: python3 tools/textures/make_guide_book.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path("neoforge/src/main/resources/assets/jadm/textures")

ITEM = [
    "................",
    "..bbbbbbbbbbb...",
    ".bLLLLLLLLLLLb..",
    ".bLLLLggLLLLLpb.",
    ".bLLLgYYgLLLLpb.",
    ".bLLgYrrYgLLLpb.",
    ".bLLgYrrYgLLLpb.",
    ".bLLgYrrYgLLLpb.",
    ".bLLLgYYgLLLLpb.",
    ".bLLLLggLLLLLpb.",
    ".bLLLLLLLLLLLpb.",
    ".bDDDDDDDDDDDpb.",
    ".bLLLLLLLLLLLpb.",
    ".bbbbbbbbbbbbPb.",
    "..bbbbbbbbbbbb..",
    "................",
]
ITEM_COLORS = {
    "b": (54, 24, 18, 255),     # cover edge
    "L": (122, 38, 30, 255),    # leather
    "D": (84, 28, 22, 255),     # strap
    "g": (176, 132, 40, 255),   # gold rim
    "Y": (236, 196, 84, 255),   # gold
    "r": (60, 110, 190, 255),   # gem
    "p": (238, 228, 200, 255),  # page edges
    "P": (200, 186, 150, 255),
}


def item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ITEM):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), ITEM_COLORS[ch])
    return img


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def book():
    w, h = 380, 224
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Leather cover with a darker edge and gold corners.
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=8, fill=(92, 30, 24, 255), outline=(44, 16, 12, 255), width=2)
    d.rounded_rectangle((4, 4, w - 5, h - 5), radius=6, outline=(128, 48, 36, 255), width=1)
    for cx, cy in ((3, 3), (w - 14, 3), (3, h - 14), (w - 14, h - 14)):
        d.rectangle((cx, cy, cx + 10, cy + 10), outline=(196, 152, 56, 255))
        d.rectangle((cx + 3, cy + 3, cx + 7, cy + 7), fill=(224, 184, 80, 255))
    # Page stacks under each page, then the pages, shaded towards the spine.
    paper = (244, 233, 205)
    shade = (214, 196, 160)
    for i in range(3):
        d.rectangle((10 - i, 8 + i, 189, 217 + i), fill=mix(shade, (170, 150, 116), i / 3))
        d.rectangle((191, 8 + i, 370 + i, 217 + i), fill=mix(shade, (170, 150, 116), i / 3))
    for x in range(10, 189):
        t = max(0.0, (x - 150) / 39) ** 2
        d.line((x, 7, x, 216), fill=mix(paper, shade, t))
    for x in range(191, 370):
        t = max(0.0, (230 - x) / 39) ** 2
        d.line((x, 7, x, 216), fill=mix(paper, shade, t))
    d.line((189, 6, 189, 219), fill=(120, 96, 70, 255))
    d.line((190, 6, 190, 219), fill=(80, 52, 36, 255))
    d.line((191, 6, 191, 219), fill=(120, 96, 70, 255))
    # A thin rule under the running header of each page.
    for x0 in (22, 206):
        d.line((x0, 23, x0 + 151, 23), fill=(200, 180, 140, 255))
    return img


def save(img, path):
    out = ASSETS / path
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print("wrote", out)


if __name__ == "__main__":
    save(item(), "item/guide_book.png")
    save(book(), "gui/guide_book.png")
