package com.bernhardtwo.arcana.ability;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Cooldowns and charge pools, stored in the player's PersistentDataContainer.
 * The container is part of the player data file: the server loads it with the
 * player and saves it on quit, on shutdown and on autosave, so nothing here
 * needs a cache, a join hook or a ticking task. Everything is absolute epoch
 * millis, so time spent offline counts exactly like time spent online.
 */
public final class PlayerStore {

    private final Plugin plugin;

    public PlayerStore(Plugin plugin) {
        this.plugin = plugin;
    }

    // ----- cooldowns -----

    public long remainingMillis(Player player, String abilityId) {
        Long until = player.getPersistentDataContainer().get(cooldownKey(abilityId), PersistentDataType.LONG);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    /** Write-through: the expiry lands in the player data the moment the cooldown starts. */
    public void startCooldown(Player player, String abilityId, int ticks) {
        player.getPersistentDataContainer()
                .set(cooldownKey(abilityId), PersistentDataType.LONG, System.currentTimeMillis() + ticks * 50L);
    }

    /**
     * Cooldown plus the vanilla indicator on the item in hand. The indicator is
     * per material, so a short cooldown never overwrites a longer one still running.
     */
    public void startCooldownWithIndicator(Player player, String abilityId, int ticks) {
        if (ticks <= 0) {
            return;
        }
        startCooldown(player, abilityId, ticks);
        Material held = player.getInventory().getItemInMainHand().getType();
        if (player.getCooldown(held) < ticks) {
            player.setCooldown(held, ticks);
        }
    }

    // ----- charge pools -----

    /** A pool after lazy regeneration. {@code nextInMillis} is 0 when the pool is full. */
    public record Charges(int count, long nextInMillis) {
    }

    public Charges charges(Player player, String abilityId, int max, long regenMillis) {
        Pool pool = read(player, abilityId, max, regenMillis);
        long next = pool.count >= max ? 0L : Math.max(0L, pool.lastRegen + regenMillis - System.currentTimeMillis());
        return new Charges(pool.count, next);
    }

    /** Spends one charge. Returns false, and writes nothing, when the pool is empty. */
    public boolean consumeCharge(Player player, String abilityId, int max, long regenMillis) {
        Pool pool = read(player, abilityId, max, regenMillis);
        if (pool.count <= 0) {
            return false;
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(chargesKey(abilityId), PersistentDataType.INTEGER, pool.count - 1);
        data.set(regenKey(abilityId), PersistentDataType.LONG, pool.lastRegen);
        return true;
    }

    /** Admin reset: what was actually removed, so the command can say so. */
    public record Cleared(boolean cooldown, boolean charges) {
    }

    /** Drops the cooldown and the charge pool of one ability. A pool with no keys is already full. */
    public Cleared clear(Player player, String abilityId) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        boolean cooldown = data.has(cooldownKey(abilityId), PersistentDataType.LONG);
        boolean charges = data.has(chargesKey(abilityId), PersistentDataType.INTEGER);
        data.remove(cooldownKey(abilityId));
        data.remove(chargesKey(abilityId));
        data.remove(regenKey(abilityId));
        return new Cleared(cooldown, charges);
    }

    // ----- flight granted by the Levitation Boots -----

    /** What the player had before Arcana switched allowFlight on, so it can be put back exactly. */
    public record FlightGrant(boolean allowFlight, float flySpeed) {
    }

    /** True while the keys are present: Arcana granted this player's allowFlight and has not restored it yet. */
    public boolean hasFlightGrant(Player player) {
        return player.getPersistentDataContainer().has(flightAllowKey(), PersistentDataType.BYTE);
    }

    /** Write-through, like a cooldown: a crash after this point is recoverable on the next join. */
    public void grantFlight(Player player, FlightGrant previous) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(flightAllowKey(), PersistentDataType.BYTE, (byte) (previous.allowFlight() ? 1 : 0));
        data.set(flightSpeedKey(), PersistentDataType.FLOAT, previous.flySpeed());
    }

    /** The grant, or null when there is none. */
    public FlightGrant flightGrant(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Byte allow = data.get(flightAllowKey(), PersistentDataType.BYTE);
        if (allow == null) {
            return null;
        }
        Float speed = data.get(flightSpeedKey(), PersistentDataType.FLOAT);
        return new FlightGrant(allow != 0, speed == null ? 0.1f : speed);
    }

