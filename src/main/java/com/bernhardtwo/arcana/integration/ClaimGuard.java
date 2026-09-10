package com.bernhardtwo.arcana.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional bridge to GriefPrevention. Resolved by reflection so the build is not
 * tied to a specific version of that plugin and does not fail when it is absent.
 */
public final class ClaimGuard {

    private final Plugin owner;

    private boolean active;
    private Object dataStore;
    private Method getClaimAt;
    private Method checkPermission;
    private Method allowBuild;
    private Object buildPermission;

    public ClaimGuard(Plugin owner) {
        this.owner = owner;
        setup();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isBlocked(Player caster, Location location) {
        Object claim = claimAt(location);
        if (claim == null) {
            return false;
        }
        try {
            if (checkPermission != null) {
                return checkPermission.invoke(claim, caster, buildPermission, null) != null;
            }
            return allowBuild.invoke(claim, caster, Material.STONE) != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            disable(ex);
            return false;
        }
    }

    /** Whether any claim covers the location, regardless of who owns it. False when GriefPrevention is absent. */
    public boolean hasClaimAt(Location location) {
        return claimAt(location) != null;
    }

    private Object claimAt(Location location) {
        if (!active) {
            return null;
        }
        try {
            return getClaimAt.invoke(dataStore, location, false, null);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            disable(ex);
            return null;
        }
    }

    private void disable(Exception ex) {
        owner.getLogger().log(Level.WARNING, "GriefPrevention responded unexpectedly, claim checks disabled", ex);
        active = false;
    }

    private void setup() {
        Plugin griefPrevention = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (griefPrevention == null || !griefPrevention.isEnabled()) {
            return;
        }
        try {
            Field field = griefPrevention.getClass().getField("dataStore");
            dataStore = field.get(griefPrevention);
            if (dataStore == null) {
                return;
            }
            for (Method method : dataStore.getClass().getMethods()) {
                if (method.getName().equals("getClaimAt") && method.getParameterCount() == 3) {
                    getClaimAt = method;
                    break;
                }
            }
            if (getClaimAt == null) {
                return;
            }
            Class<?> claimClass = getClaimAt.getReturnType();
            resolvePermissionCheck(claimClass, griefPrevention.getClass().getClassLoader());
            active = checkPermission != null || allowBuild != null;
            if (active) {
                owner.getLogger().info("GriefPrevention detected, abilities will respect claims.");
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            owner.getLogger().log(Level.WARNING, "Could not hook into GriefPrevention", ex);
            active = false;
        }
    }

    private void resolvePermissionCheck(Class<?> claimClass, ClassLoader loader) {
        try {
            Class<?> permissionEnum = Class.forName("me.ryanhamshire.GriefPrevention.ClaimPermission", false, loader);
            for (Object constant : permissionEnum.getEnumConstants()) {
                if (constant.toString().equalsIgnoreCase("Build")) {
                    buildPermission = constant;
                    break;
                }
            }
            for (Method method : claimClass.getMethods()) {
                if (method.getName().equals("checkPermission")
                        && method.getParameterCount() == 3
                        && method.getParameterTypes()[0].isAssignableFrom(Player.class)) {
                    checkPermission = method;
                    break;
                }
            }
        } catch (ClassNotFoundException ignored) {
            buildPermission = null;
        }
        if (checkPermission != null && buildPermission != null) {
            return;
        }
        checkPermission = null;
        for (Method method : claimClass.getMethods()) {
            if (method.getName().equals("allowBuild") && method.getParameterCount() == 2) {
                allowBuild = method;
                return;
            }
        }
    }
}
