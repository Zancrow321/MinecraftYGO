# Releasing

## Build

1. Set `mod_version` in `gradle.properties` and add a section to `CHANGELOG.md`.
2. Merge to `main`, then push a tag with the same version: `git tag v0.1.0 && git push origin v0.1.0`.
3. The Build workflow compiles OCG-Core for all three platforms, builds the jar and the Figura starter avatar, and
   attaches both to a GitHub Release for the tag (marked as a pre-release while the version starts with `0.`).
   The workflow fails if the tag doesn't match `mod_version`.

Only release jars built by CI: a local build contains the native library for your own platform only.

## Upload (Modrinth and CurseForge)

| Field | Value |
|---|---|
| Name | MinecraftYGO |
| Summary | Yu-Gi-Oh! duels in Minecraft, powered by the EDOPro engine. Duel disks, holographic monsters, booster packs and NPC duelists. |
| Description | [description.md](description.md) |
| Icon | `neoforge/src/main/resources/minecraftygo_logo.png` |
| Categories | Adventure, Game Mechanics, Minigame (CurseForge: Adventure and RPG, Mobs, Miscellaneous) |
| Environment | Client and server: required on both |
| Loader / version | NeoForge, Minecraft 1.21.1 |
| Release channel | Beta |
| License | GPL-3.0-only |
| Source | https://github.com/Zancrow321/MinecraftYGO |
| Issues | https://github.com/Zancrow321/MinecraftYGO/issues |
| Changelog | the version's section of `CHANGELOG.md` |

Dependencies: GeckoLib (required), Curios (optional), Figura (optional).

Files: `minecraftygo-<version>.jar` is the primary file. `ygo-duelist-figura-<version>.zip` is the Figura starter
avatar: on Modrinth it can be an additional file of the same version, on CurseForge an additional file. It is a
resource for Figura, not a mod, so players unzip it into `figura/avatars/`.

Gallery suggestions (from the development screenshots): the duel field with summon beams, a signature attack,
Battle City 2v2, the Duel Disk, booster pack and binder, an NPC duelist, the cosmetics screen and the Figura starter
avatar.

## Notes

- OCG-Core and the card scripts are AGPL-3.0. The mod's source must stay public for every released jar, which the
  GitHub repository and the tag cover.
- Card artwork is never bundled; it is fetched from YGOPRODeck at runtime.
- Keep the Konami disclaimer at the end of the description.
