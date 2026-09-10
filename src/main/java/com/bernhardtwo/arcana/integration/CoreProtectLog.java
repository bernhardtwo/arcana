package com.bernhardtwo.arcana.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional bridge to CoreProtect's logging API, resolved by reflection like
 * {@link ClaimGuard}. Blocks removed by a plugin are invisible to CoreProtect,
 * so without this a rollback would not restore what Chain: Rend broke.
 * Absent or unrecognised, it warns once at startup and every call is a no-op.
 */
public final class CoreProtectLog {

    private static final int MIN_API_VERSION = 9;

    private final Plugin owner;

    private boolean active;
    private Object api;
    private Method logRemoval;

    public CoreProtectLog(Plugin owner) {
        this.owner = owner;
        setup();
    }

    public boolean isActive() {
        return active;
    }

    /** Records the block as removed by the player. Returns false when nothing was logged. */
    public boolean logRemoval(String player, Block block) {
        if (!active) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(logRemoval.invoke(api, player, block.getLocation(), block.getType(), block.getBlockData()));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            owner.getLogger().log(Level.WARNING, "CoreProtect responded unexpectedly, block logging disabled", ex);
            active = false;
            return false;
        }
    }

    private void setup() {
        Plugin coreProtect = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (coreProtect == null || !coreProtect.isEnabled()) {
            owner.getLogger().warning("CoreProtect not detected, blocks broken by Chain: Rend will not be logged.");
            return;
        }
        try {
            api = coreProtect.getClass().getMethod("getAPI").invoke(coreProtect);
            if (api == null || !Boolean.TRUE.equals(api.getClass().getMethod("isEnabled").invoke(api))) {
                owner.getLogger().warning("CoreProtect API is disabled, blocks broken by Chain: Rend will not be logged.");
                return;
            }
            int version = (Integer) api.getClass().getMethod("APIVersion").invoke(api);
            if (version < MIN_API_VERSION) {
                owner.getLogger().warning("CoreProtect API version " + version + " is too old, block logging disabled.");
                return;
            }
            logRemoval = api.getClass().getMethod("logRemoval", String.class, Location.class, Material.class, BlockData.class);
            active = true;
            owner.getLogger().info("CoreProtect detected, blocks broken by Chain: Rend will be logged.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            owner.getLogger().log(Level.WARNING, "Could not hook into CoreProtect, block logging disabled", ex);
            active = false;
        }
    }
}
