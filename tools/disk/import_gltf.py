#!/usr/bin/env python3
"""Builds tools/disk/duel_disk.bbmodel from the blocky Battle City duel disk by burning-icecream (CC BY-NC 4.0).

The model comes from Sketchfab as a glTF exported by Blockbench: one cube per node, five 32x32 textures, laid flat
in the duel pose (the bent 5-zone blade). This turns it into the Figura-format Blockbench file the mod's disk
pipeline reads (tools/disk/convert_disk.py):

- every cube is moved onto Figura's left arm (pivot 5, 22, 0): the hub on the outside of the forearm, the blade
  along the arm in front of it, scaled down to SCALE;
- the five textures are packed into one atlas, plus an _e layer that makes the card zones glow;
- the rest pose is standby: the 3-zone wing in front of the hub and the 2-zone wing behind it, both along the arm,
  like the toy disk. `deploy` swings the 2-zone wing under the hub onto the end of the 3-zone wing, which tilts
  into the original's bent blade; `fold` plays it backwards;
- two straps hold the hub on the arm (the original has none).

    python3 tools/disk/import_gltf.py [--gltf tools/disk/source/model.gltf]
    python3 tools/disk/convert_disk.py
    python3 tools/textures/make_cosmetic_textures.py
"""
import argparse
import base64
import io
import json
import math
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
TILE = 32  # size of each source texture
ATLAS = (128, 64)  # source texture i sits at TILE_AT[i]
TILE_AT = [(0, 0), (32, 0), (64, 0), (96, 0), (0, 32)]
GLOW_TILE = 2  # the card zones
GLOW_COLOURS = {(108, 204, 238), (222, 11, 11)}  # light blue zones and their red markers
STRAP_UV = (100, 18, 124, 30)  # black part of the vent texture

SCALE = 0.8
HUB_CENTRE = np.array([8.0, 0.0, 8.0])  # in the glTF, on the hub's underside
ARM_HUB = np.array([8.2, 15.0, 0.0])  # where it goes on the arm (Figura: left arm x 4..8, hand at y 12)
# glTF axes to Figura's: the face (+y) points out from the arm (+x), the blade's long axis (+x) runs up the arm (+y)
# and the blade (+z) sits in front of the arm (-z).
AXES = np.array([[0, 1, 0], [1, 0, 0], [0, 0, -1]], dtype=float)

HUB = "Hub"
WING_TWO = "WingTwo"
WING_THREE = "WingThree"
# glTF node -> group; the rest belongs to the hub.
GROUP_OF_NODE = {**{n: WING_TWO for n in range(3, 16)}, **{n: WING_THREE for n in range(18, 30)}}
# Standby, in glTF space: each wing turned to run along the arm (rotation about the face normal, degrees), its
# middle beside the hub at this offset across the blade (glTF z, from the hub centre).
STANDBY = {WING_THREE: (0.0, 9.0), WING_TWO: (180.0, -8.4)}
LIFT = 3.0  # how far the 2-zone wing lifts off the disk while it swings (arm-space pixels)

CUBE_FACES = {  # same corner order as convert_disk.py: top-left, top-right, bottom-right, bottom-left
    "north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
    "down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
}


def uid():
    return str(uuid.uuid4())


