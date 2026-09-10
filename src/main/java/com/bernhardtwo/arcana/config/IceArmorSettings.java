package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public record IceArmorSettings(
        int charges,
        double orbitRadius,
        double orbitHeight,
        double orbitSpeed,
        int idleSeconds,
        int cooldownTicks,
        boolean blockAbilitiesWhileActive,
        Set<EntityType> strongAttackers
) {

    public static IceArmorSettings from(ConfigurationSection section, IceArmorSettings fallback, Logger logger) {
        if (section == null) {
            return fallback;
        }
        Set<EntityType> strong = fallback.strongAttackers();
        if (section.isList("strong-attackers")) {
            strong = EnumSet.noneOf(EntityType.class);
            for (String name : section.getStringList("strong-attackers")) {
                try {
                    strong.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    logger.warning("Unknown entity type in ice_armor.strong-attackers: " + name);
                }
            }
        }
        return new IceArmorSettings(
                Math.max(1, section.getInt("charges", fallback.charges())),
                section.getDouble("orbit-radius", fallback.orbitRadius()),
                section.getDouble("orbit-height", fallback.orbitHeight()),
                section.getDouble("orbit-speed", fallback.orbitSpeed()),
                section.getInt("idle-seconds", fallback.idleSeconds()),
                section.getInt("cooldown-ticks", fallback.cooldownTicks()),
                section.getBoolean("block-abilities-while-active", fallback.blockAbilitiesWhileActive()),
                strong
        );
    }
}
