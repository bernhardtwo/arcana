# Arcana

Item-bound magic abilities for Paper servers. A staff in hand, right click,
and something happens around you.

Five staffs, a pair of boots and a hammer so far.

**Gravity Staff** (blaze rod)

| Ability | How to cast | What it does |
|---|---|---|
| Gravity: Push | Right click | Repels every living thing around the caster |
| Gravity: Pull | Sneak + right click | Drags every living thing within the radius toward the caster |
| Gravity: Leap | Left click, while airborne | A second jump in the air, once per jump, with a soft landing |

**Ice Staff** (end rod)

| Ability | How to cast | What it does |
|---|---|---|
| Ice: Slash | Left click | Frost arc in front of the caster, plus a snowball that also freezes |
| Ice: Breaker | Right click | Spiral ice beam to whatever you are aiming at, up to 24 blocks |
| Ice: Armor | Sneak + right click | Orbiting ice crystals that each absorb one attack |

**Solar Staff** (breeze rod)

| Ability | How to cast | What it does |
|---|---|---|
| Solar: Lantern | Right click (toggle) | Night vision and a small orbiting light, right click again to put it out |
| Solar: Zenith | Sneak + right click | A sun above you that burns hostiles and heals the peaceful until you walk away |
| Solar: Bloom | Left click on a block | Bone meal without bone meal, from a pool of charges |

**Shadow Staff** (echo shard)

| Ability | How to cast | What it does |
|---|---|---|
| Shadow: Blink | Right click | Short teleport toward where you are looking, never into a block |
| Shadow: Swap | Left click | Channel for three seconds, then trade places with whatever you aimed at |
| Shadow: Body | Sneak + right click | Brief invisibility with the armor actually hidden |

**Chainshot** (iron chain)

| Ability | How to cast | What it does |
|---|---|---|
| Chain: Hook | Left click | Fires a claw that latches onto the first block or creature it reaches |
| Chain: Reel | Right click | Pulls along the chain: you toward a block, a creature toward you |
| Chain: Rend | Sneak + right click | The claw spins at the anchor: damage on a creature, a break on a block |

**Levitation Boots** (chainmail boots)

| Ability | How to cast | What it does |
|---|---|---|
| Levitation | Wear them, sneak + right click to arm, then double tap jump | Sustained flight that costs mana, more the longer you stay up; double tap again to land |

**Thor's Hammer** (mace)

| Ability | How to cast | What it does |
|---|---|---|
| Storm: Beam | Right click, hold | A continuous bolt on whatever you aim at, a share of its maximum health per second for a share of your maximum mana |
| Storm: Charge | Sneak + right click, hold sneak, release | Rooted while you charge, then a dash where you look and a second one where you look after it |
| Storm: Smash | Left click | The mace hit, and lightning on whatever it connects with, harder the further you fell |

Written for Paper 1.21.11 on Java 21. No NMS, no mixins, no runtime
dependencies.

## Building

Two ways, pick whichever fits your machine.

**Docker, nothing installed locally.** This needs no JDK and no Gradle on the
host, only Docker:

```bash
docker run --rm --user "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/app/.gradletmp \
  -v "$PWD":/app -w /app gradle:8.14.3-jdk21 gradle build
```

The Gradle cache lands in `.gradletmp/`, which is gitignored.

**Wrapper, if you already have JDK 21:**

```bash
./gradlew build
```

Either way the jar lands in `build/libs/Arcana-<version>.jar`.

## Installing

Copy the jar to `plugins/` and start the server. On first start it writes
`plugins/Arcana/config.yml`.

Under `dev/` there is a `docker-compose.yml` with a throwaway Paper server on
port 25566 for testing without touching production.

## Commands

All under the `arcana.admin` permission (op only by default).

```
/arcana give <player> <id> [amount]   hands out a staff, a mana potion, the boots or the hammer
/arcana list                          registered items and the GriefPrevention, CoreProtect and AuraSkills status
/arcana reload                  re-reads config.yml without a restart
/arcana reset [player]          clears every cooldown and refills every charge pool
/arcana reset [player] <id>     clears only that ability, for example solar_zenith
```

`reset` defaults to the sender and only works on online players, because
the cooldowns live in the player's data container. It reports what it
removed, `Cleared 4 cooldowns and refilled 1 charge pool for vegabernh`, and
never errors on a player who has none. It exists for balancing: without it
the only way to skip a 15 minute cooldown is deleting the playerdata file.

Registered items: the staffs `gravity`, `ice`, `solar`, `shadow` and `chain`, `mana_potion`, `gravity_boots` and `storm_hammer`.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `arcana.admin` | op | Admin commands |
| `arcana.use.gravity_push` | true | Cast Push |
| `arcana.use.gravity_pull` | true | Cast Pull |
| `arcana.use.gravity_leap` | true | Cast Leap |
| `arcana.use.ice_slash` | true | Cast Slash |
| `arcana.use.ice_breaker` | true | Cast Breaker |
| `arcana.use.ice_armor` | true | Cast Armor |
| `arcana.use.solar_lantern` | true | Cast Lantern |
| `arcana.use.solar_zenith` | true | Cast Zenith |
| `arcana.use.solar_bloom` | true | Cast Bloom |
| `arcana.use.shadow_blink` | true | Cast Blink |
| `arcana.use.shadow_swap` | true | Cast Swap |
| `arcana.use.shadow_body` | true | Cast Body |
| `arcana.use.chain_hook` | true | Cast Hook |
| `arcana.use.chain_reel` | true | Cast Reel |
| `arcana.use.chain_rend` | true | Cast Rend |
| `arcana.use.gravity_boots` | true | Fly with the Levitation Boots |
| `arcana.use.storm_hammer` | true | Use Thor's Hammer: Charge, Smash and Beam |

The `arcana.use.*` nodes are checked on every cast, so they can be handed out
per rank from LuckPerms without touching the plugin.

## How the physics works

The two abilities are the same function with the sign flipped, but **not** with
the same distance curve, and that is deliberate:

- **Push** uses `max(0.2, 1 - dist/radius)`. It hits harder up close, which is
  what you expect from a shockwave.
- **Pull** uses `0.4 + 0.6 * (dist/radius)`. It hits harder from afar. With the
  same curve as push, someone fleeing at 12 blocks would barely move, which is
  exactly the case the ability exists for.

A vertical component (`lift`) is added on top of the impulse and the result is
clamped to `max-velocity`. Without the lift, push only makes people slide along
the floor; without the cap, pull sends the target flying over the caster's
head.

**Leap** is different: it only affects the caster. It keeps the horizontal
velocity, sets the vertical one to `jump-power`, and adds `forward-boost` along
the look direction flattened to the horizontal plane. It only casts while
airborne and once per airborne period, so it is a double jump, not flight. The
landing is covered by its own `fall-grace-ticks`.

