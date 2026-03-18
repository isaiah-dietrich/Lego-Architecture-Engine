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

import com.lego.data.CatalogPartRepository;
import com.lego.data.CsvCatalogPartRepository;
import com.lego.model.Brick;
import com.lego.model.CatalogPart;
import com.lego.model.Facing;

/**
 * Exports a placed brick list to an LDraw .ldr file for rendering in tools like BrickLink Studio.
 *
 * This exporter outputs an assembly (parts + transforms), not triangle geometry. Part geometry and
 * exact dimensions are provided by the LDraw parts library installed in the viewer.
 *
 * Coordinate conventions used here (LDraw standard):
 * 
 *   - Stud pitch: 20 LDU
 *   - Brick height: 24 LDU (per full brick height unit of 3)
 *   - Vertical axis is Y, with -Y being "up" in LDraw, so stacking upward decreases Y.
 *   - Standard brick parts are centered in X/Z around their origin and use Y=0 at the top surface.
 * 
 */
public final class LDrawExporter {

    // LDraw units (LDU)
    private static final double STUD_PITCH_LDU = 20.0;
    /** LDU height per heightUnit. A full brick (heightUnits=3) = 24 LDU, a plate (heightUnits=1) = 8 LDU. */
    private static final double HEIGHT_UNIT_LDU = 8.0;
    /** LDU per voxel layer. Each voxel layer represents one plate height (1 × 8 = 8 LDU). */
    private static final double LAYER_HEIGHT_LDU = HEIGHT_UNIT_LDU;
    private static final int DEFAULT_COLOR = 16; // "current color" in LDraw workflows

    /** Non-instantiable utility class. */
    private LDrawExporter() {
        // Utility class
    }

    /** Exports bricks to an LDraw file using default catalog and no color codes. */
    public static void export(List<Brick> bricks, Path outputPath) throws IOException {
        export(bricks, outputPath, (CatalogPartRepository) null, null);
    }

    /**
     * Exports bricks using catalog-derived part ids as LDraw part file names (<part_id>.dat).
     * If catalogBaseDir is non-null, it is used to resolve the curated catalog path.
     */
    public static void export(List<Brick> bricks, Path outputPath, Path catalogBaseDir) throws IOException {
        export(bricks, outputPath, catalogBaseDir, null);
    }

