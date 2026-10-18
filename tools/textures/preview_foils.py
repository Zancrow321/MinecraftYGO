"""Renders every rarity's foil (assets/jadm/foil/looks.json) over card images, the way FoilEffect draws it in the
game: same boxes, texture scrolling, additive and multiply blending, and ghost art. Handy for tuning a look without
starting the game.

    python3 tools/textures/preview_foils.py --cards 46986420 44508094 --out foils.png
    python3 tools/textures/preview_foils.py --cards 44508094 --only starlight ghost --gif glare.gif

Card images are downloaded from YGOPRODeck into --cache (default: build/foil-preview). Needs Pillow and numpy.
"""
import argparse
import json
import urllib.request
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "neoforge/src/main/resources/assets/jadm"
LOOKS = json.loads((ASSETS / "foil/looks.json").read_text(encoding="utf-8"))
# Textures with "blur" in their .mcmeta are sampled smoothly; the others pixel by pixel, as in the game.
BLUR = {p.name.split(".")[0] for p in (ASSETS / "textures/foil").glob("*.png.mcmeta")}
_textures = {}


def texture(name):
    if name not in _textures:
        img = Image.open(ASSETS / "textures/foil" / (name + ".png")).convert("RGB")
        _textures[name] = np.asarray(img, dtype=np.float32) / 255
    return _textures[name]


def boxes(on):
    """The boxes a layer covers, by name, expanding groups such as 'metal'."""
    out = []
    for name in on if isinstance(on, list) else [on]:
        for b in LOOKS["boxes"][name]:
            out += boxes(b) if isinstance(b, str) else [b]
    return out


def layers(rarity):
    out = []
    for layer in LOOKS["looks"][rarity]:
        out += LOOKS["presets"][layer["use"]] if "use" in layer else [layer]
    return out


def sample(t, u, v, blur):
    h, w, _ = t.shape
    if not blur:
        return t[np.floor(v * h).astype(int) % h, np.floor(u * w).astype(int) % w]
    fx, fy = u * w - 0.5, v * h - 0.5
    x0, y0 = np.floor(fx).astype(int), np.floor(fy).astype(int)
    ax, ay = (fx - x0)[..., None], (fy - y0)[..., None]
    c = lambda xx, yy: t[yy % h, xx % w]
    return (c(x0, y0) * (1 - ax) * (1 - ay) + c(x0 + 1, y0) * ax * (1 - ay)
            + c(x0, y0 + 1) * (1 - ax) * ay + c(x0 + 1, y0 + 1) * ax * ay)


def ghost_art(a):
    """As CardArt.ghost: the colors gone, a pale silver image."""
    lum = a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114
    v = 0.30 + 0.70 * lum ** 0.85
    return np.stack([v * 0.93, v * 0.97, v * 1.04], axis=-1).clip(0, 1)


def render(card_image, rarity, width=220, t=2.6):
    h = round(width * card_image.height / card_image.width)
    out = np.asarray(card_image.convert("RGB").resize((width, h), Image.LANCZOS), dtype=np.float32) / 255
    for layer in layers(rarity):
        if "ghost" in layer:
            for b in boxes(layer["ghost"]):
                x0, y0, x1, y1 = round(b[0] * width), round(b[1] * h), round(b[2] * width), round(b[3] * h)
                out[y0:y1, x0:x1] = ghost_art(out[y0:y1, x0:x1])
            continue
        color = np.array([int(layer["color"][i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255
        for b in boxes(layer["on"]):
            x0, y0, x1, y1 = round(b[0] * width), round(b[1] * h), round(b[2] * width), round(b[3] * h)
            ys, xs = np.mgrid[y0:y1, x0:x1]
            fx, fy = (xs + 0.5) / width, (ys + 0.5) / h
            if layer["mode"] == "flat":
                u, v = np.full_like(fx, 0.5), np.full_like(fy, 0.5)
            elif layer["mode"] == "sheen":
                u, v = fx * 0.25 + fy * 0.12 - t * 0.15, np.full_like(fy, 0.5)
            else:
                u, v = fx * 1.2 + t * 0.03, fy * 1.2 * 1.46 - t * 0.02
            foil = sample(texture(layer["texture"]), u, v, layer["texture"] in BLUR) * color
            if layer.get("blend") == "multiply":
                out[y0:y1, x0:x1] *= 1 - layer["strength"] * (1 - foil)
            else:
                out[y0:y1, x0:x1] += foil
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def card(code, cache):
    path = cache / f"{code}.jpg"
    if not path.exists():
        cache.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(f"https://images.ygoprodeck.com/images/cards/{code}.jpg",
                                         headers={"User-Agent": "Just Another Dueling Mod"})
        path.write_bytes(urllib.request.urlopen(request).read())
    return Image.open(path)


def sheet(cards, rarities, width, t, columns):
    tiles = [(r, render(c, r, width, t)) for c in cards for r in rarities]
    cw, ch = tiles[0][1].width + 10, tiles[0][1].height + 26
    rows = -(-len(tiles) // columns)
    out = Image.new("RGB", (columns * cw + 10, rows * ch + 10), (30, 30, 36))
    draw = ImageDraw.Draw(out)
    for i, (rarity, img) in enumerate(tiles):
        x, y = 10 + (i % columns) * cw, 10 + (i // columns) * ch
        draw.text((x + 2, y), rarity, fill=(255, 255, 255))
        out.paste(img, (x, y + 16))
    return out


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cards", nargs="+", default=["46986420"], help="passcodes")
    parser.add_argument("--only", nargs="+", help="rarity ids, e.g. starlight ghost (default: all)")
    parser.add_argument("--width", type=int, default=220)
    parser.add_argument("--columns", type=int, default=6)
    parser.add_argument("--out", help="a PNG sheet")
    parser.add_argument("--gif", help="an animated GIF of the glare going by")
    parser.add_argument("--cache", default=str(ROOT / "build/foil-preview"))
    args = parser.parse_args()
    images = [card(c, Path(args.cache)) for c in args.cards]
    rarities = args.only or list(LOOKS["looks"])
    if args.out:
        sheet(images, rarities, args.width, 2.6, args.columns).save(args.out)
    if args.gif:
        # The glare comes round every 1 / 0.15 seconds.
        frames = [sheet(images, rarities, args.width, i / 24 / 0.15, args.columns) for i in range(24)]
        frames[0].save(args.gif, save_all=True, append_images=frames[1:], duration=110, loop=0)
