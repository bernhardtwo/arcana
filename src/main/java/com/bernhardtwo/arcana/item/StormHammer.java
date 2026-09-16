package com.bernhardtwo.arcana.item;

import com.bernhardtwo.arcana.ArcanaPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;

/**
 * Thor's Hammer: a mace registered as a wand, so the click dispatcher, the
 * lore, the mana bar and the give command cover it like a staff, plus what a
 * weapon needs on top: unbreakable, and Wind Burst at the configured level.
 * The wand tag it carries is what every item guard checks by namespace, so
 * it can never be enchanted, repaired or ground: the item never enters those
 * inventories in the first place.
 */
public final class StormHammer {

    public static final String ID = "storm_hammer";
    /** One node for the whole weapon, unlike the staffs' one per ability. */
    public static final String PERMISSION = "arcana.use." + ID;

    private StormHammer() {
    }

    public static ItemStack create(ArcanaPlugin plugin, Wand wand, int amount) {
        ItemStack stack = AbilityItems.create(plugin, wand);
        stack.setAmount(amount);
        ItemMeta meta = stack.getItemMeta();
        meta.setUnbreakable(true);
        int level = plugin.settings().storm().windBurstLevel();
        if (level > 0) {
            meta.addEnchant(Enchantment.WIND_BURST, level, true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** In the main hand. Reads the item's container, which copies its meta: keep it out of per-tick paths. */
    public static boolean isHolding(ArcanaPlugin plugin, Player player) {
        return isHammer(plugin, player.getInventory().getItemInMainHand());
    }

    public static boolean isHammer(ArcanaPlugin plugin, ItemStack stack) {
        return AbilityItems.wandOf(plugin, stack).filter(wand -> ID.equals(wand.id())).isPresent();
    }

    /** Optional recipe: a mace, two breeze rods and a lightning rod, shapeless. */
    public static void syncRecipe(ArcanaPlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, ID);
        Optional<Wand> wand = plugin.wands().find(ID);
        AbilityItems.syncRecipe(key, wand.isPresent() && plugin.settings().storm().recipeEnabled(), () -> {
            ShapelessRecipe recipe = new ShapelessRecipe(key, create(plugin, wand.get(), 1));
            recipe.addIngredient(Material.MACE);
            recipe.addIngredient(2, Material.BREEZE_ROD);
            recipe.addIngredient(Material.LIGHTNING_ROD);
            return recipe;
        });
    }
}
