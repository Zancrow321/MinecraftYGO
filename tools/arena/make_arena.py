#!/usr/bin/env python3
"""Generates the Duelist Kingdom arena: GeckoLib geometry, animations, texture atlas, glow mask and kit icon.

The arena is one block entity drawn as one model, 21 by 21 blocks: a white platform with a grid of blue tiles on
top, a red podium at one end and a blue one at the other, and a lantern pylon on each corner. Units are pixels
(16 per block), with the origin at the bottom centre of the arena's middle block. The podiums sit on the z axis,
and the model is the same turned half way round apart from their colours, so the block's facing only picks the
axis.

Animations: "raise" lifts both podiums 3 blocks while a column grows under them, "lower" brings them back down,
"lowered" holds them flush with the platform.

    python3 tools/arena/make_arena.py
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / "neoforge/src/main/resources/assets/minecraftygo"

BLOCK = 16
HALF = 21 * BLOCK // 2  # 168: the platform reaches 10.5 blocks out from the middle block's centre
TOP = BLOCK  # the platform is one block tall
TILES = 7  # tiles per side, each 3 blocks
PODIUM_ALONG = 9 * BLOCK  # where a duelist stands, measured to the centre of their block
LIFT = 3 * BLOCK
SECONDS = 2.0  # the server lifts duelists over the same 40 ticks

CELL = 16
ATLAS = 128
# Swatch name -> cell (column, row) in the atlas.
SWATCHES = {
    "tile": (0, 0), "grout": (1, 0), "side": (2, 0), "end": (3, 0), "rim": (4, 0), "red": (5, 0), "blue": (6, 0),
    "screen": (7, 0), "pylon": (0, 1), "lantern": (1, 1), "trim": (2, 1), "column": (3, 1), "gold": (4, 1),
    "dark": (5, 1),
}
GLOWING = {"screen", "lantern"}


def paint_atlas():
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def cell(name):
        cx, cy = SWATCHES[name]
        return cx * CELL, cy * CELL

    def fill(name, colour):
        x, y = cell(name)
        d.rectangle([x, y, x + CELL - 1, y + CELL - 1], fill=colour)
        return x, y

    # A glassy slate-blue tile: lighter rim on two edges, a soft highlight across it.
    x, y = fill("tile", (74, 104, 140, 255))
    d.line([x, y, x + 15, y], fill=(120, 150, 186, 255))
    d.line([x, y, x, y + 15], fill=(120, 150, 186, 255))
    d.line([x + 15, y + 1, x + 15, y + 15], fill=(50, 72, 102, 255))
    d.line([x + 1, y + 15, x + 15, y + 15], fill=(50, 72, 102, 255))
    for i in range(3, 9):
        d.point((x + i, y + 12 - i), fill=(104, 136, 172, 255))
    fill("grout", (44, 52, 64, 255))
    # White sides with the dark markings of the anime arena.
    x, y = fill("side", (226, 228, 232, 255))
    d.rectangle([x, y + 4, x + 15, y + 5], fill=(46, 52, 62, 255))
    d.rectangle([x + 2, y + 8, x + 6, y + 11], fill=(46, 52, 62, 255))
    d.rectangle([x + 9, y + 8, x + 13, y + 11], fill=(46, 52, 62, 255))
    d.line([x, y + 15, x + 15, y + 15], fill=(160, 164, 172, 255))
    # Red ends with a white band.
    x, y = fill("end", (178, 34, 38, 255))
    d.rectangle([x, y + 5, x + 15, y + 6], fill=(236, 236, 236, 255))
    d.line([x, y + 15, x + 15, y + 15], fill=(120, 20, 24, 255))
    x, y = fill("rim", (206, 210, 216, 255))
    d.line([x, y, x + 15, y], fill=(236, 238, 242, 255))
    x, y = fill("red", (190, 38, 42, 255))
    d.line([x, y, x + 15, y], fill=(226, 80, 80, 255))
    d.line([x, y + 15, x + 15, y + 15], fill=(120, 22, 26, 255))
    x, y = fill("blue", (40, 84, 186, 255))
    d.line([x, y, x + 15, y], fill=(90, 132, 224, 255))
    d.line([x, y + 15, x + 15, y + 15], fill=(22, 46, 116, 255))
    # A dark screen with cyan read-outs (the life point display).
    x, y = fill("screen", (16, 30, 40, 255))
    for row in (3, 7, 11):
        d.line([x + 2, y + row, x + 13, y + row], fill=(80, 230, 240, 255))
    d.rectangle([x + 2, y + 13, x + 7, y + 14], fill=(250, 200, 70, 255))
    x, y = fill("pylon", (92, 98, 110, 255))
    d.line([x + 7, y, x + 7, y + 15], fill=(70, 74, 84, 255))
    x, y = fill("lantern", (255, 214, 120, 255))
    d.rectangle([x + 3, y + 3, x + 12, y + 12], fill=(255, 240, 190, 255))
    fill("trim", (240, 240, 242, 255))
    x, y = fill("column", (150, 156, 168, 255))
    for col in (2, 7, 12):
        d.line([x + col, y, x + col, y + 15], fill=(118, 124, 136, 255))
    fill("gold", (214, 172, 64, 255))
    fill("dark", (36, 40, 48, 255))
    return img


def glow_mask(atlas):
    mask = Image.new("RGBA", atlas.size, (0, 0, 0, 0))
    for name in GLOWING:
        cx, cy = SWATCHES[name]
        box = (cx * CELL, cy * CELL, cx * CELL + CELL, cy * CELL + CELL)
        mask.paste(atlas.crop(box), box[:2])
    return mask


def faces(default, **override):
    """Per-face UVs: every face shows one whole swatch, stretched over the face."""
    out = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        cx, cy = SWATCHES[override.get(face, default)]
        out[face] = {"uv": [cx * CELL, cy * CELL], "uv_size": [CELL, CELL]}
    return out


def cube(x1, y1, z1, x2, y2, z2, swatch, rotation=None, pivot=None, **override):
    c = {"origin": [x1, y1, z1], "size": [x2 - x1, y2 - y1, z2 - z1], "uv": faces(swatch, **override)}
    if rotation:
        c["rotation"] = rotation
        c["pivot"] = pivot
    return c


def platform():
    cubes = [cube(-HALF, 0, -HALF, HALF, TOP - 2, HALF, "side", north="end", south="end", up="grout",
                  down="dark")]
    size = 2 * HALF // TILES
    for i in range(TILES):
        for j in range(TILES):
            x = -HALF + i * size
            z = -HALF + j * size
            cubes.append(cube(x + 1, TOP - 2, z + 1, x + size - 1, TOP, z + size - 1, "tile", down="grout"))
    # A low rim around the edge.
    cubes += [
        cube(-HALF, TOP - 2, -HALF, HALF, TOP + 1, -HALF + 2, "rim"),
        cube(-HALF, TOP - 2, HALF - 2, HALF, TOP + 1, HALF, "rim"),
        cube(-HALF, TOP - 2, -HALF + 2, -HALF + 2, TOP + 1, HALF - 2, "rim"),
        cube(HALF - 2, TOP - 2, -HALF + 2, HALF, TOP + 1, HALF - 2, "rim"),
    ]
    return cubes


def pylons():
    cubes = []
    for sx in (-1, 1):
        for sz in (-1, 1):
            x = sx * (HALF - 9)
            z = sz * (HALF - 9)
            cubes += [
                cube(x - 7, TOP, z - 7, x + 7, TOP + 6, z + 7, "dark"),
                cube(x - 4, TOP + 6, z - 4, x + 4, TOP + 84, z + 4, "pylon"),
                cube(x - 3, TOP + 30, z - 5, x + 3, TOP + 70, z + 5, "trim", east="trim", west="trim"),
                cube(x - 7, TOP + 84, z - 7, x + 7, TOP + 98, z + 7, "lantern", up="gold", down="gold"),
                cube(x - 8, TOP + 98, z - 8, x + 8, TOP + 101, z + 8, "gold"),
                cube(x - 2, TOP + 101, z - 2, x + 2, TOP + 110, z + 2, "gold"),
            ]
    return cubes


def podium(sign, colour):
    """A duelist's podium at the +z (sign 1) or -z (sign -1) end, its front desk toward the middle."""
    def zs(a, b):
        lo, hi = sorted((sign * a, sign * b))
        return lo, hi

    near = PODIUM_ALONG - 24  # 7.5 blocks: the podium's edge toward the middle
    far = PODIUM_ALONG + 24  # 10.5 blocks: the platform's end
    cubes = []
    z1, z2 = zs(near, far)
    cubes.append(cube(-24, TOP - 1, z1, 24, TOP + 1, z2, colour, down="dark"))
    # A raised floor plate in the arena's tile blue, with a trim edge.
    z1, z2 = zs(near + 8, far - 2)
    cubes.append(cube(-20, TOP + 1, z1, 20, TOP + 2, z2, "tile"))
    # The front desk: low enough to look over, a red or blue body, a white trim stripe and a slanted screen.
    z1, z2 = zs(near, near + 6)
    cubes.append(cube(-22, TOP + 1, z1, 22, TOP + 10, z2, colour, up="trim"))
    z1, z2 = zs(near - 1, near)
    cubes.append(cube(-22, TOP + 5, z1, 22, TOP + 7, z2, "trim"))
    z1, z2 = zs(near - 1, near + 7)
    pz = sign * (near + 3)
    cubes.append(cube(-16, TOP + 10, z1, 16, TOP + 11, z2, "screen", rotation=[sign * 15, 0, 0],
                      pivot=[0, TOP + 10, pz], down="dark"))
    # Side wings beside the duelist.
    for sx in (-1, 1):
        x1, x2 = sorted((sx * 20, sx * 24))
        z1, z2 = zs(near + 6, far - 6)
        cubes.append(cube(x1, TOP + 1, z1, x2, TOP + 9, z2, colour, up="trim"))
    # A back rail.
    z1, z2 = zs(far - 4, far)
    cubes.append(cube(-24, TOP + 1, z1, 24, TOP + 6, z2, colour, up="gold"))
    return cubes


