#!/usr/bin/env python3
"""Builds a resource pack that gives monsters a 3D model.

Takes Blockbench models (.bbmodel, "Bedrock Entity" or "GeckoLib Animated Model" format, with their animations) and
the card each one is for, and writes a resource pack the mod picks up: a monster with a model in the pack is drawn
with it instead of its artwork hologram, and a pack model replaces a bundled one for the same card.

  <out>/pack.mcmeta
  <out>/assets/<namespace>/geo/monster/<name>.geo.json
  <out>/assets/<namespace>/animations/monster/<name>.animation.json
  <out>/assets/<namespace>/textures/monster/<name>.png
  <out>/assets/<namespace>/jadm/models.json

The first animation in a model is also used as "idle", the loop a monster plays on the field. The texture is the
model's own unless --model gives one. Zip the output folder's contents (or use --zip) and put it in
resourcepacks/.

Usage:
  python3 tools/models/make_pack.py --namespace mypack --out MyMonsters \\
      --model 44508094 StardustDragon.bbmodel \\
      --model 20721928 Sparkman.bbmodel sparkman_texture.png --zip
"""
import argparse
import base64
import json
import re
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from import_models import bounds, convert_animations, convert_geometry  # noqa: E402

PACK_FORMAT = 34  # Minecraft 1.21.1


def embedded_texture(model):
    """The first texture saved inside the .bbmodel, as PNG bytes, or None."""
    for texture in model.get("textures", []):
        source = texture.get("source", "")
        if source.startswith("data:image/png;base64,"):
            return base64.b64decode(source.split(",", 1)[1])
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--namespace", required=True, help="the pack's namespace, lower case, e.g. mypack")
    parser.add_argument("--out", required=True, type=Path, help="folder to write the pack to")
    parser.add_argument("--model", required=True, action="append", nargs="+", metavar=("CODE", "BBMODEL"),
                        help="a card passcode, its .bbmodel and optionally a texture .png (repeatable)")
    parser.add_argument("--description", default="Monster models for Just Another Dueling Mod")
    parser.add_argument("--zip", action="store_true", help="also write <out>.zip")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9_.-]+", args.namespace):
        sys.exit("the namespace may only hold a-z, 0-9, _ . and -")

    assets = args.out / "assets" / args.namespace
    for sub in ("geo/monster", "animations/monster", "textures/monster", "jadm"):
        (assets / sub).mkdir(parents=True, exist_ok=True)
    entries = []
    for spec in args.model:
        if len(spec) not in (2, 3):
            sys.exit(f"--model takes CODE BBMODEL [TEXTURE], got {spec}")
        code, bbmodel = int(spec[0]), Path(spec[1])
        name = re.sub(r"[^a-z0-9_]+", "_", bbmodel.stem.lower()).strip("_")
        model = json.loads(bbmodel.read_text(encoding="utf-8"))
        geo = convert_geometry(model, name)
        animations = convert_animations(model)
        if not animations["animations"]:
            sys.exit(f"{bbmodel} has no animation; add at least one (it becomes the idle loop)")
        (assets / f"geo/monster/{name}.geo.json").write_text(json.dumps(geo, separators=(",", ":")), encoding="utf-8")
        (assets / f"animations/monster/{name}.animation.json").write_text(
            json.dumps(animations, separators=(",", ":")), encoding="utf-8")
        texture = assets / f"textures/monster/{name}.png"
        if len(spec) == 3:
            shutil.copy(spec[2], texture)
        else:
            png = embedded_texture(model)
            if png is None:
                sys.exit(f"{bbmodel} has no embedded PNG texture; pass one after it")
            texture.write_bytes(png)
        width, height = bounds(geo)
        entries.append({"code": code, "model": name, "width": width, "height": height,
                        "animations": sorted(animations["animations"])})
        print(f"{code}: {name} ({width} x {height} blocks, animations {sorted(animations['animations'])})")

    (assets / "jadm/models.json").write_text(json.dumps(entries, indent=1) + "\n", encoding="utf-8")
    (args.out / "pack.mcmeta").write_text(json.dumps(
        {"pack": {"pack_format": PACK_FORMAT, "description": args.description}}, indent=1) + "\n", encoding="utf-8")
    if args.zip:
        archive = shutil.make_archive(str(args.out), "zip", args.out)
        print(f"wrote {archive}")
    print(f"wrote {len(entries)} models to {args.out}")


if __name__ == "__main__":
    main()
