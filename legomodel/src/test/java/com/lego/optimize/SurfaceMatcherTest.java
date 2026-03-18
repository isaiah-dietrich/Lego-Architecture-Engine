package com.lego.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.Facing;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.optimize.SurfaceMatcher.MatchResult;

/**
 * Tests for SurfaceMatcher: continuous angle matching between surface normals and brick specs.
 */
class SurfaceMatcherTest {

    // ── Helper factories ──────────────────────────────────────────────

    private static BrickSpec flat(String id) {
        return new BrickSpec(2, 4, 3, "Bricks", id, "Flat " + id, null);
    }

    private static BrickSpec slope(String id, double angle) {
        return new BrickSpec(2, 4, 3, "Slopes", id, "Slope " + id, angle);
    }

    // ── Null / zero normal ────────────────────────────────────────────

    @Test
    void nullNormal_flatSpec_eligible() {
        MatchResult r = SurfaceMatcher.match(null, flat("3001"));
        assertTrue(r.eligible());
        assertEquals(Facing.NONE, r.facing());
    }

    @Test
    void nullNormal_slopeSpec_notEligible() {
        MatchResult r = SurfaceMatcher.match(null, slope("3037", 45.0));
        assertFalse(r.eligible());
    }

    @Test
    void zeroNormal_flatSpec_eligible() {
        MatchResult r = SurfaceMatcher.match(Vector3.ZERO, flat("3001"));
        assertTrue(r.eligible());
        assertEquals(Facing.NONE, r.facing());
    }

    @Test
    void zeroNormal_slopeSpec_notEligible() {
        MatchResult r = SurfaceMatcher.match(Vector3.ZERO, slope("3037", 45.0));
        assertFalse(r.eligible());
    }

    // ── Flat surface (straight-up normal) ─────────────────────────────

    @Test
    void flatNormal_flatSpec_eligible() {
        Vector3 up = new Vector3(0, 1, 0);
        MatchResult r = SurfaceMatcher.match(up, flat("3001"));
        assertTrue(r.eligible());
        assertEquals(Facing.NONE, r.facing());
    }

    @Test
    void flatNormal_slopeSpec_notEligible() {
        Vector3 up = new Vector3(0, 1, 0);
        MatchResult r = SurfaceMatcher.match(up, slope("3037", 45.0));
        assertFalse(r.eligible(), "Slope should not match a perfectly flat surface");
    }

    @Test
    void nearlyFlatNormal_flatSpec_eligible() {
        // 10° tilt — within FLAT_MAX_ANGLE_DEG (15°)
        double rad = Math.toRadians(10);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        assertTrue(SurfaceMatcher.match(n, flat("3001")).eligible());
    }

    @Test
    void moderateTilt_flatSpec_stillEligible() {
        // 20° tilt — flat specs are always eligible (universal fallback)
        double rad = Math.toRadians(20);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        assertTrue(SurfaceMatcher.match(n, flat("3001")).eligible());
    }

    // ── 45° slope matching ────────────────────────────────────────────

