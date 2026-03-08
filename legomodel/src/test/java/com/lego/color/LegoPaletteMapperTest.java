package com.lego.color;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.model.ColorRgb;

class LegoPaletteMapperTest {

    @Test
    void loadsRebrickableColors() throws IOException {
        LegoPaletteMapper mapper = LegoPaletteMapper.loadDefault();
        // After filtering transparent and effect colors (Pearl, Chrome, Metallic,
        // Speckle, Glow, Milky, Satin, Glitter), expect ~70-90 standard opaque entries.
        assertTrue(mapper.opaqueEntryCount() > 50,
            "Expected >50 opaque entries, got " + mapper.opaqueEntryCount());
    }

    @Test
    void legoRedMatchesLegoRed() throws IOException {
        LegoPaletteMapper mapper = LegoPaletteMapper.loadDefault();
        // LEGO Red sRGB = C91A09 → linear ≈ (0.585, 0.010, 0.003)
        // Use ColorRgb.fromHex which converts sRGB hex → linear float
        ColorRgb legoRedLinear = new ColorRgb(0.585f, 0.010f, 0.003f);
        int code = mapper.nearestLDrawColor(legoRedLinear);
        assertEquals(4, code, "Linear LEGO Red should map to LEGO Red (id=4)");
    }

    @Test
    void legoBlueMatchesLegoBlue() throws IOException {
        LegoPaletteMapper mapper = LegoPaletteMapper.loadDefault();
        // LEGO Blue sRGB = 0055BF → linear ≈ (0.0, 0.078, 0.510)
        ColorRgb legoBlueLinear = new ColorRgb(0.0f, 0.078f, 0.510f);
        int code = mapper.nearestLDrawColor(legoBlueLinear);
        assertEquals(1, code, "Linear LEGO Blue should map to LEGO Blue (id=1)");
    }

    @Test
    void blackMatchesLegoBlack() throws IOException {
        LegoPaletteMapper mapper = LegoPaletteMapper.loadDefault();
        int code = mapper.nearestLDrawColor(new ColorRgb(0f, 0f, 0f));
        // LEGO Black is id=0 (05131D)
        assertEquals(0, code, "Pure black should map to LEGO Black (id=0)");
    }

    @Test
    void whiteMatchesLegoWhite() throws IOException {
        LegoPaletteMapper mapper = LegoPaletteMapper.loadDefault();
        int code = mapper.nearestLDrawColor(new ColorRgb(1f, 1f, 1f));
        // LEGO White is id=15 (FFFFFF)
        assertEquals(15, code, "Pure white should map to LEGO White (id=15)");
    }

    @Test
    void srgbToLinearIdentityForZero() {
        assertEquals(0.0, LegoPaletteMapper.srgbToLinear(0.0), 1e-10);
    }

    @Test
    void srgbToLinearIdentityForOne() {
        assertEquals(1.0, LegoPaletteMapper.srgbToLinear(1.0), 1e-5);
    }

    @Test
    void linearToLabBlack() {
        double[] lab = LegoPaletteMapper.linearRgbToLab(0, 0, 0);
        assertEquals(0.0, lab[0], 1.0); // L* ≈ 0 for black
    }

    @Test
    void linearToLabWhite() {
        double[] lab = LegoPaletteMapper.linearRgbToLab(1, 1, 1);
        assertEquals(100.0, lab[0], 1.0); // L* ≈ 100 for white
        assertEquals(0.0, lab[1], 1.0);   // a* ≈ 0 for neutral
        assertEquals(0.0, lab[2], 1.0);   // b* ≈ 0 for neutral
    }

    @Test
    void deltaESameColorIsZero() {
        assertEquals(0.0, LegoPaletteMapper.deltaE(50, 20, -30, 50, 20, -30), 1e-10);
    }

    @Test
    void deltaEDifferentColors() {
        double de = LegoPaletteMapper.deltaE(50, 20, -30, 60, 10, -20);
        assertTrue(de > 0);
        // sqrt(100+100+100) = sqrt(300) ≈ 17.32
        assertEquals(Math.sqrt(300), de, 1e-10);
    }

    @Test
    void loadsFromCustomPath(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("colors.csv");
        Files.writeString(csv, "id,name,rgb,is_trans\n0,Black,05131D,FALSE\n15,White,FFFFFF,FALSE\n");

        LegoPaletteMapper mapper = LegoPaletteMapper.load(csv);
        assertEquals(2, mapper.opaqueEntryCount());
    }

    @Test
    void onlyOpaqueEntriesUsedForMatching(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("colors.csv");
        Files.writeString(csv,
            "id,name,rgb,is_trans\n0,Black,05131D,FALSE\n36,Trans-Red,C91A09,TRUE\n15,White,FFFFFF,FALSE\n");

        LegoPaletteMapper mapper = LegoPaletteMapper.load(csv);
        assertEquals(2, mapper.opaqueEntryCount(), "Transparent entry should be excluded");
    }

    @Test
    void srgbToLinearMidGrayUsesGammaCurve() {
        // sRGB 0.5 > 0.04045, so it uses the power curve
        double result = LegoPaletteMapper.srgbToLinear(0.5);
        double expected = Math.pow((0.5 + 0.055) / 1.055, 2.4);
        assertEquals(expected, result, 1e-10);
    }

