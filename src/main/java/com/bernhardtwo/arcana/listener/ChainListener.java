package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ability.chain.ChainHookAbility;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChainListener implements Listener {

    private final ChainHookAbility hook;

    public ChainListener(ChainHookAbility hook) {
        this.hook = hook;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hook.release(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        hook.release(event.getEntity().getUniqueId(), null);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        hook.release(event.getPlayer().getUniqueId(), "Chain: Hook let go.");
    }
}
