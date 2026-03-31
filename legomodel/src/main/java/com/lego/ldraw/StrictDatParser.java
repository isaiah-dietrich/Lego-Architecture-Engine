package com.lego.ldraw;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Strict recursive parser for LDraw DAT part graphs.
 */
public final class StrictDatParser implements DatParser {

    private final PartFileResolver resolver;
    private final Map<String, PartGeometry> memoized = new HashMap<>();

    public StrictDatParser(PartFileResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        this.resolver = resolver;
    }

    @Override
    public synchronized PartGeometry parse(String partReference) {
        String normalized = normalizeReference(partReference);
        PartGeometry cached = memoized.get(normalized);
        if (cached != null) {
            return cached;
        }

        Path root = resolver.resolve(normalized);
        ParseAccumulator accumulator = new ParseAccumulator();
        parseFile(root, GeometryTransform.identity(), false, new ArrayDeque<>(), accumulator);

        if (accumulator.triangles.isEmpty()) {
            throw new LDrawException("Part " + partReference + " produced no triangle geometry");
        }

        PartGeometry geometry = new PartGeometry(accumulator.triangles, accumulator.dependencies);
        memoized.put(normalized, geometry);
        return geometry;
    }

    private void parseFile(Path file,
                           GeometryTransform transform,
                           boolean inheritedInvert,
                           Deque<Path> stack,
                           ParseAccumulator out) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (stack.contains(normalizedFile)) {
            throw new LDrawException("Detected recursive include cycle: " + formatCycle(stack, normalizedFile));
        }

        stack.push(normalizedFile);
        out.dependencies.add(normalizedFile);
        boolean bfcCertified = false;
        boolean bfcCcw = true;
        boolean invertNext = false;

