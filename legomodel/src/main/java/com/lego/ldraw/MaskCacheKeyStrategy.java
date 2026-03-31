package com.lego.ldraw;

import com.lego.model.Facing;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * Computes deterministic cache keys for geometry-derived masks.
 */
public interface MaskCacheKeyStrategy {

    String keyFor(BrickSpec spec, Facing facing, int studX, int studY, String dependencyFingerprint);
}
