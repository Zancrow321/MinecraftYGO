# Changelog

## 0.1.0 (first public beta)

Minecraft 1.21.1, NeoForge 21.1. Requires GeckoLib 4.9 or newer; Curios and Figura are optional.

### Dueling
- Full Yu-Gi-Oh! duels against players or a bot, with the rules handled by OCG-Core (the engine behind EDOPro) on
  the server. Natives for Windows, Linux and macOS are bundled.
- The duel field is projected into the world between the duelists: monsters as 3D models, face-down cards, life
  points, summon beams, battle and destruction effects.
- The Duel Disk is worn on the left arm (Curios slot or off hand). Right-click another duelist to challenge them and
  both disks unfold before the field appears.
- Tag duels (2v2) and Battle City duels, where each partner plays on their own half of the team's zones.
- Rulesets: original (MR1), Goat and modern, with configurable starting life points and an era banlist.
- Ante duels: each duelist puts up a random card from their deck and the winner takes both.
- The Duel Dome: a buildable arena kit with platforms; the field appears over the core.

### Collecting
- Booster packs from the era's sets, found in chests and dropped by mobs, with rarities.
- Binders for your collection and deck boxes for your decks, with deck legality checks.
- The Card Trader villager and the Card Shop Counter workstation sell packs, binders, deck boxes and starter decks.

### Monsters and effects
- 622 monsters with models from iconmaster's YGOMCModels.
- Every monster rears back and strikes when it attacks.
- Signature moves for fan favourites (Blue-Eyes, Red-Eyes, Dark Magician, Summoned Skull, Exodia, Gaia, Kuriboh,
  Time Wizard and more) and their own effects for 22 classic spells and traps.
- Card art is downloaded on first view from YGOPRODeck and cached locally; it can be turned off in the client config.

### NPC duelists
- Duelists wander the overworld with their own random decks. Beat one for a booster pack.
- NPCs and bot seats play with a heuristic AI.

### Cosmetics
- Seven disk skins and six card sleeve designs, unlocked by winning duels, beating NPCs and opening packs.
- Figura support: avatar scripts get a `ygo` API and duel events and can replace the disk with their own.
- A separate Figura starter avatar download with the Millennium Puzzle, a Battle City coat, life points over the
  head, duel reactions and emotes.
