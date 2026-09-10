package com.bernhardtwo.arcana.ability;

import org.bukkit.NamespacedKey;
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
}
