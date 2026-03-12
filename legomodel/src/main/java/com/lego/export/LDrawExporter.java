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
 *   <li>Brick height: 24 LDU (per full brick height unit of 3)</li>
 *   <li>Vertical axis is Y, with -Y being "up" in LDraw, so stacking upward decreases Y.</li>
 *   <li>Standard brick parts are centered in X/Z around their origin and use Y=0 at the top surface.</li>
 * </ul>
 */
public final class LDrawExporter {

    // LDraw units (LDU)
    private static final double STUD_PITCH_LDU = 20.0;
    /** LDU height per heightUnit. A full brick (heightUnits=3) = 24 LDU, a plate (heightUnits=1) = 8 LDU. */
    private static final double HEIGHT_UNIT_LDU = 8.0;
    private static final int DEFAULT_COLOR = 16; // "current color" in LDraw workflows

    private LDrawExporter() {
        // Utility class
    }

    public static void export(List<Brick> bricks, Path outputPath) throws IOException {
        export(bricks, outputPath, null, null);
    }

    /**
     * Exports bricks using catalog-derived part ids as LDraw part file names ({@code <part_id>.dat}).
     * If {@code catalogBaseDir} is non-null, it is used to resolve the curated catalog path.
     */
    public static void export(List<Brick> bricks, Path outputPath, Path catalogBaseDir) throws IOException {
        export(bricks, outputPath, catalogBaseDir, null);
    }

    /**
     * Exports bricks with optional per-brick LDraw color codes.
     *
     * <p>Uses the brick's {@code partId} to determine the LDraw part file name
     * ({@code partId + ".dat"}). Rotation is determined by comparing the brick's
     * placed orientation against the catalog part's canonical dimensions.</p>
     *
     * @param bricks         placed bricks
     * @param outputPath     output .ldr path
     * @param catalogBaseDir optional catalog base directory (test-only)
     * @param brickColorCodes optional per-brick LDraw color code map; {@code null} or absent
     *                        entries use {@link #DEFAULT_COLOR} (16, "current color")
     */
    public static void export(
        List<Brick> bricks,
        Path outputPath,
        Path catalogBaseDir,
        Map<Brick, Integer> brickColorCodes
    ) throws IOException {
        Objects.requireNonNull(bricks, "bricks must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        Files.createDirectories(outputPath.toAbsolutePath().getParent());

        // Build part index for rotation resolution and fallback mapping
        Map<String, CatalogPart> partById = buildPartByIdIndex(catalogBaseDir);
        Map<StudKey, String> studKeyIndex = buildStudKeyIndex(partById);

        StringBuilder out = new StringBuilder();
        out.append("0 LEGO Architecture Engine LDraw export\n");
        out.append("0 Generated: ").append(Instant.now().toString()).append('\n');
        out.append("0 Bricks: ").append(bricks.size()).append('\n');

        for (Brick brick : bricks) {
            PartPlacement placement = resolvePlacement(brick, partById, studKeyIndex);

            // Determine color: use per-brick code if available, else default (16)
            int color = DEFAULT_COLOR;
            if (brickColorCodes != null) {
                Integer code = brickColorCodes.get(brick);
                if (code != null) {
                    color = code;
                }
            }

            // Center in studs (our brick coords are min-corner on the stud grid).
            double centerXStuds = brick.x() + (brick.studX() / 2.0);
            // Source meshes use Y-up convention (Blender OBJ default).
            // brick.z() is the OBJ Z axis (wolf front-to-back depth); maps to LDraw Z.
            double centerZStuds = brick.z() + (brick.studY() / 2.0);

            double x = centerXStuds * STUD_PITCH_LDU;
            double z = centerZStuds * STUD_PITCH_LDU;

            // LDraw parts are defined with Y=0 at the top surface and extend down.
            // brick.heightUnits() is in LDraw-relative units (bricks=3, plates=1).
            double y = -((brick.y() + brick.heightUnits()) * HEIGHT_UNIT_LDU);

            out.append("1 ")
                .append(color).append(' ')
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

    /**
     * Builds a lookup from partId to CatalogPart for rotation determination.
     */
    private static Map<String, CatalogPart> buildPartByIdIndex(Path catalogBaseDir) {
        List<CatalogPart> parts = (catalogBaseDir != null)
            ? CuratedCatalogLoader.loadActiveParts(catalogBaseDir)
            : CuratedCatalogLoader.loadActiveParts();

        Map<String, CatalogPart> index = new HashMap<>();
        for (CatalogPart part : parts) {
            index.putIfAbsent(part.partId(), part);
        }
        return index;
    }

    /**
     * Builds a StudKey→datFile fallback index for bricks with unknown partId.
     */
    private static Map<StudKey, String> buildStudKeyIndex(Map<String, CatalogPart> partById) {
        Map<StudKey, String> index = new HashMap<>();
        for (CatalogPart part : partById.values()) {
            StudKey key = new StudKey(part.studX(), part.studY());
            index.putIfAbsent(key, part.partId() + ".dat");
        }
        // Ensure 1x1 exists as a safe minimum.
        if (!index.containsKey(new StudKey(1, 1))) {
            index.put(new StudKey(1, 1), "3005.dat");
        }
        return index;
    }

    /**
     * Resolves the part file and rotation for a brick.
     *
     * <p>When the brick has a known partId, uses it directly and determines rotation
     * by comparing placed orientation with catalog canonical dimensions.</p>
     *
     * <p>Falls back to StudKey lookup for bricks with "unknown" partId.</p>
     */
    private static PartPlacement resolvePlacement(Brick brick,
                                                   Map<String, CatalogPart> partById,
                                                   Map<StudKey, String> studKeyIndex) {
        String partId = brick.partId();

        // Direct lookup for bricks with known partId
        if (!Brick.UNKNOWN_PART_ID.equals(partId)) {
            String partFile = partId + ".dat";
            CatalogPart catalogPart = partById.get(partId);
            if (catalogPart != null) {
                // Determine rotation by comparing placed orientation with catalog orientation.
                // In LDraw, catalog stud_y maps to the part's local X axis.
                // Identity: catalog (stud_x, stud_y) matches (brick.studY, brick.studX)
                if (catalogPart.studX() == brick.studY() && catalogPart.studY() == brick.studX()) {
                    return PartPlacement.identity(partFile);
                }
                // Rotated: catalog (stud_x, stud_y) matches (brick.studX, brick.studY)
                if (catalogPart.studX() == brick.studX() && catalogPart.studY() == brick.studY()) {
                    return PartPlacement.rotateY90(partFile);
                }
            }
            // partId known but no catalog match — use identity as default
            return PartPlacement.identity(partFile);
        }

        // Fallback: StudKey lookup for "unknown" partId (legacy/test bricks)
        return resolvePartByStudKey(studKeyIndex, brick.studX(), brick.studY());
    }

    private static PartPlacement resolvePartByStudKey(Map<StudKey, String> index, int studX, int studY) {
        // Identity: catalog stud_y = world X span
        String forIdentity = index.get(new StudKey(studY, studX));
        if (forIdentity != null) {
            return PartPlacement.identity(forIdentity);
        }

        // Rotated Y90
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
