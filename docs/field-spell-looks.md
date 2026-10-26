# Field Spell looks from resource packs

While a Field Spell is face-up, its owner's half of the duel field turns into that card's land: Umi floods it,
Yami covers it in shadow, Sogen grows grass in the wind. Every look is a JSON file in a resource pack, the mod's own
14 included, so a pack can add looks for more Field Spells and change or replace the built-in ones. Packs are
client-side: every player sees the looks of the packs they have turned on. Field Spells without a look tint the field
in the colour of the attribute their text is about.

## Where the files go

```
pack.mcmeta
assets/<namespace>/jadm/field_spells/<name>.json
assets/<namespace>/textures/block/<texture>.png      (optional, your own textures)
assets/<namespace>/sounds.json and sounds/            (optional, your own sounds)
```

Put the pack (folder or zip) in `resourcepacks/` and turn it on in Options > Resource Packs. F3+T reloads the looks
without restarting; the game log says how many looks it loaded and names any file it had to skip, with the reason.

- **A new look:** any namespace and any file name, for example `assets/mypack/jadm/field_spells/dark_city.json`.
- **Replacing a built-in look:** a file with the same name in the mod's namespace,
  `assets/jadm/jadm/field_spells/umi.json`, takes its place. A file in another namespace that names the same card
  also wins over the built-in one.
- The built-in files are in the mod jar under `assets/jadm/jadm/field_spells/` and make good starting points.
- [`examples/field-spell-pack`](examples/field-spell-pack) is a complete pack: a new look for Dark City with its own
  texture, and a red Umi that replaces the built-in one.

## The format

```json
{
  "cards": [53527835],
  "ground": [
    {"sprite": "mypack:block/neon_tile", "weight": 5},
    {"sprite": "minecraft:block/polished_blackstone_bricks", "weight": 1, "tint": "#C0C0FF"}
  ],
  "opacity": 0.95,
  "glow": "#FF28C8",
  "waves": 0,
  "wind": 0.3,
  "props": [
    {"shape": "box", "sprite": "minecraft:block/deepslate_bricks", "top": "minecraft:block/sea_lantern",
     "count": 14, "width": 0.6, "height": [2, 4]},
    {"shape": "peak", "sprite": "minecraft:block/gray_concrete", "top": "minecraft:block/redstone_lamp_on",
     "count": 5, "width": 1.2, "height": [3, 5]},
    {"shape": "cross", "sprite": "minecraft:block/short_grass", "tint": "#7CBD4B", "count": 40,
     "height": [0.6, 1.2], "sway": 0.3}
  ],
  "particles": [
    {"type": "minecraft:dust", "where": "ground", "perTick": 6, "spread": 2, "speed": 0.02,
     "color": "#FF28C8", "size": 1.2},
    {"type": "minecraft:falling_dust", "where": "wind", "perTick": 4, "block": "minecraft:red_sand"},
    {"type": "minecraft:electric_spark", "where": "peak", "perTick": 2}
  ],
  "sounds": {
    "pitch": 0.9,
    "reveal": ["minecraft:block.anvil.land", "minecraft:block.beacon.activate"],
    "ambient": "minecraft:ambient.cave"
  }
}
```

| Key | Meaning |
|---|---|
| `cards` | Passcodes of the Field Spells this look is for (required). Alternate artworks follow their original card. |
| `ground` | The ground's block textures (required): `sprite` is a texture in the block atlas, so `<namespace>:block/<name>` (any texture under `textures/block/`, vanilla, other mods or your pack; animated ones like `water_still` move). Each tile of the ground picks one, as often as its `weight`; `tint` multiplies its colour (grey textures such as water or grass need one). |
| `opacity` | How solid the ground is, 0 to 1 (default 0.88). Lower lets the arena floor show through. |
| `glow` | Colour of the glowing edge that spreads the ground out of the Field Zone. |
| `waves` | Height of waves that roll toward the outer edge, in blocks (0 or left out: flat ground). |
| `wind` | Wind strength: bends plants, adds wind streaks over the field and blows `wind` particles (0 to about 2). |
| `props` | What grows out of the ground around the mat. `shape` is `cross` (a plant: two crossed quads, like flowers and grass), `box` (a block-textured rock or pillar, `top` is its top texture) or `peak` (a three-tier mountain with `top` on its summit). `count` how many, `height` in blocks as one number or `[lowest, highest]`, `width` for boxes and peaks, `tint`, and `sway` for how far plants bend. |
| `particles` | Vanilla particles. `where`: `ground` (rise from the ground, up to `spread` blocks high), `edge` (along the outer edge, where waves break), `air` (drift down from up to `spread` blocks), `wind` (blown across the field) or `peak` (around the tops of peaks). `perTick` is how many per tick at full spread, `speed` how fast they fly off. `dust` takes `color` and `size`; `block` and `falling_dust` take `block`. |
| `sounds` | `reveal` play once when the card lands, each a little lower than the one before; `ambient` plays now and then while it lies there; `pitch` for both. Any sound id, including sounds your pack adds in its `sounds.json`. |

Everything except `cards` and `ground` is optional. The client setting `fieldSpellArena` turns all looks off.
