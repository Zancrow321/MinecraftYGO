#!/usr/bin/env python3
"""Imports iconmaster's YGOMCModels into the mod and builds the card pool from them.

For every model folder it finds the card with that name, then writes:

  neoforge/src/main/resources/assets/jadm/geo/monster/<slug>.geo.json        GeckoLib geometry
  neoforge/src/main/resources/assets/jadm/animations/monster/<slug>.animation.json
  neoforge/src/main/resources/assets/jadm/textures/monster/<slug>.png
  engine/src/main/resources/jadm/models.json   which card uses which model
  engine/src/main/resources/jadm/pool.json     the playable pool: modeled monsters, plus every
                                                       spell and trap released up to the era cutoff

The era cutoff is the newest OCG release date shared by at least ERA_MIN_MODELS modeled monsters, so a
single reprint with a late date can't drag the whole era forward. Release dates come from the YGOProDeck API.
Run tools/carddata/build_carddata.py afterwards to bundle card data and scripts for the new pool.

Usage:
  git clone --depth 1 https://github.com/iconmaster5326/YGOMCModels
  git clone --depth 1 https://github.com/ProjectIgnis/BabelCDB
  curl -o ygoprodeck.json "https://db.ygoprodeck.com/api/v7/cardinfo.php?misc=yes"
  python3 tools/models/import_models.py --models YGOMCModels --cdb BabelCDB/cards.cdb --ygoprodeck ygoprodeck.json
"""
import argparse
import json
import re
import shutil
import sqlite3
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "neoforge/src/main/resources/assets/jadm"
ENGINE_RESOURCES = ROOT / "engine/src/main/resources/jadm"
OVERRIDES = Path(__file__).resolve().parent / "overrides.json"

ERA_MIN_MODELS = 3
TYPE_MONSTER, TYPE_SPELL, TYPE_TRAP, TYPE_TOKEN = 0x1, 0x2, 0x4, 0x4000


def key(name):
    return re.sub("[^a-z0-9]", "", name.lower())


def slug(folder):
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", "_", folder).lower()


def number(value):
    """Blockbench stores some keyframe values as strings ("0", or molang)."""
    if isinstance(value, str):
        try:
            value = float(value)
        except ValueError:
            return value
    return int(value) if float(value).is_integer() else round(value, 5)


def nonzero(vector):
    return vector and any(v for v in vector)


def convert_geometry(model, identifier):
    """Converts a bbmodel (Bedrock entity / GeckoLib format) into Bedrock geometry 1.12.0, as Blockbench exports it."""
    elements = {e["uuid"]: e for e in model["elements"] if e.get("type", "cube") == "cube"}
    groups = {g["uuid"]: g for g in model.get("groups", [])}
    bones = []
    names = Counter()

    def cube(e):
        size = [t - f for f, t in zip(e["from"], e["to"])]
        origin = list(e["from"])
        origin[0] = -(origin[0] + size[0])
        out = {"origin": [number(v) for v in origin], "size": [number(v) for v in size]}
        if nonzero(e.get("rotation")):
            pivot = list(e.get("origin", [0, 0, 0]))
            pivot[0] *= -1
            r = e["rotation"]
            out["pivot"] = [number(v) for v in pivot]
            out["rotation"] = [number(-r[0]), number(-r[1]), number(r[2])]
        if e.get("inflate"):
            out["inflate"] = number(e["inflate"])
        if e.get("box_uv", model["meta"].get("box_uv", True)):
            out["uv"] = [number(v) for v in e.get("uv_offset", [0, 0])]
            if e.get("mirror_uv"):
                out["mirror"] = True
        else:
            faces = {}
            for side, face in e.get("faces", {}).items():
                if face.get("texture") is None:
                    continue
                u0, v0, u1, v1 = face["uv"]
                faces[side] = {"uv": [number(u0), number(v0)], "uv_size": [number(u1 - u0), number(v1 - v0)]}
            out["uv"] = faces
        return out

    def unique(name):
        names[name] += 1
        return name if names[name] == 1 else f"{name}_{names[name]}"

    def visit(node, parent):
        # Newer Blockbench files keep group properties in "groups" and only uuid + children in the outliner.
        group = {**groups.get(node.get("uuid"), {}), **node}
        if group.get("export") is False:
            return
        bone = {"name": unique(group["name"])}
        if parent:
            bone["parent"] = parent
        pivot = list(group.get("origin", [0, 0, 0]))
        pivot[0] *= -1
        bone["pivot"] = [number(v) for v in pivot]
        if nonzero(group.get("rotation")):
            r = group["rotation"]
            bone["rotation"] = [number(-r[0]), number(-r[1]), number(r[2])]
        if group.get("mirror_uv"):
            bone["mirror"] = True
        bones.append(bone)
        cubes = []
        for child in group.get("children", []):
            if isinstance(child, str):
                if child in elements and elements[child].get("export", True):
                    cubes.append(cube(elements[child]))
            else:
                visit(child, bone["name"])
        if cubes:
            bone["cubes"] = cubes

    loose = []
    for node in model["outliner"]:
        if isinstance(node, str):
            if node in elements:
                loose.append(cube(elements[node]))
        else:
            visit(node, None)
    if loose:
        bones.insert(0, {"name": unique("root_cubes"), "pivot": [0, 0, 0], "cubes": loose})

    box = model.get("visible_box", [1, 1, 0])
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{identifier}",
                "texture_width": model["resolution"]["width"],
                "texture_height": model["resolution"]["height"],
                "visible_bounds_width": box[0],
                "visible_bounds_height": box[1],
                "visible_bounds_offset": [0, box[2], 0],
            },
            "bones": bones,
        }],
    }