        try {
            List<String> lines = Files.readAllLines(normalizedFile);
            for (int idx = 0; idx < lines.size(); idx++) {
                int lineNo = idx + 1;
                String raw = lines.get(idx);
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] tokens = line.split("\\s+");
                int type = parseInt(tokens[0], normalizedFile, lineNo, "line type");

                switch (type) {
                    case 0 -> {
                        BfcUpdate update = parseBfc(tokens);
                        if (update.certified() != null) {
                            bfcCertified = update.certified();
                        }
                        if (update.ccw() != null) {
                            bfcCcw = update.ccw();
                        }
                        if (update.invertNext()) {
                            invertNext = true;
                        }
                    }
                    case 1 -> {
                        if (tokens.length < 15) {
                            throw parseError(normalizedFile, lineNo, "Malformed type-1 line (expected >=15 tokens)");
                        }
                        double x = parseDouble(tokens[2], normalizedFile, lineNo, "x");
                        double y = parseDouble(tokens[3], normalizedFile, lineNo, "y");
                        double z = parseDouble(tokens[4], normalizedFile, lineNo, "z");
                        GeometryTransform local = new GeometryTransform(
                            parseDouble(tokens[5], normalizedFile, lineNo, "a"),
                            parseDouble(tokens[6], normalizedFile, lineNo, "b"),
                            parseDouble(tokens[7], normalizedFile, lineNo, "c"),
                            x,
                            parseDouble(tokens[8], normalizedFile, lineNo, "d"),
                            parseDouble(tokens[9], normalizedFile, lineNo, "e"),
                            parseDouble(tokens[10], normalizedFile, lineNo, "f"),
                            y,
                            parseDouble(tokens[11], normalizedFile, lineNo, "g"),
                            parseDouble(tokens[12], normalizedFile, lineNo, "h"),
                            parseDouble(tokens[13], normalizedFile, lineNo, "i"),
                            z
                        );
                        String reference = joinReference(tokens, 14);
                        Path subfile = resolver.resolve(reference);
                        boolean invert = inheritedInvert ^ invertNext ^ (local.determinant3x3() < 0.0);
                        parseFile(subfile, transform.compose(local), invert, stack, out);
                        invertNext = false;
                    }
                    case 2 -> {
                        // Edges are parsed but intentionally ignored for solid occupancy.
                        ensureTokenCount(tokens, 8, normalizedFile, lineNo, "type-2 line");
                        invertNext = false;
                    }
                    case 3 -> {
                        ensureTokenCount(tokens, 11, normalizedFile, lineNo, "type-3 line");
                        Vector3 v1 = parseVertex(tokens, 2, normalizedFile, lineNo);
                        Vector3 v2 = parseVertex(tokens, 5, normalizedFile, lineNo);
                        Vector3 v3 = parseVertex(tokens, 8, normalizedFile, lineNo);
                        v1 = transform.apply(v1);
                        v2 = transform.apply(v2);
                        v3 = transform.apply(v3);

                        boolean flip = inheritedInvert ^ invertNext ^ (bfcCertified && !bfcCcw);
                        out.triangles.add(flip ? new Triangle(v1, v3, v2) : new Triangle(v1, v2, v3));
                        invertNext = false;
                    }
                    case 4 -> {
                        ensureTokenCount(tokens, 14, normalizedFile, lineNo, "type-4 line");
                        Vector3 v1 = transform.apply(parseVertex(tokens, 2, normalizedFile, lineNo));
                        Vector3 v2 = transform.apply(parseVertex(tokens, 5, normalizedFile, lineNo));
                        Vector3 v3 = transform.apply(parseVertex(tokens, 8, normalizedFile, lineNo));
                        Vector3 v4 = transform.apply(parseVertex(tokens, 11, normalizedFile, lineNo));

                        boolean flip = inheritedInvert ^ invertNext ^ (bfcCertified && !bfcCcw);
                        if (flip) {
                            out.triangles.add(new Triangle(v1, v3, v2));
                            out.triangles.add(new Triangle(v1, v4, v3));
                        } else {
                            out.triangles.add(new Triangle(v1, v2, v3));
                            out.triangles.add(new Triangle(v1, v3, v4));
                        }
                        invertNext = false;
                    }
                    case 5 -> {
                        // Optional lines are parsed but ignored for solid occupancy.
                        ensureTokenCount(tokens, 14, normalizedFile, lineNo, "type-5 line");
                        invertNext = false;
                    }
                    default -> throw parseError(normalizedFile, lineNo, "Unsupported DAT line type: " + type);
                }
            }
        } catch (IOException e) {
            throw new LDrawException("Failed to read DAT file: " + normalizedFile, e);
        } finally {
            stack.pop();
        }
    }

    private static String normalizeReference(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new LDrawException("Invalid empty part reference");
        }
        String out = ref.trim().replace('\\', '/');
        if (!out.toLowerCase(Locale.ROOT).endsWith(".dat")) {
            out = out + ".dat";
        }
        return out;
    }

    private static BfcUpdate parseBfc(String[] tokens) {
        if (tokens.length < 2 || !"BFC".equalsIgnoreCase(tokens[1])) {
            return BfcUpdate.NONE;
        }
        Boolean certified = null;
        Boolean ccw = null;
        boolean invertNext = false;

        for (int i = 2; i < tokens.length; i++) {
            String token = tokens[i].toUpperCase(Locale.ROOT);
            switch (token) {
                case "CERTIFY" -> {
                    certified = true;
                    if (i + 1 < tokens.length) {
                        String next = tokens[i + 1].toUpperCase(Locale.ROOT);
                        if ("CCW".equals(next)) {
                            ccw = true;
                        } else if ("CW".equals(next)) {
                            ccw = false;
                        }
                    }
                }
                case "NOCERTIFY" -> certified = false;
                case "CCW" -> ccw = true;
                case "CW" -> ccw = false;
                case "INVERTNEXT" -> invertNext = true;
                default -> {
                    // Ignore unrelated BFC tokens (e.g., CLIP/NOCLIP).
                }
            }
        }
        return new BfcUpdate(certified, ccw, invertNext);
    }

    private static Vector3 parseVertex(String[] tokens, int from, Path file, int lineNo) {
        return new Vector3(
            parseDouble(tokens[from], file, lineNo, "vertex-x"),
            parseDouble(tokens[from + 1], file, lineNo, "vertex-y"),
            parseDouble(tokens[from + 2], file, lineNo, "vertex-z")
        );
    }

    private static int parseInt(String token, Path file, int lineNo, String field) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw parseError(file, lineNo, "Invalid integer for " + field + ": " + token);
        }
    }

    private static double parseDouble(String token, Path file, int lineNo, String field) {
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw parseError(file, lineNo, "Invalid number for " + field + ": " + token);
        }
    }

    private static void ensureTokenCount(String[] tokens, int min, Path file, int lineNo, String label) {
        if (tokens.length < min) {
            throw parseError(file, lineNo, "Malformed " + label + " (expected >= " + min + " tokens)");
        }
    }

    private static String joinReference(String[] tokens, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < tokens.length; i++) {
            if (i > start) {
                out.append(' ');
            }
            out.append(tokens[i]);
        }
        return out.toString();
    }

    private static LDrawException parseError(Path file, int lineNo, String reason) {
        return new LDrawException(file + ":" + lineNo + " - " + reason);
    }

    private static String formatCycle(Deque<Path> stack, Path candidate) {
        List<Path> cycle = new ArrayList<>(stack);
        cycle.add(0, candidate);
        StringBuilder out = new StringBuilder();
        for (int i = cycle.size() - 1; i >= 0; i--) {
            out.append(cycle.get(i));
            if (i > 0) {
                out.append(" -> ");
            }
        }
        return out.toString();
    }

    private static final class ParseAccumulator {
        private final List<Triangle> triangles = new ArrayList<>();
        private final Set<Path> dependencies = new HashSet<>();
    }

    private record BfcUpdate(Boolean certified, Boolean ccw, boolean invertNext) {
        private static final BfcUpdate NONE = new BfcUpdate(null, null, false);
    }
}
