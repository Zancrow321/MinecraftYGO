# Changelog

## Unreleased

### Card pictures when picking cards
- Searching your Deck now shows the cards you can pick. Before, every card in the window showed its back without a
  name, so you couldn't tell what you were choosing.
- Cards an effect has just shown you (your opponent's hand you look at, the top of a Deck) stay visible when you
  then pick from them, until the turn ends.
- The pick window shows cards as big as fit (a few cards from a Deck search show large), and cards you picked
  earlier in a pick-one-at-a-time choice stay in it, marked, to click again to put back.
- Declaring a card name lists each name with its card picture; hovering one shows it in the card panel.
- A question about a card ("use the effect of...?", a position, a zone for a card) shows that card in the card
  panel.

### Beyond the Brave
- **Beyond the Brave** (BETB, October 8, 2026) is a full booster with all 100 cards and their scripts. The 16
  TCG-first cards (Angelechy and others) use ProjectIgnis' pre-release scripts under their real passcodes.

### Admin menu
- Operators get an **admin menu** (key O, or `/jadm admin`; permission level 2, like `/give`): any booster pack,
  structure deck or tin, a random pack, any card in any rarity (search by name or passcode, locked cards too), and
  Duel Points to give, take or set, each for yourself, another player or everyone online. The server checks every
  click, and each one shows up in the operators' chat and the server log like a command.

### Arena auto duel
- Step onto a podium of a Duel Arena and a duel starts by itself once someone steps onto the other one, no
  invitation needed. "Waiting for Opponent..." floats over whoever waits and "Step up to duel!" over the free
  podium; once both are taken a countdown runs over the arena, and stepping off calls it off.
- Needs a legal deck box (or a lent starter deck where the server allows it). After a duel, or when logging in on a
  podium, step off and back on to go again. Arenas added for tournaments wait for the tournament while one is open.
- Server settings `[arena] autoStart` (on) and `countdownSeconds` (5).

### New name
- The mod is now called **Just Another Dueling Mod**, mod id `jadm`. The command is `/jadm`, Figura avatar scripts
  use the `jadm` global and `jadm.<event>` events, and the starter avatar is `jadm-duelist`.
- Renamed from MinecraftYGO (mod id `minecraftygo`, command `/ygo`, config folder `config/minecraftygo`).
- Worlds from 0.1.0 lose the mod's items, blocks and saved data (decks, binders, progress); the config starts fresh.

### Card preview
- Hovering a card in the inventory, a chest or any other container, the binder or the deck box shows it big on the
  left, like the duel's card panel: its picture, name, rarity, type, stats and full text. The tooltip by the mouse
  then keeps to the card's name. A card held in the hand is shown the same way in the top left corner while
  playing (not during a duel, which has its own card panel). Drawn only where there is room left of the window (else the old tooltip stays);
  `cardPreview = false` in the client config turns it off.

### Real products
- Booster packs, structure and starter decks and tins look like the real products: the item shows the product's
  picture with its background cut away, in slots, in the hand, on the ground and in item frames, as a pack, a
  thicker deck box or a tin. The pictures come from YGOPRODeck on first view, like card art, and none ship with the
  mod. A random pack, a product without a picture, or `realProductImages = false` in the client config keeps the
  old icon.

### Handbook
- New **Duelist's Handbook**: an open book with a title page, clickable contents and 13 chapters on dueling and duel
  records, the duel controls, collecting and foils, decks, shops and Duel Points, progression and rules, arenas and tournaments, NPC
  duelists, cosmetics, every command and every setting. Keys show your current bindings, settings show the world's current values, crafting grids come
  from the real recipes, and commands can be clicked to type them in chat. English and German.
- Every player gets it once on their first join (also players of existing worlds, on their next join); `/jadm guide`
  opens it any time, and a book and paper craft a new one. Both can be turned off: `[guide] giveOnFirstJoin` and
  `[guide] craftable`.

### Tournaments
- Tournaments in four formats: single elimination, double elimination with a losers' bracket and a grand final
  reset, Swiss rounds with an optional top cut, and round robin. Operators open one with `/jadm tournament create`,
  or a schedule in the config opens them by themselves; players join from a chat link or `/jadm tournament join`.
- Matches are played on the Duel Arenas added with `/jadm tournament arena add`: duelists are brought onto the podiums
  when an arena is free and sent back afterwards. NPC duelists fill empty seats, stand on the podium against
  players and play each other unseen.
- The tournament window (`/jadm tournament`) shows the bracket, the Swiss or round robin table, the matches and the
  rules and prizes, and follows the tournament live.
- A new `[tournament]` config section holds every setting, and each tournament can change them while it is open
  with `/jadm tournament set`: format, best of, number of duelists, registration time, NPC fillers, Swiss rounds and
  top cut, rules, banlist, life points, time and turn limits, locked decks, entry fee and pot, prizes per place,
  no-show time and announcements. A tournament survives a server restart.
- Entry fees are paid in the shop currency (Duel Points by default, or an item), and prizes can include Duel
  Points (`points 500`). Tournament games don't give the `[results]` duel rewards; the tournament's prizes
  replace them.

### Foil cards
- Foil cards shine like the real ones, in the inventory, in your hand, in the world, in the binder and when a pack
  is opened: a Rare has a silver name, a Super Rare holofoil artwork, an Ultra Rare holofoil artwork and a gold name,
  a Secret Rare rainbow-lined artwork and a silver name. A glare sweeps over the foil now and then.
- Binders keep each card's rarity. A foil put in a binder stays a foil, and the binder lists each rarity of a card
  on its own, with its rarity (R, SR, UR, ScR) in the corner and in the tooltip. Taking a copy out gives that copy;
  building a deck uses commons first so the foils stay in the binder.

### Duel wins and losses
- Every player's duel record is kept: wins, losses and draws, against players and against NPCs and bots, and how
  many wins in a row. The result screen shows it after each duel; `/jadm stats` shows yours, `/jadm stats <player>`
  someone else's (also when they are offline), `/jadm stats top` the ten players with the most wins, and
  `/jadm stats reset <player>` (operators) clears one. A duelist who logs off mid-duel still has the loss counted.
- What a won or lost duel brings is set in the server config, separately for duels against players
  (`[results.players]`) and against NPC duelists (`[results.npcs]`): booster packs, emeralds and experience for each
  winner (`winPacks`, `winEmeralds`, `winXp`) and as a consolation for each loser (`lossPacks`, `lossEmeralds`,
  `lossXp`). By default it stays as before: one booster pack for beating an NPC, nothing else.
- With Duel Points as the currency, every duel brings DP too: `winPoints` (100) for each winner and `lossPoints` (20)
  for each loser, in `[results.players]` and `[results.npcs]`.
- `[results] rewardBotDuels` gives duels against the bot the NPC rewards too (off by default), `announce` tells the
  whole server who won each duel, `trackRecord` turns the record off, and `[results.npcs] rematchMinutes` sets how
  long a beaten NPC won't duel you again (20 minutes, one Minecraft day, as before).

### Duel comfort
- Graveyards, banished cards and extra decks of both duelists can be looked through at any time: click the pile
  (right-click when a card there can be activated) and a window shows its cards, newest first. Hovering a card
  shows it in the card panel; the opponent's face-down cards show their back. Escape or x closes it.
- Hotkeys in duel mode: Space passes a response, confirms a pick or goes to the next phase; Enter confirms a pick;
  1 to 9 pick a plain answer (the dialog numbers its buttons).
- Responses can be switched to "Pass all" with C, the Ask/Skip button under Log or the duel menu: every chance to
  respond that you don't have to take is passed without asking. The choice is kept in the client config.
- A banner and a chime mark each new turn ("YOUR TURN" or the opponent's), a smaller banner the Battle Phase, Main
  Phase 2 and End Phase, and a ping says when you get a chance to respond. They come in step with the field's
  effects, not ahead of them.
- Life points count down or up when they change, in step with the damage effect, and their panel flashes red or
  green; the floating change beside them is twice as big.
- The card panel always shows the card's artwork, big, above its name and text. Long card text is drawn smaller
  instead of pushing the picture out, and the panel keeps the last card you looked at.
- Card text too long for the card panel even at the smaller size (Pendulum monsters, for one) gets a scroll bar
  instead of being cut off: turn the mouse wheel anywhere but over the duel log, or drag the bar.
- The materials under an Xyz monster can be looked at: right-click it (or click it when it has nothing to do) and a
  window shows them big. The card panel lists them as small pictures while the monster is under the mouse.

### Card shop
- The card shop is set in `[shop]` of the server config, and card traders follow a change within a few seconds,
  keeping what they have sold since their last restock:
  - `currency`: `"points"` for Duel Points (the default, see below) or the item traders take and pay for paper, any
    item id such as `"minecraft:emerald"` or `"minecraft:diamond"`.
  - `priceMultiplier`: every price times this, e.g. 0.5 for half price.
  - `dynamicPrices = false` keeps prices fixed; by default they rise when a trade sells out often and drop for
    players the village likes, as with other villagers.
  - `[shop.prices]`: the price of core, all-foil and small packs (tournament, mini and battle packs), structure
    decks, tins, the random packs of novice, journeyman and master traders, binders, deck boxes, starter decks and
    duel disks, how much paper a trader buys for one currency item, and the wandering trader's pack and duel disk. 0 means
    traders don't sell it (and stop if they did).
  - `[shop.stock]`: how many of each a trader can sell before it restocks.
  - `wanderingTrader = false` stops wandering traders from selling packs and duel disks.
- New **Card Vending Machine** (three iron ingots / glass pane, emerald, glass pane / iron ingot, redstone, iron
  ingot): a shop without a villager. Right-clicking it opens the trade window with the `[shop]` prices and currency;
  prices don't rise with demand. Set in `[shop.machine]`:
  - `products`: `"rotation"` (the newest and a few older products, the same on every machine), `"all"` or `"none"`.
  - `supplies`: random packs, binders, deck boxes, the starter decks and duel disks too.
  - `limitPerPlayer`: each player can buy the `[shop.stock]` amounts a day; off, machines never run out.
  - `command`: `/jadm shop` opens the machine's shop anywhere (operators can always use it).
  - `operatorsOnly`: only operators place and break machines, for server-run shops.
  - `enabled` and `name` (the title of its window).
- New **Shop Stand** (red wool, white wool, red wool / plank, chest, plank / three planks): a shop run by a player.
  Its owner sets up to nine wares with a price each (any item, a diamond or a rare card too), fills the stock and
  takes the takings out of the till; everyone else gets a trade window with what is in stock. Set in
  `[shop.players]`:
  - `maxPerPlayer`: how many stands each player can set up (3; 0 for any number).
  - `currencyOnly`: prices must be in the `[shop] currency`.
  - `taxPercent`: this share of every price is kept back from the owner.
  - `onlyModItems`: stands only sell this mod's items.
  - `operatorsManage`: operators can open, stock and break anyone's stand.
  - `notifyOwner`: the owner gets a chat message on every sale.
  - `enabled`: off, stands are closed (owners can still empty them).
- New currency **Duel Points (DP)**, the default `[shop] currency = "points"`: a balance per player instead of an item,
  shown above the inventory and in the shops. Card traders, Card Vending Machines and Shop Stands open a DP shop
  (click buys once, shift-click as many as you can); stand owners type their prices in DP and are paid straight into
  their balance. `currency` set to an item id brings back the villager trade window paid in that item. Set in
  `[shop.points]`:
  - `symbol` ("DP"), `pricePoints` (each 1 of a `[shop.prices]` price is 25 DP).
  - Earning: `startBalance` (500), `dailyBonus` (50 a day), paper sold to card traders, loose cards sold to Card
    Vending Machines by rarity (`sellCommon` 5, `sellRare` 15, `sellSuper` 30, `sellUltra` 60, `sellSecret` 120) and
    `emeraldExchange` (10 DP per emerald at the machines). 0 turns each off.
  - `transfers`: `/jadm dp pay <player> <amount>`.
  - `/jadm dp` shows your balance; operators use `/jadm dp <player>` and `/jadm dp give|take|set <players> <amount>`.

### Deck editor
- The deck box filters the cards you own by type (monsters, spells, traps, extra deck), attribute and level and sorts
  them by name, ATK, DEF or level. Click a filter to step through it, shift-click to step back.
- The deck is listed monsters first, then spells and traps, and shows how many of each it has.
- Export copies the deck to the clipboard as a YDK list for YGOPro, EDOPro and most deck builders. Import replaces
  the deck with a YDK list from the clipboard, built from the cards you own (another printing of a card stands in
  for it), and says which cards were missing or not allowed.

### Duel Arena
- New Duel Arena Kit: sets up the Duelist Kingdom arena from the anime as one big model: a box 3 blocks tall and
  21 by 27 blocks with steps up at each end, grey-green glass tiles, white sides with dark markings, a ribbed red
  and a ribbed blue end, a red console podium, a blue pillar podium with a green gem, red sign posts and
  stained-glass lamp posts. Duelists on the podiums
  duel over the arena and rise 3 blocks with them when the duel starts; the podiums come back down at the end. An
  NPC opponent takes the free podium. Breaking any part packs the arena back into its kit.

### Holograms
- Artwork holograms on a full board no longer run into each other: they fit their zone, all face the same way
  instead of each turning to the camera, stand a little lower, and their name and stats lines are smaller and cut
  to the zone's width.

### NPC decks
- NPC duelists play real decks: about a third play a famous tournament deck once its era has come (Goat Control
  2005, Chaos Return 2006, Tele-DAD 2009), a third a starter or structure deck that is out, the rest a random deck
  from the pool. They say which deck they play when the duel starts. Copies the current banlist forbids are left
  out.
- Random NPC decks get an Extra Deck of whatever the pool offers: Fusions, Synchros (with Tuners in the main deck),
  Xyz monsters of ranks the deck can build and small Links. Cards with a 3D model are picked about three times as
  often.
- The AI brings out a Synchro, Xyz or Link monster when it is stronger than everything it already has, picks its
  least valuable cards as materials, sets Pendulum scales and Pendulum Summons as many monsters as it may.
- The AI no longer tributes a monster that is stronger than the one it Tribute Summons.

### Extra deck and newer summons
- The field follows the duel's rules. From Master Rule 4 the two Extra Monster Zones sit between the halves, which
  move apart to make room for them, and under Master Rule 3 the Pendulum Zones are their own zones at both ends of
  the spell/trap row. Clicking, dragging and zone choices reach these zones too.
- Xyz monsters show their materials as cards fanned out under them, with a count. Link monsters show their arrows
  as red triangles at the edges of their zone. Pendulum cards in a Pendulum Zone show their scale, and face-up
  Pendulum monsters in the extra deck are counted on it.
- Holograms get an inner band in the colour of the card's frame: Fusion, Ritual, Synchro, Xyz, Pendulum or Link.
- The card panel and tooltips name the monster's kinds (Synchro, Tuner, Xyz, Pendulum, Link, Effect...) and show
  Rank, LINK rating with arrows or Pendulum scale where they apply, plus an Xyz monster's materials.
- The deck box shows the extra deck in its own row under the main deck (up to 15 cards).
- Card texts no longer show a stray line-break symbol in the card panel.

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
  in a deck box. `/jadm starter` opens the choice again for someone who chose "Later". Forbidden cards stay out of it.
- Packs from loot and duel rewards favor newer sets.

### Duel disk
- New disk model: the blocky Battle City disk by burning-icecream (CC BY-NC 4.0). Standby is the hub with one wing
  in front and one behind; when a duel starts the short wing swings under the hub and joins the long one into the
  bent five-zone blade. The disk skins are recolors of the new model.

### Progression
- New worlds start with Legend of Blue Eyes White Dragon, and operators unlock the TCG sets in release order:
  `/jadm progression status`, `next [count]`, `until <set code or date>`, `set <set code or date>` and `list`. Chat
  says what is new. Reprint products come along with the next set that brings new cards.
- This is the new default (`[pool] mode = "progression"`), also for worlds that already have a config file: they
  switch from the modeled pool, which held almost only LOB, MRD and MRL cards anyway. `mode = "modeled"` brings the
  old pool back.
- Rules and banlist follow the unlocked cards (`ruleset = "auto"` and `banlist = "auto"`, the new defaults): Master
  Rule 1 to 5 as Xyz, Pendulum and Link sets arrive, and the TCG Forbidden & Limited List of the time (all 73 lists
  since 1999 are bundled). New rule sets `mr2`, `mr3` and `mr4`; a fixed `banlist` names a file in
  `config/jadm/banlists/`.
- `[progression] scope = "player"` gives each player their own progress. When two players at different steps duel,
  the one further along sets the rules; an NPC builds its deck from the cards of the player it duels.
- Cards from locked sets show a padlock in the binder (which can show only unlocked cards) and the deck box, and
  can't be added to decks unless `[cards] lockedInDeck = false`.
- Card traders sell the newest unlocked sets.
- Old config files are updated once: `mode` becomes `progression` and `ruleset` becomes `auto`. A server's own
  `config/jadm/banlist.json` now only counts for the modeled pool; elsewhere put it in
  `config/jadm/banlists/` and name it in `banlist`.

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
- Watch a duel: right-click someone who is dueling (or a dueling NPC), or `/jadm watch <player>`. Spectators see
  the field from above with life points, the card panel and the log, but no hidden cards or hands. Escape, Stop
  watching (or `/jadm unwatch`) leaves.
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
- Figura support: avatar scripts get a `jadm` API and duel events and can replace the disk with their own.
- A separate Figura starter avatar download with the Millennium Puzzle, a Battle City coat, life points over the
  head, duel reactions and emotes.
