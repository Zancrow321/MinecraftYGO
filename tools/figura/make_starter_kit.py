"""Builds the Figura starter kit's model: a Millennium Puzzle and a Battle City long coat over the player's own skin.

The model is written as a Blockbench file, so it can be opened and reshaped there; the script that animates it is
figura/ygo-duelist/script.lua. Coordinates follow Figura's player template: pixels, feet at the origin, facing north.

Run from the repository root: python3 tools/figura/make_starter_kit.py
"""
import base64
import io
import json
import random
import uuid
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path("figura/ygo-duelist/model.bbmodel")
UV = 64  # UV units across the texture
TEXELS = 4  # texture pixels per UV unit, so small faces like the Eye can carry detail
SIZE = UV * TEXELS

WHITE = (236, 236, 240)
SHADE = (200, 200, 212)
NAVY = (40, 46, 92)
GOLD = (232, 182, 58)
GOLD_DARK = (168, 120, 32)
CORD = (70, 52, 34)

# name: (u1, v1, u2, v2) in UV units
REGIONS = {
    "white": (0, 0, 8, 8),
    "shade": (8, 0, 16, 8),
    "navy": (16, 0, 24, 8),
    "gold": (24, 0, 32, 8),
    "cord": (32, 0, 40, 8),
    "hem": (40, 0, 48, 10),
    "coat_front": (0, 16, 8, 28),
    "puzzle": (16, 16, 26, 26),
}

rng = random.Random(7)


def stable_uuid(name):
    """Blockbench wants uuids; deriving them from names keeps the generated file stable between runs."""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "minecraftygo/figura/" + name))


def box(region):
    u1, v1, u2, v2 = REGIONS[region]
    return u1 * TEXELS, v1 * TEXELS, u2 * TEXELS, v2 * TEXELS


def noisy(draw, region, colour, amount=6):
    x1, y1, x2, y2 = box(region)
    for x in range(x1, x2):
        for y in range(y1, y2):
            d = rng.randint(-amount, amount)
            draw.point((x, y), tuple(max(0, min(255, c + d)) for c in colour) + (255,))


