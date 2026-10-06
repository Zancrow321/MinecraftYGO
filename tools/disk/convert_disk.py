#!/usr/bin/env python3
"""Converts the duel disk .bbmodel (Figura model format) into the mod's own disk model format.

GeckoLib can't draw Blockbench meshes, and the disk's round hub and LP counter are meshes, so the mod renders the
disk itself. This bakes every cube and mesh into quads in Blockbench's absolute coordinates, keeps the group
hierarchy (pivot, rest rotation) for animation, and copies the animations' keyframes as they are. The game applies
transforms the way Blockbench displays them, so what you see in Blockbench is what you get on the arm.

Only the group named LeftArm (Figura's left-arm part) and everything inside it is exported, so reference models
next to it are ignored. Coordinates are Figura's: the left arm sits on +x and the player faces -z. Textures are written next to the model; a texture named <name>_e is the emissive layer of
<name>, as in Figura.

    python3 tools/disk/convert_disk.py [--model tools/disk/duel_disk.bbmodel] [--root LeftArm]
"""
import argparse
import base64
import json
import math
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / "neoforge/src/main/resources/assets/jadm"

# Vertex order per cube face (top-left, top-right, bottom-right, bottom-left as seen from outside), matching
# Blockbench's UV layout. Corners index into (x1|x2, y1|y2, z1|z2).
CUBE_FACES = {
    "north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
    "down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
}


def euler_zyx(rotation):
    """Rotation matrix for Blockbench's ZYX Euler order (applied X, then Y, then Z), in degrees."""
    x, y, z = (math.radians(a) for a in rotation)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return mul(rz, mul(ry, rx))


def mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def around(point, origin, rotation):
    rel = apply(euler_zyx(rotation), [point[i] - origin[i] for i in range(3)])
    return [rel[i] + origin[i] for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def normalize(v):
    length = math.sqrt(sum(c * c for c in v)) or 1
    return [c / length for c in v]


def num(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0  # Molang expressions aren't evaluated


class Converter:
    def __init__(self, model):
        self.model = model
        res = model.get("resolution", {})
        self.resolution = (res.get("width", 16), res.get("height", 16))
        self.textures = model.get("textures", [])
        self.groups = {g["uuid"]: g for g in model.get("groups", [])}
        self.elements = {e["uuid"]: e for e in model.get("elements", [])}
        self.base_textures = []  # bbmodel texture index -> output texture index
        self.texture_map = {}
        self.bones = []
        self.bone_of_uuid = {}

    # -- textures

    def texture_names(self):
        def stem(t):
            return Path(t.get("name") or t.get("id") or "texture").stem
        stems = [stem(t) for t in self.textures]
        for i, s in enumerate(stems):
            if not s.endswith("_e"):
                self.texture_map[i] = len(self.base_textures)
                self.base_textures.append({"index": i, "id": s, "emissive": f"{s}_e" in stems})
        return stems

    def uv_size(self, index):
        if index is None or index is False or not (0 <= int(index) < len(self.textures)):
            return self.resolution
        t = self.textures[int(index)]
        return (t.get("uv_width") or self.resolution[0], t.get("uv_height") or self.resolution[1])

    # -- geometry

    def quad(self, texture, points, uvs, normal=None):
        if texture is None or texture is False or int(texture) not in self.texture_map:
            return None
        w, h = self.uv_size(texture)
        if normal is None:
            normal = normalize(cross(sub(points[1], points[0]), sub(points[2], points[0])))
        verts = [[round(c, 5) for c in p] + [round(uv[0] / w, 6), round(uv[1] / h, 6)] for p, uv in zip(points, uvs)]
        while len(verts) < 4:
            verts.append(verts[-1])  # triangles become degenerate quads
        return {"t": self.texture_map[int(texture)], "v": verts, "n": [round(c, 5) for c in normal]}

    def cube_quads(self, e):
        inflate = e.get("inflate", 0)
        lo = [e["from"][i] - inflate for i in range(3)]
        hi = [e["to"][i] + inflate for i in range(3)]
        origin, rotation = e.get("origin", [0, 0, 0]), e.get("rotation", [0, 0, 0])
        quads = []
        for name, corners in CUBE_FACES.items():
            face = e.get("faces", {}).get(name)
            if not face or face.get("texture") is None or face.get("texture") is False:
                continue
            pts = [around([(hi if c[i] else lo)[i] for i in range(3)], origin, rotation) for c in corners]
            u1, v1, u2, v2 = face.get("uv", [0, 0, 0, 0])
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            turns = int(face.get("rotation", 0)) // 90 % 4
            uvs = uvs[-turns:] + uvs[:-turns] if turns else uvs
            # Emit counter-clockwise as seen from outside: top-right, top-left, bottom-left, bottom-right.
            order = (1, 0, 3, 2)
            q = self.quad(face["texture"], [pts[i] for i in order], [uvs[i] for i in order])
            if q:
                quads.append(q)
        return quads

    def mesh_quads(self, e):
        origin, rotation = e.get("origin", [0, 0, 0]), e.get("rotation", [0, 0, 0])
        verts = {k: around([v[i] + origin[i] for i in range(3)], origin, rotation)
                 for k, v in e.get("vertices", {}).items()}
        if not verts:
            return []
        centroid = [sum(v[i] for v in verts.values()) / len(verts) for i in range(3)]
        quads = []
        for face in e.get("faces", {}).values():
            keys = face.get("vertices", [])
            if len(keys) not in (3, 4):
                continue
            pts = [verts[k] for k in keys]
            center = [sum(p[i] for p in pts) / len(pts) for i in range(3)]
            normal = normalize(cross(sub(pts[1], pts[0]), sub(pts[2], pts[0])))
            if len(keys) == 4:
                # Blockbench doesn't keep quad vertices in order; sort them round the face so it isn't a bowtie.
                u = normalize(sub(pts[0], center))
                v = cross(normal, u)
                angle = lambda p: math.atan2(sum(a * b for a, b in zip(sub(p, center), v)),
                                             sum(a * b for a, b in zip(sub(p, center), u)))
                keys = sorted(keys, key=lambda k: angle(verts[k]))
                pts = [verts[k] for k in keys]
            # Meshes don't store which side is outside; assume the side away from the mesh's middle.
            if sum(n * d for n, d in zip(normal, sub(center, centroid))) < 0:
                keys, pts, normal = keys[::-1], pts[::-1], [-c for c in normal]
            uvs = [face.get("uv", {}).get(k, [0, 0]) for k in keys]
            q = self.quad(face.get("texture"), pts, uvs, normal)
            if q:
                quads.append(q)
        return quads

    # -- hierarchy

    def group(self, node):
        if isinstance(node, str):
            return self.groups.get(node)
        merged = dict(self.groups.get(node.get("uuid"), {}))
        merged.update({k: v for k, v in node.items() if v is not None})
        return merged

    def find_root(self, nodes, name):
        for node in nodes:
            if isinstance(node, str) and node in self.elements:
                continue
            g = self.group(node)
            if g is None:
                continue
            if g.get("name", "").lower() == name.lower():
                return g
            found = self.find_root(g.get("children", []), name)
            if found:
                return found
        return None

    def add_bone(self, g, parent):
        index = len(self.bones)
        self.bone_of_uuid[g.get("uuid")] = index
        bone = {"name": g.get("name", f"bone{index}"), "parent": parent, "origin": g.get("origin", [0, 0, 0]),
                "rotation": g.get("rotation", [0, 0, 0]), "quads": []}
        self.bones.append(bone)
        for child in g.get("children", []):
            element = self.elements.get(child) if isinstance(child, str) else None
            if element is not None:
                if element.get("export", True) is False or element.get("visibility", True) is False:
                    continue
                kind = element.get("type", "cube")
                if kind == "cube":
                    bone["quads"] += self.cube_quads(element)
                elif kind == "mesh":
                    bone["quads"] += self.mesh_quads(element)
                continue
            sub_group = self.group(child)
            if sub_group is not None and sub_group.get("export", True) is not False:
                self.add_bone(sub_group, index)

    # -- animations

    def animations(self):
        out = {}
        for anim in self.model.get("animations", []):
            bones = {}
            for key, animator in anim.get("animators", {}).items():
                bone = self.bone_of_uuid.get(key)
                if bone is None:
                    continue
                channels = {}
                for kf in animator.get("keyframes", []):
                    point = (kf.get("data_points") or [{}])[0]
                    channel = kf.get("channel")
                    if channel not in ("rotation", "position", "scale"):
                        continue
                    channels.setdefault(channel, []).append(
                        [round(num(kf.get("time")), 4)] + [num(point.get(a)) for a in "xyz"]
                        + [kf.get("interpolation", "linear")])
                for frames in channels.values():
                    frames.sort(key=lambda f: f[0])
                if channels:
                    bones[str(bone)] = channels
            out[anim["name"]] = {"length": num(anim.get("length")), "bones": bones}
        return out

    def rest_bounds(self):
        lo, hi = [math.inf] * 3, [-math.inf] * 3
        for bone in self.bones:
            for q in bone["quads"]:
                for v in q["v"]:
                    p = v[:3]
                    b = bone
                    while True:  # apply the rest rotations of the bone and its parents
                        p = around(p, b["origin"], b["rotation"])
                        if b["parent"] < 0:
                            break
                        b = self.bones[b["parent"]]
                    lo = [min(lo[i], p[i]) for i in range(3)]
                    hi = [max(hi[i], p[i]) for i in range(3)]
        return [round(c, 4) for c in lo], [round(c, 4) for c in hi]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model", type=Path, default=Path(__file__).with_name("duel_disk.bbmodel"))
    parser.add_argument("--root", default="LeftArm", help="group to export (Figura's left arm by default)")
    parser.add_argument("--name", default="duel_disk", help="asset name to write")
    args = parser.parse_args()

    model = json.loads(args.model.read_text(encoding="utf-8"))
    conv = Converter(model)
    stems = conv.texture_names()
    root = conv.find_root(model.get("outliner", []), args.root)
    if root is None:
        raise SystemExit(f"no group named {args.root} in {args.model}")
    conv.add_bone(root, -1)
    if not any(b["quads"] for b in conv.bones):
        raise SystemExit("the root group has no visible cubes or meshes with a texture")

    tex_dir = ASSETS / "textures/disk"
    tex_dir.mkdir(parents=True, exist_ok=True)
    out_textures = []
    for i, base in enumerate(conv.base_textures):
        name = args.name if len(conv.base_textures) == 1 else f"{args.name}_{base['id']}"
        for stem, suffix in ((base["id"], ""), (f"{base['id']}_e", "_e")):
            if stem not in stems:
                continue
            source = conv.textures[stems.index(stem)].get("source", "")
            if not source.startswith("data:image/png;base64,"):
                raise SystemExit(f"texture {stem} isn't embedded in the .bbmodel; save it with textures embedded")
            (tex_dir / f"{name}{suffix}.png").write_bytes(base64.b64decode(source.split(",", 1)[1]))
        out_textures.append({"id": name, "emissive": base["emissive"]})

    lo, hi = conv.rest_bounds()
    out = {"source": args.model.name, "pivot": root.get("origin", [0, 0, 0]), "bounds": [lo, hi],
           "textures": out_textures, "bones": conv.bones, "animations": conv.animations()}
    model_dir = ASSETS / "disk"
    model_dir.mkdir(parents=True, exist_ok=True)
    (model_dir / f"{args.name}.json").write_text(json.dumps(out, separators=(",", ":")), encoding="utf-8")
    quads = sum(len(b["quads"]) for b in conv.bones)
    print(f"wrote disk/{args.name}.json: {len(conv.bones)} bones, {quads} quads, "
          f"{len(out_textures)} texture(s), animations {sorted(out['animations'])}")


if __name__ == "__main__":
    main()
