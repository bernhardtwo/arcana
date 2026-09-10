package com.bernhardtwo.arcana.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownTracker {

    private final Map<UUID, Map<String, Long>> expiry = new HashMap<>();

    public boolean isReady(UUID player, String abilityId) {
        return remainingMillis(player, abilityId) <= 0L;
    }

    public long remainingMillis(UUID player, String abilityId) {
        Map<String, Long> byAbility = expiry.get(player);
        if (byAbility == null) {
            return 0L;
        }
        Long until = byAbility.get(abilityId);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void start(UUID player, String abilityId, int ticks) {
        expiry.computeIfAbsent(player, key -> new HashMap<>())
                .put(abilityId, System.currentTimeMillis() + ticks * 50L);
    }

    public void forget(UUID player) {
        expiry.remove(player);
    }
}
