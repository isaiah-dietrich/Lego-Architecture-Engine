package com.lego.ldraw;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.lego.optimize.PartMask;

/**
 * In-process cache store for generated masks.
 */
public final class MemoryMaskCacheStore implements MaskCacheStore {

    private final Map<String, PartMask> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<PartMask> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void put(String key, PartMask mask) {
        if (key == null || key.isBlank() || mask == null) {
            return;
        }
        cache.put(key, mask);
    }
}
