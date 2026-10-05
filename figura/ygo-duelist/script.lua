-- YGO Duelist: the MinecraftYGO starter avatar for Figura.
--
-- Wears a Millennium Puzzle and a Battle City long coat over your own skin, shows your life points above your head
-- during duels, reacts to what happens in them and adds emotes to the action wheel. Everything here is meant to be
-- copied into your own avatar: see docs/figura.md in the MinecraftYGO repository.
--
-- Duel events only fire on your own client, so every reaction goes out as a ping and plays for everyone.

local model = models.model
local puzzle = model.Body.Puzzle
local coat = model.Body.Coat
local tails = coat.Tails

-- Settings ------------------------------------------------------------------------------------------------------------

config:setName("ygo_duelist")
local show = {
  puzzle = config:load("puzzle") ~= false,
  coat = config:load("coat") ~= false,
  lp = config:load("lp") ~= false,
}

-- Life points and speech bubble above the head -----------------------------------------------------------------------

local hud = models:newPart("ygoHud")
hud:setPos(0, 37, 0)
local billboard = hud:newPart("ygoBillboard", "Camera")
local lpText = billboard:newText("lp")
lpText:setAlignment("CENTER")
lpText:setShadow(true)
lpText:setScale(0.35)
lpText:setVisible(false)
local bubble = billboard:newText("bubble")
bubble:setAlignment("CENTER")
bubble:setBackground(true)
bubble:setBackgroundColor(0, 0, 0, 0.6)
bubble:setWidth(160)
bubble:setWrap(true)
bubble:setScale(0.3)
bubble:setVisible(false)

-- State shared by every client ---------------------------------------------------------------------------------------

local dueling, lifePoints = false, nil
local pose, poseTicks, poseLength = nil, 0, 0
local sayTicks = 0

local function applySettings()
  puzzle:setVisible(show.puzzle)
  coat:setVisible(show.coat)
  lpText:setVisible(show.lp and dueling and lifePoints ~= nil)
end

local function lpColour(lp)
  if lp > 4000 then return "green" elseif lp > 1000 then return "gold" else return "red" end
end

local function showLp()
  if lifePoints then
    lpText:setText(toJson({ text = "LP " .. lifePoints, color = lpColour(lifePoints), bold = true }))
  end
  applySettings()
end

