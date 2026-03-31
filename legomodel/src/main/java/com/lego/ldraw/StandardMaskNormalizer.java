package com.lego.ldraw;

import java.util.ArrayList;
import java.util.List;

import com.lego.optimize.PartMask.VoxelOffset;

/**
 * Shifts offsets so minimum dx/dy/dz become zero.
 */
public final class StandardMaskNormalizer implements MaskNormalizer {

    @Override
    public List<VoxelOffset> normalize(List<VoxelOffset> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            throw new LDrawException("Cannot normalize empty mask offsets");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (VoxelOffset offset : offsets) {
            minX = Math.min(minX, offset.dx());
            minY = Math.min(minY, offset.dy());
            minZ = Math.min(minZ, offset.dz());
        }

        List<VoxelOffset> normalized = new ArrayList<>(offsets.size());
        for (VoxelOffset offset : offsets) {
            normalized.add(new VoxelOffset(
                offset.dx() - minX,
                offset.dy() - minY,
                offset.dz() - minZ
            ));
        }
        return normalized;
    }
}