## How the ice works

Both ice abilities share the `ice` lockout group: while either is on cooldown,
neither can be cast. Everything they do is damage and particles. They never
place ice, freeze water or touch a block, because that is a grief vector and a
claims headache.

Damage always goes through `LivingEntity#damage(amount, caster)`. The kill is
attributed to the caster, and GriefPrevention or any PvP plugin can cancel the
hit the same way it cancels a sword swing, so the abilities inherit the
server's PvP rules for free. The frost read (freeze ticks and Slowness II) is
only applied when the damage actually went through.

- **Slash** hits every valid target within `range` whose direction from the
  caster, on the horizontal plane, is within `arc-degrees` of the look
  direction, for `slash-damage`. It also launches a snowball tagged as ours;
  vanilla snowballs deal no damage, so the plugin sets `snowball-damage` on the
  damage event instead of applying it by hand, which keeps vanilla knockback and
  invulnerability frames. Left clicking a mob also lands the staff's normal
  melee hit, and the slash stacks on top of it. That is intended.
- **Breaker** ray traces blocks and entities from the eye up to `range`,
  ignoring passable blocks and fluids, and takes whichever is closer. The hit
  is resolved before the spiral is drawn, so it never feels like a shot did not
  register. On an entity it deals `damage` plus freeze and slow; on a block it
  is only a particle burst. The spiral is capped at 160 particles per cast.
- **Armor** summons `charges` crystals orbiting the caster. Only attacks
  consume a charge: damage from a living entity, or from a projectile shot by
  one. Fall damage, drowning, fire, starvation and every other environmental
  cause pass straight through. A normal attacker shatters one crystal and the
  hit is fully cancelled. A strong attacker, from the `strong-attackers` list
  or a projectile shot by one, shatters every remaining crystal at once and
  that hit is still fully cancelled: you survive the boss hit once, then you
  are exposed. Players count as normal attackers; that is a deliberate first
  pass and easy to revisit if PvP asks for it. A hit another plugin already
  cancelled never wastes a charge.

  The armor ends when the last charge goes or after `idle-seconds` without an
  absorbed hit. Its cooldown starts only when the armor ends, never at cast
  time. Because the `ice` lockout only blocks while something is on cooldown,
  Slash and Breaker stay usable while the crystals are up. If you want the
  armor to be a stance instead, set `block-abilities-while-active: true`.

  The crystals are block display entities marked non-persistent, so they are
  never written to disk and a crash cannot leave orphans. They are removed on
  the last charge, on idle timeout, on quit, on death, on world change and on
  plugin disable, and the plugin sweeps loaded worlds for stray crystals on
  startup as insurance.

## Configuration

Full `config.yml` with the default values:

```yaml
effects: true

targets:
  players: true
  hostiles: true
  passives: true
  armor-stands: false
  respect-claims: true

items:
  protect-from-crafting: true
  indestructible-when-dropped: true

mana:
  bar: true
  potion:
    restore: 20.0
    recipe-enabled: false

abilities:
  gravity_push:
    radius: 12.0
    strength: 2.6
    lift: 0.6
    max-velocity: 4.0
    cooldown-ticks: 100
    cancel-fall-damage:
      players: false
      mobs: true
    fall-grace-ticks: 120
    mana-cost: 10.0

  gravity_pull:
    radius: 28.0
    strength: 2.2
    lift: 0.35
    max-velocity: 3.5
    cooldown-ticks: 140
    cancel-fall-damage:
      players: false
      mobs: true
    fall-grace-ticks: 120
    mana-cost: 10.0

  gravity_leap:
    jump-power: 0.9
    forward-boost: 0.35
    cooldown-ticks: 20
    fall-grace-ticks: 100
    mana-cost: 5.0

  ice_slash:
    range: 3.5
    arc-degrees: 70.0
    slash-damage: 4.0
    snowball-damage: 3.0
    snowball-speed: 1.8
    freeze-ticks: 60
    slow-duration-ticks: 40
    cooldown-ticks: 30
    mana-cost: 6.0

  ice_breaker:
    range: 24.0
    damage: 7.0
    freeze-ticks: 100
    slow-duration-ticks: 60
    cooldown-ticks: 120
    mana-cost: 12.0

  ice_armor:
    charges: 3
    orbit-radius: 1.2
    orbit-height: 1.0
    orbit-speed: 0.12
    idle-seconds: 15
    cooldown-ticks: 1200
    block-abilities-while-active: false
    strong-attackers:
      - WARDEN
      - ENDER_DRAGON
      - WITHER
      - GHAST
    mana-cost: 20.0

  solar_lantern:
    duration-ticks: 24000
    cooldown-ticks: 12000
    orbit-radius: 0.9
    mana-cost: 15.0

  solar_zenith:
    height: 6
    radius: 20.0
    burn-ticks: 60
    heal-amount: 1.0
    tick-interval: 20
    marker-interval: 10
    marker-height: 12.0
    marker-rings: 5
    marker-points-per-ring: 32
    marker-particle-size: 1.6
    marker-color-a: "FFFFFF"
    marker-color-b: "FF6A00"
    marker-force-render: true
    marker-gradient-cycles: 2.0
    marker-gradient-speed: 0.02
    cooldown-ticks: 18000
    mana-cost: 30.0

  solar_bloom:
    max-charges: 5
    charge-regen-minutes: 12
    mana-cost: 4.0

  shadow_blink:
    range: 14.0
    cooldown-ticks: 60
    mana-cost: 8.0

  shadow_swap:
    range: 20.0
    allow-players: true
    channel-ticks: 60
    channel-min-speed-factor: 0.15
    cancel-on-damage: true
    warn-target: true
    failed-cooldown-ticks: 40
    cooldown-ticks: 200
    fall-grace-ticks: 100
    mana-cost: 12.0

  shadow_body:
    duration-ticks: 100
    break-on-attack: true
    hide-armor: true
    cooldown-ticks: 700
    mana-cost: 15.0

  chain_hook:
    range: 24.0
    travel-speed: 1.6
    hold-ticks: 200
    cooldown-ticks: 40
    mana-cost: 0

  chain_reel:
    pull-self-speed: 1.2
    pull-entity-speed: 1.1
    max-velocity: 2.5
    fall-grace-ticks: 100
    cooldown-ticks: 60
    mana-cost: 0

  chain_rend:
    entity-damage: 6.0
    block-drop-chance: 0.5
    max-block-hardness: 5.0
    blocked-materials:
      - BEDROCK
      - BARRIER
      - SPAWNER
      - END_PORTAL_FRAME
      - REINFORCED_DEEPSLATE
    cooldown-ticks: 100
    mana-cost: 0

  gravity_boots:
    mana-per-second-base: 1.0
    mana-per-second-step: 1.0
    step-interval-seconds: 5
    max-step: 0
    decay-interval-seconds: 5
    tick-period: 10
    fly-speed: 0.05
    grace-slow-falling-seconds: 10
    respect-claims: false
    disarm-on-damage: true
    recipe-enabled: false

  storm_charge:
    min-charge-ticks: 5
    max-charge-ticks: 60
    dash-power-min: 0.8
    dash-power-max: 3.2
    second-dash-delay-ticks: 8
    second-dash-power-multiplier: 0.8
    root-while-charging: true
    cancel-on-damage: true
    mana-cost: 15
    cooldown-seconds: 8

  storm_smash:
    lightning-base-damage: 4.0
    lightning-damage-per-block: 0.5
    lightning-max-damage: 15.0
    lightning-radius: 0.0
    height-source: fall-distance
    mana-cost: 10
    cooldown-seconds: 3

  storm_beam:
    damage-percent-per-second: 5.0
    mana-percent-per-second: 10.0
    max-damage-per-second: 0
    range: 20.0
    tick-period: 10
    affect-players: true
    lose-target-grace-seconds: 1.0

  storm_hammer:
    wind-burst-level: 1
    recipe-enabled: false
```

