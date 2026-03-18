package com.lego.optimize;

import com.lego.model.Facing;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * Matches surface normals against brick specs using continuous angle comparison.
 *
 * This is the extensibility point for non-rectangular parts. Adding new part types
 * (curved, wedge, arbitrary angle) requires only adding fields to BrickSpec/CatalogPart
 * and conditions in this matcher — no new enums or class hierarchies.
 */
public final class SurfaceMatcher {

    /** Angular tolerance in degrees for slope matching. */
    private static final double ANGLE_TOLERANCE_DEG = 10.0;

    /** Maximum surface angle (from vertical) for a standard flat brick. */
    private static final double FLAT_MAX_ANGLE_DEG = 15.0;

    private SurfaceMatcher() {}

    /**
     * Result of a surface match attempt.
     *
     * @param eligible whether the spec can be placed at this surface angle
     * @param facing   the resolved facing direction (NONE if not directional or not eligible)
     */
    public record MatchResult(boolean eligible, Facing facing) {
        public static final MatchResult NOT_ELIGIBLE = new MatchResult(false, Facing.NONE);
    }

    /**
     * Determines whether a brick spec is eligible for placement at a voxel with the given
     * surface normal, and if so, what facing direction the part should have.
     *
     * @param normal   voxel surface normal (unit-length; ZERO means no normal data)
     * @param spec     candidate brick spec
     * @return match result with eligibility and facing
     */
    public static MatchResult match(Vector3 normal, BrickSpec spec) {
        // No normal data — only standard bricks are eligible
        if (normal == null || normal.length() < 1e-6) {
            return spec.isSlope()
                ? MatchResult.NOT_ELIGIBLE
                : new MatchResult(true, Facing.NONE);
        }

        if (!spec.isSlope()) {
            // Standard bricks are always eligible — they serve as the universal
            // fallback on any surface angle (horizontal, vertical, or in-between).
            return new MatchResult(true, Facing.NONE);
        }

        // Compute inclination: angle from the Y axis (vertical).
        // Y-up: a flat surface has normal ~(0,1,0) → inclination ~0°.
        // A 45° slope has inclination ~45°.
        double cosAngle = Math.abs(normal.y());
        double inclinationDeg = Math.toDegrees(Math.acos(Math.min(1.0, cosAngle)));

        // Slope spec: check if surface angle matches the part's declared angle
        double specAngle = spec.slopeAngle();
        if (Math.abs(inclinationDeg - specAngle) > ANGLE_TOLERANCE_DEG) {
            return MatchResult.NOT_ELIGIBLE;
        }

        // Resolve facing direction from horizontal projection of normal
        Facing facing = resolveCardinalFacing(normal);
        return new MatchResult(true, facing);
    }

    /**
     * Projects the normal onto the XZ plane and resolves the nearest cardinal direction.
     * Convention: -Z is NORTH, +X is EAST, +Z is SOUTH, -X is WEST.
     */
    static Facing resolveCardinalFacing(Vector3 normal) {
        double nx = normal.x();
        double nz = normal.z();

        // If horizontal component is negligible, no directional preference
        if (Math.abs(nx) < 1e-6 && Math.abs(nz) < 1e-6) {
            return Facing.NORTH; // default for purely vertical slopes
        }

        if (Math.abs(nz) >= Math.abs(nx)) {
            return nz < 0 ? Facing.NORTH : Facing.SOUTH;
        } else {
            return nx > 0 ? Facing.EAST : Facing.WEST;
        }
    }
}
