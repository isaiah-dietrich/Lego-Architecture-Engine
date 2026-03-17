package com.lego.color;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ShadowRemoverTest {

    /**
     * Stats centered around a typical animal model: median L*=55, IQR=20.
     * Shadow threshold = 55 - 10 = 45, highlight threshold = 55 + 10 = 65.
     */
    private static final ShadowRemover.LightnessStats TYPICAL_STATS =
        new ShadowRemover.LightnessStats(55.0, 45.0, 65.0, 20.0);

    // ---- DARK_FEATURE_FLOOR behavior ----

    @Test
    void darkFeatureFloorReducesLiftingForDarkBrownEyes() {
        // L*=25 (dark brown eye) is below DARK_FEATURE_FLOOR=35.
        // Effective strength should be reduced by factor (25/35) ≈ 0.71.
        // Without the floor, full 0.85 lifting would push L* from 25 to ~42,
        // making it indistinguishable from face colors.
        double lifted = ShadowRemover.normalizeLightnessForRegion(25.0, TYPICAL_STATS);

        // With floor at 35: effectiveStrength = 0.85 * (25/35) ≈ 0.607
        // deficit = 45 - 25 = 20, lifted = 45 - 20 * (1 - 0.607) = 45 - 7.86 = 37.14
        // This should stay noticeably below the face region (~55)
        assertTrue(lifted < 40, "Dark brown eye (L*=25) should not be lifted above 40, got " + lifted);
        assertTrue(lifted > 25, "Dark brown eye should still be lifted somewhat, got " + lifted);
    }

    @Test
    void veryDarkFeaturesGetMinimalLifting() {
        // L*=10 (near-black eye/nose) should get minimal lifting.
        double lifted = ShadowRemover.normalizeLightnessForRegion(10.0, TYPICAL_STATS);

        // effectiveStrength = 0.85 * (10/35) ≈ 0.243
        // deficit = 45 - 10 = 35, lifted = 45 - 35 * (1 - 0.243) = 45 - 26.5 = 18.5
        assertTrue(lifted < 25, "Very dark feature (L*=10) should stay dark, got " + lifted);
    }

    @Test
    void moderateShadowGetsFullLifting() {
        // L*=40 is above DARK_FEATURE_FLOOR=35, so should get full shadow lifting.
        double lifted = ShadowRemover.normalizeLightnessForRegion(40.0, TYPICAL_STATS);

        // Full strength 0.85: deficit = 45 - 40 = 5, lifted = 45 - 5 * 0.15 = 44.25
        assertTrue(lifted > 43, "Moderate shadow (L*=40) above floor should get full lifting, got " + lifted);
    }

    @Test
    void aboveThresholdUnchanged() {
        // L*=55 (at median) should not be lifted or compressed.
        double result = ShadowRemover.normalizeLightnessForRegion(55.0, TYPICAL_STATS);
        assertEquals(55.0, result, 0.01, "Value at median should be unchanged");
    }

    // ---- Statistics ----

    @Test
    void computeLightnessStatsReturnsNullForTooFewValues() {
        assertNull(ShadowRemover.computeLightnessStats(List.of(1.0, 2.0, 3.0)));
    }

    @Test
    void computeLightnessStatsCalculatesMedianAndIqr() {
        List<Double> values = List.of(10.0, 30.0, 50.0, 70.0, 90.0);
        ShadowRemover.LightnessStats stats = ShadowRemover.computeLightnessStats(values);
        assertNotNull(stats);
        assertEquals(50.0, stats.median(), 0.01);
        assertTrue(stats.iqr() > 0, "IQR should be positive");
    }

    // ---- Default vs Region strength ----

    @Test
    void regionLiftingIsMoreAggressiveThanDefault() {
        double defaultLifted = ShadowRemover.normalizeLightness(30.0, TYPICAL_STATS);
        double regionLifted = ShadowRemover.normalizeLightnessForRegion(30.0, TYPICAL_STATS);
        // Both should lift, but region should lift more (but now both are
        // modulated by the dark floor for L*=30 < 35)
        assertTrue(regionLifted >= defaultLifted,
            "Region lifting should be at least as aggressive as default");
    }

    // ---- Chroma stabilization ----

    @Test
    void stabilizeChromaBoostsNearGray() {
        double[] lab = {50.0, 2.0, 2.0}; // chroma = sqrt(8) ≈ 2.83
        // Below MIN_CHROMA but also below 3.0 → should be left alone (truly neutral)
        double[] copy = lab.clone();
        ShadowRemover.stabilizeChroma(copy);
        assertEquals(lab[1], copy[1], 0.001, "Very low chroma should be left alone");
    }

    @Test
    void stabilizeChromaBoostsMidChroma() {
        double[] lab = {50.0, 3.0, 4.0}; // chroma = 5.0, between 3 and MIN_CHROMA
        ShadowRemover.stabilizeChroma(lab);
        double newChroma = Math.sqrt(lab[1] * lab[1] + lab[2] * lab[2]);
        assertEquals(ShadowRemover.MIN_CHROMA, newChroma, 0.01,
            "Chroma should be boosted to MIN_CHROMA");
    }
}
