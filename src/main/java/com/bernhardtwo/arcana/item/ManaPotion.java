package com.bernhardtwo.arcana.item;

import com.bernhardtwo.arcana.ArcanaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * A drinkable potion that restores mana. A real POTION with a custom colour,
 * so it looks like one and drinks like one; tagged in its container, so the
 * item guard and the dropped item protection cover it like a wand.
 */
public final class ManaPotion {

    public static final String ID = "mana_potion";
    private static final Color COLOR = Color.fromRGB(0x3C6CFF);

    private ManaPotion() {
    }

    public static ItemStack create(ArcanaPlugin plugin, int amount) {
        ItemStack stack = new ItemStack(Material.POTION, amount);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.setColor(COLOR);
        meta.displayName(Component.text("Mana Potion", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(String.format("Restores %.0f mana", plugin.settings().mana().potionRestore()),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(plugin.itemKey(), PersistentDataType.STRING, ID);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean is(ArcanaPlugin plugin, ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && ID.equals(stack.getItemMeta().getPersistentDataContainer().get(plugin.itemKey(), PersistentDataType.STRING));
    }

    /**
     * Registers or removes the optional recipe to match the config: one glass
     * bottle, two lapis lazuli and one glowstone dust, shapeless. Re-registered
     * on every reload so the result carries the current restore amount.
     */
    public static void syncRecipe(ArcanaPlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, ID);
        boolean had = Bukkit.getRecipe(key) != null;
        if (had) {
            Bukkit.removeRecipe(key);
        }
        boolean want = plugin.settings().mana().recipeEnabled();
        if (want) {
            ShapelessRecipe recipe = new ShapelessRecipe(key, create(plugin, 1));
            recipe.addIngredient(Material.GLASS_BOTTLE);
            recipe.addIngredient(2, Material.LAPIS_LAZULI);
            recipe.addIngredient(Material.GLOWSTONE_DUST);
            Bukkit.addRecipe(recipe);
        }
        if (had != want) {
            Bukkit.updateRecipes();
        }
    }
}
