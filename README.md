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
| `tools/carddata/build_carddata.py` | Bundles card data, Lua scripts and system strings for every official card. Run it after `import_models.py`. |
| `tools/carddata/build_banlists.py` | Writes every TCG Forbidden & Limited List since 1999 with its date (`banlists.json`). |
| `tools/carddata/build_collection.py` | Picks the booster sets of the modeled pool, lists every TCG product in release order (`products.json`) and writes the era banlist. |
| `tools/textures/make_collection_textures.py` | Draws the pack, binder, deck box, Card Shop and Card Trader textures. |
| `tools/textures/make_logo.py` | Draws the mod logo (`minecraftygo_logo.png`) from the card back. |
| `tools/models/make_pack.py` | Builds a resource pack that gives monsters a 3D model from Blockbench files ([guide](docs/resource-pack-models.md)). |
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
- **Collecting:** cards come from **Booster Packs**, opened like the real product: a core booster has 9 cards (one
  rare or better until 2014, a rare and a foil after), a tournament pack 3, mini boosters and battle packs 5, and
  all-foil sets such as Dragons of Legend 5 foils. **Structure Decks** put their 40 cards into your binder, and a
  **Tin** gives its promo card and three packs. Packs and single cards turn up in dungeon, temple, mineshaft and
  treasure chests, and players sometimes get one from a mob they kill.
