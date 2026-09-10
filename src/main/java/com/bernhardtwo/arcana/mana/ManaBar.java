package com.bernhardtwo.arcana.mana;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.item.AbilityItems;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One boss bar per player, shown only while a wand is in either hand and the
 * mana bridge is active. Refreshed by one task every 10 ticks over the online
 * players, and on the spot when the held item changes.
 */
public final class ManaBar {

    private final ArcanaPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public ManaBar(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /** Shows, updates or hides the bar for this player right now. */
    public void refresh(Player player) {
        boolean holding = AbilityItems.wandOf(plugin, player.getInventory().getItemInMainHand()).isPresent()
                || AbilityItems.wandOf(plugin, player.getInventory().getItemInOffHand()).isPresent();
        double max = holding ? plugin.mana().maxMana(player) : 0.0;
        if (!plugin.mana().isActive() || !plugin.settings().mana().bar() || max <= 0.0) {
            hide(player);
            return;
        }
        double mana = plugin.mana().mana(player);
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bar.progress((float) Math.min(1.0, Math.max(0.0, mana / max)));
        bar.name(Component.text(String.format("Mana %.0f / %.0f", mana, max)));
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /** Plugin disable: every bar goes. */
    public void hideAll() {
        bars.forEach((id, bar) -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(bar);
            }
        });
        bars.clear();
    }
}