    /** Removes the grant and returns what to restore, or null when there was none. */
    public FlightGrant revokeFlight(Player player) {
        FlightGrant grant = flightGrant(player);
        if (grant != null) {
            PersistentDataContainer data = player.getPersistentDataContainer();
            data.remove(flightAllowKey());
            data.remove(flightSpeedKey());
        }
        return grant;
    }

    /**
     * Accumulated flight seconds after lazy decay: one step of
     * {@code stepSeconds} is lost for every whole {@code decayMillis} elapsed
     * since the last write. Never resets on its own; only decays to 0.
     */
    public double flightSeconds(Player player, double stepSeconds, long decayMillis) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Double seconds = data.get(flightSecondsKey(), PersistentDataType.DOUBLE);
        Long at = data.get(flightAtKey(), PersistentDataType.LONG);
        if (seconds == null || at == null) {
            return 0.0;
        }
        long lost = (System.currentTimeMillis() - at) / decayMillis;
        return lost <= 0L ? seconds : Math.max(0.0, seconds - lost * stepSeconds);
    }

    /** Write-through with the wall clock, so decay is measured from the last moment the boots were charging. */
    public void writeFlightSeconds(Player player, double seconds) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(flightSecondsKey(), PersistentDataType.DOUBLE, seconds);
        data.set(flightAtKey(), PersistentDataType.LONG, System.currentTimeMillis());
    }

    // ----- walk speed taken by Storm: Charge -----

    /** Write-through, like the flight grant: the speed to put back is in the file before walkSpeed goes to 0. */
    public void grantRoot(Player player, float walkSpeed) {
        player.getPersistentDataContainer().set(rootKey(), PersistentDataType.FLOAT, walkSpeed);
    }

    /** Removes the grant and returns the walk speed to restore, or null when there was none. */
    public Float revokeRoot(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Float walkSpeed = data.get(rootKey(), PersistentDataType.FLOAT);
        if (walkSpeed != null) {
            data.remove(rootKey());
        }
        return walkSpeed;
    }

    private record Pool(int count, long lastRegen) {
    }

    /**
     * Regeneration is computed from the wall clock on read: whole intervals
     * elapsed since the last regeneration become charges, and the timestamp
     * moves forward by exactly those intervals so no partial progress is lost.
     * A full pool has no pending progress, so its timestamp is "now".
     */
    private Pool read(Player player, String abilityId, int max, long regenMillis) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Integer count = data.get(chargesKey(abilityId), PersistentDataType.INTEGER);
        Long lastRegen = data.get(regenKey(abilityId), PersistentDataType.LONG);
        long now = System.currentTimeMillis();
        if (count == null || lastRegen == null || count >= max) {
            return new Pool(max, now);
        }
        long gained = regenMillis <= 0L ? max : (now - lastRegen) / regenMillis;
        if (gained <= 0L) {
            return new Pool(count, lastRegen);
        }
        int regenerated = (int) Math.min(max, count + gained);
        return new Pool(regenerated, regenerated >= max ? now : lastRegen + gained * regenMillis);
    }

    private NamespacedKey cooldownKey(String abilityId) {
        return new NamespacedKey(plugin, "cooldown." + abilityId);
    }

    private NamespacedKey chargesKey(String abilityId) {
        return new NamespacedKey(plugin, "charges." + abilityId);
    }

    private NamespacedKey regenKey(String abilityId) {
        return new NamespacedKey(plugin, "charges." + abilityId + ".at");
    }

    private NamespacedKey flightAllowKey() {
        return new NamespacedKey(plugin, "flight.allow");
    }

    private NamespacedKey flightSpeedKey() {
        return new NamespacedKey(plugin, "flight.speed");
    }

    private NamespacedKey flightSecondsKey() {
        return new NamespacedKey(plugin, "flight.seconds");
    }

    private NamespacedKey flightAtKey() {
        return new NamespacedKey(plugin, "flight.at");
    }

    private NamespacedKey rootKey() {
        return new NamespacedKey(plugin, "storm.walkspeed");
    }
}