local function say(text)
  bubble:setText(toJson({ text = text, color = "white" }))
  bubble:setPos(0, 8 + 4 * math.ceil(#text / 32), 0) -- longer lines wrap upwards, away from the name tag
  bubble:setVisible(true)
  sayTicks = 60
end

local function burst(particle, count)
  if not player:isLoaded() then return end
  local pos = player:getPos():add(0, 1.2, 0)
  for _ = 1, count do
    particles:newParticle(particle, pos, vec(math.random() - 0.5, math.random() * 0.6, math.random() - 0.5) * 0.4)
  end
end

local function sound(id, pitch)
  if player:isLoaded() then
    sounds:playSound(id, player:getPos(), 0.6, pitch or 1)
  end
end

-- Poses, as rotation offsets in degrees for the vanilla parts. "length" is in ticks.
local POSES = {
  draw = { length = 30, rightArm = vec(150, 0, -15), head = vec(10, 0, 0) },
  summon = { length = 30, rightArm = vec(165, 0, 15), head = vec(20, 0, 0) },
  activate = { length = 25, rightArm = vec(85, 25, 0) },
  point = { length = 40, rightArm = vec(90, -10, 0), head = vec(0, 0, 0) },
  hit = { length = 15, head = vec(25, 0, 0), rightArm = vec(-25, 0, 35), leftArm = vec(-25, 0, -35) },
  cheer = { length = 50, rightArm = vec(170, 0, 20), leftArm = vec(170, 0, -20), head = vec(20, 0, 0) },
  laugh = { length = 50, rightArm = vec(30, 0, 70), leftArm = vec(30, 0, -70), head = vec(30, 0, 0), shake = true },
  slump = { length = 60, head = vec(-40, 0, 0), rightArm = vec(15, 0, 5), leftArm = vec(15, 0, -5) },
  heart = { length = 45, rightArm = vec(65, -35, 0), head = vec(-15, 0, 0) },
  bow = { length = 35, head = vec(-45, 0, 0), rightArm = vec(55, -40, 0) },
}

local function playPose(name)
  local p = POSES[name]
  if not p then return end
  pose, poseTicks, poseLength = p, p.length, p.length
end

-- Pings: the only way the host's reactions reach other players --------------------------------------------------------

function pings.ygoSettings(puzzleOn, coatOn, lpOn)
  show.puzzle, show.coat, show.lp = puzzleOn, coatOn, lpOn
  applySettings()
end

function pings.ygoDuel(active, lp)
  dueling, lifePoints = active, lp
  puzzle:setSecondaryRenderType(active and "EMISSIVE" or "NONE")
  showLp()
end

function pings.ygoLp(lp)
  lifePoints = lp
  showLp()
end

-- One ping carries a pose, an optional line to say and an optional effect, so a busy turn stays within the limits.
function pings.ygoReact(poseName, text, effect)
  playPose(poseName)
  if text then say(text) end
  if effect == "win" then
    burst("minecraft:totem_of_undying", 30)
    sound("minecraft:ui.toast.challenge_complete", 1)
  elseif effect == "summon" then
    burst("minecraft:end_rod", 12)
    sound("minecraft:block.beacon.power_select", 1.4)
  elseif effect == "loss" then
    burst("minecraft:smoke", 15)
  end
end

puzzle:setSecondaryRenderType("NONE")
applySettings()

-- Animation ----------------------------------------------------------------------------------------------------------

local tailSwing, lastTailSwing = 0, 0

function events.tick()
  if poseTicks > 0 then poseTicks = poseTicks - 1 end
  if sayTicks > 0 then
    sayTicks = sayTicks - 1
    if sayTicks == 0 then bubble:setVisible(false) end
  end
  -- The coat tails flare back with speed, more when sprinting, and settle when standing.
  lastTailSwing = tailSwing
  local velocity = player:getVelocity()
  local speed = math.sqrt(velocity.x ^ 2 + velocity.z ^ 2)
  local target = math.min(speed * 120, player:isSprinting() and 45 or 25)
  tailSwing = math.lerp(tailSwing, target, 0.3)
end

local function weight(delta)
  if not pose or poseTicks <= 0 then return 0 end
  local elapsed = poseLength - poseTicks + delta
  local remaining = poseTicks - delta
  return math.clamp(math.min(elapsed / 4, remaining / 6), 0, 1)
end

local NO_ROT = vec(0, 0, 0)

function events.render(delta)
  local w = weight(delta)
  local p = pose or {}
  local shake = p.shake and vec(0, math.sin((world.getTime() + delta) * 1.5) * 8, 0) or NO_ROT
  vanilla_model.RIGHT_ARM:setOffsetRot((p.rightArm or NO_ROT) * w)
  vanilla_model.LEFT_ARM:setOffsetRot((p.leftArm or NO_ROT) * w)
  vanilla_model.HEAD:setOffsetRot(((p.head or NO_ROT) + shake) * w)
  tails:setRot(math.lerp(lastTailSwing, tailSwing, delta), 0, 0)
  puzzle:setRot(math.lerp(lastTailSwing, tailSwing, delta) * 0.3, 0, 0)
end

-- Everything below runs on your own client only ----------------------------------------------------------------------

if not host:isHost() then return end

local function syncSettings()
  pings.ygoSettings(show.puzzle, show.coat, show.lp)
end

-- Action wheel: emotes and the wardrobe.
local page = action_wheel:newPage("YGO Duelist")
action_wheel:setPage(page)

local function emote(title, item, poseName, text)
  page:newAction():title(title):item(item):onLeftClick(function()
    pings.ygoReact(poseName, text)
  end)
end

emote("Draw!", "minecraft:paper", "draw", "Draw!")
emote("Your move", "minecraft:arrow", "point", "Your move!")
emote("Heart of the Cards", "minecraft:heart_of_the_sea", "heart", "Heart of the Cards, guide me!")
emote("Laugh", "minecraft:white_banner", "laugh", "Hahaha!")
emote("Bow", "minecraft:iron_sword", "bow", "Good duel.")

local function toggle(title, item, key)
  page:newAction():title(title):item(item):toggled(show[key]):onToggle(function(on)
    show[key] = on
    config:save(key, on)
    syncSettings()
  end)
end

toggle("Millennium Puzzle", "minecraft:gold_ingot", "puzzle")
toggle("Battle City coat", "minecraft:white_wool", "coat")
toggle("Life points", "minecraft:redstone", "lp")

-- Players who join later learn the settings and the duel state from a slow refresh.
local refresh = 0
events.tick:register(function()
  refresh = refresh + 1
  if refresh % 200 == 0 then
    syncSettings()
    if dueling then pings.ygoDuel(true, lifePoints) end
  end
  -- A duel can also end without a result, for example when the opponent leaves.
  if dueling and refresh % 20 == 0 and not (ygo and ygo:isDueling()) then
    pings.ygoDuel(false, nil)
  end
end)

-- Duel reactions. Without MinecraftYGO the ygo events don't exist and only the emotes remain.
if not ygo then return end

local function on(event, handler)
  local e = events[event]
  if e then e:register(handler) end
end

on("ygo.duel_start", function(opponent)
  pings.ygoDuel(true, ygo:getLifePoints() or 8000)
  pings.ygoReact("point", "It's time to duel, " .. (opponent or "opponent") .. "!")
end)

on("ygo.turn_start", function(mine)
  if mine then pings.ygoReact("draw", "My turn, draw!") end
end)

on("ygo.summon", function(card, mine)
  if not mine then return end
  if card then
    pings.ygoReact("summon", "I summon " .. card .. "!", "summon")
  else
    pings.ygoReact("activate", "I set a monster face-down.")
  end
end)

on("ygo.activate", function(card, mine)
  if mine and card then pings.ygoReact("activate", "I activate " .. card .. "!") end
end)

on("ygo.attack", function(mine, direct)
  if mine then pings.ygoReact("point", direct and "Direct attack!" or "Attack!") end
end)

on("ygo.lp_change", function(mine, change, lp)
  if not mine then return end
  pings.ygoLp(lp)
  if change <= -2000 then
    pings.ygoReact("hit", "Aaargh!")
  elseif change < 0 then
    pings.ygoReact("hit")
  end
end)

on("ygo.duel_end", function(won)
  if won == true then
    pings.ygoReact("cheer", "I win!", "win")
  elseif won == false then
    pings.ygoReact("slump", "...", "loss")
  else
    pings.ygoReact("bow", "A draw. Good duel.")
  end
  pings.ygoDuel(false, nil)
end)