def column(sign):
    """What the podium stands on while it is raised; grows from the platform as the podium goes up."""
    z1, z2 = sorted((sign * (PODIUM_ALONG - 22), sign * (PODIUM_ALONG + 22)))
    return [
        cube(-22, TOP, z1, 22, TOP + LIFT, z2, "column", up="dark", down="dark"),
    ]


def geometry():
    bones = [
        {"name": "arena", "pivot": [0, 0, 0], "cubes": platform() + pylons()},
        {"name": "column_a", "parent": "arena", "pivot": [0, TOP, 0], "cubes": column(1)},
        {"name": "column_b", "parent": "arena", "pivot": [0, TOP, 0], "cubes": column(-1)},
        {"name": "podium_a", "parent": "arena", "pivot": [0, TOP, PODIUM_ALONG], "cubes": podium(1, "red")},
        {"name": "podium_b", "parent": "arena", "pivot": [0, TOP, -PODIUM_ALONG], "cubes": podium(-1, "blue")},
    ]
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.duel_arena", "texture_width": ATLAS, "texture_height": ATLAS,
                "visible_bounds_width": 24, "visible_bounds_height": 10, "visible_bounds_offset": [0, 4, 0],
            },
            "bones": bones,
        }],
    }


def animations():
    flat = [1, 0.01, 1]

    def move(start, end, loop=False):
        bones = {}
        for name in ("podium_a", "podium_b"):
            bones[name] = {"position": {"0.0": [0, start, 0], str(SECONDS): [0, end, 0]}}
        for name in ("column_a", "column_b"):
            bones[name] = {"scale": {"0.0": flat if start == 0 else [1, 1, 1],
                                     str(SECONDS): flat if end == 0 else [1, 1, 1]}}
        anim = {"animation_length": SECONDS, "bones": bones}
        if loop:
            anim["loop"] = True
        return anim

    still = {"loop": True, "bones": {
        "podium_a": {"position": [0, 0, 0]}, "podium_b": {"position": [0, 0, 0]},
        "column_a": {"scale": flat}, "column_b": {"scale": flat},
    }}
    return {"format_version": "1.8.0", "animations": {
        "raise": move(0, LIFT), "lower": move(LIFT, 0), "lowered": still,
    }}


