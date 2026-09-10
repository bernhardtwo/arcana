# Arcana

Item-bound magic abilities for Paper servers. A staff in hand, right click,
and something happens around you.

Two staffs so far.

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

Sneak + right click on the ice staff is reserved for Ice Armor, coming later.

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
```

Registered staffs: `gravity` and `ice`.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `arcana.admin` | op | Admin commands |
| `arcana.use.gravity_push` | true | Cast Push |
| `arcana.use.gravity_pull` | true | Cast Pull |
| `arcana.use.gravity_leap` | true | Cast Leap |
| `arcana.use.ice_slash` | true | Cast Slash |
| `arcana.use.ice_breaker` | true | Cast Breaker |

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
- Items are identified by `PersistentDataContainer`, never by name or lore, so
  renaming a stick in an anvil forges nothing.

The same applies to bosses: a boss is a vanilla mob with changed attributes and
a `BukkitRunnable` that decides which ability to cast based on its health, and
those abilities can be exactly these.

## License

MIT.