Notes on decisions that are not obvious:

- `passives: true` is on by design. The abilities deal no damage on their own,
  so pushing and pulling is a valid way to move cows and villagers around.
- `cancel-fall-damage` is split between players and mobs. Players do take fall
  damage, because in PvP that is half the fun. Mobs do not, because otherwise
  transporting animals ends with a dead cow.
- Players in creative and spectator mode are never affected.
- Entities tagged as NPC (the Citizens convention) are ignored.
- `gravity_leap` has no radius or targets: it never touches anyone but the
  caster. Its `cooldown-ticks` is short because the real limit is one use per
  airborne period.
- The ice abilities ignore `targets.players`, `hostiles`, `passives` and
  `armor-stands`: they hit anything living except the caster, NPCs, and
  players in creative or spectator. They do honor `respect-claims`.
- `freeze-ticks` below 140 never deals freezing damage on its own; it is the
  shiver and the frost overlay. Damage is only `slash-damage`,
  `snowball-damage` and `damage`.
- `strong-attackers` takes Bukkit `EntityType` names. Unknown names are logged
  and skipped. `orbit-speed` is radians per tick.
- `solar_zenith` ignores `targets.*`: hostile is anything Bukkit tags as
  `Enemy`, peaceful is any other mob. Players in creative or spectator and
  NPCs are never burned. `heal-amount` is in half hearts, like health.
- `marker-color-a` and `marker-color-b` are `RRGGBB` hex strings, with or
  without a leading `#`. An invalid value is logged and the default kept.
  `marker-force-render: false` goes back to vanilla particle range and
  honors the client's particle setting, which is cheaper on a crowded server.
- `solar_bloom` has no `cooldown-ticks`. Its limit is the charge pool, and
  `charge-regen-minutes` is read on every use, so changing it applies to
  charges already regenerating.
- `shadow_swap` ignores `targets.*` except through `allow-players`, which
  gates whether players are valid targets at all. Its claim rule is not tied
  to `respect-claims`: it always applies, like Bloom's.
- The shadow abilities charge nothing on a cast that never reaches the
  effect: a blink with no room or a swap with nothing in front of it. The
  exceptions are the swap refusals that could be spammed to probe, a target
  inside a claim on cast and a fizzled channel, which cost
  `failed-cooldown-ticks`. `channel-min-speed-factor` is clamped to 0..1.
- `mana-cost` is read from every ability block and clamped at 0. A missing
  key means the default cost, not free; set it to `0` to make an ability
  free. Costs are ignored entirely without AuraSkills.
- `chain_hook.travel-speed` is blocks per tick; at the default the claw
  crosses its full `range` in 15 ticks. `hold-ticks` is how long the claw
  stays latched with nothing done to it. Reel and Rend have no range of
  their own: the hook already is the range.
- `chain_reel.pull-self-speed` and `pull-entity-speed` are blocks per tick
  like the gravity strengths, and `max-velocity` caps both. `fall-grace-ticks`
  covers the caster reeled to a block, and mobs yanked by the chain; players
  yanked take fall damage, like with Gravity: Pull.
- `chain_rend.max-block-hardness` compares against the block's vanilla
  hardness: 5.0 lets deepslate ores (4.5) through and refuses obsidian (50)
  and ancient debris (30). `blocked-materials` takes Bukkit `Material`
  names; unknown names are logged and skipped. `block-drop-chance` is
  clamped to 0..1.
- `gravity_boots` has no `mana-cost` and no `cooldown-ticks`. Its mana keys
  are separate from the cast costs on purpose: the ladder limits itself, so
  a server that doubles every `mana-cost` for a bigger pool should leave
  these alone. `fly-speed` is Bukkit's scale, where vanilla creative flight
  is 0.1. `max-step: 0` means the ladder never stops climbing.
- The storm cooldowns are in seconds, not ticks, and one permission,
  `arcana.use.storm_hammer`, covers the whole weapon. `storm_beam` has no
  `mana-cost`: `mana-percent-per-second` is a share of the caster's maximum
  mana, already calibrated against any pool, so a server that scales every
  `mana-cost` for a bigger pool should leave it alone. `max-damage-per-second:
  0` means no cap. `lightning-radius: 0` means the bolt hits only what the
  hammer hit; above 0 it also hits everything valid within that radius.
  `height-source` accepts `fall-distance` only; anything else is logged and
  falls back to it.

## How the sun works

The three solar abilities have no lockout group: none of them blocks the
others.

- **Lantern** is a personal light for mining. It grants Night Vision for
  `duration-ticks`, particles hidden and icon visible, and spawns one small
  glowing display that orbits the caster at `orbit-radius`. It modifies no
  block. Right click is a **toggle**: with no lantern lit and no cooldown
  running it lights one, and with a lantern lit it dismisses it. Dismissing
  never checks the cooldown. The cooldown is **proportional to the lit
  time**: when the lantern ends, for any reason, it is `cooldown-ticks` times
  `min(1, lit / duration-ticks)`. Running the full 20 minutes costs the full
  10 minute cooldown; dismissing after 5 minutes costs 2.5 minutes. The
  action bar says so on lighting, on dismissing (with the cooldown charged)
  and once when under a minute of light remains. The lantern ends on
  dismiss, when the light runs out, on quit, on death, on world change and on
  plugin disable, and Night Vision is removed on every one of those paths.
