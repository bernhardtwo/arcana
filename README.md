# Arcana

Item-bound magic abilities for Paper servers. A staff in hand, right click,
and something happens around you.

First staff: **gravity**. A single item with three abilities.

| Ability | How to cast | What it does |
|---|---|---|
| Gravity: Push | Right click | Repels every living thing around the caster |
| Gravity: Pull | Sneak + right click | Drags every living thing within the radius toward the caster |
| Gravity: Leap | Left click, while airborne | A second jump in the air, once per jump, with a soft landing |

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

The only staff registered today is `gravity`.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `arcana.admin` | op | Admin commands |
| `arcana.use.gravity_push` | true | Cast Push |
| `arcana.use.gravity_pull` | true | Cast Pull |
| `arcana.use.gravity_leap` | true | Cast Leap |

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
  on cooldown, none of the others in the group can be cast. The gravity
  abilities do not use it.
- Items are identified by `PersistentDataContainer`, never by name or lore, so
  renaming a stick in an anvil forges nothing.

The same applies to bosses: a boss is a vanilla mob with changed attributes and
a `BukkitRunnable` that decides which ability to cast based on its health, and
those abilities can be exactly these.

## License

MIT.
