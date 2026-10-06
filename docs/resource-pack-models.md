# Monster models from resource packs

A monster without a model stands on the field as an artwork hologram: its art (cropped, without the card frame)
floats above the zone in a frame of its attribute's color, with its name above and its stats below. A resource pack
can give any monster a real 3D model instead, and can replace a bundled model too. Packs are client-side: every
player sees the models of the packs they have turned on.

## Building a pack

Make the model in [Blockbench](https://www.blockbench.net/) as a "Bedrock Entity" or "GeckoLib Animated Model",
with at least one animation (the first one is also the idle loop a monster plays on the field). Then:

```sh
python3 tools/models/make_pack.py --namespace mypack --out MyMonsters \
    --model 44508094 StardustDragon.bbmodel \
    --model 20721928 Sparkman.bbmodel sparkman_texture.png --zip
```

Each `--model` takes a card passcode, the `.bbmodel` and optionally a texture `.png` (otherwise the texture saved in
the model is used). The same model can be given to several cards. Put `MyMonsters.zip` in `resourcepacks/` and turn
it on in Options > Resource Packs; the game log says how many monster models it found.

## What the pack contains

```
pack.mcmeta
assets/<namespace>/geo/monster/<name>.geo.json
assets/<namespace>/animations/monster/<name>.animation.json
assets/<namespace>/textures/monster/<name>.png
assets/<namespace>/jadm/models.json
```

`models.json` lists the cards the pack gives a model:

```json
[
 {"code": 44508094, "model": "stardustdragon", "width": 3.1, "height": 2.4,
  "animations": ["idle", "wings"]}
]
```

`model` is the file name under `geo/monster/`, `animations/monster/` and `textures/monster/` without the extension;
`width` and `height` are the model's size in blocks, used to scale it to its zone. When two packs map the same card,
the higher one in the resource pack list wins; a pack model always wins over a bundled one.