- **Zenith** is a static sun `height` blocks above the cast point: a large
  glowing display, plus **one** real `LIGHT` block at that position, placed
  only if the block there is air and GriefPrevention lets the caster build
  there. The light block is removed on every end path and its position is
  written to `plugins/Arcana/lights.yml` the moment it is placed, so after a
  crash the plugin removes any recorded light block still present on the next
  start. Every `tick-interval` ticks, for living entities within `radius` of
  the sun: hostile mobs are set on fire for `burn-ticks`, everywhere; other
  players are set on fire only when there is no claim at their location, which
  matches PvP being free only outside claims; the caster, villagers, tamed
  animals and every other non-hostile mob heal `heal-amount`, capped at max
  health, with heart particles when something was actually healed. The
  boundary is a **wall** of dust particles at `radius`, visible to everyone:
  `marker-rings` rings of `marker-points-per-ring` points stacked over
  `marker-height` blocks centered on the cast height, refreshed every
  `marker-interval` ticks at `marker-particle-size`, and forced with
  `marker-force-render` so they carry to long range regardless of the
  client's particle setting. The color of each point is a **flowing
  gradient** between `marker-color-a` and `marker-color-b`: a triangle wave
  over the point's angle, its ring and a phase that advances every refresh,
  so it ramps A to B and back with no seam and reads as bands of color
  travelling around and along the wall. `marker-gradient-cycles` is how many
  full A to B to A bands are visible around the perimeter at once, and
  `marker-gradient-speed` is how far the bands move per refresh, in cycles;
  zero gives a static gradient. There is a hard cap of 400 points per
  refresh, so a big radius gets sparser rings, not more particles, and points
  inside solid blocks are skipped. The sun has **no time cap**, by design: it ends when the caster
  leaves the radius, and also on quit, death, world change and plugin disable.
  The full cooldown starts when the sun ends, like Ice Armor.
- **Bloom** applies bone meal to the clicked block, through
  `Block#applyBoneMeal`, so it does exactly what a bone meal item would. It
  needs build permission at that block. It is limited by a pool of
  `max-charges`, one regenerating every `charge-regen-minutes`, and a charge
  is spent **only** when the bone meal did something: clicking stone or a
  fully grown crop costs nothing. Success shows happy villager particles and
  an action bar with the charges left and the time until the next one.

## How the shadow works

The three shadow abilities have no lockout group and none of them spawns an
entity. The only repeating task is the swap channel's own, which lives
exactly as long as the channel.

- **Blink** ray traces blocks from the eye along the look direction up to
  `range`, ignoring passable blocks and fluids, and then walks back along
  that ray from the impact point, a quarter block at a time, until it finds
  the furthest point where a standing player fits: a 0.6 by 1.8 box there
  touches no block, no hard entity such as a boat, is inside the world
  border and above the void. That point becomes the feet position. Nothing
  fits within one block of the caster means no blink and no teleport. Because
  the destination is always on the unobstructed line of sight, Blink can
  never put the caster on the far side of a wall, and that is why it has no
  claim check: it is equivalent to walking there. There is **no fall grace**:
  teleporting gives you no control over gravity, so a badly aimed blink over
  a drop hurts, and that is the skill in it. The smoke and portal puff at the
  origin is deliberate, it is what tells other players where you went.
- **Swap** is a **channel**. On cast it ray traces for the first living
  entity within `range`, skipping the caster, NPCs, players in creative or
  spectator, anything riding or being ridden, and players altogether when
  `allow-players` is false, and locks that target for `channel-ticks`. The
  trace ignores blocks, so unlike Blink this can cross a wall, which is why
  it **always refuses** a target standing where the caster has no build
  permission per GriefPrevention: swapping into someone's base would be a
  real protection bypass. While the channel runs the caster slows down
  gradually, through `Player#setWalkSpeed` and not a potion effect, from
  their own walk speed down to that times `channel-min-speed-factor`; the
  speed they had is stored at the start and restored on every exit path.
  Smoke drifts inward from a ring that tightens and thickens as the channel
  advances, the action bar shows a progress bar, and with `warn-target` on a
  targeted player gets a message and particles, because the point of a
  windup is that it can be answered. At the end the target is validated
  again: still alive and valid, same world and within `range` of the
  caster's eyes, still not mounted, still not in a claim the caster cannot
  build in, and the caster not mounted either. Any of those failing makes
  the channel **fizzle** with a message and `failed-cooldown-ticks` instead
  of the full cooldown; running out of range is the counterplay, and it
  pairs with the caster being slowed. On success both trade places, each
  keeping its own pitch and yaw, both with fall grace for `fall-grace-ticks`
  since both are moved against their will, the full cooldown starts, and a
  swapped player is told `You were swapped by <caster>`. The channel is
  cancelled, for nothing, by the caster taking any damage that actually went
  through when `cancel-on-damage` is on, by left clicking again, and on
  quit, death, world change and plugin disable. One channel per player at a
  time.
- **Body** gives the caster Invisibility for `duration-ticks`, particles
  hidden and icon visible, and with `hide-armor` on it hides what vanilla
  invisibility does not: every player tracking the caster is sent AIR for
  the four armor slots and both hands through `Player#sendEquipmentChange`,
  and the real items when it ends. A player who joins or comes into range
  while it is active gets the override one tick after they start tracking
  the caster, since the spawn packets carry the real equipment. Equipment
  changed mid-body is not re-hidden, it is a five second window. With
  `break-on-attack` on, dealing damage to any entity, melee, projectile or
  through another staff, ends it at once with a message, which keeps it an
  escape and ambush tool instead of a free gank. It ends when the duration
  elapses, on attack when enabled, on quit, death, world change, when the
  effect is removed by anything else (milk), and on plugin disable, and the
  armor is restored on every one of those paths.

## How the chain works

The chain is the first **two stage** staff: Hook creates a state, Reel and
Rend spend it. There is one hook per player, held in memory as what it is
anchored to, its claw display and when it expires. Reel and Rend with no
hook anchored do nothing but say so, and charge nothing. None of the three
has a lockout group.

- **Hook** fires a claw, a small chain block display, along the look
  direction. It **travels**: every tick the head advances `travel-speed`
  blocks and that segment is ray traced for a solid block or a living
  entity, whichever comes first, ignoring passable blocks and fluids.
  Entities follow the ice filters: anything living except the caster, NPCs
  and players in creative or spectator. It also applies **Swap's claim
  rule**, always and regardless of `respect-claims`: a target standing where
  the caster has no build permission is refused with a message, because
  yanking someone out of their own base is the same bypass as swapping into
  it. Nothing found within `range` retracts the claw with a sound and no
  cooldown. Latching charges the cooldown, says what was caught, and from
  then on a chain of dust links is drawn every tick from the caster's hand to
  the claw, which follows an entity anchor around, and chain links clink
  every few ticks while it flies and softly while it holds, both at the
  claw for bystanders and to the caster directly so the ability is never
  silent at range. The hook releases by
  itself after `hold-ticks`, when the anchored entity dies or leaves, when
  the anchored block is gone, or when the distance from the caster's eyes to
  the claw exceeds `range` because either side moved. Firing again while
  hooked releases the current hook first and fires a new one. It also
  releases on quit, death, world change and plugin disable, and the claw is
  a non-persistent display tagged like the ice crystals, so it is swept on
  startup as insurance and can never be written to disk.
