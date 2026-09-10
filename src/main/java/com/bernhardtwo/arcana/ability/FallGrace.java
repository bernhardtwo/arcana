package com.bernhardtwo.arcana.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class FallGrace {

    private final Map<UUID, Long> until = new HashMap<>();

    public void grant(UUID entity, int ticks) {
        until.put(entity, System.currentTimeMillis() + ticks * 50L);
    }

    public boolean isActive(UUID entity) {
        Long expiry = until.get(entity);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            until.remove(entity);
            return false;
        }
        return true;
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = until.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < now) {
                iterator.remove();
            }
        }
    }
}