    @Test
    void srgbToLinearBelowThresholdUsesLinearSegment() {
        // Values ≤ 0.04045 use the linear segment c/12.92
        double result = LegoPaletteMapper.srgbToLinear(0.04);
        assertEquals(0.04 / 12.92, result, 1e-10);
    }

    @Test
    void srgbToLinearAtThresholdBoundary() {
        // At exactly 0.04045, should use the linear segment
        double result = LegoPaletteMapper.srgbToLinear(0.04045);
        assertEquals(0.04045 / 12.92, result, 1e-10);
    }

    @Test
    void linearToLabRedHasPositiveA() {
        // Pure linear red should have positive a* (red-green axis)
        double[] lab = LegoPaletteMapper.linearRgbToLab(1, 0, 0);
        assertTrue(lab[1] > 0, "Red should have positive a*. Got a*=" + lab[1]);
    }

    @Test
    void linearToLabGreenHasNegativeA() {
        // Pure linear green should have negative a*
        double[] lab = LegoPaletteMapper.linearRgbToLab(0, 1, 0);
        assertTrue(lab[1] < 0, "Green should have negative a*. Got a*=" + lab[1]);
    }

    @Test
    void nearestColorIsConsistentForSameInput() throws IOException {
        LegoPaletteMapper mapper = LegoPaletteMapper.loadDefault();
        ColorRgb color = new ColorRgb(0.5f, 0.2f, 0.1f);
        int code1 = mapper.nearestLDrawColor(color);
        int code2 = mapper.nearestLDrawColor(color);
        assertEquals(code1, code2, "Same input should always produce the same nearest color");
    }

    @Test
    void getColorNameReturnsCorrectName() throws IOException {
        LegoPaletteMapper mapper = LegoPaletteMapper.loadDefault();
        String blackName = mapper.getColorName(0);
        assertEquals("Black", blackName);

        String whiteName = mapper.getColorName(15);
        assertEquals("White", whiteName);

        String redName = mapper.getColorName(4);
        assertEquals("Red", redName);
    }

    @Test
    void getColorNameReturnsUnknownForInvalidCode() throws IOException {
        LegoPaletteMapper mapper = LegoPaletteMapper.loadDefault();
        String unknownName = mapper.getColorName(9999);
        assertEquals("Unknown", unknownName);
    }

    @Test
    void ignoresOutOfRangeRebrickableIds() throws IOException {
        Path csv = Files.createTempFile("colors-out-of-range", ".csv");
        Files.writeString(csv,
            "id,name,rgb,is_trans\n"
                + "4,Red,C91A09,FALSE\n"
                + "1137,UnstableDark,050505,FALSE\n"
                + "15,White,FFFFFF,FALSE\n");

        LegoPaletteMapper mapper = LegoPaletteMapper.load(csv);
        int code = mapper.nearestLDrawColor(new ColorRgb(0.01f, 0.01f, 0.01f));

        // 1137 should be filtered out; nearest among [4,15] should be red for near-black
        // in this minimal palette.
        assertEquals(4, code);
    }

    @Test
    void effectColorsExcludedFromMatching() throws IOException {
        Path csv = Files.createTempFile("colors-effects", ".csv");
        Files.writeString(csv,
            "id,name,rgb,is_trans\n"
                + "15,White,FFFFFF,FALSE\n"
                + "183,Pearl White,F2F3F2,FALSE\n"
                + "383,Chrome Silver,E0E0E0,FALSE\n"
                + "21,Glow In Dark Opaque,D4D5C9,FALSE\n"
                + "80,Metallic Silver,A5A9B4,FALSE\n");

        LegoPaletteMapper mapper = LegoPaletteMapper.load(csv);
        // Near-white linear input: Pearl White (F2F3F2) is closest in raw ΔE,
        // but it should be excluded as an effect color, leaving White.
        ColorRgb nearWhite = new ColorRgb(0.888f, 0.888f, 0.888f);
        int code = mapper.nearestLDrawColor(nearWhite);
        assertEquals(15, code, "Should match White, not Pearl White");
    }

    @Test
    void isEffectColorDetectsEffectNames() {
        assertTrue(LegoPaletteMapper.isEffectColor("Pearl White"));
        assertTrue(LegoPaletteMapper.isEffectColor("Chrome Silver"));
        assertTrue(LegoPaletteMapper.isEffectColor("Metallic Gold"));
        assertTrue(LegoPaletteMapper.isEffectColor("Speckle Black-Copper"));
        assertTrue(LegoPaletteMapper.isEffectColor("Glow In Dark Opaque"));
        assertTrue(LegoPaletteMapper.isEffectColor("Milky White"));
        assertFalse(LegoPaletteMapper.isEffectColor("White"));
        assertFalse(LegoPaletteMapper.isEffectColor("Red"));
        assertFalse(LegoPaletteMapper.isEffectColor("Dark Bluish Gray"));
    }

    @Test
    void getColorNameFindsEffectColorsByCode() throws IOException {
        // Effect colors should still be findable by name lookup
        // even though they're excluded from matching
        Path csv = Files.createTempFile("colors-name-lookup", ".csv");
        Files.writeString(csv,
            "id,name,rgb,is_trans\n"
                + "15,White,FFFFFF,FALSE\n"
                + "183,Pearl White,F2F3F2,FALSE\n");

        LegoPaletteMapper mapper = LegoPaletteMapper.load(csv);
        assertEquals("Pearl White", mapper.getColorName(183));
        assertEquals("White", mapper.getColorName(15));
    }
}
