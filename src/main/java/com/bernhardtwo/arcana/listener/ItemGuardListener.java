package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.item.AbilityItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every wand is a vanilla item with vanilla uses: end rods and chains place,
 * blaze rods burn and craft, breeze rods and echo shards craft. This blocks
 * all of that for anything carrying an Arcana tag, by the tag alone, so
 * items added later are covered without touching this class. Storage
 * inventories are untouched: putting a wand in a chest is normal.
 */
public final class ItemGuardListener implements Listener {

    private static final int MESSAGE_INTERVAL_TICKS = 20;
    private static final Set<InventoryType> PROCESSING = EnumSet.of(
            InventoryType.CRAFTING, InventoryType.WORKBENCH, InventoryType.CRAFTER,
            InventoryType.FURNACE, InventoryType.BLAST_FURNACE, InventoryType.SMOKER,
            InventoryType.ANVIL, InventoryType.SMITHING, InventoryType.GRINDSTONE, InventoryType.STONECUTTER,
            InventoryType.BREWING, InventoryType.LOOM, InventoryType.CARTOGRAPHY, InventoryType.ENCHANTING);

    private final ArcanaPlugin plugin;
    private final Map<UUID, Integer> lastMessageTick = new HashMap<>();

    public ItemGuardListener(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Any hand: the ability listener only handles the main hand, and an end rod places from the off hand too. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isArcana(event.getItemInHand())) {
            event.setCancelled(true);
            tell(event.getPlayer());
        }
    }

    /** No result while a tagged item sits in the grid, so the recipe never lights up. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (anyArcana(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (anyArcana(event.getInventory().getMatrix())) {
            event.setCancelled(true);
            tell(event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        if (isArcana(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    /** Hoppers, droppers and the like: nothing tagged flows into a processing inventory. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (PROCESSING.contains(event.getDestination().getType()) && isArcana(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Three ways a click puts an item into the top inventory: dropping the
     * cursor or a hotbar item on a top slot, and shift clicking from the
     * bottom. Shift click never feeds the two crafting grids, only the rest.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryType top = event.getView().getTopInventory().getType();
        if (!PROCESSING.contains(top)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        boolean intoTop;
        if (clicked != null && clicked.equals(event.getView().getTopInventory())) {
            ItemStack hotbar = event.getHotbarButton() >= 0
                    ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton())
                    : null;
            intoTop = isArcana(event.getCursor()) || isArcana(hotbar)
                    || (event.getAction() == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() < 0
                    && isArcana(event.getWhoClicked().getInventory().getItemInOffHand()));
        } else {
            intoTop = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    && top != InventoryType.CRAFTING && top != InventoryType.WORKBENCH
                    && isArcana(event.getCurrentItem());
        }
        if (intoTop) {
            event.setCancelled(true);
            tell(event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!PROCESSING.contains(event.getView().getTopInventory().getType()) || !isArcana(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                tell(event.getWhoClicked());
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessageTick.remove(event.getPlayer().getUniqueId());
    }

    private boolean isArcana(ItemStack stack) {
        return AbilityItems.isArcana(plugin, stack);
    }

    private boolean anyArcana(ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            if (isArcana(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Once per attempt: a drag or a double click fires several events in the same instant. */
    private void tell(HumanEntity who) {
        int tick = Bukkit.getCurrentTick();
        Integer last = lastMessageTick.put(who.getUniqueId(), tick);
        if (last != null && tick - last < MESSAGE_INTERVAL_TICKS) {
            return;
        }
        who.sendActionBar(Component.text("Arcana items cannot be crafted or placed", NamedTextColor.GRAY));
    }
}