def bounds(geo):
    """The model's size in blocks (ignoring rotations): width is the larger horizontal extent."""
    lo = [float("inf")] * 3
    hi = [float("-inf")] * 3
    for bone in geo["minecraft:geometry"][0]["bones"]:
        for c in bone.get("cubes", []):
            for i in range(3):
                origin = float(c["origin"][i])
                lo[i] = min(lo[i], origin)
                hi[i] = max(hi[i], origin + float(c["size"][i]))
    if lo[0] == float("inf"):
        return 0, 0
    width = max(hi[0] - lo[0], hi[2] - lo[2]) / 16
    return round(width, 3), round(hi[1] / 16, 3)


def convert_animations(model):
    """Converts the bbmodel's animations to Bedrock animation JSON. The first one is also exported as "idle"."""
    out = {}
    for animation in model.get("animations", []):
        bones = {}
        for animator in animation.get("animators", {}).values():
            channels = {}
            for frame in animator.get("keyframes", []):
                point = frame["data_points"][0]
                value = [number(point["x"]), number(point["y"]), number(point["z"])]
                channels.setdefault(frame["channel"], []).append((frame["time"], value, frame.get("interpolation")))
            bone = {}
            for channel, frames in channels.items():
                if channel not in ("rotation", "position", "scale"):
                    continue
                frames.sort(key=lambda f: f[0])
                if len(frames) == 1 and frames[0][0] == 0:
                    bone[channel] = {"vector": frames[0][1]}
                else:
                    timeline = {}
                    for time, value, interpolation in frames:
                        stamp = f"{number(time)}" if "." in str(number(time)) else f"{number(time)}.0"
                        timeline[stamp] = ({"post": value, "lerp_mode": "catmullrom"}
                                           if interpolation == "catmullrom" else value)
                    bone[channel] = timeline
            if bone:
                bones[animator["name"]] = bone
        name = animation["name"].split(".")[-1]
        entry = {"loop": animation.get("loop", "loop") != "once"}
        if animation.get("length"):
            entry["animation_length"] = number(animation["length"])
        entry["bones"] = bones
        out[name] = entry
    if out and "idle" not in out:
        out["idle"] = next(iter(out.values()))
    return {"format_version": "1.8.0", "animations": out}


