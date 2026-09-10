# Arcana

Item-bound magic abilities for Paper servers. A staff in hand, right click,
and something happens around you.

Four staffs so far.

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
/arcana give <player> <staff>   hands out a staff
/arcana list                    registered staffs and GriefPrevention status
/arcana reload                  re-reads config.yml without a restart
/arcana reset [player]          clears every cooldown and refills every charge pool
/arcana reset [player] <id>     clears only that ability, for example solar_zenith
```

`reset` defaults to the sender and only works on online players, because
the cooldowns live in the player's data container. It reports what it
removed, `Cleared 4 cooldowns and refilled 1 charge pool for vegabernh`, and
never errors on a player who has none. It exists for balancing: without it
the only way to skip a 15 minute cooldown is deleting the playerdata file.

Registered staffs: `gravity`, `ice`, `solar` and `shadow`.

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

  gravity_leap:
    jump-power: 0.9
    forward-boost: 0.35
    cooldown-ticks: 20
    fall-grace-ticks: 100

  ice_slash:
    range: 3.5
    arc-degrees: 70.0
    slash-damage: 4.0
    snowball-damage: 3.0
    snowball-speed: 1.8
    freeze-ticks: 60
    slow-duration-ticks: 40
    cooldown-ticks: 30

  ice_breaker:
    range: 24.0
    damage: 7.0
    freeze-ticks: 100
    slow-duration-ticks: 60
    cooldown-ticks: 120

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

  solar_lantern:
    duration-ticks: 24000
    cooldown-ticks: 12000
    orbit-radius: 0.9

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

  solar_bloom:
    max-charges: 5
    charge-regen-minutes: 12

  shadow_blink:
    range: 14.0
    cooldown-ticks: 60

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

  shadow_body:
    duration-ticks: 100
    break-on-attack: true
    hide-armor: true
    cooldown-ticks: 700
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
- Zenith is the one ability that places a block. It is a single `LIGHT`
  block, invisible and passable, only in air and only where the caster may
  build, tracked on disk so a crash cannot leave it behind. The sun itself is
  a non-persistent display tagged like the ice crystals, removed on every end
  path and swept on startup. If the server dies
  between placing the block and writing `lights.yml` (microseconds), that one
  block survives until someone breaks it or a zenith is cast there again.
- Dragon breath is an area effect cloud, not a living entity or a projectile,
  so it passes through Ice Armor like environmental damage.
- `setVelocity` on players is the same mechanism anticheats flag as suspicious.
  Without an anticheat there is no problem. The day Grim comes in, those ticks
  will need an exemption.

## Growing this

The structure is already laid out for more than one ability:

- `Ability` is the interface. A new ability is implemented and registered in
  `ArcanaPlugin#onEnable`.
- `Wand` maps an item to three slots: right click, sneak + right click and
  left click. The left click slot may be null. A new staff is one line.
- `Ability#lockoutGroup` lets a set of abilities share a lockout: while one is
  on cooldown, none of the others in the group can be cast. The ice abilities
  use it, the gravity ones do not.
- An ability with a duration returns false from `startsCooldownOnCast` and
  starts its own cooldown when it ends. `blocksGroupWhileActive` lets it hold
  the group meanwhile. Ice Armor is the reference implementation, including
  the cleanup on every exit path a stateful ability needs. Solar Lantern and
  Zenith follow the same shape, and `Displays` holds the shared glowing block
  display and orbit math.
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

The same applies to bosses: a boss is a vanilla mob with changed attributes and
a `BukkitRunnable` that decides which ability to cast based on its health, and
those abilities can be exactly these.

## License

MIT.
