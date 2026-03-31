package com.lego.ldraw;

import com.lego.model.Facing;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.optimize.PartMask;

/**
 * Converts normalized part triangle geometry into a placement mask.
 */
public interface GeometryRasterizer {

    PartMask rasterize(PartGeometry geometry, BrickSpec spec, Facing facing, int studX, int studY);
}
