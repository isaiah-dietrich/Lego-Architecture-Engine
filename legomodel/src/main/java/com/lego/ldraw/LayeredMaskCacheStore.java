package com.lego.ldraw;

import java.util.Optional;

import com.lego.optimize.PartMask;

/**
 * Reads memory first, then disk. Writes through to both.
 */
public final class LayeredMaskCacheStore implements MaskCacheStore {

    private final MaskCacheStore memory;
    private final MaskCacheStore disk;

    public LayeredMaskCacheStore(MaskCacheStore memory, MaskCacheStore disk) {
        this.memory = memory;
        this.disk = disk;
    }

    @Override
    public Optional<PartMask> get(String key) {
        Optional<PartMask> fromMemory = memory.get(key);
        if (fromMemory.isPresent()) {
            return fromMemory;
        }
        Optional<PartMask> fromDisk = disk.get(key);
        fromDisk.ifPresent(mask -> memory.put(key, mask));
        return fromDisk;
    }

    @Override
    public void put(String key, PartMask mask) {
        memory.put(key, mask);
        disk.put(key, mask);
    }
}
