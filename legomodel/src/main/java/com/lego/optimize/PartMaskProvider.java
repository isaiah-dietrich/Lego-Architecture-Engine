package com.lego.optimize;

import com.lego.model.Facing;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * Supplies occupancy and coverage masks for part specs and orientations.
 */
public interface PartMaskProvider {

    /**
     * Returns a local-space mask for a spec in the given orientation.
     *
     * @param spec candidate part spec
     * @param facing resolved facing (NONE for non-directional parts)
     * @param studX oriented X footprint
     * @param studY oriented Z footprint
     */
    PartMask getMask(BrickSpec spec, Facing facing, int studX, int studY);

    /**
     * Indicates whether this provider is backed by exact geometry assets.
     */
    default boolean isGeometryBacked() {
        return false;
    }
}