def textures():
    base = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d, g = ImageDraw.Draw(base), ImageDraw.Draw(glow)
    noisy(d, "white", WHITE, 4)
    noisy(d, "shade", SHADE, 4)
    noisy(d, "navy", NAVY, 4)
    noisy(d, "gold", GOLD, 10)
    noisy(d, "cord", CORD, 6)

    # Coat hem: white with a navy band at the bottom.
    x1, y1, x2, y2 = box("hem")
    noisy(d, "hem", WHITE, 4)
    d.rectangle((x1, y2 - TEXELS, x2 - 1, y2 - 1), fill=NAVY + (255,))

    # Coat front: the coat hangs open, so only the outer edges of the chest are white.
    x1, y1, x2, y2 = box("coat_front")
    for x in range(x1, x2):
        for y in range(y1, y2):
            if x < x1 + 2 * TEXELS or x >= x2 - 2 * TEXELS:
                d.point((x, y), tuple(c + rng.randint(-4, 4) for c in WHITE) + (255,))
    d.rectangle((x1 + 2 * TEXELS - 2, y1, x1 + 2 * TEXELS - 1, y2 - 1), fill=NAVY + (255,))
    d.rectangle((x2 - 2 * TEXELS, y1, x2 - 2 * TEXELS + 1, y2 - 1), fill=NAVY + (255,))

    # Millennium Puzzle face: an inverted triangle with the Eye of Wdjat.
    x1, y1, x2, y2 = box("puzzle")
    w, h = x2 - x1, y2 - y1
    tri = [(x1, y1), (x2 - 1, y1), (x1 + w // 2, y2 - 1)]
    d.polygon(tri, fill=GOLD + (255,), outline=GOLD_DARK + (255,))
    for i in range(1, 4):  # the seams between the puzzle's pieces
        y = y1 + h * i // 4
        half = (w // 2) * (1 - i / 4)
        d.line((x1 + w // 2 - half + 1, y, x1 + w // 2 + half - 2, y), fill=GOLD_DARK + (255,))
    cx, cy = x1 + w // 2, y1 + h // 5
    eye = (cx - 8, cy - 3, cx + 8, cy + 4)
    d.ellipse(eye, outline=(60, 40, 10, 255), width=2)
    d.ellipse((cx - 3, cy - 2, cx + 3, cy + 3), fill=(60, 40, 10, 255))
    d.line((cx - 2, cy + 4, cx - 4, cy + 9), fill=(60, 40, 10, 255), width=2)  # the Eye's tear line
    g.ellipse(eye, outline=(255, 230, 120, 255), width=2)
    g.ellipse((cx - 3, cy - 2, cx + 3, cy + 3), fill=(255, 245, 190, 255))
    g.line((cx - 2, cy + 4, cx - 4, cy + 9), fill=(255, 230, 120, 255), width=2)
    g.polygon(tri, outline=(255, 210, 90, 255))
    return base, glow


def png(image):
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()


elements = []


def cube(name, frm, to, faces, origin=None, inflate=0.0):
    """faces: {direction: region}; directions left out or set to None are not drawn."""
    element = {
        "name": name, "type": "cube", "uuid": stable_uuid(name),
        "from": list(frm), "to": list(to), "origin": origin or [0, 0, 0], "inflate": inflate,
        "faces": {f: ({"uv": list(REGIONS[faces[f]]), "texture": 0} if faces.get(f) else {"uv": [0, 0, 0, 0], "texture": None})
                  for f in ("north", "east", "south", "west", "up", "down")},
    }
    elements.append(element)
    return element["uuid"]


def all_faces(region, **overrides):
    faces = {f: region for f in ("north", "east", "south", "west", "up", "down")}
    faces.update(overrides)
    return faces


def group(name, origin, children):
    return {"name": name, "uuid": stable_uuid("group/" + name), "origin": list(origin), "rotation": [0, 0, 0],
            "export": True, "visibility": True, "children": children}


# Millennium Puzzle on a cord, hanging in front of the chest.
puzzle = [
    cube("cord_left", (-1.75, 19.5, -2.35), (-1.25, 24, -2.05), all_faces("cord")),
    cube("cord_right", (1.25, 19.5, -2.35), (1.75, 24, -2.05), all_faces("cord")),
]
for i in range(5):
    half = (5 - i) / 2
    puzzle.append(cube(f"layer{i}", (-half, 18.5 - i, -3.25), (half, 19.5 - i, -2.25), all_faces("gold")))
puzzle.append(cube("face", (-2.5, 14.5, -3.3), (2.5, 19.5, -3.25), {"north": "puzzle"}))

# The long coat: an open-fronted shell over the torso, a stiff collar and tails down to the knees.
coat = [
    cube("shell", (-4, 12, -2), (4, 24, 2), all_faces("white", north="coat_front", down=None), inflate=0.35),
    cube("collar_back", (-4, 24, 1.6), (4, 27, 2.6), all_faces("white", north="navy")),
    cube("collar_left", (3.6, 24, -1.5), (4.6, 26.5, 2.6), all_faces("white", west="navy")),
    cube("collar_right", (-4.6, 24, -1.5), (-3.6, 26.5, 2.6), all_faces("white", east="navy")),
]
tails = [
    cube("tail_back", (-4.4, 2, 1.8), (4.4, 12, 2.5), all_faces("hem", north="shade", up="white", down="navy")),
    cube("tail_front_left", (1.3, 2, -2.5), (4.4, 12, -1.8), all_faces("hem", south="shade", up="white", down="navy")),
    cube("tail_front_right", (-4.4, 2, -2.5), (-1.3, 12, -1.8), all_faces("hem", south="shade", up="white", down="navy")),
    cube("tail_left", (3.7, 2, -1.8), (4.4, 12, 1.8), all_faces("hem", west="shade", up="white", down="navy")),
    cube("tail_right", (-4.4, 2, -1.8), (-3.7, 12, 1.8), all_faces("hem", east="shade", up="white", down="navy")),
]

outliner = [group("Body", (0, 24, 0), [
    group("Puzzle", (0, 24, -2.2), puzzle),
    group("Coat", (0, 24, 0), coat + [group("Tails", (0, 12, 0), tails)]),
])]

base, glow = textures()
model = {
    "meta": {"format_version": "4.5", "model_format": "free", "box_uv": False},
    "name": "model",
    "resolution": {"width": UV, "height": UV},
    "elements": elements,
    "outliner": outliner,
    "textures": [
        {"name": "duelist", "id": "0", "uuid": stable_uuid("texture/duelist"), "width": SIZE, "height": SIZE,
         "uv_width": UV, "uv_height": UV, "source": png(base)},
        {"name": "duelist_e", "id": "1", "uuid": stable_uuid("texture/duelist_e"), "width": SIZE, "height": SIZE,
         "uv_width": UV, "uv_height": UV, "source": png(glow)},
    ],
    "animations": [],
}
OUT.write_text(json.dumps(model, indent=1) + "\n")
print(f"wrote {OUT} ({len(elements)} cubes)")
