package com.bernhardtwo.arcana.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

public record ChainRendSettings(
        double entityDamage,
        double blockDropChance,
        double maxBlockHardness,
        Set<Material> blockedMaterials,
        int cooldownTicks
) {

    public static ChainRendSettings from(ConfigurationSection section, ChainRendSettings fallback, Logger logger) {
        if (section == null) {
            return fallback;
        }
        Set<Material> blocked = fallback.blockedMaterials();
        if (section.isList("blocked-materials")) {
            blocked = EnumSet.noneOf(Material.class);
            for (String name : section.getStringList("blocked-materials")) {
                Material material = Material.matchMaterial(name.trim());
                if (material == null) {
                    logger.warning("Unknown material in chain_rend.blocked-materials: " + name);
                } else {
                    blocked.add(material);
                }
            }
        }
        return new ChainRendSettings(
                Math.max(0.0, section.getDouble("entity-damage", fallback.entityDamage())),
                Math.min(1.0, Math.max(0.0, section.getDouble("block-drop-chance", fallback.blockDropChance()))),
                Math.max(0.0, section.getDouble("max-block-hardness", fallback.maxBlockHardness())),
                blocked,
                section.getInt("cooldown-ticks", fallback.cooldownTicks())
        );
    }
}
