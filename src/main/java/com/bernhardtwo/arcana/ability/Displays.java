package com.bernhardtwo.arcana.ability;

import com.bernhardtwo.arcana.ArcanaPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Glowing block displays shared by the stateful abilities, plus the orbit math. */
public final class Displays {

    private Displays() {
    }

    /** A full-bright cube of {@code scale} blocks centered on {@code at}, tagged as ours and never written to disk. */
    public static BlockDisplay spawn(ArcanaPlugin plugin, Location at, Material material, float scale) {
        return spawn(plugin, at, material, new Vector3f(scale));
    }

    /** Same as above with a per-axis size, for pillars and slabs. */
    public static BlockDisplay spawn(ArcanaPlugin plugin, Location at, Material material, Vector3f size) {
        return at.getWorld().spawn(at, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(
                    new Vector3f(size).mul(-0.5f), new AxisAngle4f(),
                    new Vector3f(size), new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(1);
            // Never written to disk: a crash or a hard kill cannot leave orphans in the world.
            display.setPersistent(false);
            display.getPersistentDataContainer().set(plugin.displayKey(), PersistentDataType.BYTE, (byte) 1);
        });
    }

    public static Location orbitPoint(Location center, double angle, int index, int count, double radius) {
        double theta = angle + 2.0 * Math.PI * index / count;
        return center.clone().add(Math.cos(theta) * radius, 0.0, Math.sin(theta) * radius);
    }
}
