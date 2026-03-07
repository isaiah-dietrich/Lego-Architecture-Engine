package com.lego.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.lego.data.CuratedCatalogLoader;
import com.lego.model.Brick;
import com.lego.model.CatalogPart;

/**
 * Exports a placed brick list to an LDraw {@code .ldr} file for rendering in tools like BrickLink Studio.
 *
 * <p>This exporter outputs an assembly (parts + transforms), not triangle geometry. Part geometry and
 * exact dimensions are provided by the LDraw parts library installed in the viewer.</p>
 *
 * <p>Coordinate conventions used here (LDraw standard):</p>
 * <ul>
 *   <li>Stud pitch: 20 LDU</li>
 *   <li>Brick height: 24 LDU</li>
 *   <li>Vertical axis is Y, with -Y being "up" in LDraw, so stacking upward decreases Y.</li>
 *   <li>Standard brick parts are centered in X/Z around their origin and use Y=0 at the top surface.</li>
 * </ul>
 */
public final class LDrawExporter {

    // LDraw units (LDU)
    private static final double STUD_PITCH_LDU = 20.0;
    private static final double BRICK_HEIGHT_LDU = 24.0;
    private static final int DEFAULT_COLOR = 16; // "current color" in LDraw workflows

    private LDrawExporter() {
        // Utility class
    }

    public static void export(List<Brick> bricks, Path outputPath) throws IOException {
        export(bricks, outputPath, null);
    }

    /**
     * Exports bricks using catalog-derived part ids as LDraw part file names ({@code <part_id>.dat}).
     * If {@code catalogBaseDir} is non-null, it is used to resolve the curated catalog path.
     */
    public static void export(List<Brick> bricks, Path outputPath, Path catalogBaseDir) throws IOException {
        Objects.requireNonNull(bricks, "bricks must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        Files.createDirectories(outputPath.toAbsolutePath().getParent());

        Map<StudKey, String> studKeyToPartFile = buildPartIndex(catalogBaseDir);

        StringBuilder out = new StringBuilder();
        out.append("0 LEGO Architecture Engine LDraw export\n");
        out.append("0 Generated: ").append(Instant.now().toString()).append('\n');
        out.append("0 Bricks: ").append(bricks.size()).append('\n');

        for (Brick brick : bricks) {
            PartPlacement placement = resolvePart(studKeyToPartFile, brick.studX(), brick.studY());

            // Center in studs (our brick coords are min-corner on the stud grid).
            double centerXStuds = brick.x() + (brick.studX() / 2.0);
            // Source meshes use Y-up convention (Blender OBJ default).
            // brick.z() is the OBJ Z axis (wolf front-to-back depth); maps to LDraw Z.
            double centerZStuds = brick.z() + (brick.studY() / 2.0);

            double x = centerXStuds * STUD_PITCH_LDU;
            double z = centerZStuds * STUD_PITCH_LDU;

            // LDraw parts are defined with Y=0 at the top surface and extend down to +24.
            // brick.y() is the OBJ Y axis (wolf height, Y-up); stacking upward decreases LDraw Y.
            double y = -((brick.y() + brick.heightUnits()) * BRICK_HEIGHT_LDU);

            out.append("1 ")
                .append(DEFAULT_COLOR).append(' ')
                .append(formatLdu(x)).append(' ')
                .append(formatLdu(y)).append(' ')
                .append(formatLdu(z)).append(' ')
                .append(placement.a).append(' ')
                .append(placement.b).append(' ')
                .append(placement.c).append(' ')
                .append(placement.d).append(' ')
                .append(placement.e).append(' ')
                .append(placement.f).append(' ')
                .append(placement.g).append(' ')
                .append(placement.h).append(' ')
                .append(placement.i).append(' ')
                .append(placement.partFile)
                .append('\n');
        }

        Files.writeString(outputPath, out.toString(), StandardCharsets.UTF_8);
    }

    private static String formatLdu(double value) {
        // Keep output stable and readable for Studio.
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static Map<StudKey, String> buildPartIndex(Path catalogBaseDir) {
        List<CatalogPart> parts = (catalogBaseDir != null)
            ? CuratedCatalogLoader.loadActiveParts(catalogBaseDir)
            : CuratedCatalogLoader.loadActiveParts();

        Map<StudKey, String> index = new HashMap<>();
        for (CatalogPart part : parts) {
            String height = part.heightUnitsRaw().trim();
            if (!"1".equals(height) && !"1.0".equals(height)) {
                continue;
            }
            if (!"bricks".equalsIgnoreCase(part.categoryName().trim())) {
                continue;
            }

            // Only first part per stud key is used to keep mapping deterministic.
            StudKey key = new StudKey(part.studX(), part.studY());
            index.putIfAbsent(key, part.partId() + ".dat");
        }

        // Ensure 1x1 exists as a safe minimum.
        if (!index.containsKey(new StudKey(1, 1))) {
            index.put(new StudKey(1, 1), "3005.dat");
        }

        return index;
    }

    private static PartPlacement resolvePart(Map<StudKey, String> index, int studX, int studY) {
        // In LDraw part files, catalog stud_y = studs along the part's local X axis.
        // Identity placement: part X aligns with world X.
        //   → need catalog stud_y = studX (world X span) and stud_x = studY
        //   → key is StudKey(studY, studX)
        String forIdentity = index.get(new StudKey(studY, studX));
        if (forIdentity != null) {
            return PartPlacement.identity(forIdentity);
        }

        // rotateY90 placement: part X maps to world -Z, part Z maps to world X.
        //   → need catalog stud_x = studX (world X span) and stud_y = studY (world Z span)
        //   → key is StudKey(studX, studY)
        String forRotated = index.get(new StudKey(studX, studY));
        if (forRotated != null) {
            return PartPlacement.rotateY90(forRotated);
        }

        throw new IllegalStateException(
            "No LDraw part mapping found for brick dimension " + studX + "x" + studY +
            ". Add an active brick with matching studs to curated catalog, or extend mapping logic."
        );
    }

    private record StudKey(int studX, int studY) { }

    private static final class PartPlacement {
        final int a, b, c, d, e, f, g, h, i;
        final String partFile;

        private PartPlacement(int a, int b, int c, int d, int e, int f, int g, int h, int i, String partFile) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
            this.h = h;
            this.i = i;
            this.partFile = partFile;
        }

        static PartPlacement identity(String partFile) {
            return new PartPlacement(1, 0, 0, 0, 1, 0, 0, 0, 1, partFile);
        }

        // +90 degrees about Y: X -> Z, Z -> -X
        static PartPlacement rotateY90(String partFile) {
            return new PartPlacement(0, 0, 1, 0, 1, 0, -1, 0, 0, partFile);
        }
    }
}
