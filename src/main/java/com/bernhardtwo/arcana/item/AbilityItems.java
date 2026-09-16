package com.bernhardtwo.arcana.item;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class AbilityItems {

    private AbilityItems() {
    }

    /** Every id the give command accepts: the wands, the potion and the boots. */
    public static List<String> ids(ArcanaPlugin plugin) {
        List<String> ids = new ArrayList<>();
        plugin.wands().all().forEach(wand -> ids.add(wand.id()));
        ids.add(ManaPotion.ID);
        ids.add(LevitationBoots.ID);
        return ids;
    }

    /** Any registered Arcana item by id, or empty for an unknown id. */
    public static Optional<ItemStack> create(ArcanaPlugin plugin, String id, int amount) {
        if (ManaPotion.ID.equals(id)) {
            return Optional.of(ManaPotion.create(plugin, amount));
        }
        if (LevitationBoots.ID.equals(id)) {
            return Optional.of(LevitationBoots.create(plugin, amount));
        }
        if (StormHammer.ID.equals(id)) {
            return plugin.wands().find(id).map(wand -> StormHammer.create(plugin, wand, amount));
        }
        return plugin.wands().find(id).map(wand -> {
            ItemStack stack = create(plugin, wand);
            stack.setAmount(amount);
            return stack;
        });
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

    /**
     * Whether the stack carries any tag in this plugin's namespace: a wand
     * today, and whatever else gets tagged later. Only wand identification
     * needs a specific key; protection asks this.
     */
    public static boolean isArcana(ArcanaPlugin plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String namespace = plugin.wandKey().getNamespace();
        for (NamespacedKey key : stack.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (key.getNamespace().equals(namespace)) {
                return true;
            }
        }
        return false;
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

    /**
     * Registers or removes an optional recipe to match the config. Re-registered
     * on every reload so the result carries the current item.
     */
    public static void syncRecipe(NamespacedKey key, boolean enabled, Supplier<Recipe> recipe) {
        boolean had = Bukkit.getRecipe(key) != null;
        if (had) {
            Bukkit.removeRecipe(key);
        }
        if (enabled) {
            Bukkit.addRecipe(recipe.get());
        }
        if (had != enabled) {
            Bukkit.updateRecipes();
        }
    }

    private static void addSlot(List<Component> lore, ArcanaPlugin plugin, String label, String abilityId) {
        if (abilityId == null) {
            return;
        }
        Optional<Ability> ability = plugin.abilities().find(abilityId);
        String name = ability.map(Ability::displayName).orElse(abilityId);
        String hint = ability.map(Ability::loreHint).orElse(null);
        lore.add(line(label + name + (hint == null ? "" : " (" + hint + ")")));
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
