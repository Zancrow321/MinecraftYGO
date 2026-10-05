# MinecraftYGO

Fully automated Yu-Gi-Oh! duels in Minecraft (NeoForge 26.1.2), powered by the EDOPro rules engine
[ygopro-core](https://github.com/edo9300/ygopro-core) ("ocgcore") and the
[ProjectIgnis card scripts](https://github.com/ProjectIgnis/CardScripts). Duels are played in the world: cards lie
on a Duel Arena, face-up monsters appear as 3D figures from
[YGOMCModels](https://github.com/iconmaster5326/YGOMCModels), choices are made on holographic prompts.

> Status: early development. The headless engine (native core + Java bindings + bots) works; the Minecraft side is
> being built milestone by milestone.

## Modules

| Module | Content |
|---|---|
| `ocgcore-native/` | ygopro-core as git submodule + CMake build of the shared library, layout probe, constants dump |
| `engine/` | Pure Java 25 engine (no Minecraft dependency): FFM bindings, message parser, prompt/response encoding, duel session, bots |
| `tools/` | Build-time data pipeline (card pool, model conversion) |
| `neoforge/` | The mod (coming) |

## Building

Requirements: JDK 25, CMake ≥ 3.20, a C++17 compiler, git.

```bash
git submodule update --init --recursive
./gradlew :engine:test          # builds ocgcore for the host, fetches pinned card DB/scripts, runs all engine tests
./gradlew syncUpstreams         # fetches all pinned upstream data (incl. the large YGOMCModels repo)
./gradlew :engine:generateOcgConstants   # after bumping the ygopro-core submodule
```

Pinned upstream revisions live in `sources.lock.json`; ygopro-core, CardScripts and BabelCDB must be bumped together.

## License

AGPL-3.0-or-later (see `LICENSE` and `NOTICE`). Unofficial fan project, not affiliated with Konami.
