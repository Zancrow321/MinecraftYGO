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

## Building

```sh
git submodule update --init --recursive
python3 -m pip install meson ninja

./native/build.sh                       # Linux/macOS; on Windows run native/build.ps1 from a VS developer shell
./gradlew :engine:test -PengineOnly     # engine smoke tests against the native library
./gradlew :neoforge:build               # mod jar in neoforge/build/libs/
./gradlew :neoforge:runClient           # dev client
```

CI builds OCG-Core for Linux x86_64, Windows x86_64 and macOS (universal), runs the engine tests on each,
and packages all three into one mod jar.

In game:
- `/ygo version` reports the loaded OCG-Core version.
- `/ygo duel bot` or `/ygo duel <player>` (then `/ygo accept`) starts a duel; press Y to open the duel screen.
- `/ygo gallery [page]` puts a page of modeled monsters in front of you (operators only); `/ygo gallery clear` removes them.

Card artwork is downloaded on first view and cached in `minecraftygo/card_art/`. The source URL can be changed,
or downloads turned off, in `config/minecraftygo-client.toml`.

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

## Licenses and credits

- This mod: GPL-3.0.
- OCG-Core: AGPL-3.0-or-later, © Project Ignis contributors. Because the mod bundles it, the mod's source must stay public.
- Monster models: MIT, © iconmaster ([YGOMCModels](https://github.com/iconmaster5326/YGOMCModels)); the license ships as `assets/minecraftygo/YGOMCModels-LICENSE.md`.
- Card data and scripts: BabelCDB and CardScripts by Project Ignis (AGPL-3.0).
- Rendering: [GeckoLib](https://github.com/bernie-g/geckolib) (required dependency, MIT).
- Yu-Gi-Oh! is a trademark of Konami. This is an unofficial fan project, and no card artwork is bundled.