def release_dates(ygoprodeck):
    dates = {}
    for card in json.loads(ygoprodeck.read_text(encoding="utf-8"))["data"]:
        misc = (card.get("misc_info") or [{}])[0]
        date = misc.get("ocg_date") or misc.get("tcg_date")
        for image in card.get("card_images", []):
            dates[image["id"]] = date
        dates[card["id"]] = date
    return dates


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--models", required=True, type=Path, help="path to a YGOMCModels checkout")
    parser.add_argument("--cdb", required=True, type=Path, help="path to BabelCDB cards.cdb")
    parser.add_argument("--ygoprodeck", required=True, type=Path, help="cardinfo.php?misc=yes download")
    args = parser.parse_args()

    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    db = sqlite3.connect(args.cdb)
    by_name = {}
    types = {}
    for code, name, alias, ctype in db.execute(
            "select d.id, t.name, d.alias, d.type from datas d join texts t on d.id = t.id"):
        types[code] = (ctype, alias)
        if alias == 0:
            by_name.setdefault(key(name), code)
    dates = release_dates(args.ygoprodeck)

    # Folders starting with "_" are April Fools models and pre-quest models with no guarantees; skip them.
    folders = sorted(p for p in args.models.iterdir() if p.is_dir() and not p.name.startswith((".", "_")))
    models = []
    unmatched = []
    for folder in folders:
        code = overrides["cards"].get(folder.name, by_name.get(key(folder.name)))
        if code is None:
            unmatched.append(folder.name)
            continue
        bbmodels = list(folder.glob("*.bbmodel"))
        texture = folder / "texture.png"
        if len(bbmodels) != 1 or not texture.exists():
            sys.exit(f"{folder.name}: expected one .bbmodel and texture.png")
        models.append((folder, code, slug(folder.name), bbmodels[0], texture))
    if unmatched:
        sys.exit(f"no card found for model folders {unmatched}; add them to {OVERRIDES}")
    codes = Counter(code for _, code, *_ in models)
    duplicates = [code for code, n in codes.items() if n > 1]
    if duplicates:
        sys.exit(f"several models map to the same card: {duplicates}")

    for sub in ("geo/monster", "animations/monster", "textures/monster"):
        shutil.rmtree(ASSETS / sub, ignore_errors=True)
        (ASSETS / sub).mkdir(parents=True)
    entries = []
    for folder, code, name, bbmodel, texture in models:
        model = json.loads(bbmodel.read_text(encoding="utf-8"))
        geo = convert_geometry(model, name)
        (ASSETS / f"geo/monster/{name}.geo.json").write_text(json.dumps(geo, separators=(",", ":")), encoding="utf-8")
        animations = convert_animations(model)
        (ASSETS / f"animations/monster/{name}.animation.json").write_text(
            json.dumps(animations, separators=(",", ":")), encoding="utf-8")
        shutil.copy(texture, ASSETS / f"textures/monster/{name}.png")
        width, height = bounds(geo)
        entries.append({"code": code, "model": name, "folder": folder.name, "width": width, "height": height,
                        "animations": sorted(animations["animations"])})
    shutil.copy(args.models / "LICENSE.md", ASSETS / "YGOMCModels-LICENSE.md")

    # Era cutoff: the newest release date shared by enough modeled monsters.
    per_date = Counter(dates.get(e["code"]) for e in entries if dates.get(e["code"]))
    cutoff = overrides.get("cutoff") or max(d for d, n in per_date.items() if n >= ERA_MIN_MODELS)
    spells_traps = sorted(code for code, (ctype, alias) in types.items()
                          if alias == 0 and ctype & (TYPE_SPELL | TYPE_TRAP) and not ctype & TYPE_MONSTER
                          and dates.get(code) and dates[code] <= cutoff)
    monsters = sorted(e["code"] for e in entries)
    late = sorted(e["folder"] for e in entries if (dates.get(e["code"]) or "") > cutoff)

    ENGINE_RESOURCES.mkdir(parents=True, exist_ok=True)
    (ENGINE_RESOURCES / "models.json").write_text(
        json.dumps(sorted(entries, key=lambda e: e["code"]), indent=1) + "\n", encoding="utf-8")
    (ENGINE_RESOURCES / "pool.json").write_text(json.dumps({
        "cutoff": cutoff,
        "monsters": monsters,
        "spellsTraps": spells_traps,
    }, indent=1) + "\n", encoding="utf-8")
    print(f"{len(entries)} models imported; era cutoff {cutoff}; pool: {len(monsters)} monsters, "
          f"{len(spells_traps)} spells/traps")
    if late:
        print(f"modeled monsters dated after the cutoff (still playable): {late}")


if __name__ == "__main__":
    main()
