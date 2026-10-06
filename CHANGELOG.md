# Changelog

## Unreleased

### Products
- Booster packs open like the real thing: 9 cards for core boosters (one rare or better until 2014, then a rare and
  a foil), 3 for tournament packs, 5 for mini boosters, battle packs and speed duel packs, and 5 foils for
  all-foil sets such as Dragons of Legend. The pack's tooltip says how many cards it holds.
- New items: **Structure Decks** (right-click puts their 40 cards into your binder) and **Tins** (a promo card with
  its printed rarity and three booster packs).
- Card traders keep a shop that follows the unlocked products: the newest packs, structure decks and tins always,
  plus older ones that rotate every week. New server settings `[shop] newestAlways`, `rotatingOlder` and
  `rotationDays`.
- New players pick their first deck: Yugi's or Kaiba's starter deck, or any starter or structure deck that is out,
  in a deck box. `/ygo starter` opens the choice again for someone who chose "Later". Forbidden cards stay out of it.
- Packs from loot and duel rewards favor newer sets.

### Progression
- New worlds start with Legend of Blue Eyes White Dragon, and operators unlock the TCG sets in release order:
  `/ygo progression status`, `next [count]`, `until <set code or date>`, `set <set code or date>` and `list`. Chat
  says what is new. Reprint products come along with the next set that brings new cards.
- This is the new default (`[pool] mode = "progression"`), also for worlds that already have a config file: they
  switch from the modeled pool, which held almost only LOB, MRD and MRL cards anyway. `mode = "modeled"` brings the
  old pool back.
- Rules and banlist follow the unlocked cards (`ruleset = "auto"` and `banlist = "auto"`, the new defaults): Master
  Rule 1 to 5 as Xyz, Pendulum and Link sets arrive, and the TCG Forbidden & Limited List of the time (all 73 lists
  since 1999 are bundled). New rule sets `mr2`, `mr3` and `mr4`; a fixed `banlist` names a file in
  `config/minecraftygo/banlists/`.
- `[progression] scope = "player"` gives each player their own progress. When two players at different steps duel,
  the one further along sets the rules; an NPC builds its deck from the cards of the player it duels.
- Cards from locked sets show a padlock in the binder (which can show only unlocked cards) and the deck box, and
  can't be added to decks unless `[cards] lockedInDeck = false`.
- Card traders sell the newest unlocked sets.
- Old config files are updated once: `mode` becomes `progression` and `ruleset` becomes `auto`. A server's own
  `config/minecraftygo/banlist.json` now only counts for the modeled pool; elsewhere put it in
  `config/minecraftygo/banlists/` and name it in `banlist`.

### All cards (first step)
- Every official card is bundled now: about 14,700 cards with their scripts (the jar grows to about 20 MB).
- New server setting `[pool] mode`: `modeled` or `all`, which plays with every card. With
  `all`, packs, loot and NPC decks draw from every card and booster packs exist for every TCG booster, from Legend
  of Blue Eyes to today. Monsters without a model stand on the field as an artwork hologram (below).
- Decks may only hold cards the server plays with; a deck box with other cards says which one isn't allowed.
- Monsters without a model stand on the field as an artwork hologram: the art floats above the zone in a frame of
  its attribute's color, with the name above and ATK/DEF below. It flickers in when summoned, lunges when it
  attacks, tilts back with a blue frame in defense and fades when it leaves the field.
- Resource packs can give any monster a 3D model (or replace a bundled one). `tools/models/make_pack.py` builds such
  a pack from Blockbench files; see `docs/resource-pack-models.md`.
- Synchro, Xyz and Link monsters count as extra deck cards.
- "Declare a card name" lists only the names the card allows, and long lists can be searched by typing.

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
- Watch a duel: right-click someone who is dueling (or a dueling NPC), or `/ygo watch <player>`. Spectators see
  the field from above with life points, the card panel and the log, but no hidden cards or hands. Escape, Stop
  watching (or `/ygo unwatch`) leaves.
- In tag duels both partners see the team's hand, not only the one whose turn it is.
- Turn time limit as a server setting (`turnTimeLimit`, seconds per person per turn, off by default). The time left
  shows under the phase buttons; once it runs out, the bot makes your choices until the turn ends.

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
