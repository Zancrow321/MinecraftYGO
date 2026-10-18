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


def sparkles(img, rng, count, peak=255, arm=2):
    """Adds little four-pointed stars: a bright point with arms that fade out."""
    size = img.size[0]
    for _ in range(count):
        cx, cy = rng.randrange(size), rng.randrange(size)
        v = rng.randint(peak // 2, peak)
        points = [(0, 0, v)]
        for d in range(1, arm + 1):
            f = int(v * (1 - d / (arm + 1)) ** 2)
            points += [(d, 0, f), (-d, 0, f), (0, d, f), (0, -d, f)]
        for dx, dy, f in points:
            x, y = (cx + dx) % size, (cy + dy) % size
            r, g, b, _ = img.getpixel((x, y))
            img.putpixel((x, y), (max(r, f), max(g, f), max(b, f), 255))


def starlight(size=128, seed=11):
    """Starlight Rare, over the whole card: fine rainbow streaks that run across it, glittering with little stars."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            hue = (2 * x + y) / size + 0.08 * math.sin(3 * tau * y)
            # Thin streaks that lean a little, two pixels apart, brighter in waves.
            streak = 1.0 if (x + y // 4) % 2 == 0 else 0.3
            wave = 0.55 + 0.45 * math.sin(2 * tau * (x + 2 * y))
            img.putpixel((x, y), rgb(hue, 0.65, streak * (0.35 + 0.65 * wave)))
    sparkles(img, random.Random(seed), 70, arm=2)
    return img


def ghost(size=128):
    """Ghost Rare artwork: a pale, silvery blue hologram with soft waves, hardly any color."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            wave = 0.5 + 0.5 * math.sin(2 * tau * (x + y) + 1.5 * math.sin(tau * (x - 2 * y)))
            hue = 0.55 + 0.12 * math.sin(tau * (x - y))
            img.putpixel((x, y), rgb(hue, 0.18 + 0.12 * wave, 0.35 + 0.65 * wave * wave))
    return img


def prismatic(size=128, seed=5):
    """Prismatic Secret Rare artwork: a glitter of little rainbow diamonds in a grid, brighter in rings, like the
    cross-hatched prism foil."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            # Rings around the tile's corners (which meet as one point once it tiles) and its middle.
            ring = math.sin(4 * tau * math.hypot(min(x, size - x), min(y, size - y)))
            ring2 = math.sin(3 * tau * math.hypot(x - size / 2, y - size / 2))
            hue = (x + 2 * y) / size + 0.2 * ring
            # A diamond around every point of a 4-pixel grid, with its centre the brightest.
            d = abs((x % 4) - 1.5) + abs((y % 4) - 1.5)
            dot = 1.0 if d < 1.1 else 0.55 if d < 2.1 else 0.1
            v = dot * (0.6 + 0.25 * ring + 0.15 * ring2)
            img.putpixel((x, y), rgb(hue, 0.85, v))
    sparkles(img, random.Random(seed), 50, arm=1)
    return img


def rainbow(size=128):
    """A tint for whole-card foils, multiplied over the card: a bright rainbow in fine streaks, so white parts of the
    card take its colors (adding light can't color white)."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            hue = (2 * x + y) / size + 0.08 * math.sin(3 * tau * y)
            streak = 1.0 if (x + y // 4) % 2 == 0 else 0.55
            img.putpixel((x, y), rgb(hue, 0.7 * streak, 1.0))
    return img


def brushed(size=128, seed=3):
    """Gold and platinum foil: brushed metal, fine bright streaks along the card. Tinted gold or silver in the game."""
    rng = random.Random(seed)
    rows = [rng.random() for _ in range(size)]
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            streak = 0.5 * rows[y] + 0.5 * rows[(y + 1) % size]
            v = 0.55 + 0.3 * streak + 0.15 * math.sin(tau * (x + 2 * y))
            img.putpixel((x, y), rgb(0, 0, v))
    return img


def dots(size=128):
    """Premium Gold Rare frame: rows of little raised dots."""
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            dx, dy = (x % 4) - 1.5, (y % 4) - 1.5
            d = math.hypot(dx, dy)
            img.putpixel((x, y), rgb(0, 0, 1.0 if d < 1 else 0.35 if d < 1.8 else 0.1))
    return img


def relief(size=128):
    """Ultimate Rare: the foil pressed into little domes, lit from the top left. Multiplied over the card, white is
    flat, darker is the shadow side of a dome; the glare on top lights the other side."""
    img = Image.new("RGBA", (size, size))
    k = 2 * math.pi * 16 / size
    for y in range(size):
        for x in range(size):
            # Slope of an egg-crate surface sin(kx)·sin(ky) towards the light.
            slope = math.cos(k * x) * math.sin(k * y) + math.sin(k * x) * math.cos(k * y)
            img.putpixel((x, y), rgb(0, 0, 0.75 + 0.25 * slope))
    return img


def collector(size=128):
    """Collector's Rare artwork: very fine wavy rainbow lines, like a fingerprint of foil."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            phase = 16 * tau * y + 3 * math.sin(2 * tau * x) + 2 * math.sin(tau * (x + y))
            line = 0.5 + 0.5 * math.sin(phase)
            hue = (x - 2 * y) / size + 0.1 * math.sin(3 * tau * x)
            img.putpixel((x, y), rgb(hue, 0.55, 0.15 + 0.85 * line ** 3))
    return img


def quarter_century(size=128, seed=25):
    """Quarter Century Secret Rare artwork: rainbow lines in two crossing directions, a fine diamond mesh, glittering."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            hue = (x - y) / size + 0.12 * math.sin(2 * tau * (x + y))
            a, b = (x + y) % 4, (x - y) % 4
            v = 1.0 if a == 0 and b == 0 else 0.6 if a == 0 or b == 0 else 0.08
            img.putpixel((x, y), rgb(hue, 0.7, v))
    sparkles(img, random.Random(seed), 40, arm=1)
    return img


def mosaic(size=128, seed=9):
    """Mosaic Rare, over the whole card: little triangles of foil, each its own color and brightness."""
    rng = random.Random(seed)
    cell = 8
    n = size // cell
    tiles = {(i, j, k): (rng.random(), rng.random()) for i in range(n) for j in range(n) for k in range(2)}
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            i, j = x // cell, y // cell
            k = 1 if (x % cell) > (y % cell) else 0
            hue, light = tiles[(i, j, k)]
            edge = (x % cell) in (0,) or (y % cell) in (0,) or (x % cell) == (y % cell)
            img.putpixel((x, y), rgb(hue, 0.6, 0.15 if edge else 0.25 + 0.75 * light * light))
    return img


def starfoil(size=128, seed=13):
    """Starfoil Rare, over the whole card: little rainbow stars and rings on a dark ground."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    tau = 2 * math.pi / size
    for _ in range(260):
        cx, cy = rng.randrange(size), rng.randrange(size)
        r = rng.choice((1, 2, 2, 3))
        ring = rng.random() < 0.35
        hue = rng.random()
        for dy in range(-r - 1, r + 2):
            for dx in range(-r - 1, r + 2):
                d = math.hypot(dx, dy)
                ang = math.atan2(dy, dx)
                if ring:
                    inside = abs(d - r) < 0.6
                else:  # a five-pointed star
                    inside = d <= r * (0.45 + 0.55 * max(0.0, math.cos(5 * ang / 2) ** 8))
                if inside:
                    x, y = (cx + dx) % size, (cy + dy) % size
                    img.putpixel((x, y), rgb(hue + 0.05 * math.sin(tau * x), 0.6, 1.0))
    return img


def shatter(size=128, seed=17):
    """Shatterfoil Rare, over the whole card: foil cracked into shards, each catching the light differently."""
    rng = random.Random(seed)
    points = [(rng.uniform(0, size), rng.uniform(0, size), rng.random(), rng.random()) for _ in range(40)]
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            best, second, shard = 1e9, 1e9, None
            for px, py, hue, light in points:
                # Distance on the tile, so the shards wrap around its edges.
                dx = min(abs(x - px), size - abs(x - px))
                dy = min(abs(y - py), size - abs(y - py))
                d = math.hypot(dx, dy)
                if d < best:
                    best, second, shard = d, best, (hue, light)
                elif d < second:
                    second = d
            crack = second - best < 1.2
            hue, light = shard
            img.putpixel((x, y), rgb(hue, 0.5, 1.0 if crack else 0.15 + 0.6 * light))
    return img


def parallel(size=128):
    """Parallel Rare, over the whole card: a fine screen of dots that shimmers in soft rainbow bands."""
    img = Image.new("RGBA", (size, size))
    tau = 2 * math.pi / size
    for y in range(size):
        for x in range(size):
            hue = y / size + 0.1 * math.sin(tau * x)
            dot = 1.0 if x % 2 == 0 and y % 2 == 0 else 0.2
            img.putpixel((x, y), rgb(hue, 0.5, dot * (0.5 + 0.5 * math.sin(3 * tau * (x + y)) ** 2)))
    return img


def pharaoh(size=128):
    """Pharaoh's Rare: the Eye of Wdjat pressed into the foil, over and over."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    cell = 32
    for y in range(size):
        for x in range(size):
            # Every other row of eyes is shifted half a cell.
            ox = (x + (cell // 2 if (y // cell) % 2 else 0)) % cell - cell / 2
            oy = y % cell - cell / 2
            eye = (ox / 11) ** 2 + (oy / 5.5) ** 2
            outline = 0.8 < eye < 1.25
            pupil = math.hypot(ox, oy) < 3.2
            tail = -6 < ox < 2 and 5 < oy < 8 and abs(oy - 5.5 - (ox + 6) * 0.2) < 1.2
            brow = -10 < ox < 10 and -10 < oy < -7.5
            if outline or pupil or tail or brow:
                img.putpixel((x, y), rgb(0, 0, 1.0))
    return img


def tint_of(img):
    """The tint of a pattern for multiplying over a card: white where the pattern is dark or grey, its colors where
    it is colored. Multiplied, it colors the white parts of a card, which adding light can't."""
    out = Image.new("RGBA", img.size)
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, _ = img.getpixel((x, y))
            m = max(r, g, b)
            out.putpixel((x, y), (255 - m + r, 255 - m + g, 255 - m + b, 255))
    return out


if __name__ == "__main__":
    save(holo(), "holo.png")
    save(secret(), "secret.png")
    save(sheen(), "sheen.png")
    save(starlight(), "starlight.png")
    save(ghost(), "ghost.png")
    save(prismatic(), "prismatic.png")
    save(rainbow(), "rainbow.png")
    save(brushed(), "brushed.png")
    save(dots(), "dots.png")
    save(relief(), "relief.png")
    save(collector(), "collector.png")
    save(quarter_century(), "quarter_century.png")
    for name, img in (("mosaic", mosaic()), ("starfoil", starfoil()), ("shatter", shatter()),
                      ("parallel", parallel())):
        save(img, name + ".png")
        save(tint_of(img), name + "_tint.png")
    save(pharaoh(), "pharaoh.png")
