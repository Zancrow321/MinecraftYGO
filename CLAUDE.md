# MinecraftYGO – notes for Claude

Minecraft (NeoForge 26.1.2, Java 25) Yu-Gi-Oh! dueling mod on top of edo9300/ygopro-core (API 11.0).

## Layout
- `ocgcore-native/` – submodule `ygopro-core` (+ Lua submodule) and `CMakeLists.txt`; `cmakeBuildHost` builds
  `libocgcore`, `ocg_layout_probe` and `ocg_constants_dump` into `ocgcore-native/build/cmake/`.
- `engine/` – pure Java, package `io.github.zancrow321.minecraftygo.engine`:
  `ffi` (FFM bindings, `OcgLayouts` verified by `LayoutProbeTest`), `message` (`MessageParser` for every core
  message, `Event`), `prompt` (`Prompt`, `PromptResponse`, `ResponseEncoder` mirrors `playerop.cpp` checks),
  `query` (`QueryParser`, `FieldStateReader` -> `FieldState`), `view` (`Visibility`: hidden-information rules, a
  whitelist), `duel` (`DuelSession`, `DuelRunner` = one thread per duel with prompt tickets, `HeadlessDuel`,
  `TagRotation`, `DuelReplay`), `ai` (`RandomLegalAgent`, `HeuristicAgent`, `SafeDefaultAgent`), `deck`
  (`DeckValidator`, `Banlist`, `YdkCodec`), `data` (`CardData`, `GoatVariantResolver`), `script`, `natives`.
  `src/generated/java/.../OcgConstants.java` is generated – regenerate with `:engine:generateOcgConstants`,
  never edit by hand (`checkOcgConstants` runs in `check`).
- Test fixtures (`engine/src/testFixtures`) read the pinned BabelCDB/CardScripts from `build/upstream/`.

- `tools/` – data pipeline (`./gradlew :tools:regenerate`, needs the big upstream checkouts):
  `pool.GeneratePool` maps YGOMCModels folders to passcodes (YGOJSON names, overrides in
  `tools/data/model-overrides.json`), computes the era cutoff and spells/traps (`tools/data/pool-config.json`) and
  writes `engine/src/generated/resources/minecraftygo/data/{cards.json,pool.json,texts_en.json,sets.json,scripts.txt}`
  plus `docs/PoolReport.md`; `bbmodel.ConvertModels` writes GeckoLib assets to `neoforge/src/generated/resources`
  (`geckolib/models/monster/<code>.geo.json`, animations, `textures/monster/<code>.png`, `ygo/monster_models.json`).
  All outputs are committed; `.github/workflows/data.yml` checks they are current.
- Hand-made assets: `assets-src/asset-contract.json` (required bones/animations per asset), sources in
  `assets-src/blockbench/<id>.bbmodel` + `<id>.animation.json`; `:tools:processAssets` converts them (placeholders
  when missing) and writes `data/minecraftygo/arena_layout/*.json`; `:tools:validateAssets` checks only. Workflow for
  Blockbench MCP sessions: `docs/assets.md`.
- `engine:bundleScripts` zips the scripts listed in `scripts.txt` into `minecraftygo/scripts.zip` (engine resources);
  `BundledData` loads cards/pool/texts/scripts from the classpath.

## Commands
- `./gradlew :engine:test` – full engine test suite (needs JDK 25 as JAVA_HOME, cmake, g++).
- `./gradlew :engine:test -Pygo.showTestOutput` – with stdout.
- `./gradlew :engine:test --tests '*FuzzDuelTest' -Pygo.fuzzDuels=500` – fuzzing with the leak oracle
  (`-Pygo.fuzzSeed=<n>` reproduces a failure).
- `-Pygo.nativeDir=<dir>` runs the engine tests against a prebuilt library + layout probe;
  `-Pygo.cmakeArgs="..."` passes extra CMake configure arguments.
- Upstream pins: `sources.lock.json` (`syncUpstream<Name>` tasks). ygopro-core (submodule), CardScripts and BabelCDB
  are bumped together; afterwards run `:engine:generateOcgConstants`, `:tools:regenerate` and the full test suite.

## Conventions
- Tabs in Java, 4 spaces in Kotlin DSL. Javac runs with `-Xlint:all -Werror`.
- Message formats must match ygopro-core exactly; when adding parsers, read the writer in the core source and keep
  `MessageParser`'s "consume exactly" rule.
- Never forward raw core messages to clients; per-player filtering happens in the engine.
- License AGPL-3.0-or-later. Do not copy code from Haxerus/duelcraft (All Rights Reserved).