- **Reel** pulls, and what moves depends on the anchor. On a **block** the
  caster is pulled toward the claw: a task sets the velocity toward the
  anchor every tick, ramped up over the first eight ticks and clamped to
  `max-velocity`, so it reads as being reeled in and stays controllable,
  until the caster is within a block and a half of the anchor, stops moving
  against something, or a generous time cap runs out. Fall grace for
  `fall-grace-ticks` is refreshed every tick of the pull: the chain is
  carrying you, unlike Blink where you teleport under your own power. On an
  **entity** it is Gravity: Pull narrowed to one target, the same impulse
  math with `pull-entity-speed` as the strength, a small lift, and the clamp.
  Both release the hook when the pull finishes.
- **Rend** spins the claw at the anchor. On an **entity** it deals
  `entity-damage` through `LivingEntity#damage(amount, caster)`, so PvP
  rules apply as always, with crit points circling the target and a crit
  impact sound. On a **block** it breaks it with the block's own vanilla
  break sound, with `block-drop-chance` odds of the normal drops,
  as if mined with a diamond pickaxe; the rest of the time the block simply
  vanishes. The chance is there so this is not a way to mine diamonds
  without ever making an iron pickaxe. Every guard must pass, or the break
  is refused with a message, nothing is charged and the hook stays: build
  permission at the block per GriefPrevention, the block's hardness at most
  `max-block-hardness`, not in `blocked-materials`, and never a block with
  an inventory (chests, barrels, shulker boxes, furnaces, hoppers), whatever
  the list says. A successful break is **logged in CoreProtect** as the
  caster before the block goes, so `/co inspect` shows it and a rollback
  restores it; without that, blocks removed by a plugin leave no trace. Rend
  releases the hook after use.

## How the boots work

The Levitation Boots are the first armor item and the first ability that
charges mana continuously instead of per cast. Nothing is cast: the boots
are worn, armed with a sneak click, and the double tap that toggles
creative flight is the switch. Three states:

- **Worn.** The boots do nothing on their own. `allowFlight` stays whatever
  it was and falls hurt like vanilla. This is deliberate: `allowFlight`
  itself is what lets the client send the double tap, and vanilla never
  applies fall damage to a player who has it, so keeping it on all the
  time would make the boots a permanent feather falling and turn every
  jump spam into an accidental flight.
- **Armed.** Sneak and right click with an **empty main hand**, on air or on
  any block that has no interaction of its own, while wearing the boots in
  survival or adventure with the permission. A chest, an anvil, a door or a
  villager behaves as if the boots did not exist, and so does anything held
  in the hand, which is why the hand must be empty: an item there would be
  placed, eaten or fired at the same time. A claim plugin that cancels the
  click on ground it protects does not stop it: the boots listen to the
  cancelled event too, like the staffs, because the click is only a signal
  and nothing in the world is touched. Vanilla spawn protection is
  different, the server drops the click before any event fires, so inside
  it only ops can arm. Arming switches `allowFlight` on, says so in chat
  and chimes. Chat, not the action bar: AuraSkills rewrites the action bar
  every few ticks with its own mana bar, so an action bar message is gone
  before it can be read. What the player had before, `allowFlight` and
  `flySpeed`, is written to their data container the moment it is granted,
  and that grant **is** the armed state, so it survives a relog and a
  crash like a cooldown. The same sneak click disarms. Disarming puts the
  previous values back and, if a flight was running, ends it first with the
  Slow Falling grace. Everything that takes the boots out of play disarms
  the same way: taking them off, quit, death, a world change, a gamemode
  change, plugin disable, and, with `disarm-on-damage` on, any hit from an
  entity that actually went through, melee or projectile. That last one is
  for a PvP server where claims are the only protection: without it, flying
  boots make fleeing any fight trivial and attacking from above free. That
  is also what keeps an Essentials `/fly` alive: an admin with flight on who
  arms and disarms still has flight on afterwards. A grant still present
  when a player joins, or when the plugin enables with players online,
  means the last session ended without a clean disarm, and the previous
  values are restored before anything else sees the player. The console
  logs it. While armed and not flying, vanilla's no-fall-damage rule for
  `allowFlight` applies, so arm before a drop is a legitimate use; nothing
  is free about it since a hit disarms.
- **Flying.** The double tap turns flight on, with the checks in the usual
  order: permission, then elytra (never while gliding), then the claim when
  `respect-claims` is on, then mana for the first charge. Each refusal
  cancels the toggle so the client lands again. While flying, every
  `tick-period` ticks the boots charge `mana-per-second` scaled to the
  period, where `mana-per-second` is `mana-per-second-base` plus
  `mana-per-second-step` times the **ladder step**, the accumulated flight
  seconds divided by `step-interval-seconds`, rounded down and capped by
  `max-step` when it is not 0. With the defaults that is 1 mana per second
  for the first five seconds, 2 for the next five, 3 for the next, and so
  on. A double tap, or touching the ground, turns it off. Fly speed is set
  to `fly-speed` while flying and put back afterwards.
- **The counter is persistent and decays.** The accumulated seconds live in
  the player's data container, written every charge, and with the flight
  off they lose one step every `decay-interval-seconds`. They are never
  reset by landing, by toggling or by a relog. Without the decay, switching
  the flight off and on every few seconds would fly at the base cost
  forever and the ladder would limit nothing; with it, resting for ten
  seconds is worth two steps, and resting for one second is worth nothing.
- **Out of mana** ends the flight at once, applies Slow Falling for
  `grace-slow-falling-seconds` and says so. Taking the boots off in the air
  does the same, immediately. The grace is 10 seconds because Slow Falling
  starts from rest and only approaches its 9.8 m/s after a few seconds:
  measured on the dev server, 6 seconds of it cover about 37 blocks from a
  hover and 10 seconds cover a 60 block drop with the effect still on at
  the ground. The flight also ends on quit, death, world
  change, gamemode change and plugin disable, and if another plugin sets
  the player to not flying without an event, the next charge notices and
  ends it too.
