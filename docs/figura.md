# Figura integration

[Figura](https://modrinth.com/mod/figura) is optional. With it installed (0.1.5 or later), avatar scripts can react
to duels and hide or replace the duel disk. Figura is never bundled with this mod.

Duel state is only known on the duelist's own client, so the events fire there. Use Figura pings to show reactions
to other players, as with any host-only logic. Only public information is exposed: hand and deck counts, never the
cards in them.

## The `ygo` global

| Method | Returns |
|---|---|
| `ygo:isDueling()` | whether the avatar's owner is in a duel (works on every client) |
| `ygo:getDiskSkin()` | the skin of the disk they wear (`battle_city`, `slifer_red`, ...), or `nil` |
| `ygo:setDiskVisible(visible)` | hides or shows the mod's disk on this avatar, e.g. to draw your own |
| `ygo:getLifePoints()`, `ygo:getOpponentLifePoints()` | life points |
| `ygo:getOpponentName()`, `ygo:getTurn()`, `ygo:isMyTurn()`, `ygo:getPhase()` | turn state |
| `ygo:getHandCount()`, `ygo:getOpponentHandCount()`, `ygo:getDeckCount()` | card counts |

The duel getters return `nil` on other players' clients and when not dueling.

## Events

Register them as `events["ygo.<name>"]:register(function(...) end)`.

| Event | Arguments |
|---|---|
| `ygo.duel_start` | opponent's name |
| `ygo.duel_end` | `true` if you won, `false` if you lost, `nil` for a draw |
| `ygo.turn_start` | whether it's your turn, turn number |
| `ygo.phase` | phase name, whether it's your turn |
| `ygo.summon` | card name (`nil` if hidden), whether it's your card |
| `ygo.activate` | card name (`nil` if hidden), whether it's your card |
| `ygo.attack` | whether you attack, whether it's a direct attack |
| `ygo.lp_change` | whether it's your life points, the change (negative for damage), the new life points |

Several events can arrive in the same tick: the server sends the duel in steps, one per decision.

## Example

```lua
-- Hide the built-in disk; this avatar draws its own on the left arm.
ygo:setDiskVisible(false)

function pings.ygoCheer()
  animations.model.cheer:play()
end

events["ygo.summon"]:register(function(card, mine)
  if mine and card == "Blue-Eyes White Dragon" then
    pings.ygoCheer()
  end
end)

events["ygo.duel_end"]:register(function(won)
  if won then pings.ygoCheer() end
end)
```
