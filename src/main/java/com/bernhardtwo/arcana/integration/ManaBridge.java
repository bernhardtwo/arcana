package com.bernhardtwo.arcana.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional bridge to AuraSkills mana, resolved by reflection like
 * {@link ClaimGuard}. Inactive means every mana cost is ignored and the plugin
 * behaves exactly as it does without mana: no refusals, no bar, no errors.
 * A player AuraSkills has not loaded yet counts as inactive for that cast.
 */
public final class ManaBridge {

    private static final String API_CLASS = "dev.aurelium.auraskills.api.AuraSkillsApi";

    private final Plugin owner;

    private boolean active;
    private Object api;
    private Method getUser;
    private Method getMana;
    private Method getMaxMana;
    private Method setMana;
    private Method consumeMana;

    public ManaBridge(Plugin owner) {
        this.owner = owner;
        setup();
    }

    public boolean isActive() {
        return active;
    }

    /** Current mana, or 0 when unknown. */
    public double mana(Player player) {
        Object user = user(player);
        return user == null ? 0.0 : read(getMana, user);
    }

    /** Maximum mana, or 0 when unknown. */
    public double maxMana(Player player) {
        Object user = user(player);
        return user == null ? 0.0 : read(getMaxMana, user);
    }

    /** True when the player can pay, and also when the bridge cannot tell: mana never blocks a cast it cannot see. */
    public boolean hasMana(Player player, double amount) {
        Object user = user(player);
        return user == null || amount <= 0.0 || read(getMana, user) >= amount;
    }

    /** Spends the mana. Nothing happens when the bridge is inactive or the player is unknown. */
    public void consume(Player player, double amount) {
        Object user = user(player);
        if (user == null || amount <= 0.0) {
            return;
        }
        try {
            if (consumeMana != null) {
                consumeMana.invoke(user, amount);
            } else {
                setMana.invoke(user, Math.max(0.0, read(getMana, user) - amount));
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            disable(ex);
        }
    }

    /** Adds mana up to the maximum. Returns what was actually added. */
    public double restore(Player player, double amount) {
        Object user = user(player);
        if (user == null) {
            return 0.0;
        }
        try {
            double mana = read(getMana, user);
            double target = Math.min(read(getMaxMana, user), mana + amount);
            setMana.invoke(user, target);
            return Math.max(0.0, target - mana);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            disable(ex);
            return 0.0;
        }
    }

    private Object user(Player player) {
        if (!active) {
            return null;
        }
        try {
            return getUser.invoke(api, player.getUniqueId());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            disable(ex);
            return null;
        }
    }

    private double read(Method getter, Object user) {
        try {
            return ((Number) getter.invoke(user)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            disable(ex);
            return 0.0;
        }
    }

    private void disable(Exception ex) {
        owner.getLogger().log(Level.WARNING, "AuraSkills responded unexpectedly, mana costs disabled", ex);
        active = false;
    }

    private void setup() {
        Plugin auraSkills = Bukkit.getPluginManager().getPlugin("AuraSkills");
        if (auraSkills == null || !auraSkills.isEnabled()) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName(API_CLASS, false, auraSkills.getClass().getClassLoader());
            api = apiClass.getMethod("get").invoke(null);
            getUser = apiClass.getMethod("getUser", UUID.class);
            Class<?> userClass = getUser.getReturnType();
            getMana = userClass.getMethod("getMana");
            getMaxMana = userClass.getMethod("getMaxMana");
            setMana = userClass.getMethod("setMana", double.class);
            try {
                consumeMana = userClass.getMethod("consumeMana", double.class);
            } catch (NoSuchMethodException noConsume) {
                consumeMana = null;
            }
            active = true;
            owner.getLogger().info("AuraSkills detected, abilities will cost mana.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            owner.getLogger().log(Level.WARNING, "Could not hook into AuraSkills, mana costs disabled", ex);
            active = false;
        }
    }
}
