package com.lego.ldraw;

import java.util.List;

import com.lego.optimize.PartMask.VoxelOffset;

/**
 * Normalizes raw voxel offsets into stable anchor-local coordinates.
 */
public interface MaskNormalizer {

    List<VoxelOffset> normalize(List<VoxelOffset> offsets);
}
