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

## Commands
- `./gradlew :engine:test` – full engine test suite (needs JDK 25 as JAVA_HOME, cmake, g++).
- `./gradlew :engine:test -Pygo.showTestOutput` – with stdout.
- `./gradlew :engine:test --tests '*FuzzDuelTest' -Pygo.fuzzDuels=500` – fuzzing with the leak oracle
  (`-Pygo.fuzzSeed=<n>` reproduces a failure).
- `-Pygo.nativeDir=<dir>` runs the engine tests against a prebuilt library + layout probe;
  `-Pygo.cmakeArgs="..."` passes extra CMake configure arguments.
- Upstream pins: `sources.lock.json` (`syncUpstream<Name>` tasks).

## Conventions
- Tabs in Java, 4 spaces in Kotlin DSL. Javac runs with `-Xlint:all -Werror`.
- Message formats must match ygopro-core exactly; when adding parsers, read the writer in the core source and keep
  `MessageParser`'s "consume exactly" rule.
- Never forward raw core messages to clients; per-player filtering happens in the engine.
- License AGPL-3.0-or-later. Do not copy code from Haxerus/duelcraft (All Rights Reserved).