def rot_y(degrees):
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def quat_matrix(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def to_arm(p):
    """A glTF point (pixels) to Figura's arm space."""
    return ARM_HUB + SCALE * AXES @ (np.asarray(p, dtype=float) - HUB_CENTRE)


def load_gltf(path):
    g = json.loads(path.read_text(encoding="utf-8"))
    buffers = [base64.b64decode(b["uri"].split(",", 1)[1]) for b in g["buffers"]]

    def accessor(i):
        a = g["accessors"][i]
        view = g["bufferViews"][a["bufferView"]]
        width = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}[a["type"]]
        dtype = {5126: np.float32, 5123: np.uint16, 5125: np.uint32}[a["componentType"]]
        data = np.frombuffer(buffers[view["buffer"]], dtype=dtype, count=a["count"] * width,
                             offset=view.get("byteOffset", 0) + a.get("byteOffset", 0))
        return data.reshape(-1, width) if width > 1 else data

    images = [Image.open(io.BytesIO(base64.b64decode(im["uri"].split(",", 1)[1]))).convert("RGBA")
              for im in g["images"]]
    tile_of_material = [g["textures"][m["pbrMetallicRoughness"]["baseColorTexture"]["index"]]["source"]
                        for m in g["materials"]]
    nodes = []
    for index, node in enumerate(g["nodes"]):
        faces = []
        for prim in g["meshes"][node["mesh"]]["primitives"]:
            pos = accessor(prim["attributes"]["POSITION"]).astype(float)
            uv = accessor(prim["attributes"]["TEXCOORD_0"]).astype(float)
            used = sorted(set(int(i) for i in accessor(prim["indices"])))
            faces.append({"tile": tile_of_material[prim["material"]],
                          "corners": [(pos[i], uv[i]) for i in used]})
        corners = np.array([p for f in faces for p, _ in f["corners"]])
        nodes.append({"index": index, "lo": corners.min(0), "hi": corners.max(0), "faces": faces,
                      "rotation": quat_matrix(node.get("rotation", [0, 0, 0, 1])),
                      "translation": np.array(node.get("translation", [0, 0, 0]), dtype=float)})
    return nodes, images


def atlas(images):
    base = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    for image, at in zip(images, TILE_AT):
        base.paste(image, at)
    glow = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    src, dst = images[GLOW_TILE].load(), glow.load()
    for y in range(TILE):
        for x in range(TILE):
            if src[x, y][:3] in GLOW_COLOURS:
                dst[TILE_AT[GLOW_TILE][0] + x, TILE_AT[GLOW_TILE][1] + y] = src[x, y]
    return base, glow


class Placement:
    """Where one wing (or the hub) sits at rest: glTF-space points from the node's own frame to standby."""

    def __init__(self, rotation, offset):
        self.rotation = rotation
        self.offset = offset

    def __call__(self, local):
        return self.rotation @ local + self.offset


def wing_placements(nodes):
    """Per group: the standby placement of its nodes' local frames, and the rigid move from standby to duel."""
    placements, moves = {}, {}
    for group, (turn, across) in STANDBY.items():
        members = [n for n in nodes if GROUP_OF_NODE.get(n["index"]) == group]
        duel_rotation = members[0]["rotation"]
        ref = members[0]["translation"]
        # Wing frame w = R_duel^-1 (world - ref): the same for every node of the wing.
        pts = np.array([duel_rotation.T @ (n["translation"] - ref) + c for n in members for c in (n["lo"], n["hi"])])
        standby_rotation = rot_y(turn)
        turned = (standby_rotation @ pts.T).T
        middle = (turned.min(0) + turned.max(0)) / 2
        standby_offset = np.array([HUB_CENTRE[0] - middle[0], ref[1], HUB_CENTRE[2] + across - middle[2]])
        for n in members:
            shift = duel_rotation.T @ (n["translation"] - ref)
            placements[n["index"]] = Placement(standby_rotation, standby_rotation @ shift + standby_offset)
        # standby -> duel in glTF space: x_duel = A x_standby + b
        a = duel_rotation @ standby_rotation.T
        b = ref - a @ standby_offset
        moves[group] = (a, b)
    for n in nodes:
        if n["index"] not in placements:
            placements[n["index"]] = Placement(n["rotation"], n["translation"])
    return placements, moves


