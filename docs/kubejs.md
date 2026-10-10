# KubeJS integration

[KubeJS](https://modrinth.com/mod/kubejs) is optional. With it installed (2101.7 or later, NeoForge 1.21.1), server
scripts in `kubejs/server_scripts/` can react to duels, booster packs, tournaments, ranks and finished sets
(`JadmEvents`) and use the `Jadm` helpers for cards, packs and Duel Points. Run `/reload` after changing a script.

Without KubeJS the same events are plain NeoForge events in `io.github.zancrow321.jadm.api.event`, posted on
`NeoForge.EVENT_BUS` on the server thread, for other mods to listen to.

Card and pack items are normal items with data components, so KubeJS can also make them without the helpers:
`Item.of('jadm:card[jadm:card={code:89631139,rarity:"ultra"}]')` or
`Item.of('jadm:booster_pack[jadm:pack_set="legend_of_blue_eyes_white_dragon"]')`.

## Events

```js
// Stop duels in the nether.
JadmEvents.duelStart(event => {
  if (event.players.some(p => String(p.level.dimension) == 'minecraft:the_nether')) {
    event.cancelMessage = 'No duels in the Nether!'
    event.cancel()
  }
})

// 25 DP for every win against a person, shown on the result screen.
JadmEvents.duelEnd(event => {
  if (!event.decided || event.againstBot) return
  event.winners.forEach(player => {
    Jadm.giveDuelPoints(player, 25)
    event.addNote(player, '+25 DP from the server')
  })
})
```

| Event | When | What it has |
|---|---|---|
| `duelStart` | A duel is about to start. `event.cancel()` stops it; everyone in it reads `cancelMessage`. | the duel (below), `cancelMessage` |
| `duelEnd` | A duel ended, after the mod's own rewards, before the result screen. | the duel (below), `winners`, `losers`, `winnerNames`, `winningTeam` (0 or 1, 2 for a draw, -1 if it broke off), `decided`, `draw`, `finished`, `turns`, `getLifePoints(team)`, `isWinner(player)`, `addNote(player, line)` for a line on that player's result screen |
| `packOpened` | A player opened a booster pack. | `player`, `setId`, `setCode`, `setName`, `cards` (the pulled card items: add, remove or replace to change what the player gets and sees), `codes`, `addCard(item)` |
| `tournamentEnd` | A tournament finished and paid its prizes. | `server`, `name`, `format`, `placements` (`player` UUID or null for an NPC, `name`, `place`), `getPlayers(place)`, `winners`, `winnerName` |
| `rankChanged` | A ranked duel moved a player's rating. | `player` (null if offline), `playerId`, `name`, `ratingBefore`, `rating`, `change`, `rankBefore`, `rank` (0 Bronze … 5 Duel King), `rankName`, `promoted` (a rank reached for the first time this season) |
| `setCompleted` | A player owns every card of a set for the first time and got its Set Collection Book reward. | `player`, `setId`, `setName`, `cards` |

Both duel events have `server`, `players` (the online people in it), `duelists` (every seat: `team`, `player` UUID or
null for a bot, `name`), `ranked`, `ante`, `tournament`, `tag`, `battleCity`, `againstBot` (a bot or NPC plays),
`againstNpc`, `npc` and `startingLifePoints`.

## Helpers

| Helper | Gives |
|---|---|
| `Jadm.card(code)`, `Jadm.card(code, rarity)` | a card item; rarity `common`, `rare`, `super`, `ultra` or `secret` (printed names like `Ultra Rare` work too) |
| `Jadm.randomCard()`, `Jadm.randomCard(rarity)` | a random card from the server's booster pool, optionally of one rarity |
| `Jadm.booster(set)` | a booster pack of a set, by code (`LOB`) or id; a random pack if there is no such set |
| `Jadm.randomBooster()` | a pack whose set is picked when it is opened |
| `Jadm.cardCode(item)`, `Jadm.isCard(item)`, `Jadm.cardRarity(item)` | what a card item is |
| `Jadm.cardName(code)`, `Jadm.cardInfo(code)` | a card's name; or `code`, `name`, `text`, `type` (the line under the name in the card panel, e.g. `LIGHT Dragon / Normal · Level 8 · ATK 3000 / DEF 2500`), `monster`, `spell`, `trap`, `level`, `atk`, `def` (null for an unknown passcode) |
| `Jadm.sets()`, `Jadm.setName(set)` | the codes of the sets packs come from (in a progression world, the unlocked ones); a set's name |
| `Jadm.duelPointsActive()`, `Jadm.duelPoints(player)`, `Jadm.giveDuelPoints(player, n)`, `Jadm.takeDuelPoints(player, n)`, `Jadm.setDuelPoints(player, n)` | Duel Points; `take` returns whether the player had enough |
| `Jadm.isDueling(player)` | whether the player is in a duel |
| `Jadm.wins(player)`, `Jadm.losses(player)`, `Jadm.duels(player)`, `Jadm.winStreak(player)` | the duel record of `/jadm stats` |
| `Jadm.rating(player)`, `Jadm.rank(player)`, `Jadm.rankName(player)` | the ranked rating and rank |

## Testing in development

`./gradlew :neoforge:runServer -Pkubejs` (or `runClient`) adds KubeJS to the dev run. Scripts go in
`neoforge/run/kubejs/server_scripts/`.
