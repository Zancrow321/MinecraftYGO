# MinecraftYGO

**Yu-Gi-Oh! duels in Minecraft.** Strap on a Duel Disk, challenge a friend and watch your monsters appear on a
holographic field between you, Battle City style.

The rules are not a reimplementation: every duel runs on **OCG-Core**, the same engine that powers EDOPro, so card
effects, chains and timing work the way they do in the real game.

## Features

- **Real duels** against players or a bot, with the original rules, Goat format or modern rules.
- **A field in the world:** 622 monsters as 3D models, face-down cards, life points, summon beams and battle effects.
  Every monster strikes when it attacks, and fan favourites like Blue-Eyes, Dark Magician, Red-Eyes, Summoned Skull,
  Exodia and Kuriboh have their own signature moves. Classic spells and traps like Raigeki, Dark Hole, Mirror Force
  and Monster Reborn have their own effects too.
- **The Duel Disk** on your left arm unfolds when a duel starts. Right-click another duelist to challenge them.
- **Tag duels and Battle City 2v2**, ante duels, and a buildable **Duel Dome** arena.
- **Collect cards** from booster packs found in chests, dropped by mobs or bought from the Card Trader villager.
  Keep them in a binder and build decks in a deck box, with an era banlist.
- **NPC duelists** roam the world. Beat them for booster packs.
- **Cosmetics:** disk skins and card sleeves you unlock by dueling and opening packs.
- **Figura support:** avatar scripts can react to duels and replace the disk. A ready-made starter avatar with the
  Millennium Puzzle and a Battle City coat is available as a separate download.

## Getting started

Craft a Duel Disk (glass pane, redstone, glass pane / three iron ingots / one iron ingot), wear it in its Curios
slot or your off hand, and right-click another player who has one. Or type `/ygo duel bot` to duel the AI right
away. In a duel you play with the mouse from above the field: click a card for its actions or drag it onto a
zone, use the phase buttons on the right, **L** opens the duel log, **V** switches the camera, **Escape** opens the
duel menu, and **K** opens cosmetics. Right-click someone who is dueling to watch.

## Requirements

- Minecraft 1.21.1 with NeoForge 21.1
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.9 or newer (required)
- [Curios](https://modrinth.com/mod/curios) (optional, adds a Duel Disk slot)
- [Figura](https://modrinth.com/mod/figura) 0.1.5 or newer (optional)

The mod is needed on both the server and the client. It runs on Windows, Linux and macOS (x86_64; macOS also on
Apple Silicon).

Card artwork is not bundled. It is downloaded from YGOPRODeck the first time a card is shown and cached on your
computer; downloads can be turned off in `config/minecraftygo-client.toml`.

## Credits and license

- MinecraftYGO is open source under the GPL-3.0: [github.com/Zancrow321/MinecraftYGO](https://github.com/Zancrow321/MinecraftYGO)
- Duel engine: [OCG-Core](https://github.com/edo9300/ygopro-core) by Project Ignis (AGPL-3.0)
- Card data and scripts: BabelCDB and CardScripts by Project Ignis (AGPL-3.0)
- Monster models: [YGOMCModels](https://github.com/iconmaster5326/YGOMCModels) by iconmaster (MIT)
- Card artwork: [YGOPRODeck](https://ygoprodeck.com)

Yu-Gi-Oh! is a trademark of Konami. MinecraftYGO is an unofficial fan project and is not affiliated with or
endorsed by Konami.
