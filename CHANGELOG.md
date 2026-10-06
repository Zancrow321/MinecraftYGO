# Changelog

## Unreleased

### Duel mode
- Your hand is big along the bottom. Click a card for its actions (Normal Summon, Set, Activate...), or drag it
  onto a zone to play it right there. Cards played from the menu go to the middle-most free zone, unless
  `chooseZone` is on in the client config.
- Phase buttons on the right (DP SP M1 BP M2 EP) show the current phase; click BP, M2 or EP to move on.
- Responses open a window that shows what just happened (the card, what it did, its text) with every card you can
  respond with and "Don't respond". It folds away with the "-" button to look at the field.
- Picks from piles open a card window with the art; picks on the field are made there, with Confirm and Cancel
  on the right. Yes/no, positions and options get a small dialog.
- The card under the mouse is shown big on the left with its text and current ATK/DEF.
- The "every choice as a list" screen is gone, from the duel menu too.
- A duel starts with a show: the camera sweeps in over the field, a big DUEL! and a coin toss that really decides
  who goes first (before, the challenger always started).
- When the opponent activates or summons a card, it is shown big with a banner ("Duel Bot activates Sogen")
  before its effect plays.
- The duel log: L or the Log button under the phase buttons; scroll it with the mouse wheel.
- A result screen at the end: victory or defeat, how it ended, and what it brought (booster pack, ante cards,
  unlocked cosmetics). Continue or Escape takes you back to the game.

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

### Duel mode
- A duel is its own mode: you stand still and safe at your end of the field (no damage, mobs leave you
  alone), the camera looks down on the field, and you play with the mouse cursor. Click a glowing card in your hand
  or a glowing zone on the field.
- V switches between the top-down camera and your own eyes; hold the right mouse button to look around.
- Escape opens the duel menu: back to the duel, camera, surrender and the game menu.
- Monster models face their opponent.

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
