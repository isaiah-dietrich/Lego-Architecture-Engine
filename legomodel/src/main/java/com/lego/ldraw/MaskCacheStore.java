package com.lego.ldraw;

import java.util.Optional;

import com.lego.optimize.PartMask;

/**
 * Adapter for persistent or in-memory part-mask caching.
 */
public interface MaskCacheStore {

    Optional<PartMask> get(String key);

    void put(String key, PartMask mask);
}
