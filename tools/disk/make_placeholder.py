#!/usr/bin/env python3
"""Writes a stand-in duel_disk.bbmodel until the real Blockbench model is committed.

It follows the agreed design closely enough to exercise every part of the pipeline: a LeftArm root group, a round
hub and LP counter built from 24-sided meshes, a 3-zone and a 2-zone wing, a light-grey texture with light-blue
zones, an emissive _e texture, and "deploy"/"fold" animations in which the 2-zone wing swings round the hub and
docks onto the far end of the 3-zone wing, which points forward.

    python3 tools/disk/make_placeholder.py && python3 tools/disk/convert_disk.py
"""
import base64
import json
import math
import struct
import uuid
import zlib
from pathlib import Path

OUT = Path(__file__).with_name("duel_disk.bbmodel")
SIZE = 64
# Figura's LeftArm pivot; every coordinate below is relative to it.
ARM = (-5.0, 22.0, 0.0)
GREY, DARK, BLUE, BLUE_EDGE, RED = (0xC9, 0xCD, 0xD2), (0x5A, 0x5F, 0x66), (0x8F, 0xDC, 0xFF), (0x3A, 0x8F, 0xC0), (0xE2, 0x3A, 0x3A)


def png(pixels):
    raw = b"".join(b"\0" + bytes(c for px in row for c in px) for row in pixels)
    chunk = lambda tag, data: struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def textures():
    base = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    glow = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    for y in range(16):
        for x in range(64):
            if x < 16:
                base[y][x] = GREY + (255,)
            elif x < 32:
                base[y][x] = DARK + (255,)
            elif x < 48:
                edge = x % 8 in (0, 7) or y % 8 in (0, 7)
                base[y][x] = (BLUE_EDGE if edge else BLUE) + (255,)
                glow[y][x] = (BLUE_EDGE + (255,)) if edge else (0, 0, 0, 0)
            else:
                base[y][x] = RED + (255,)
                glow[y][x] = (0xFF, 0x50, 0x50, 255)
    uri = lambda p: "data:image/png;base64," + base64.b64encode(png(p)).decode()
    return [texture("duel_disk", uri(base)), texture("duel_disk_e", uri(glow))]


def texture(name, source):
    return {"name": name + ".png", "id": name, "uuid": str(uuid.uuid4()), "width": SIZE, "height": SIZE,
            "uv_width": SIZE, "uv_height": SIZE, "source": source}


def at(x, y, z):
    return [ARM[0] + x, ARM[1] + y, ARM[2] + z]


def cube(name, a, b, top_uv, side_uv=(16, 0, 32, 16)):
    faces = {f: {"uv": list(side_uv), "texture": 0} for f in ("north", "south", "east", "up", "down")}
    faces["west"] = {"uv": list(top_uv), "texture": 0}  # -x is the side facing away from the arm
    return {"type": "cube", "name": name, "uuid": str(uuid.uuid4()), "from": at(*a), "to": at(*b),
            "origin": at(*a), "rotation": [0, 0, 0], "faces": faces}


