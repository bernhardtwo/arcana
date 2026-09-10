package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.item.AbilityItems;
import com.bernhardtwo.arcana.item.Wand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AbilityUseListener implements Listener {

    private final ArcanaPlugin plugin;
    // Swinging at an entity fires both PlayerInteractEvent and an attack in the same tick.
    private final Map<UUID, Integer> lastLeftClickTick = new HashMap<>();
    // Abilities that deal damage through the API fire EntityDamageByEntityEvent with the caster as damager.
    private boolean casting;

    public AbilityUseListener(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }
        ItemStack held = event.getItem();
        Optional<Wand> wand = AbilityItems.wandOf(plugin, held);
        if (wand.isEmpty()) {
            return;
        }
        // Cancelled for both buttons: the staff never breaks blocks or opens containers.
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (left) {
            castLeftClick(player, wand.get(), held);
            return;
        }
        String abilityId = player.isSneaking() ? wand.get().sneakRightClickAbility() : wand.get().rightClickAbility();
        tryCast(player, abilityId, held);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (casting || !(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<Wand> wand = AbilityItems.wandOf(plugin, held);
        if (wand.isEmpty()) {
            return;
        }
        castLeftClick(player, wand.get(), held);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.cooldowns().forget(event.getPlayer().getUniqueId());
        lastLeftClickTick.remove(event.getPlayer().getUniqueId());
    }

    private void castLeftClick(Player player, Wand wand, ItemStack held) {
        int tick = Bukkit.getCurrentTick();
        Integer previous = lastLeftClickTick.put(player.getUniqueId(), tick);
        if (previous != null && previous == tick) {
            return;
        }
        tryCast(player, wand.leftClickAbility(), held);
    }

    private void tryCast(Player player, String abilityId, ItemStack held) {
        // An empty wand slot does nothing at all. The message below is only for a slot naming a missing ability.
        if (abilityId == null) {
            return;
        }
        Optional<Ability> ability = plugin.abilities().find(abilityId);
        if (ability.isEmpty()) {
            player.sendMessage(warn("That ability no longer exists on this server."));
            return;
        }

        Ability selected = ability.get();
        if (!player.hasPermission(selected.permission())) {
            player.sendMessage(warn("You do not have permission to use " + selected.displayName() + "."));
            return;
        }
        if (!selected.canCast(player)) {
            return;
        }

        UUID id = player.getUniqueId();
        Component lockout = lockoutMessage(player, selected);
        if (lockout != null) {
            player.sendActionBar(lockout);
            return;
        }

        long remaining = plugin.cooldowns().remainingMillis(id, selected.id());
        if (remaining > 0L) {
            player.sendActionBar(warn(selected.displayName() + " ready in " + format(remaining)));
            return;
        }

        casting = true;
        try {
            selected.cast(player);
        } finally {
            casting = false;
        }
        if (selected.startsCooldownOnCast()) {
            plugin.cooldowns().start(id, selected.id(), selected.cooldownTicks());
            // The vanilla indicator is per material, so a short cooldown must not overwrite a longer one still running.
            if (held != null && player.getCooldown(held.getType()) < selected.cooldownTicks()) {
                player.setCooldown(held.getType(), selected.cooldownTicks());
            }
        }
        player.sendActionBar(Component.text(selected.displayName(), NamedTextColor.LIGHT_PURPLE));
    }

    private Component lockoutMessage(Player player, Ability selected) {
        String group = selected.lockoutGroup();
        if (group == null) {
            return null;
        }
        for (Ability other : plugin.abilities().all()) {
            if (other == selected || !group.equals(other.lockoutGroup())) {
                continue;
            }
            if (other.blocksGroupWhileActive(player)) {
                return warn(other.displayName() + " blocks " + selected.displayName() + " while active");
            }
            long left = plugin.cooldowns().remainingMillis(player.getUniqueId(), other.id());
            if (left > 0L) {
                return warn(other.displayName() + " blocks " + selected.displayName() + " for " + format(left));
            }
        }
        return null;
    }

    private Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private String format(long millis) {
        double seconds = millis / 1000.0;
        return String.format("%.1fs", seconds);
    }
}