    /**
     * Exports bricks using parts from the given repository.
     *
     * @param bricks          placed bricks
     * @param outputPath      output .ldr path
     * @param repository      catalog part data source; null uses default catalog
     * @param brickColorCodes optional per-brick LDraw color code map
     */
    public static void export(
        List<Brick> bricks,
        Path outputPath,
        CatalogPartRepository repository,
        Map<Brick, Integer> brickColorCodes
    ) throws IOException {
        Objects.requireNonNull(bricks, "bricks must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        Files.createDirectories(outputPath.toAbsolutePath().getParent());

        CatalogPartRepository repo = repository != null ? repository : new CsvCatalogPartRepository();
        List<CatalogPart> parts = repo.findActiveParts();

        Map<String, CatalogPart> partById = buildPartByIdIndexFromList(parts);
        Map<StudKey, String> studKeyIndex = buildStudKeyIndex(partById);

        String content = renderLdr(bricks, partById, studKeyIndex, brickColorCodes);
        Files.writeString(outputPath, content, StandardCharsets.UTF_8);
    }

    /**
     * Exports bricks with optional per-brick LDraw color codes.
     *
     * Uses the brick's partId to determine the LDraw part file name
     * (partId + ".dat"). Rotation is determined by comparing the brick's
     * placed orientation against the catalog part's canonical dimensions.
     *
     * @param bricks         placed bricks
     * @param outputPath     output .ldr path
     * @param catalogBaseDir optional catalog base directory (test-only)
     * @param brickColorCodes optional per-brick LDraw color code map; null or absent
     *                        entries use #DEFAULT_COLOR (16, "current color")
     * @deprecated Use the CatalogPartRepository-based overload instead.
     */
    @Deprecated
    public static void export(
        List<Brick> bricks,
        Path outputPath,
        Path catalogBaseDir,
        Map<Brick, Integer> brickColorCodes
    ) throws IOException {
        export(bricks, outputPath, new CsvCatalogPartRepository(catalogBaseDir), brickColorCodes);
    }

    /** Formats a double as an LDU coordinate string (integer if whole, otherwise 2 decimal places). */
    private static String formatLdu(double value) {
        // Keep output stable and readable for Studio.
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /**
     * Renders the LDR file content for the given bricks and part indices.
     */
    private static String renderLdr(
        List<Brick> bricks,
        Map<String, CatalogPart> partById,
        Map<StudKey, String> studKeyIndex,
        Map<Brick, Integer> brickColorCodes
    ) {
        StringBuilder out = new StringBuilder();
        out.append("0 LEGO Architecture Engine LDraw export\n");
        out.append("0 Generated: ").append(Instant.now().toString()).append('\n');
        out.append("0 Bricks: ").append(bricks.size()).append('\n');

        for (Brick brick : bricks) {
            PartPlacement placement = resolvePlacement(brick, partById, studKeyIndex);

            int color = DEFAULT_COLOR;
            if (brickColorCodes != null) {
                Integer code = brickColorCodes.get(brick);
                if (code != null) {
                    color = code;
                }
            }

            double centerXStuds = brick.x() + (brick.studX() / 2.0);
            double centerZStuds = brick.z() + (brick.studY() / 2.0);

            double x = centerXStuds * STUD_PITCH_LDU;
            double z = centerZStuds * STUD_PITCH_LDU;
            double y = -(brick.y() * LAYER_HEIGHT_LDU + brick.heightUnits() * HEIGHT_UNIT_LDU);

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

        return out.toString();
    }

    /**
     * Builds a partId→CatalogPart index from a pre-loaded list of parts.
     */
    private static Map<String, CatalogPart> buildPartByIdIndexFromList(List<CatalogPart> parts) {
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
     * For directional parts (facing != NONE), uses the Facing-based rotation table.
     * For standard parts, uses orientation comparison with catalog dimensions.
     *
     * Falls back to StudKey lookup for bricks with "unknown" partId.
     */
    private static PartPlacement resolvePlacement(Brick brick,
                                                   Map<String, CatalogPart> partById,
                                                   Map<StudKey, String> studKeyIndex) {
        // Directional parts (slopes, curves, wedges) use Facing-based rotation.
        // LDraw slope parts have their slope face pointing toward -Z in default orientation.
        // Rotations map -Z to the target Facing direction:
        //   NORTH (-Z): identity     — already facing -Z
        //   SOUTH (+Z): Y180         — flips -Z to +Z
        //   EAST  (+X): Y270         — maps -Z to +X
        //   WEST  (-X): Y90          — maps -Z to -X
        if (brick.facing() != Facing.NONE) {
            String partFile = brick.partId() + ".dat";
            return switch (brick.facing()) {
                case NORTH -> PartPlacement.identity(partFile);
                case EAST  -> PartPlacement.rotateY270(partFile);
                case SOUTH -> PartPlacement.rotateY180(partFile);
                case WEST  -> PartPlacement.rotateY90(partFile);
                default    -> PartPlacement.identity(partFile);
            };
        }

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

    /** Looks up a brick's part file and rotation by its stud dimensions in the index. */
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

    /** Composite key of (studX, studY) for part lookup. */
    private record StudKey(int studX, int studY) { }

    /** Holds a 3x3 rotation matrix and part file name for LDraw line output. */
    private static final class PartPlacement {
        final int a, b, c, d, e, f, g, h, i;
        final String partFile;

        /** Constructs a placement with the given 3x3 rotation matrix and part file name. */
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

        /** Creates an identity (no rotation) placement for the given part file. */
        static PartPlacement identity(String partFile) {
            return new PartPlacement(1, 0, 0, 0, 1, 0, 0, 0, 1, partFile);
        }

        // +90 degrees about Y: X -> Z, Z -> -X
        /** Creates a 90-degree Y-axis rotation placement for the given part file. */
        static PartPlacement rotateY90(String partFile) {
            return new PartPlacement(0, 0, 1, 0, 1, 0, -1, 0, 0, partFile);
        }

        // +180 degrees about Y: X -> -X, Z -> -Z
        /** Creates a 180-degree Y-axis rotation placement for the given part file. */
        static PartPlacement rotateY180(String partFile) {
            return new PartPlacement(-1, 0, 0, 0, 1, 0, 0, 0, -1, partFile);
        }

        // +270 degrees about Y: X -> -Z, Z -> X
        /** Creates a 270-degree Y-axis rotation placement for the given part file. */
        static PartPlacement rotateY270(String partFile) {
            return new PartPlacement(0, 0, -1, 0, 1, 0, 1, 0, 0, partFile);
        }
    }
}