    @Test
    void slope45_exactMatch_eligible() {
        double rad = Math.toRadians(45);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("3037", 45.0));
        assertTrue(r.eligible());
        assertEquals(Facing.EAST, r.facing(), "Positive X component → EAST");
    }

    @Test
    void slope45_withinTolerance_eligible() {
        // 40° surface → within ±10° of 45° spec
        double rad = Math.toRadians(40);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        assertTrue(SurfaceMatcher.match(n, slope("3037", 45.0)).eligible());
    }

    @Test
    void slope45_outsideTolerance_notEligible() {
        // 30° surface → more than 10° away from 45° spec
        double rad = Math.toRadians(30);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        assertFalse(SurfaceMatcher.match(n, slope("3037", 45.0)).eligible());
    }

    // ── 33° slope matching ────────────────────────────────────────────

    @Test
    void slope33_exactMatch_eligible() {
        double rad = Math.toRadians(33);
        Vector3 n = new Vector3(0, Math.cos(rad), -Math.sin(rad)).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("3298", 33.0));
        assertTrue(r.eligible());
        assertEquals(Facing.NORTH, r.facing(), "Negative Z component → NORTH");
    }

    @Test
    void slope33_wrongAngle_notEligible() {
        // 50° surface against 33° spec
        double rad = Math.toRadians(50);
        Vector3 n = new Vector3(0, Math.cos(rad), -Math.sin(rad)).normalize();
        assertFalse(SurfaceMatcher.match(n, slope("3298", 33.0)).eligible());
    }

    // ── 30° slope matching ────────────────────────────────────────────

    @Test
    void slope30_exactMatch_eligible() {
        double rad = Math.toRadians(30);
        Vector3 n = new Vector3(0, Math.cos(rad), Math.sin(rad)).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("85984", 30.0));
        assertTrue(r.eligible());
        assertEquals(Facing.SOUTH, r.facing(), "Positive Z component → SOUTH");
    }

    // ── Facing resolution ─────────────────────────────────────────────

    @Test
    void facing_positiveX_east() {
        double rad = Math.toRadians(45);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("3037", 45.0));
        assertEquals(Facing.EAST, r.facing());
    }

    @Test
    void facing_negativeX_west() {
        double rad = Math.toRadians(45);
        Vector3 n = new Vector3(-Math.sin(rad), Math.cos(rad), 0).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("3037", 45.0));
        assertEquals(Facing.WEST, r.facing());
    }

    @Test
    void facing_negativeZ_north() {
        double rad = Math.toRadians(45);
        Vector3 n = new Vector3(0, Math.cos(rad), -Math.sin(rad)).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("3037", 45.0));
        assertEquals(Facing.NORTH, r.facing());
    }

    @Test
    void facing_positiveZ_south() {
        double rad = Math.toRadians(45);
        Vector3 n = new Vector3(0, Math.cos(rad), Math.sin(rad)).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("3037", 45.0));
        assertEquals(Facing.SOUTH, r.facing());
    }

    @Test
    void facing_diagonal_resolvesToDominantAxis() {
        // 45° inclination, XZ diagonal with |Z| > |X| → resolves to Z-based facing
        double rad = Math.toRadians(45);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        // XZ projection: x=0.3, z=-0.7 → |z| > |x| → NORTH
        Vector3 n = new Vector3(0.3 * sin, cos, -0.7 * sin).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("3037", 45.0));
        assertTrue(r.eligible());
        assertEquals(Facing.NORTH, r.facing());
    }

    // ── resolveCardinalFacing directly ────────────────────────────────

    @Test
    void resolveCardinal_pureVertical_defaultsNorth() {
        assertEquals(Facing.NORTH, SurfaceMatcher.resolveCardinalFacing(new Vector3(0, 1, 0)));
    }

    @Test
    void resolveCardinal_negativeZ_north() {
        assertEquals(Facing.NORTH, SurfaceMatcher.resolveCardinalFacing(new Vector3(0, 0.5, -1)));
    }

    @Test
    void resolveCardinal_positiveZ_south() {
        assertEquals(Facing.SOUTH, SurfaceMatcher.resolveCardinalFacing(new Vector3(0, 0.5, 1)));
    }

    @Test
    void resolveCardinal_positiveX_east() {
        assertEquals(Facing.EAST, SurfaceMatcher.resolveCardinalFacing(new Vector3(1, 0.5, 0)));
    }

    @Test
    void resolveCardinal_negativeX_west() {
        assertEquals(Facing.WEST, SurfaceMatcher.resolveCardinalFacing(new Vector3(-1, 0.5, 0)));
    }

    // ── Downward-facing normals use abs(y) ────────────────────────────

    @Test
    void downwardNormal_flatSpec_eligible() {
        // Normal pointing down — abs(y) is used for inclination
        Vector3 down = new Vector3(0, -1, 0);
        assertTrue(SurfaceMatcher.match(down, flat("3001")).eligible());
    }

    @Test
    void downwardSloped_matchesAngle() {
        // 45° downward slope: y negative, tilted in +X
        double rad = Math.toRadians(45);
        Vector3 n = new Vector3(Math.sin(rad), -Math.cos(rad), 0).normalize();
        MatchResult r = SurfaceMatcher.match(n, slope("3037", 45.0));
        assertTrue(r.eligible());
    }

    // ── Boundary conditions ───────────────────────────────────────────

    @Test
    void exactlyAtFlatMaxAngle_flatSpec_eligible() {
        // 15° — exactly at FLAT_MAX_ANGLE_DEG boundary
        double rad = Math.toRadians(15);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        assertTrue(SurfaceMatcher.match(n, flat("3001")).eligible());
    }

    @Test
    void flatSpec_verticalSurface_eligible() {
        // 90° — vertical wall, flat specs always eligible
        Vector3 n = new Vector3(1, 0, 0).normalize();
        assertTrue(SurfaceMatcher.match(n, flat("3001")).eligible());
    }

    @Test
    void flatSpec_steepSurface_eligible() {
        // 70° — steep surface, flat specs always eligible
        double rad = Math.toRadians(70);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        assertTrue(SurfaceMatcher.match(n, flat("3001")).eligible());
    }

    @Test
    void exactlyAtSlopeTolerance_eligible() {
        // 55° surface against 45° spec → exactly 10° difference (at boundary)
        double rad = Math.toRadians(55);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        assertTrue(SurfaceMatcher.match(n, slope("3037", 45.0)).eligible());
    }

    @Test
    void justOverSlopeTolerance_notEligible() {
        // 56° surface against 45° spec → 11° difference (over 10° tolerance)
        double rad = Math.toRadians(56);
        Vector3 n = new Vector3(Math.sin(rad), Math.cos(rad), 0).normalize();
        assertFalse(SurfaceMatcher.match(n, slope("3037", 45.0)).eligible());
    }

    // ── MatchResult constants ─────────────────────────────────────────

    @Test
    void notEligibleConstant_isFalseAndNone() {
        assertFalse(MatchResult.NOT_ELIGIBLE.eligible());
        assertEquals(Facing.NONE, MatchResult.NOT_ELIGIBLE.facing());
    }
}
