package com.bernhardtwo.arcana.item;

import com.bernhardtwo.arcana.ArcanaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Chainmail boots that grant sustained flight for mana while worn. Tagged in
 * the item container like the potion, so the item guard and the dropped item
 * protection cover them with no extra work. The flight itself lives in
 * {@link com.bernhardtwo.arcana.ability.gravity.GravityBoots}.
 */
public final class LevitationBoots {

    public static final String ID = "gravity_boots";

    private LevitationBoots() {
    }

    public static ItemStack create(ArcanaPlugin plugin, int amount) {
        ItemStack stack = new ItemStack(Material.CHAINMAIL_BOOTS, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Levitation Boots", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Wear, then sneak + right click to arm"),
                line("Armed: double tap jump to fly, again to land"),
                line("Costs mana while flying, more the longer you stay up")));
        meta.setEnchantmentGlintOverride(true);
        // Armor wears out on every hit; a magic item that quietly breaks after a few fights would just be lost.
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(plugin.itemKey(), PersistentDataType.STRING, ID);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean is(ArcanaPlugin plugin, ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && ID.equals(stack.getItemMeta().getPersistentDataContainer().get(plugin.itemKey(), PersistentDataType.STRING));
    }

    /** In the boots slot only; in a hand they are just an item. */
    public static boolean isWearing(ArcanaPlugin plugin, Player player) {
        return is(plugin, player.getInventory().getBoots());
    }

    /** Optional recipe: chainmail boots, a feather and two phantom membranes, shapeless. */
    public static void syncRecipe(ArcanaPlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, ID);
        AbilityItems.syncRecipe(key, plugin.settings().gravityBoots().recipeEnabled(), () -> {
            ShapelessRecipe recipe = new ShapelessRecipe(key, create(plugin, 1));
            recipe.addIngredient(Material.CHAINMAIL_BOOTS);
            recipe.addIngredient(Material.FEATHER);
            recipe.addIngredient(2, Material.PHANTOM_MEMBRANE);
            return recipe;
        });
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
