# MinecraftYGO
New Take on a Old Game

Yu-Gi-Oh! duels in Minecraft (NeoForge 1.21.1). Rules are handled by [OCG-Core](https://github.com/edo9300/ygopro-core),
the engine behind EDOPro, running on the server. Monster models come from
[iconmaster's YGOMCModels](https://github.com/iconmaster5326/YGOMCModels).

## Layout

| Path | What |
|---|---|
| `engine/` | Plain-Java JNA bindings to OCG-Core (`OcgCore`, `OcgDuel`). No Minecraft dependency, so it builds and tests on its own. |
| `neoforge/` | The NeoForge mod. Compiles the engine in and bundles the native libraries. |
| `native/ocgcore` | OCG-Core source (git submodule). |
| `native/build.sh`, `native/build.ps1` | Build OCG-Core for the host into `engine/src/main/resources/natives/<platform>/`. |
| `tools/models/import_models.py` | Converts YGOMCModels to GeckoLib assets, maps each model to its card and writes the era pool (`pool.json`). |
| `tools/carddata/build_carddata.py` | Bundles card data, Lua scripts and system strings for the pool. Run it after `import_models.py`. |
| `tools/carddata/build_collection.py` | Picks the booster sets for the pool (with each card's rarity) and writes the era banlist. |
| `tools/textures/make_collection_textures.py` | Draws the pack, binder, deck box, Card Shop and Card Trader textures. |
| `tools/textures/make_logo.py` | Draws the mod logo (`minecraftygo_logo.png`) from the card back. |
| `tools/disk/convert_disk.py` | Converts the duel disk `.bbmodel` (Figura format, meshes allowed) into the mod's disk model. |

## Building

```sh
git submodule update --init --recursive
python3 -m pip install meson ninja

./native/build.sh                       # Linux/macOS; on Windows run native/build.ps1 from a VS developer shell
./gradlew :engine:test -PengineOnly     # engine smoke tests against the native library
./gradlew :engine:test -PengineOnly -Pplaytest=1000 --tests '*PlaytestTest'   # long bot playtest, report in engine/build/playtest.txt
./gradlew :neoforge:build               # mod jar in neoforge/build/libs/
./gradlew :neoforge:runClient           # dev client
```

CI builds OCG-Core for Linux x86_64, Windows x86_64 and macOS (universal), runs the engine tests on each,
and packages all three into one mod jar.

In game:
- Craft a **Duel Disk** (glass pane, redstone, glass pane / three iron ingots / one iron ingot below the middle) and
  wear it in its Curios slot, or in your off hand without Curios. Right-click another player who has a disk to
  challenge them; they right-click you back (or click [Accept]) and both disks unfold before the field appears.
- **Collecting:** cards come from **Booster Packs** (9 cards, one of them rare or better). Packs and single cards
  turn up in dungeon, temple, mineshaft and treasure chests, and players sometimes get one from a mob they kill.
  The **Card Trader** villager sells packs, binders, deck boxes, the two starter decks and later a duel disk; any
  villager takes the job at a **Card Shop Counter** (glass panes / plank, book, plank / three planks).
- A **Binder** (leather and paper around a string) holds your collection: "Put all cards in" moves every loose card
  into it, and clicking a card takes it back out (shift-click for every copy).
- A **Deck Box** (eight leather) holds a deck. Click a card on the right to add it, click it on the left to put it
  back. A legal deck has 40 to 60 main deck cards, up to 15 Fusion monsters and at most three copies of a card, fewer
  for cards on the banlist. Your first legal deck box (hands first, then inventory) is the deck you duel with. Without
  one you duel with Yugi's starter deck; servers can require a deck box with `starterDecksWithoutDeckBox = false` in the
  world's `serverconfig/minecraftygo-server.toml`.
- The banlist is an approximation of the OCG list from May 2000. A server can replace it with
  `config/minecraftygo/banlist.json`, in the same format as the bundled
  `engine/src/main/resources/minecraftygo/banlist.json` (`"limits": {"<card code>": <copies allowed>}`).
- **Ante:** `/ygo duel <player> ante`, or sneak while right-clicking with the disk. Each duelist puts up a random
  card from their deck box and the winner takes both. The cards are held by the server until the duel ends (a
  crash or restart returns them), and a winner who logged off gets them on their next login. Servers can turn
  ante off with `allowAnte = false`.
- **Tag duels (2v2):** `/ygo tag <partner> <opponent1> <opponent2>`, where any of them can be `bot`. Partners share
  life points and the field and take turns with their own decks; only the one whose turn it is answers prompts and
  sees the team's hand.
- **Battle City duels (2v2):** `/ygo battlecity <partner> <opponent1> <opponent2>` is a tag duel where each partner
  plays on their own half of the team's zones: monster and spell/trap zones 1-2 for the first partner, 4-5 for the
  second, the middle column shared. The second partner's half is drawn in green (orange for the opponents), and each
  half shows its owner's card sleeves. Anyone can attack any opponent's monster, and each team shares life points,
  since OCG-Core only knows two players (see the research note in the PR).
- **Attacks and signature moves:** every monster rears back and strikes when it attacks (head, jaw, arms and wings
  are posed from the model's bone names). Fan favourites have their own moves: Blue-Eyes' White Lightning (three beams
  for the Ultimate Dragon), Red-Eyes' Inferno Fire Blast, Dark Magic Attack and Summoned Skull's Lightning Strike, and
  Raigeki, Dark Hole, Mirror Force, Monster Reborn, Pot of Greed and Swords of Revealing Light have their own spell
  effects. So do Gaia's Spiral Shaver, the sword slashes of Black Luster Soldier, Celtic Guardian and Flame Swordsman,
  the dragons' breath (Curse of Dragon, Thousand Dragon, Baby Dragon), Barrel Dragon's shots, the Harpies, Exodia's
  summoning circle and Obliterate, Kuriboh's Multiply and Time Wizard's roulette, and Polymerization, Change of Heart,
  Harpie's Feather Duster, Mystical Space Typhoon, Heavy Storm, Trap Hole, Fissure, Hinotama, Ookazi, Sparks, Dian
  Keto, Waboku and the counter traps. Right-click a gallery monster to see its attack.
- **Rules:** `ruleset` in `serverconfig/minecraftygo-server.toml` is `mr1` (original, the default), `goat` or
  `modern`; `startingLifePoints` defaults to 8000.
- **Duel Dome:** craft a **Duel Dome Kit** (a Duel Dome Core, two Duelist Platforms, two quartz blocks, a sea lantern
  and light blue concrete) and use it on flat ground to build a 13 by 21 arena facing the way you look. When every
  duelist stands on a platform, one team at each end, the field appears over the core instead of between the
  players. Cores and platforms can also be placed by hand: platforms 4 to 14 blocks from the core, up to 2 off the
  center line.
- **NPC duelists** wander the overworld now and then, each with a title, one of the default skins, a duel disk and
  its own random deck from the card pool. Right-click one to duel it; beat it and it gives you a booster pack, then
  won't duel you again for a Minecraft day. They can't be hurt while dueling. A **Duelist Spawn Egg** is in the
  creative tab. NPCs and the `bot` seats play with a heuristic AI that summons its strongest monsters, only attacks
  when it wins the fight and saves its traps for your turn.
- **Cosmetics:** `/ygo cosmetics` (or K) picks your **disk skin** (Battle City, Slifer Red, Ra Yellow, Obelisk Blue,
  Shadow, Crimson, Gold) and your **card sleeves**, which your opponent sees on your face-down cards and piles.
  Locked ones show how to unlock them: winning duels, beating NPC duelists or opening booster packs. The skin is
  stored on the disk, so it goes wherever the disk goes.
- **Figura** (optional): avatar scripts get a `ygo` API and duel events, and can hide the disk to draw their own. See
  [docs/figura.md](docs/figura.md). A ready-made starter avatar (Millennium Puzzle, Battle City coat, duel reactions,
  emotes) lives in [figura/ygo-duelist](figura/ygo-duelist) and is built as its own download by
  `./gradlew figuraStarterKit`.
- `/ygo version` reports the loaded OCG-Core version.
- `/ygo duel bot` or `/ygo duel <player>` (then `/ygo accept`) starts a duel. In a duel you stand still
  and play with the mouse: click a card for its actions or drag it from your hand onto a zone, use the phase
  buttons on the right, answer responses in their own window, V switches between the top-down and first-person
  camera, and Escape opens the duel menu (camera, surrender).
- `/ygo gallery [page]` puts a page of modeled monsters in front of you (operators only); `/ygo gallery clear` removes them.

Card artwork is downloaded on first view and cached in `minecraftygo/card_art/`. The source URL can be changed,
or downloads turned off, in `config/minecraftygo-client.toml`.

## Releasing

See [docs/release/README.md](docs/release/README.md): pushing a `v<version>` tag builds the jar and the Figura
starter avatar and attaches them to a GitHub Release. What changed is in [CHANGELOG.md](CHANGELOG.md).

## Updating the card pool

```sh
git clone --depth 1 https://github.com/iconmaster5326/YGOMCModels
git clone --depth 1 https://github.com/ProjectIgnis/BabelCDB
git clone --depth 1 https://github.com/ProjectIgnis/CardScripts
git clone --depth 1 https://github.com/ProjectIgnis/Distribution
curl -o ygoprodeck.json "https://db.ygoprodeck.com/api/v7/cardinfo.php?misc=yes"
python3 tools/models/import_models.py --models YGOMCModels --cdb BabelCDB/cards.cdb --ygoprodeck ygoprodeck.json
python3 tools/carddata/build_carddata.py --cdb BabelCDB/cards.cdb --scripts CardScripts \
    --strings Distribution/config/strings.conf
```

New models in YGOMCModels join the pool on the next run. A folder whose name doesn't match a card goes in
`tools/models/overrides.json`. The era cutoff is the newest release date shared by at least three modeled
monsters, and every spell and trap released up to it is playable.

The booster sets are rebuilt from the same YGOPRODeck dump: `python3 tools/carddata/build_collection.py --ygoprodeck
ygoprodeck.json`. A set is included when at least 60% of its cards are in the pool.

## Updating the duel disk model

Save the model from Blockbench with textures embedded as `tools/disk/duel_disk.bbmodel`, then run
`python3 tools/disk/convert_disk.py`. Only the `LeftArm` group is exported, in Figura's coordinates: the pivot on
the left-arm pivot (5, 22, 0), the arm on +X and the player facing north (-Z). A texture named `<name>_e` is the glowing layer of `<name>`. The animations
named `deploy` and `fold` play when a duel starts and ends; the rest pose is the disk when not dueling.

To check it in a dev client, `./gradlew :neoforge:runClient -PquickPlay=<world> -Pcamera=THIRD_PERSON_FRONT:90`
turns the body sideways so the disk faces the camera. `-PnoCurios` runs without Curios, and `-PuseItem=80,200`
uses the main-hand item at those ticks (to open packs, binders and deck boxes without a mouse).

## Licenses and credits

- This mod: GPL-3.0.
- OCG-Core: AGPL-3.0-or-later, © Project Ignis contributors. Because the mod bundles it, the mod's source must stay public.
- Monster models: MIT, © iconmaster ([YGOMCModels](https://github.com/iconmaster5326/YGOMCModels)); the license ships as `assets/minecraftygo/YGOMCModels-LICENSE.md`.
- Card data and scripts: BabelCDB and CardScripts by Project Ignis (AGPL-3.0).
- Rendering: [GeckoLib](https://github.com/bernie-g/geckolib) (required dependency, MIT).
- Yu-Gi-Oh! is a trademark of Konami. This is an unofficial fan project, not affiliated with or endorsed by Konami, and no card artwork is bundled.
