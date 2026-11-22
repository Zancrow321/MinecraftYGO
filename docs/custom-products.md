# Custom products (datapacks)

A server can add its own booster packs, structure decks and tins. Each product is one JSON file in a datapack:

```
my_products.zip
├── pack.mcmeta
└── data/<namespace>/jadm/products/<id>.json
```

Put the datapack in `world/datapacks/` (single player: the world's Data Packs screen) and run `/reload`. The server
sends the products to every player, so clients need no datapack of their own.

- `/jadm products` lists the loaded products and everything that was wrong with their files (also in the server log).
- `/jadm products give <product> [player]` hands out a pack, deck or tin.

The product editor makes these datapacks without writing JSON: open `tools/product-editor/jadm-product-editor.html`
in any browser (it works offline), add products, search cards by name, set rarities and pack formats, see a sample
pack, and download the finished `.zip`. It also opens existing datapacks.

## Ids

The file's path is the product's id: `data/duelmc/jadm/products/legends.json` is `legends`, `event/tin.json` is
`event_tin`. A file whose id is a real product's id (for example `legend_of_blue_eyes_white_dragon`) replaces that
product. Of two files with the same id the one in the namespace that sorts last wins; `/jadm products` warns about it.

## Fields

| Field | For | Meaning |
|---|---|---|
| `type` | all | `booster`, `deck` or `tin` (required) |
| `name` | all | name shown on the item (required) |
| `code` | all | set code, e.g. `SVLG`; default: the id in capitals |
| `date` | all | `YYYY-MM-DD`: when it comes out in a progression world (after the real products of that day). Without one it is out from the start |
| `description` | all | an extra tooltip line |
| `image` | all | a picture: an `https://` address, or a texture from a resource pack (`duelmc:textures/item/legends.png`). Without one the item keeps its own icon |
| `price` | all | shop price (0–64) before the `[shop] priceMultiplier`; default: the `[shop]` price for its kind. 0 = not sold |
| `unlocks_cards` | all | default `true`: in a progression world its cards unlock with it, even if their real set comes later. `false` leaves the unlocks to the real sets |
| `cards` | all | the cards, see below |
| `pack` | booster | pack format, see below |
| `starter` | deck | default `true`: offered as a new player's first deck (if its main deck has 40–60 cards) |
| `packs` | tin | the packs in the tin: set codes or product ids, `{"product": "legends", "count": 2}` for several. Default: the three newest boosters by the tin's date |
| `promos` | tin | how many of the tin's cards opening it gives (default 1, different cards while there are any) |

### Cards

Each entry is a card's name, its passcode, or an object:

```json
"cards": [
  "Mystical Space Typhoon",
  46986414,
  {"card": "Blue-Eyes White Dragon", "rarity": ["Ultra Rare", "Starlight Rare"]},
  {"card": 89631139, "count": 3}
]
```

- `rarity`: one rarity or a list of the rarities it comes in, plainest first. Names (`Ultra Rare`), short names (`UR`,
  `QCScR`) and ids (`ultra`, `quarter_century`) all work. Default: `Common`, in a tin `Ultra Rare`. In a booster each
  rarity is a separate pull, as in the real sets: Starlight Rares come from the secret slot, a fifth as often.
- `count`: copies in a deck (structure decks hold exactly these copies). Listing a card twice adds up.
- Unknown cards are left out with a warning.

### Pack formats

`"pack"` is a name or your own format. Without it the pack is made as a core booster of its date (no date: modern):
nine cards, or seven rares and a foil if it has no commons, or four supers and a secret if its plainest cards are
supers.

| Name | Cards | Slots above the base rarity |
|---|---|---|
| `classic` | 9 | one rare slot (secret 1:24, ultra 1:12, super 1:6) |
| `modern` / `core` | 9 | a rare and a foil (secret 1:12, ultra 1:6, else super) |
| `rare_only` | 7 | a foil |
| `super_secret` | 5 | a secret |
| `mini` | 5 | a foil |
| `mini_classic` | 5 | one classic rare slot |
| `premium` | 5 | five premium foils (secret 1:4, ultra 1:2, super 1:4) |
| `tournament` | 3 | one classic rare slot |
| `battle` | 5 | one classic rare slot |

Your own:

```json
"pack": {"size": 6, "slots": ["rare", "foil", {"secret": 0.1, "ultra": 0.25, "super": 0.65}]}
```

`size` is the cards per pack (1–30); every card that isn't a slot has the set's base rarity (the plainest rarity at
least a tenth of its cards have). Slots: `classic`, `rare`, `foil`, `premium`, `secret`, `ultra`, `super`, or odds for
secret, ultra and super (the rest is a rare). A slot falls back to the next rarity the set has.

## Example

```json
{
  "type": "booster",
  "name": "Server Legends",
  "code": "SVLG",
  "date": "2003-01-01",
  "description": "Only on our server",
  "pack": "modern",
  "cards": [
    {"card": "Blue-Eyes White Dragon", "rarity": ["Ultra Rare", "Starlight Rare"]},
    {"card": "Dark Magician", "rarity": "Secret Rare"},
    {"card": "Red-Eyes Black Dragon", "rarity": "Super Rare"},
    {"card": "Summoned Skull", "rarity": "Rare"},
    "Mystical Space Typhoon", "Dark Hole", "Raigeki", "Monster Reborn", "Mirror Force", "Trap Hole"
  ]
}
```

```json
{
  "type": "tin",
  "name": "Server Legends Tin",
  "promos": 1,
  "cards": [{"card": "Blue-Eyes White Dragon", "rarity": "Ultra Rare"}, {"card": "Dark Magician", "rarity": "Ultra Rare"}],
  "packs": [{"product": "server_legends", "count": 2}, "LOB"]
}
```

## For developers

`JadmData.customProducts()` holds the loaded products; they are part of `JadmData.products()` like the real ones,
with `Product.custom()` set. The editor is built by `python3 tools/product-editor/build_editor.py` from
`tools/product-editor/editor.html` and the bundled card data; run it again after the card data changes.