def cube(node, placement, name):
    """The node as a Blockbench cube in arm space; its UVs taken from the glTF's per-face vertices."""
    lo, hi = node["lo"], node["hi"]
    box = [[lo[i], hi[i]] for i in range(3)]
    corners = {(i, j, k): to_arm(placement(np.array([box[0][i], box[1][j], box[2][k]])))
               for i in (0, 1) for j in (0, 1) for k in (0, 1)}
    pts = np.array(list(corners.values()))
    frm, to = pts.min(0), pts.max(0)
    faces = {}
    for face_name, order in CUBE_FACES.items():
        targets = [np.array([(to if c[a] else frm)[a] for a in range(3)]) for c in order]
        # Find the glTF face whose four corners land on these four points.
        best = None
        for face in node["faces"]:
            placed = [(to_arm(placement(p)), uv) for p, uv in face["corners"]]
            uvs = []
            for t in targets:
                match = [uv for p, uv in placed if np.allclose(p, t, atol=1e-3)]
                if not match:
                    break
                uvs.append(match[0])
            if len(uvs) == 4:
                best = (face["tile"], uvs)
                break
        if best is None:
            raise SystemExit(f"node {node['index']}: no face for {face_name}")
        tile, uvs = best
        ox, oy = TILE_AT[tile]
        px = [(ox + u * TILE, oy + v * TILE) for u, v in uvs]
        uv, turns = face_uv(px, node["index"], face_name)
        faces[face_name] = {"uv": uv, "texture": 0, **({"rotation": turns * 90} if turns else {})}
    return {"name": name, "box_uv": False, "render_order": "default", "locked": False, "export": True,
            "allow_mirror_modeling": True, "from": r(frm), "to": r(to), "autouv": 0, "color": 0,
            "origin": r((frm + to) / 2), "faces": faces, "type": "cube", "uuid": uid()}


def face_uv(px, index, face_name):
    """Blockbench face uv [u1, v1, u2, v2] (+ rotation) giving these UVs at top-left, top-right, bottom-right,
    bottom-left; see convert_disk.py for how they are read back."""
    for turns in range(4):
        # convert_disk.py rotates the uv corners (u1v1, u2v1, u2v2, u1v2) right by `turns` before use.
        rect = px[turns:] + px[:turns]  # undo that rotation
        (u1, v1), (u2, v1b), (u2b, v2), (u1b, v2b) = rect
        if all(abs(a - b) < 1e-3 for a, b in ((v1, v1b), (u2, u2b), (u1, u1b), (v2, v2b))):
            return r([u1, v1, u2, v2]), turns
    raise SystemExit(f"node {index} {face_name}: UVs aren't a rectangle")


def r(values):
    return [round(float(v), 4) for v in values]


def strap(y, name):
    faces = {f: {"uv": list(STRAP_UV), "texture": 0} for f in CUBE_FACES}
    return {"name": name, "box_uv": False, "render_order": "default", "locked": False, "export": True,
            "allow_mirror_modeling": True, "from": [3.8, y, -2.2], "to": [8.2, y + 1.2, 2.2], "autouv": 0,
            "color": 0, "origin": [6, y + 0.6, 0], "faces": faces, "type": "cube", "uuid": uid()}


def x_degrees(a):
    """The angle of a rotation about Figura's x axis."""
    return math.degrees(math.atan2(a[2][1], a[1][1]))


def keyframes(group_origin, move, sweep):
    """Deploy end pose for a wing: Blockbench rotation/position keyframe values (see DiskModel.boneTransforms)."""
    a_gltf, b_gltf = move
    a = AXES @ a_gltf @ AXES.T  # the same move in arm space
    # x_arm = to_arm(A x + b) for x = to_arm^-1(x_arm): x_arm' = a x_arm + d
    d = to_arm(b_gltf + a_gltf @ HUB_CENTRE) - a @ ARM_HUB
    angle = x_degrees(a)
    if sweep is not None and (angle > 0) != (sweep > 0):
        angle += 360 if sweep > 0 else -360
    o = np.asarray(group_origin, dtype=float)
    p = d - o + a @ o  # translate(o + p) . rotate . translate(-o) = a x + d
    return {"x": -angle, "y": 0, "z": 0}, {"x": -p[0], "y": p[1], "z": p[2]}


