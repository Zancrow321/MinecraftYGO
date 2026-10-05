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

In game, `/ygo version` reports the loaded OCG-Core version.

## Licenses and credits

- This mod: GPL-3.0.
- OCG-Core: AGPL-3.0-or-later, © Project Ignis contributors. Because the mod bundles it, the mod's source must stay public.
- Monster models: MIT, © iconmaster.
- Yu-Gi-Oh! is a trademark of Konami. This is an unofficial fan project, and no card artwork is bundled.