- **Feedback.** From the moment the boots are armed, the mana bar is shown
  even with no staff in hand, with `Levitation Boots armed` in the title,
  and `flying, step N (cost/s)` while flying; it goes when they disarm. The
  state is always visible instead of depending on a message. Every arm and
  disarm, including the one caused by a hit, is a chat line, and a chime
  marks each step up. Without AuraSkills the flight is free, like
  every other cost.
- The boots are tagged like the potion, so the item guard and the dropped
  item protection cover them, and they are unbreakable: armor wears out on
  every hit, and a magic item that quietly breaks after a few fights would
  just be lost. There is an optional recipe, off by default like the
  potion's: chainmail boots, a feather and two phantom membranes,
  shapeless, under `gravity_boots.recipe-enabled`.
- Gravity: Leap and the boots share nothing. Leap is a left click with the
  Gravity Staff while airborne and never touches `allowFlight` or the
  flight toggle, so the two coexist. The one rule is that Leap refuses,
  silently, while the boots are flying the caster: a jump means nothing in
  flight and would only spend mana.

## How the storm works

Thor's Hammer is the first weapon: a mace, unbreakable, with Wind Burst at
`wind-burst-level` applied when it is created, registered as a wand so the
click dispatcher, the lore, the mana bar and the give command cover it like a
staff. It is also the first item with a percentage cost and a percentage
damage, though not true damage: see below. Because it is a real mace, the vanilla smash attack, its knockback
and the Wind Burst launch all work as usual; the plugin adds on top and never
cancels the attack.

- **Charge** is a channel that starts on the sneak click and ends when sneak
  is released. Time accumulates up to `max-charge-ticks`; with
  `root-while-charging` on, `walkSpeed` goes to 0 for the duration, and the
  speed the caster had is written to their data container the moment it is
  taken, like the boots' flight grant: it is restored on every exit path, and
  a grant still present on join, or when the plugin enables with players
  online, means the last session died mid-channel and the previous speed is
  put back before anything else sees the player. Measured: after a `docker
  kill` with a player rooted, the player file carried `walkSpeed 0.0`, and on
  the next join the console logged the restore and the file read `0.1`. On
  release, held at least `min-charge-ticks`, the caster is launched along
  the look direction at a power interpolated from `dash-power-min` at the
  minimum to `dash-power-max` at `max-charge-ticks`, and
  `second-dash-delay-ticks` later a second dash fires toward wherever they
  look by then at `second-dash-power-multiplier` of the first. The chat says
  `Storm: Charge: released at 53%`. Released before the minimum, nothing
  happens: no dash, no cooldown, and the mana paid on cast, like Swap's,
  comes back. The same refund happens on every cancel: damage that went
  through with `cancel-on-damage` on, the hammer leaving the hand (hotbar
  scroll, hand swap, drop, or noticed at release), quit, death, world change,
  gamemode change and plugin disable. The cooldown starts only at a release
  that fired. There is no fall grace on purpose: a dash aimed upward ends in
  a fall, and a fall with the hammer in hand is a smash. Measured with a
  headless client on flat stone, both dashes summed: 4.2, 9.3 and 15.0
  blocks for the minimum, half and full charge aimed level, where ground
  friction eats most of it, and 8.0, 19.7 and 34.1 blocks aimed 30 degrees
  up. On the ground the distance is linear in the dash power, 4.7 blocks
  per block per tick; aimed up it grows a little faster than that, since a
  stronger dash also stays in the air longer. Chained back to back with the
  mana refilled, five charges a minute: 75 blocks per minute aimed level
  and 156 aimed up, against a walk of about 260.
- **Smash** is the mace's own melee hit, seen from `EntityDamageByEntityEvent`
  at MONITOR with `ignoreCancelled`: a swing that misses does nothing, and
  only a hit that actually went through strikes. The vanilla hit is never
  cancelled and never waits: no mana, no permission or a running cooldown
  only lose the bolt, and the refusal for mana is a chat line. The checks run
  in the usual order, permission, cooldown, mana, then the claim rule, and
  the bolt lands one tick later so the two damages never nest inside the
  same event. The bolt is `strikeLightningEffect`, the flash and the thunder
  with no fire and no damage of its own, plus `lightning-base-damage` plus
  `lightning-damage-per-block` times the caster's fall distance at the
  moment of the hit, the same number the mace reads for its own bonus,
  capped at `lightning-max-damage`, dealt as lightning damage with the
  caster as damager. Measured on a 1000 health husk, mace and bolt
  separated by repeating each hit with the pool empty: from 0, 3.3, 8.6 and
  18.8 blocks of fall the mace did 5.9, 27.6, 42.2 and 57.2 and the bolt
  added 3.9, 5.6, 8.2 and 13.2. The chat line after each bolt says both the
  damage and the blocks.
- **Beam** lives on the held right button. The vanilla client repeats a held
  right click every four ticks, so the first click starts the beam, every one
  after refreshes it, and eight ticks without one end it. Every tick it ray
  traces blocks and living entities from the eye up to `range`, so it never
  crosses a wall, and draws the sparks to whatever it hit. Every
  `tick-period` ticks with a target in the line of fire it charges
  `mana-percent-per-second` of the caster's maximum mana, scaled to the
  period, and deals `damage-percent-per-second` of the target's maximum
  health the same way, capped by `max-damage-per-second` when that is not
  0. With nothing in the line of fire it charges nothing at all, and after
  `lose-target-grace-seconds` of that it ends with `Storm: Beam lost its
  target.`; out of mana ends it with `Storm: Beam: out of mana.` A right
  click on an entity with an interaction of its own, a villager, a horse,
  an item frame, is that interaction on the client and never sends the use
  packet, so with the hammer in the main hand the interaction is cancelled
  and the click goes to the dispatcher like any other: the beam, or the
  charge when sneaking. With the hammer wielded there is no trading and no
  mounting; change slot for that. Measured at two blocks: a villager and a
  tame horse get the beam, no trade window opens and nobody mounts, and an
  item frame keeps its rotation and gets a beam with no target, since a
  frame is not a living entity. Players are
  targets only with `affect-players` on, and both the beam and the bolt
  apply the usual claim rule: a target standing where the caster cannot
  build is refused with a message, so the beam on such a player is a beam
  with no target. The hand is only read on charge ticks, never per tick,
  since reading an item's container copies its meta. Measured from a full
  pool of 22: 11.0 seconds and 53% of the target's maximum health, the same
  on a husk, a ravager and a warden, 21 charges where the pool pays 20 and
  regeneration one.
