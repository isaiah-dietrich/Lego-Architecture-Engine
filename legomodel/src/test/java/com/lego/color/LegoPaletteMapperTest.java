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
        assertTrue(mapper.opaqueEntryCount() > 100,
            "Expected >100 opaque entries, got " + mapper.opaqueEntryCount());
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
}