def animator(name, frames):
    keys = []
    for channel, time, value in frames:
        keys.append({"channel": channel, "data_points": [{k: str(round(v, 4)) for k, v in value.items()}],
                     "uuid": uid(), "time": time, "color": -1, "interpolation": "catmullrom"})
    return {"name": name, "type": "bone", "keyframes": keys}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--gltf", type=Path, default=HERE / "source/model.gltf")
    parser.add_argument("--out", type=Path, default=HERE / "duel_disk.bbmodel")
    args = parser.parse_args()

    nodes, images = load_gltf(args.gltf)
    base, glow = atlas(images)
    placements, moves = wing_placements(nodes)

    groups = {name: {"name": name, "origin": r(ARM_HUB), "rotation": [0, 0, 0], "uuid": uid(), "export": True,
                     "visibility": True, "children": []}
              for name in ("LeftArm", "DuelDisk", "Bracer", HUB, WING_TWO, WING_THREE)}
    groups["LeftArm"]["origin"] = [5, 22, 0]
    elements = []
    counts = {}
    for node in nodes:
        group = GROUP_OF_NODE.get(node["index"], HUB)
        counts[group] = counts.get(group, 0) + 1
        element = cube(node, placements[node["index"]], f"{group.lower()}_{counts[group]}")
        elements.append(element)
        groups[group]["children"].append(element["uuid"])
    for y, name in ((ARM_HUB[1] - 3.2, "strap_wrist"), (ARM_HUB[1] + 2.0, "strap_elbow")):
        element = strap(round(y, 4), name)
        elements.append(element)
        groups["Bracer"]["children"].append(element["uuid"])

    deploy, fold = {}, {}
    for group, sweep, lift in ((WING_THREE, None, 0), (WING_TWO, 1, LIFT)):
        rotation, position = keyframes(groups[group]["origin"], moves[group], sweep)
        zero = {"x": 0, "y": 0, "z": 0}
        # Halfway the wing is lifted off the disk's face (keyframe x is mirrored), so it swings over the hub.
        half_rotation = {k: v / 2 for k, v in rotation.items()}
        half_position = {k: v / 2 for k, v in position.items()}
        half_position["x"] -= lift
        uuid_ = groups[group]["uuid"]
        deploy[uuid_] = animator(group, [("rotation", 0, zero), ("rotation", 0.4, half_rotation),
                                         ("rotation", 0.8, rotation), ("rotation", 1, rotation),
                                         ("position", 0, zero), ("position", 0.4, half_position),
                                         ("position", 0.8, position), ("position", 1, position)])
        fold[uuid_] = animator(group, [("rotation", 0, rotation), ("rotation", 0.2, rotation),
                                       ("rotation", 0.6, half_rotation), ("rotation", 1, zero),
                                       ("position", 0, position), ("position", 0.2, position),
                                       ("position", 0.6, half_position), ("position", 1, zero)])
        print(f"{group}: deploy rotation {rotation['x']:.1f}, position "
              f"{', '.join(f'{v:.2f}' for v in position.values())}")

    def outline(name, children):
        g = groups[name]
        return {"uuid": g["uuid"], "isOpen": False, "children": children + g["children"]}

    outliner = [outline("LeftArm", [outline("DuelDisk", [outline("Bracer", []), outline(HUB, []),
                                                         outline(WING_THREE, []), outline(WING_TWO, [])])])]

    def texture(name, image, index):
        buf = io.BytesIO()
        image.save(buf, "PNG")
        return {"name": name, "folder": "", "namespace": "", "id": str(index), "width": ATLAS[0],
                "height": ATLAS[1], "uv_width": ATLAS[0], "uv_height": ATLAS[1], "particle": False,
                "render_mode": "default", "render_sides": "auto", "visible": True, "internal": True,
                "saved": False, "uuid": uid(),
                "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}

    def animation(name, animators):
        return {"uuid": uid(), "name": name, "loop": "hold", "override": False, "length": 1, "snapping": 20,
                "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                "animators": animators}

    model = {
        "meta": {"format_version": "5.0", "model_format": "figura", "box_uv": False},
        "name": "duel_disk",
        "model_identifier": "",
        "visible_box": [1, 1, 0],
        "resolution": {"width": ATLAS[0], "height": ATLAS[1]},
        "elements": elements,
        "groups": [{k: v for k, v in g.items() if k != "children"} for g in groups.values()],
        "outliner": outliner,
        "textures": [texture("duel_disk.png", base, 0), texture("duel_disk_e.png", glow, 1)],
        "animations": [animation("deploy", deploy), animation("fold", fold)],
    }
    args.out.write_text(json.dumps(model, indent=1), encoding="utf-8")
    print(f"wrote {args.out.name}: {len(elements)} cubes")


if __name__ == "__main__":
    main()
