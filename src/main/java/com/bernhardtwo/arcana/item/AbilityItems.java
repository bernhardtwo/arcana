package com.bernhardtwo.arcana.item;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AbilityItems {

    private AbilityItems() {
    }

    public static ItemStack create(ArcanaPlugin plugin, Wand wand) {
        ItemStack stack = new ItemStack(wand.material());
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text(wand.displayName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        addSlot(lore, plugin, "Right click: ", wand.rightClickAbility());
        addSlot(lore, plugin, "Sneak + right click: ", wand.sneakRightClickAbility());
        addSlot(lore, plugin, "Left click: ", wand.leftClickAbility());
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(plugin.wandKey(), PersistentDataType.STRING, wand.id());

        stack.setItemMeta(meta);
        return stack;
    }

    public static Optional<Wand> wandOf(ArcanaPlugin plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = stack.getItemMeta().getPersistentDataContainer()
                .get(plugin.wandKey(), PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        return plugin.wands().find(id);
    }

    private static void addSlot(List<Component> lore, ArcanaPlugin plugin, String label, String abilityId) {
        if (abilityId == null) {
            return;
        }
        String name = plugin.abilities().find(abilityId).map(Ability::displayName).orElse(abilityId);
        lore.add(line(label + name));
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