- **The damage** is `LivingEntity#damage(amount, DamageSource)` with
  `DamageType.MAGIC` for the beam and `DamageType.LIGHTNING_BOLT` for the
  bolt, the caster as both causing and direct entity. That is the vanilla
  pipeline: `EntityDamageByEntityEvent` fires with the caster as damager, so
  the PvP flag, claims, god modes, totems, absorption and kill credit all
  apply as for a sword. Neither is true damage. Magic damage ignores armor
  points and shields, and that is all it ignores: the Protection
  enchantment reduces it like any other damage, a full set of Protection
  IV by 64% and the enchantment formula caps at 80%, Resistance takes its
  20% per level, and witches take 15% of it, as in vanilla. The beam is a
  PvE tool, and a fully enchanted player shrugs most of it off; that is
  accepted, not a bug to chase. Lightning damage is reduced by armor and
  by Protection the same way. What neither does is touch the
  target's invulnerability window: vanilla keeps every hit within ten ticks
  of the last only for the amount above it and refreshes the window on each
  hit, so a beam ticking twice a second would leave its target immune to
  everyone else half the time. So the window and the last damage amount are
  saved, cleared for the hit, and put back afterwards. Measured with a
  second player hitting the same 1000 health husk with an iron sword every
  0.65 seconds for ten seconds: 94.5 damage alone, 94.5 next to a beam that
  took 525 of its own.
- **Feedback.** While a channel or a beam runs the mana bar shows it in the
  title, `Storm: Charge 53%` or `Storm: Beam on Zombie`, next to the boots
  state when both apply. Every event, a release, a cancel, a refusal, the
  bolt, is a chat line, for the reason the boots' are.
- **Not upgradeable.** Wind Burst II and III exist in vanilla and drop from
  ominous vaults. The hammer never reaches an anvil, an enchanting table or
  a grindstone: the item guard refuses any tagged item moved into those
  inventories, by click, drag, shift click, hotbar key or hopper. Measured
  with a Wind Burst II book in the anvil's second slot: the hammer is
  refused in the first slot, by drop and by shift click, while a plain mace
  in the same slot combines with the book as usual; the same in the
  enchanting table and the grindstone.
- Measured with spark on a warm server, two players holding a beam on a
  target for a full minute: the beam task took 0.30% of the server thread
  for both, about 0.15 ms per tick, and the repeated clicks another 0.12%.
  There is an optional recipe, off by default: a mace, two breeze rods and a
  lightning rod, shapeless, under `storm_hammer.recipe-enabled`.

## Item protection

Every staff is a vanilla item with vanilla uses: an end rod or a chain
places as a block, from either hand, a blaze rod burns as fuel and crafts
into blaze powder, a breeze rod crafts into wind charges, an echo shard into
a recovery compass. Anything carrying an Arcana tag is kept out of all of
that, by the tag alone and not by material, so items added later are
covered automatically. Placing is cancelled from either hand, crafting is
refused and shows no result while a tagged item sits in the grid, a furnace
never burns one as fuel, and a click, drag or shift click that would move
one into a crafting or processing inventory (crafting grid, crafter,
furnace, blast furnace, smoker, anvil, smithing table, grindstone,
stonecutter, brewing stand, loom, cartography table, enchanting table) is
refused, as is a hopper feeding one in. The player gets
`Arcana items cannot be crafted or placed` on the action bar, at most once
a second. Chests, barrels, shulker boxes and ender chests are untouched:
storing a wand is normal, destroying one is not. This is
`items.protect-from-crafting`.

A tagged item on the ground is also **indestructible**: the moment an item
entity spawns, dropped by hand, dropped on death or popped out of a broken
block, it is made invulnerable and given unlimited lifetime, so lava, fire,
explosions and cactus cannot destroy it and it never despawns. Damage to a
tagged item entity is cancelled as well, as a backstop. The void still
deletes it, nothing can be done there, and merging and pickup are untouched.
This is `items.indestructible-when-dropped`, applied when the item spawns:
turning it off leaves items already on the ground protected until they are
picked up and dropped again. Both flags default to true; a server that
wants wands to be losable turns them off.

## Mana