def cylinder(name, x0, x1, cy, cz, radius, cap_uv, side_uv=(16, 0, 32, 16), sides=24):
    """A round disc whose axis runs along x, with triangle-fan caps (Blockbench meshes mix tris and quads)."""
    verts, faces = {}, {}
    for i in range(sides):
        a = 2 * math.pi * i / sides
        verts[f"a{i}"] = [x0, cy + radius * math.sin(a), cz + radius * math.cos(a)]
        verts[f"b{i}"] = [x1, cy + radius * math.sin(a), cz + radius * math.cos(a)]
    verts["ca"], verts["cb"] = [x0, cy, cz], [x1, cy, cz]
    cu, cv = (cap_uv[0] + cap_uv[2]) / 2, (cap_uv[1] + cap_uv[3]) / 2
    r = (cap_uv[2] - cap_uv[0]) / 2
    for i in range(sides):
        j = (i + 1) % sides
        faces[f"s{i}"] = {"vertices": [f"a{i}", f"a{j}", f"b{j}", f"b{i}"], "texture": 0,
                          "uv": {f"a{i}": [side_uv[0], side_uv[1]], f"a{j}": [side_uv[2], side_uv[1]],
                                 f"b{j}": [side_uv[2], side_uv[3]], f"b{i}": [side_uv[0], side_uv[3]]}}
        for ring, center in (("a", "ca"), ("b", "cb")):
            uv = lambda k: [cu + r * math.cos(2 * math.pi * k / sides), cv + r * math.sin(2 * math.pi * k / sides)]
            faces[f"{ring}{i}c"] = {"vertices": [center, f"{ring}{i}", f"{ring}{j}"], "texture": 0,
                                    "uv": {center: [cu, cv], f"{ring}{i}": uv(i), f"{ring}{j}": uv(j)}}
    return {"type": "mesh", "name": name, "uuid": str(uuid.uuid4()), "origin": list(ARM), "rotation": [0, 0, 0],
            "vertices": verts, "faces": faces}


def group(name, origin, children):
    return {"name": name, "uuid": str(uuid.uuid4()), "origin": at(*origin), "rotation": [0, 0, 0],
            "children": children}


def keyframes(channel, points):
    return [{"channel": channel, "time": t, "interpolation": "catmullrom" if smooth else "linear",
             "data_points": [{"x": str(x), "y": str(y), "z": str(z)}]} for t, (x, y, z), smooth in points]


def main():
    hub_y, out = -6.0, -3.0  # wrist height, outer face of the arm
    elements = [
        cylinder("hub", out - 2, out, hub_y, 0, 4.5, (0, 0, 16, 16)),
        cylinder("lp_counter", out - 2.6, out - 2, hub_y, 0, 2, (48, 0, 64, 16)),
        cube("deck_holder", (out - 3.5, hub_y + 2.5, -1.75), (out - 2, hub_y + 4.5, 1.75), (16, 0, 32, 16)),
    ]
    wings = {}
    for name, start, zones in (("wing_large", -4 - 5 * 3, 3), ("wing_small", 4, 2)):
        parts = [cube(name + "_plate", (out - 1.5, hub_y - 2.5, start), (out - 0.5, hub_y + 2.5, start + 5 * zones),
                      (0, 0, 16, 16))]
        for i in range(zones):
            z = start + 0.5 + 5 * i
            parts.append(cube(f"{name}_zone{i}", (out - 2, hub_y - 2, z), (out - 1.5, hub_y + 2, z + 4),
                              (32, 0, 40, 8)))
        elements += parts
        wings[name] = group(name, (out - 1, hub_y, 0), [p["uuid"] for p in parts])
    root = group("LeftArm", (0, 0, 0), [e["uuid"] for e in elements[:3]] + list(wings.values()))

    small = wings["wing_small"]["uuid"]
    deploy = {"rotation": [(0, (0, 0, 0), True), (0.5, (180, 0, 0), True)],
              "position": [(0, (0, 0, 0), False), (0.25, (0, 0, 0), True), (0.8, (0, 0, -15), True)]}
    fold = {"rotation": [(0.3, (180, 0, 0), True), (0.8, (0, 0, 0), True)],
            "position": [(0, (0, 0, -15), True), (0.55, (0, 0, 0), True)]}
    animations = []
    for name, channels in (("deploy", deploy), ("fold", fold)):
        frames = keyframes("rotation", channels["rotation"]) + keyframes("position", channels["position"])
        animations.append({"uuid": str(uuid.uuid4()), "name": name, "loop": "hold", "length": 0.8,
                           "animators": {small: {"name": "wing_small", "type": "bone", "keyframes": frames}}})

    model = {"meta": {"format_version": "4.10", "model_format": "figura", "box_uv": False},
             "name": "duel_disk", "resolution": {"width": SIZE, "height": SIZE}, "elements": elements,
             "outliner": [root], "textures": textures(), "animations": animations}
    OUT.write_text(json.dumps(model, indent=1))
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
