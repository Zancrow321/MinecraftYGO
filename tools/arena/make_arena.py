#!/usr/bin/env python3
"""Generates the Duelist Kingdom arena: GeckoLib geometry, animations, texture atlas, glow mask and kit icon.

The arena is one block entity drawn as one model, after the arena in the anime: a long box 3 blocks tall with white
sides and dark markings, a ribbed red end and a ribbed blue end, a grid of grey-green glass tiles on top, a red
podium console on the red end and a blue pillar console on the blue end, red sign posts and stained-glass lamp
posts beside them, and steps up at either end. Units are pixels (16 per block), with the origin at the bottom
centre of the arena's middle block. The podiums sit on the z axis (red at +z).

Animations: "raise" lifts both podiums 3 blocks while a column grows under them, "lower" brings them back down,
"lowered" holds them on the platform.

    python3 tools/arena/make_arena.py
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / "neoforge/src/main/resources/assets/minecraftygo"

BLOCK = 16
ACROSS = 21 * BLOCK // 2  # 168: 10.5 blocks either side of the middle block's centre
ALONG = 27 * BLOCK // 2  # 216: 13.5 blocks toward either end
DECK = ALONG - 2 * BLOCK  # 184: where the coloured end decks start
TOP = 3 * BLOCK  # the platform is three blocks tall
PODIUM_ALONG = 12 * BLOCK  # where a duelist stands, measured to the centre of their block
LIFT = 3 * BLOCK
SECONDS = 2.0  # the server lifts duelists over the same 40 ticks

CELL = 16
ATLAS = 128
# Swatch name -> cell (column, row) in the atlas.
SWATCHES = {
    "tile": (0, 0), "grout": (1, 0), "side": (2, 0), "red_rib": (3, 0), "blue_rib": (4, 0), "red": (5, 0),
    "blue": (6, 0), "screen": (7, 0), "pole": (0, 1), "lamp": (1, 1), "white": (2, 1), "column": (3, 1),
    "gem": (4, 1), "dark": (5, 1), "sign": (6, 1), "blue_light": (7, 1), "red_dark": (0, 2), "side_plain": (1, 2),
}
GLOWING = {"screen", "lamp", "gem"}


def paint_atlas():
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def fill(name, colour):
        cx, cy = SWATCHES[name]
        x, y = cx * CELL, cy * CELL
        d.rectangle([x, y, x + CELL - 1, y + CELL - 1], fill=colour)
        return x, y

    # Grey-green glass tile with a pale sheen across it, as the anime draws the field.
    x, y = fill("tile", (92, 112, 112, 255))
    for i in range(16):
        for j in range(16):
            if 9 <= i + j <= 13:
                d.point((x + i, y + j), fill=(122, 142, 142, 255))
    d.line([x, y, x + 15, y], fill=(112, 132, 132, 255))
    d.line([x, y, x, y + 15], fill=(112, 132, 132, 255))
    fill("grout", (40, 46, 50, 255))
    # White sides: a dark horizontal dash and vertical bars, the arena's markings.
    x, y = fill("side", (232, 232, 236, 255))
    d.rectangle([x + 2, y + 5, x + 6, y + 6], fill=(40, 44, 60, 255))
    d.rectangle([x + 9, y + 3, x + 10, y + 11], fill=(40, 44, 60, 255))
    d.rectangle([x + 12, y + 3, x + 13, y + 11], fill=(40, 44, 60, 255))
    d.line([x, y + 15, x + 15, y + 15], fill=(176, 178, 186, 255))
    x, y = fill("side_plain", (232, 232, 236, 255))
    d.line([x, y + 15, x + 15, y + 15], fill=(176, 178, 186, 255))
    # Ribbed end panels.
    for name, base, groove in (("red_rib", (176, 40, 52, 255), (126, 24, 36, 255)),
                               ("blue_rib", (70, 74, 170, 255), (44, 46, 118, 255))):
        x, y = fill(name, base)
        for col in (3, 7, 11, 15):
            d.line([x + col, y, x + col, y + 15], fill=groove)
    fill("red", (186, 44, 56, 255))
    fill("red_dark", (130, 26, 38, 255))
    fill("blue", (84, 88, 190, 255))
    x, y = fill("blue_light", (176, 180, 236, 255))
    d.line([x, y + 15, x + 15, y + 15], fill=(120, 124, 200, 255))
    # The console's screen and keys.
    x, y = fill("screen", (30, 34, 40, 255))
    d.rectangle([x + 2, y + 2, x + 13, y + 7], fill=(60, 120, 130, 255))
    for i in range(2, 14, 3):
        for j in (10, 13):
            d.point((x + i, y + j), fill=(200, 210, 210, 255))
            d.point((x + i + 1, y + j), fill=(200, 210, 210, 255))
    x, y = fill("pole", (150, 154, 162, 255))
    d.line([x + 7, y, x + 7, y + 15], fill=(110, 114, 124, 255))
    # Stained-glass lamp: coloured panes.
    x, y = fill("lamp", (40, 40, 60, 255))
    panes = [(240, 80, 70), (250, 200, 60), (90, 200, 110), (80, 150, 240), (200, 100, 220)]
    for k, colour in enumerate(panes):
        d.rectangle([x + 1, y + 1 + 3 * k, x + 14, y + 2 + 3 * k], fill=colour + (255,))
    fill("white", (244, 244, 246, 255))
    x, y = fill("column", (196, 198, 206, 255))
    for col in (2, 7, 12):
        d.line([x + col, y, x + col, y + 15], fill=(160, 162, 172, 255))
    x, y = fill("gem", (40, 200, 120, 255))
    d.rectangle([x + 4, y + 4, x + 9, y + 9], fill=(170, 255, 200, 255))
    fill("dark", (34, 36, 44, 255))
    # The red posts' sign: a white hourglass on red.
    x, y = fill("sign", (186, 44, 56, 255))
    d.polygon([(x + 3, y + 2), (x + 12, y + 2), (x + 8, y + 8), (x + 12, y + 13), (x + 3, y + 13), (x + 7, y + 8)],
              fill=(244, 230, 230, 255))
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
    x1, x2 = sorted((x1, x2))
    z1, z2 = sorted((z1, z2))
    c = {"origin": [x1, y1, z1], "size": [x2 - x1, y2 - y1, z2 - z1], "uv": faces(swatch, **override)}
    if rotation:
        c["rotation"] = rotation
        c["pivot"] = pivot
    return c


def platform():
    cubes = []
    # The white middle of the box, in sections so its markings repeat along the sides.
    sections = 4
    step = 2 * DECK / sections
    for k in range(sections):
        z1 = -DECK + k * step
        cubes.append(cube(-ACROSS, 0, z1, ACROSS, TOP - 2, z1 + step, "side", up="grout", down="dark"))
    # Ribbed coloured ends, red at +z and blue at -z, with their deck on top.
    for sign, rib, deck in ((1, "red_rib", "red"), (-1, "blue_rib", "blue")):
        cubes.append(cube(-ACROSS, 0, sign * DECK, ACROSS, TOP, sign * ALONG, rib, up=deck, down="dark"))
        # Steps up to the deck behind the podium.
        cubes.append(cube(-24, 0, sign * ALONG, 24, 2 * BLOCK, sign * (ALONG + BLOCK), rib, up=deck, down="dark"))
        cubes.append(cube(-24, 0, sign * (ALONG + BLOCK), 24, BLOCK, sign * (ALONG + 2 * BLOCK), rib, up=deck,
                          down="dark"))
    # Glass tiles, 7 across and 8 along.
    nx, nz = 7, 8
    wx = (2 * ACROSS - 8) / nx
    wz = 2 * DECK / nz
    for i in range(nx):
        for j in range(nz):
            x = -ACROSS + 4 + i * wx
            z = -DECK + j * wz
            cubes.append(cube(x + 0.75, TOP - 2, z + 0.75, x + wx - 0.75, TOP, z + wz - 0.75, "tile", down="grout"))
    # A white lip along the long sides.
    for sx in (-1, 1):
        cubes.append(cube(sx * ACROSS, TOP - 2, -DECK, sx * (ACROSS - 4), TOP + 1, DECK, "white"))
    return cubes


def posts():
    cubes = []
    for sx in (-1, 1):
        x = sx * 80
        # Red end: sign posts with a white hourglass.
        z = DECK + 16
        cubes += [
            cube(x - 2, TOP, z - 2, x + 2, TOP + 36, z + 2, "pole"),
            cube(x - 9, TOP + 30, z - 3, x + 9, TOP + 60, z + 3, "sign", up="red_dark", down="red_dark"),
            cube(x - 7, TOP + 60, z - 2, x + 7, TOP + 64, z + 2, "red_dark"),
        ]
        # Blue end: lamp posts with stained-glass lamps.
        z = -(DECK + 16)
        cubes += [
            cube(x - 2, TOP, z - 2, x + 2, TOP + 52, z + 2, "pole"),
            cube(x - 6, TOP + 52, z - 6, x + 6, TOP + 74, z + 6, "lamp", up="blue", down="blue"),
            cube(x - 7, TOP + 74, z - 7, x + 7, TOP + 77, z + 7, "blue"),
            cube(x - 2, TOP + 77, z - 2, x + 2, TOP + 82, z + 2, "blue"),
        ]
    return cubes


def red_podium():
    """The red console: a box desk toward the middle with a slanted screen and keys, low walls either side."""
    near = PODIUM_ALONG - 18
    cubes = [
        cube(-24, TOP, near - 2, 24, TOP + 1, PODIUM_ALONG + 18, "red_dark"),
        cube(-20, TOP + 1, near, 20, TOP + 15, near + 8, "red", up="red_dark"),
        cube(-20, TOP + 15, near - 1, 20, TOP + 16, near + 9, "screen", rotation=[18, 0, 0],
             pivot=[0, TOP + 15, near + 4], down="red_dark"),
    ]
    for sx in (-1, 1):
        cubes.append(cube(sx * 20, TOP + 1, near, sx * 24, TOP + 12, PODIUM_ALONG + 14, "red", up="red_dark"))
    return cubes


def blue_podium():
    """The blue console: a tall chamfered pillar toward the middle with a green gem, low walls either side."""
    near = -(PODIUM_ALONG - 18)
    cubes = [
        cube(-24, TOP, near + 2, 24, TOP + 1, -(PODIUM_ALONG + 18), "blue"),
        cube(-11, TOP + 1, near, 11, TOP + 22, near - 10, "blue_light"),
        cube(-9, TOP + 22, near - 1, 9, TOP + 24, near - 9, "blue"),
        # Chamfered corners: the same block turned 45 degrees.
        cube(-8, TOP + 1, near - 13, 8, TOP + 21, near + 3, "blue_light", rotation=[0, 45, 0],
             pivot=[0, TOP, near - 5]),
        cube(-3, TOP + 14, near + 1, 3, TOP + 18, near, "gem"),
    ]
    for sx in (-1, 1):
        cubes.append(cube(sx * 20, TOP + 1, near, sx * 24, TOP + 12, -(PODIUM_ALONG + 14), "blue", up="blue_light"))
    return cubes


def column(sign):
    """What the podium stands on while it is raised; grows from the deck as the podium goes up."""
    return [cube(-24, TOP, sign * (PODIUM_ALONG - 20), 24, TOP + LIFT, sign * (PODIUM_ALONG + 18), "column",
                 up="dark", down="dark")]


def geometry():
    bones = [
        {"name": "arena", "pivot": [0, 0, 0], "cubes": platform() + posts()},
        {"name": "column_a", "parent": "arena", "pivot": [0, TOP, 0], "cubes": column(1)},
        {"name": "column_b", "parent": "arena", "pivot": [0, TOP, 0], "cubes": column(-1)},
        {"name": "podium_a", "parent": "arena", "pivot": [0, TOP, PODIUM_ALONG], "cubes": red_podium()},
        {"name": "podium_b", "parent": "arena", "pivot": [0, TOP, -PODIUM_ALONG], "cubes": blue_podium()},
    ]
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.duel_arena", "texture_width": ATLAS, "texture_height": ATLAS,
                "visible_bounds_width": 34, "visible_bounds_height": 14, "visible_bounds_offset": [0, 6, 0],
            },
            "bones": bones,
        }],
    }


def animations():
    flat = [1, 0.01, 1]

    def move(start, end):
        bones = {}
        for name in ("podium_a", "podium_b"):
            bones[name] = {"position": {"0.0": [0, start, 0], str(SECONDS): [0, end, 0]}}
        for name in ("column_a", "column_b"):
            bones[name] = {"scale": {"0.0": flat if start == 0 else [1, 1, 1],
                                     str(SECONDS): flat if end == 0 else [1, 1, 1]}}
        return {"animation_length": SECONDS, "bones": bones}

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
    d.rectangle([1, 3, 14, 13], fill=(232, 232, 236, 255))
    d.rectangle([1, 12, 14, 13], fill=(150, 154, 162, 255))
    d.rectangle([2, 4, 13, 11], fill=(92, 112, 112, 255))
    for i in (5, 8, 11):
        d.line([i, 4, i, 11], fill=(44, 52, 64, 255))
    d.line([2, 8, 13, 8], fill=(44, 52, 64, 255))
    d.rectangle([0, 6, 2, 9], fill=(190, 38, 42, 255))
    d.rectangle([13, 6, 15, 9], fill=(40, 84, 186, 255))
    for x in (1, 14):
        for y in (1, 2):
            d.point((x, y), fill=(90, 200, 110, 255))
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