Mana comes from [AuraSkills](https://aurelium.dev/auraskills), through its
API, and the whole thing is **optional**: without AuraSkills the plugin
behaves exactly as described everywhere else in this file, with no mana
costs, no refusals, no bar and no errors. `/arcana list` says whether the
bridge is active.

Every ability has a `mana-cost` in its own config block. On cast the checks
run in this order: permission, group lockout, cooldown, mana, and only then
the ability. Not enough mana refuses with
`Solar: Zenith needs 30 mana, you have 12` and starts no cooldown. The mana
is spent when the ability actually executes, never when it refuses: a blink
with no room, a reel with no hook, or bone meal on stone cost nothing.
Dismissing a lantern or cancelling a swap channel is free too. Shadow: Swap
pays at the start of the channel, since the channel is the cast and a
fizzle already has its own penalty. The defaults are tuned against a pool of
roughly 40 to 60. The Chainshot costs 0 **on purpose**, it is a tool and not
magic, but the keys exist so another server can charge for it.

**Mana bar.** A boss bar shown while an Arcana wand is in either hand or the
Levitation Boots are armed, hidden the moment neither is true, with the
current and maximum mana in the title, plus the boots state: armed, or the
ladder step and cost per second while flying. One task refreshes it every 10 ticks for every online
player, and a hand change refreshes it on the spot. Never shown when the
bridge is inactive; `mana.bar: false` turns it off for servers that already
display mana elsewhere. It is removed on quit, world change and plugin
disable.

**Mana potions.** `/arcana give <player> mana_potion [amount]` hands out a
real potion with a custom colour, so it drinks like one. Drinking restores
`mana.potion.restore` mana, capped at the maximum, consumes one from the
stack and leaves a glass bottle behind, like any potion. It refuses, and is
not consumed, when the server has no mana or the mana is already full. The
potion is tagged like a wand, so the item guard and the dropped item
protection cover it with no extra work. There is an **optional recipe**,
off by default so no server is surprised by a new recipe:
`mana.potion.recipe-enabled: true` registers a shapeless recipe of one glass
bottle, two lapis lazuli and one glowstone dust, and a reload adds or removes
it to match. The recipe result carries the restore amount of the moment it
was registered, which is why a reload re-registers it.

Like the GriefPrevention and CoreProtect hooks, the bridge is done **by
reflection**: `AuraSkillsApi.get().getUser(uuid)` and the `SkillsUser` mana
methods, resolved at startup. If AuraSkills answers unexpectedly the bridge
disables itself with one warning and every mana cost is ignored from then on.
A player AuraSkills has not loaded yet is treated the same way for that
cast: mana never blocks a cast it cannot see.

## Cooldowns and charges

Cooldowns are stored in the player's `PersistentDataContainer`, which is part
of the player data file, so they survive a relog and a server restart. What is
stored is the absolute expiry time in epoch millis, not the remaining ticks:
waiting offline costs exactly the same as waiting online, and a crash loses at
most what the server had not yet saved. The write happens the moment the
cooldown starts. There is no memory cache, the container is the store.

Abilities limited by charges instead of a cooldown use the same container: a
charge count and the timestamp of the last regeneration. Regeneration is
computed from the wall clock whenever the pool is read, so there is no ticking
task and offline time counts.

## GriefPrevention

If the plugin is installed and `respect-claims: true`, no ability affects
entities standing inside a claim where the caster has no build permission. This
rules out the obvious abuse: shoving someone out of their own base.

The hook is done **by reflection**, not as a compile-time dependency. The build
is not tied to a specific GriefPrevention version and the plugin starts the same
when it is absent. If the API changes, the check disables itself, logs it to the
console, and the abilities keep working without claim protection.

## CoreProtect

Chain: Rend is the one ability that removes a block, and CoreProtect does
not see blocks removed by plugins. So the plugin hands the break to
CoreProtect's logging API itself, as the caster, before removing the block.
Like the GriefPrevention hook this is done **by reflection**: a soft
dependency, no compile time coupling. If CoreProtect is absent, disabled,
older than API version 9, or answers unexpectedly, the plugin warns once in
the console and Rend keeps working unlogged; the action bar then says `(not
logged)` after a break. `/arcana list` reports whether the logging is
active, next to the GriefPrevention line.

## Known limitations

- The visual cooldown uses `setCooldown(Material, ticks)`, which in vanilla
  applies to **every** item of that material in the inventory. The real cooldown
  is tracked by the plugin per player and per ability; the visual one is only
  that.
- With the staff in hand, both mouse buttons are cancelled. You will not open
  a chest or break a block while wielding it. Hitting a mob still lands the
  staff's melee hit before the left click ability fires.
- Leap decides whether you are airborne from the on-ground flag the client
  reports, the same one vanilla uses for fall distance.
- Rend removes a block outside of `BlockBreakEvent`, so other plugins that
  listen for breaks do not see it. The guards above (claims, hardness, the
  blocklist, containers) and the CoreProtect log are what stands in for that.
- Zenith is the one ability that places a block. It is a single `LIGHT`
  block, invisible and passable, only in air and only where the caster may
  build, tracked on disk so a crash cannot leave it behind. The sun itself is
  a non-persistent display tagged like the ice crystals, removed on every end
  path and swept on startup. If the server dies
  between placing the block and writing `lights.yml` (microseconds), that one
  block survives until someone breaks it or a zenith is cast there again.
- Dragon breath is an area effect cloud, not a living entity or a projectile,
  so it passes through Ice Armor like environmental damage.
- While the Levitation Boots are armed, `allowFlight` is on, and vanilla
  never applies fall damage to a player with `allowFlight` on, flying or
  not (`Player#causeFallDamage` returns early on `mayfly`). Arming is a
  deliberate act and a hit disarms, so this is contained, but an armed
  player who is not flying does not take fall damage. Measured: a 15 block
  drop with the boots merely worn does 12 damage, the same as without them.
- While armed, the vanilla double tap is live: a player who spams jump in
  the air will start flying and pay the first charge. That is the cost of
  using the vanilla toggle instead of a key the client does not have, and
  why arming is explicit.
- Arming needs an empty main hand and a click on air or on a block with no
  interaction of its own. Right clicking air with an empty hand sends no
  packet in vanilla, so in practice it is a click on the ground or a wall.
  `/arcana reset` does not touch the ladder counter; it decays by itself
  within seconds.
- Wind Burst is vanilla and does what it does: every smash on a target
  launches the caster again, and a player who keeps landing on the same
  target keeps bouncing. Measured with a 2.2 dash straight up next to a
  target: nine hits in thirteen seconds, one per bounce, each a mace
  smash from about four blocks. Smash's cooldown only limits the bolt, not
  the bounce.
- A rooted caster still steers in the air: `walkSpeed` only governs ground
  movement.
- `setVelocity` on players is the same mechanism anticheats flag as suspicious.
  Without an anticheat there is no problem. The day Grim comes in, those ticks
  will need an exemption.

## Growing this

The structure is already laid out for more than one ability:

- `Ability` is the interface. A new ability is implemented and registered in
  `ArcanaPlugin#onEnable`.
- `Wand` maps an item to three slots: right click, sneak + right click and
  left click. The left click slot may be null. A new staff is one line. A
  wand that needs more than the generic item, Thor's Hammer with its
  enchantment and unbreakable flag, builds on `AbilityItems#create` in its
  own class and is special cased in the give command.
- A left click that lands on an entity within reach never fires
  `PlayerInteractEvent`. The dispatcher sees it from
  `EntityDamageByEntityEvent`, and an ability that needs the hit itself,
  Storm: Smash, listens there at MONITOR, refuses the dispatcher through
  `canCast`, and keeps its slot only for the lore, the permission and the
  reset command.
- `Ability#lockoutGroup` lets a set of abilities share a lockout: while one is
  on cooldown, none of the others in the group can be cast. The ice abilities
  use it, the gravity ones do not.
- An ability with a duration returns false from `startsCooldownOnCast` and
  starts its own cooldown when it ends. `blocksGroupWhileActive` lets it hold
  the group meanwhile. Ice Armor is the reference implementation, including
  the cleanup on every exit path a stateful ability needs. Solar Lantern and
  Zenith follow the same shape, and `Displays` holds the shared glowing block
  display and orbit math. Chain: Hook holds state that two other abilities
  consume, which is the shape for any multi stage staff.
- `Ability#replacesActiveCast` lets a cast reach an ability while it is
  running, skipping the lockout and cooldown checks, so it can dismiss or
  replace the running instance. Solar Lantern uses it as an off switch.
- `Ability#loreHint` appends a short note to the wand lore, such as
  `(toggle)`.
- `Ability#cast(Player, Block)` receives the clicked block for left clicks on
  a block, null otherwise. Solar Bloom is the only one that needs it.
- Abilities limited by charges instead of a cooldown read and spend them
  through `PlayerStore`, the same container the cooldowns live in.
- Items are identified by `PersistentDataContainer`, never by name or lore, so
  renaming a stick in an anvil forges nothing.
- Not everything is a cast. The Levitation Boots are worn, not wielded,
  and are driven by events: `GravityBoots` holds the flight state and
  `GravityBootsListener` feeds it. An item that is not a wand is tagged
  with `itemKey`, like the potion, and registered in `AbilityItems` for the
  give command.
- `Ability#cast` returns whether the ability actually executed. False means
  it refused, dismissed or cancelled, and the caller then starts no cooldown
  and spends no mana. Any new refusal path should return false.

The same applies to bosses: a boss is a vanilla mob with changed attributes and
a `BukkitRunnable` that decides which ability to cast based on its health, and
those abilities can be exactly these.

## License

MIT.