- The **Card Trader** villager sells binders, deck boxes, the two starter decks and later a duel disk, and keeps a
  shop of packs, structure decks and tins: the newest products always, plus a few older ones that change every week
  (`[shop] newestAlways`, `rotatingOlder`, `rotationDays`). Any villager takes the job at a **Card Shop Counter**
  (glass panes / plank, book, plank / three planks). The rest of the shop is set in `[shop]` of the server config
  too, and traders follow a change within a few seconds: `currency` (emeralds, or any item such as
  `"minecraft:diamond"`), `priceMultiplier`, `dynamicPrices` (prices that rise when a trade sells out often, as with
  other villagers), `wanderingTrader` (packs and a rare duel disk from wandering traders), the price of everything in
  `[shop.prices]` (core, all-foil and small packs, structure decks, tins, random packs per trader level, binders, deck
  boxes, starter decks, duel disks, paper bought, the wandering trader's two; 0 stops a trade) and how many a trader
  has before it restocks in `[shop.stock]`.
- The **Card Vending Machine** (three iron ingots / glass pane, emerald, glass pane / iron ingot, redstone, iron
  ingot) is the same shop without a villager: right-click it for the villager's trade window with the
  `[shop.prices]` and `currency`. Its prices don't rise with demand. `[shop.machine]` sets what it sells
  (`products = "rotation"`, the same pick as card traders, `"all"` or `"none"`, and `supplies` for random packs,
  binders, deck boxes, starter decks and duel disks), whether each player can only buy the `[shop.stock]` amounts a
  day (`limitPerPlayer`), `command` to open it anywhere with `/ygo shop` (operators always can), `operatorsOnly` so
  only operators place and break machines, `enabled` and the shop's `name`.
- **First deck:** on their first join a player picks a deck: Yugi's or Kaiba's starter deck, or any starter or
  structure deck that is already out. It comes in a deck box, ready to duel with. "Later" puts it off; `/ygo starter`
  opens the choice again until one is taken.
- A **Binder** (leather and paper around a string) holds your collection: "Put all cards in" moves every loose card
  into it, and clicking a card takes it back out (shift-click for every copy).
- A **Deck Box** (eight leather) holds a deck. Click a card on the right to add it, click it on the left to put it
  back; Fusion, Synchro, Xyz and Link monsters go to the extra deck below the main deck. A legal deck has 40 to 60
  main deck cards, up to 15 extra deck cards and at most three copies of a card, fewer for cards on the banlist.
  Your first legal deck box (hands first, then inventory) is the deck you duel with. Without one you duel with
  Yugi's starter deck; servers can require a deck box with `starterDecksWithoutDeckBox = false` in the world's
  `serverconfig/minecraftygo-server.toml`.
  The cards you own can be searched, filtered by type (monsters, spells, traps, extra deck), attribute and level,
  and sorted by name, ATK, DEF or level; the deck is listed monsters first, then spells and traps, with their counts
  under it. Export copies the deck as a YDK list (the format of YGOPro, EDOPro and most deck builders) and Import
  replaces it with the YDK list in the clipboard, built from the cards you own and saying which ones were missing.
- **Progression** (the default `[pool] mode = "progression"`): a new world starts with Legend of Blue Eyes White
  Dragon, and operators unlock the TCG products in release order. Every TCG product with new cards is a step; reprint
  packs come along with the next one. Cards from sets that are still locked show a padlock in binders and can't go in
  a deck (`[cards] lockedInDeck`). Duels follow the newest unlocked cards: Master Rule 1 until the first Xyz set, 2
  until the first Pendulum set, 3 until the first Link set, 4 until April 2020, then 5, with the TCG Forbidden &
  Limited List of the time. When two players at different steps duel, the one further along sets the rules; an NPC
  follows the player it duels.
  - `/ygo progression status` shows the current set, rules, banlist and the next set.
  - `/ygo progression next [count]` unlocks the next set (or several), `/ygo progression until <code or date>`
    everything up to `MRL` or `2005-03-01`, `/ygo progression set <code or date>` also goes back.
  - `/ygo progression list [page]` lists the sets in order with what is unlocked.
  - `[progression] scope = "player"` gives each player their own progress (`/ygo progression next <player>`),
    `startProduct` sets where worlds or players start and `announce` the chat messages.
- **Banlist:** `banlist = "auto"` (the default) is the TCG list of the time in a progression world, today's list with
  `mode = "all"` and the May 2000 OCG list for the modeled pool (which a server can still replace with
  `config/minecraftygo/banlist.json`). `"none"` turns it off, and any other value names a file in
  `config/minecraftygo/banlists/`, e.g. `banlist = "goat"` for `goat.json`, in the format of the bundled
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
- **Rules:** `ruleset` in `serverconfig/minecraftygo-server.toml` is `auto` (the default, see Progression above),
  `mr1` (original), `goat`, `mr2`, `mr3`, `mr4` or `modern`; `startingLifePoints` defaults to 8000.
- **Duel Dome:** craft a **Duel Dome Kit** (a Duel Dome Core, two Duelist Platforms, two quartz blocks, a sea lantern
  and light blue concrete) and use it on flat ground to build a 13 by 21 arena facing the way you look. When every
  duelist stands on a platform, one team at each end, the field appears over the core instead of between the
  players. Cores and platforms can also be placed by hand: platforms 4 to 14 blocks from the core, up to 2 off the
  center line.
- **Duel Arena:** craft a **Duel Arena Kit** (a Duel Dome Kit, two white concrete, red and blue concrete, two
  lanterns and two pistons) and use it on flat ground to set up the Duelist Kingdom arena: one model, a box 3 blocks tall
  and 21 by 27 blocks with steps up at each end, glass tiles on top, white sides, a ribbed red and a ribbed blue end,
  a red console podium and a blue pillar podium, sign posts and stained-glass lamp posts, facing the way you look. When every person
  in a duel stands on a podium, one team at each end, the field appears over the arena and the podiums carry the
  duelists 3 blocks up; they come back down when the duel ends. An NPC opponent walks over to the free podium. Break
  any part of the arena (not during a duel) to pack it back into its kit. The model, its texture and animations are
  generated by `tools/arena/make_arena.py`.
- **NPC duelists** wander the overworld now and then, each with a title, one of the default skins, a duel disk and
  its own deck: a well-known tournament deck of an era that has come (Goat Control, Chaos Return, Tele-DAD...), a
  starter or structure deck that is out, or a random deck from the card pool with an Extra Deck of what the pool
  offers, favoring cards with a 3D model. The NPC says which deck it plays. Right-click one to duel it; beat it and
  it gives you a booster pack, then won't duel you again for a Minecraft day. They can't be hurt while dueling. A
  **Duelist Spawn Egg** is in the creative tab. NPCs and the `bot` seats play with a heuristic AI that summons its
  strongest monsters, brings out a Synchro, Xyz or Link monster when that beats what it has, Pendulum Summons,
  only attacks when it wins the fight and saves its traps for your turn. Tournament decks live in
  `engine/src/main/resources/minecraftygo/tournament_decks.json`, listed by card name with the date they were
  played.
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
  buttons on the right, answer responses in their own window, click (or right-click) a graveyard, the banished cards
  or an extra deck to look through it, L opens the duel log, V switches between the
  top-down and first-person camera, and Escape opens the duel menu (camera, responses, surrender). Space passes a
  response, confirms a pick or goes to the next phase, Enter confirms a pick, 1 to 9 pick a plain answer, and C (or
  the Ask/Skip button under Log) switches between being asked to respond and passing every response you don't have
  to take. Banners and chimes mark each turn and phase, and life points count down when damage lands.
- `/ygo gallery [page]` puts a page of modeled monsters in front of you (operators only); `/ygo gallery clear` removes them.

Card artwork is downloaded on first view and cached in `minecraftygo/card_art/` (the cropped art for holograms in
`minecraftygo/card_art_cropped/`). The source URLs can be changed, or downloads turned off, in
`config/minecraftygo-client.toml`.

Monsters without a model stand on the field as an artwork hologram. A resource pack can give any monster a 3D model
instead; see [docs/resource-pack-models.md](docs/resource-pack-models.md).

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
    --strings Distribution/config/strings.conf --all
```

Every official OCG and TCG card is bundled, with its script. Which of them a world plays with is the server
config's `[pool] mode`: `progression` (the default) unlocks them set by set, `modeled` plays with the monsters that
have a model and the spells and traps of their era, and `all` plays with every card, with packs from every TCG
booster. Monsters without a model stand
on the field as an artwork hologram. The engine test `ScriptLoadTest` loads every card once; a card whose script fails goes
in `engine/src/main/resources/minecraftygo/broken.json`, which keeps it out of the pool.

New models in YGOMCModels join the modeled pool on the next run. A folder whose name doesn't match a card goes in
`tools/models/overrides.json`. The era cutoff is the newest release date shared by at least three modeled
monsters, and every spell and trap released up to it is playable.

The booster sets and the product list are rebuilt from the same YGOPRODeck dump and its set list:

```sh
curl -o cardsets.json https://db.ygoprodeck.com/api/v7/cardsets.php
python3 tools/carddata/build_collection.py --ygoprodeck ygoprodeck.json --cardsets cardsets.json
```

A set joins the modeled pool's boosters when at least 60% of its cards are in that pool.

The banlist history comes from yaml-yugi-limit-regulation:

```sh
git clone --depth 1 https://github.com/DawnbrandBots/yaml-yugi-limit-regulation
python3 tools/carddata/build_banlists.py --lists yaml-yugi-limit-regulation --ygoprodeck ygoprodeck.json
```

## Updating the duel disk model

The disk is the blocky Battle City duel disk by burning-icecream (see Licenses and credits), kept as the original
glTF in `tools/disk/source/`. `python3 tools/disk/import_gltf.py` puts it on the arm, packs its textures and writes
the standby pose and the `deploy`/`fold` animations into `tools/disk/duel_disk.bbmodel`; after that, run
`python3 tools/disk/convert_disk.py` and `python3 tools/textures/make_cosmetic_textures.py` (disk skins).

To edit it by hand instead, save the model from Blockbench with textures embedded as `tools/disk/duel_disk.bbmodel`,
then run `python3 tools/disk/convert_disk.py`. Only the `LeftArm` group is exported, in Figura's coordinates: the pivot on
the left-arm pivot (5, 22, 0), the arm on +X and the player facing north (-Z). A texture named `<name>_e` is the glowing layer of `<name>`. The animations
named `deploy` and `fold` play when a duel starts and ends; the rest pose is the disk when not dueling.

To check it in a dev client, `./gradlew :neoforge:runClient -PquickPlay=<world> -Pcamera=THIRD_PERSON_FRONT:90`
turns the body sideways so the disk faces the camera. `-PnoCurios` runs without Curios, and `-PuseItem=80,200`
uses the main-hand item at those ticks (to open packs, binders and deck boxes without a mouse).
`-PduelSetup=<file.lua>` runs a Lua script at the start of every duel to put cards on the field, e.g.
`Debug.AddCard(84013237,0,0,LOCATION_MZONE,1,POS_FACEUP_ATTACK,true)` (a second card added to the same monster zone
becomes an Xyz material, and `LOCATION_EMZONE` and `LOCATION_PZONE` reach the Extra Monster and Pendulum Zones).

## Licenses and credits

- This mod: GPL-3.0.
- OCG-Core: AGPL-3.0-or-later, © Project Ignis contributors. Because the mod bundles it, the mod's source must stay public.
- Duel disk model: "duel disk from Yu-Gi-Oh Duel Monsters (blocky)" by
  [burning-icecream](https://sketchfab.com/burning-icecream), from
  [Sketchfab](https://sketchfab.com/3d-models/duel-disk-from-yu-gi-oh-duel-monsters-blocky-14d4cc46e53a46ad903a9aa5f64e681c),
  [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/). Placed on the arm, given straps, a glowing layer,
  recolored skins and a standby pose with deploy/fold animations. Not for commercial use; the rest of the mod stays
  GPL-3.0. The notice ships as `assets/minecraftygo/DuelDisk-LICENSE.md`.
- Monster models: MIT, © iconmaster ([YGOMCModels](https://github.com/iconmaster5326/YGOMCModels)); the license ships as `assets/minecraftygo/YGOMCModels-LICENSE.md`.
- Card data and scripts: BabelCDB and CardScripts by Project Ignis (AGPL-3.0).
- Historical Forbidden & Limited Lists: gathered by DawnbrandBots'
  [yaml-yugi-limit-regulation](https://github.com/DawnbrandBots/yaml-yugi-limit-regulation) from Konami's published lists.
- Rendering: [GeckoLib](https://github.com/bernie-g/geckolib) (required dependency, MIT).
- Yu-Gi-Oh! is a trademark of Konami. This is an unofficial fan project, not affiliated with or endorsed by Konami, and no card artwork is bundled.
