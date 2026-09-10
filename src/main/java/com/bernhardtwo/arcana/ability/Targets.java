package com.bernhardtwo.arcana.ability;

import com.bernhardtwo.arcana.ArcanaPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Target rules shared by abilities that hit "anything living": not the caster, not NPCs, not creative or spectator, and claims. */
public final class Targets {

    private Targets() {
    }

    public static boolean isValid(ArcanaPlugin plugin, Player caster, Entity entity) {
        return isTargetable(caster, entity)
                && (!plugin.settings().targets().respectClaims()
                || !plugin.claims().isBlocked(caster, entity.getLocation()));
    }

    /** The same rules without claims, for abilities that apply their own claim rule and want to say so. */
    public static boolean isTargetable(Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity) || entity.equals(caster) || entity.hasMetadata("NPC")) {
            return false;
        }
        if (entity instanceof Player player) {
            GameMode gameMode = player.getGameMode();
            return gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR;
        }
        return true;
    }
}