def kit_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 3, 14, 13], fill=(226, 228, 232, 255))
    d.rectangle([1, 12, 14, 13], fill=(150, 154, 162, 255))
    d.rectangle([2, 4, 13, 11], fill=(74, 104, 140, 255))
    for i in (5, 8, 11):
        d.line([i, 4, i, 11], fill=(44, 52, 64, 255))
    d.line([2, 8, 13, 8], fill=(44, 52, 64, 255))
    d.rectangle([0, 6, 2, 9], fill=(190, 38, 42, 255))
    d.rectangle([13, 6, 15, 9], fill=(40, 84, 186, 255))
    for x in (1, 14):
        for y in (1, 2):
            d.point((x, y), fill=(255, 214, 120, 255))
    return img


def main():
    atlas = paint_atlas()
    (ASSETS / "geo/block").mkdir(parents=True, exist_ok=True)
    (ASSETS / "animations/block").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo/block/duel_arena.geo.json").write_text(json.dumps(geometry(), separators=(",", ":")))
    (ASSETS / "animations/block/duel_arena.animation.json").write_text(json.dumps(animations(), indent=1))
    atlas.save(ASSETS / "textures/block/duel_arena.png")
    glow_mask(atlas).save(ASSETS / "textures/block/duel_arena_glowmask.png")
    kit_icon().save(ASSETS / "textures/item/duel_arena_kit.png")


if __name__ == "__main__":
    main()
