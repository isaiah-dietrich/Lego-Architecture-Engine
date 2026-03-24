package com.lego.optimize;

import com.lego.model.Facing;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * v2 extension seam for exact geometry-backed masks.
 *
 * This class is intentionally a thin placeholder in v1 so callers can swap
 * providers without changing placement/solver code.
 */
public final class GeometryPartMaskProvider implements PartMaskProvider {

    private final PartMaskProvider fallback;

    public GeometryPartMaskProvider() {
        this(new ProceduralPartMaskProvider());
    }

    public GeometryPartMaskProvider(PartMaskProvider fallback) {
        this.fallback = fallback;
    }

    @Override
    public PartMask getMask(BrickSpec spec, Facing facing, int studX, int studY) {
        // Geometry-backed loading is deferred to v2.
        return fallback.getMask(spec, facing, studX, studY);
    }

    @Override
    public boolean isGeometryBacked() {
        return false;
    }
}
