package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ability.solar.SolarLanternAbility;
import com.bernhardtwo.arcana.ability.solar.SolarZenithAbility;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class SolarListener implements Listener {

    private final SolarLanternAbility lantern;
    private final SolarZenithAbility zenith;

    public SolarListener(SolarLanternAbility lantern, SolarZenithAbility zenith) {
        this.lantern = lantern;
        this.zenith = zenith;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endBoth(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        endBoth(event.getEntity().getUniqueId(), false);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        endBoth(event.getPlayer().getUniqueId(), true);
    }

    private void endBoth(UUID player, boolean notify) {
        lantern.end(player, notify ? "Solar: Lantern faded." : null);
        zenith.end(player, notify ? "Solar: Zenith set." : null);
    }
}
