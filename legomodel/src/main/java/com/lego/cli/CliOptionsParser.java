package com.lego.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses command-line arguments into a validated ParsedOptions record.
 * Extracted from Main to separate CLI parsing from pipeline orchestration.
 */
final class CliOptionsParser {

    /** Non-instantiable utility class. */
    private CliOptionsParser() {}

    /** Parses command-line arguments into a validated ParsedOptions. */
    static ParsedOptions parse(String[] args) {
        List<String> positional = new ArrayList<>();
        boolean analyzeStepping = false;
        Path analysisDir = null;
        int jumpThreshold = 25;
        List<Integer> sweepResolutions = new ArrayList<>();
        String colorMode = "none";
        int colorFallback = -1;
        boolean colorList = false;
        String colorAlgorithm = "direct";
        String placementPolicy = "scoring";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            int eq = arg.indexOf('=');
            String key = eq >= 0 ? arg.substring(0, eq) : arg;
            String val = eq >= 0 ? arg.substring(eq + 1) : null;

            switch (key) {
                case "--analyze-stepping" -> analyzeStepping = true;
                case "--analysis-dir" -> {
                    if (val != null) {
                        analysisDir = Path.of(val);
                    } else if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--analysis-dir requires a value");
                    } else {
                        analysisDir = Path.of(args[++i]);
                    }
                }
                case "--jump-threshold" ->
                    jumpThreshold = parseNonNegativeInt(val, "jump-threshold");
                case "--sweep" ->
                    sweepResolutions = parseSweepResolutions(val);
                case "--color-mode" -> {
                    colorMode = val;
                    if (!"none".equals(colorMode) && !"glb-color".equals(colorMode)) {
                        throw new IllegalArgumentException(
                            "Invalid --color-mode: " + colorMode + ". Use 'none' or 'glb-color'."
                        );
                    }
                }
                case "--color-fallback" ->
                    colorFallback = parseNonNegativeInt(val, "color-fallback");
                case "--color-list" -> colorList = true;
                case "--color-algorithm" -> colorAlgorithm = val;
                case "--placement-policy" -> placementPolicy = val;
                default -> positional.add(arg);
            }
        }

        return new ParsedOptions(positional, analyzeStepping, analysisDir, jumpThreshold,
            sweepResolutions, colorMode, colorFallback, colorList, colorAlgorithm,
            placementPolicy);
    }

    /** Parses a string as a non-negative integer, throwing on invalid input. */
    static int parseNonNegativeInt(String value, String fieldName) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be an integer");
        }
        if (parsed < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
        return parsed;
    }

    /** Parses a comma-separated string of resolution values into a list of integers. */
    private static List<Integer> parseSweepResolutions(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("sweep resolutions must not be empty");
        }

        String[] parts = csv.split(",");
        List<Integer> resolutions = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            int resolution = parseNonNegativeInt(trimmed, "sweep resolution");
            if (resolution < 2) {
                throw new IllegalArgumentException("sweep resolution must be >= 2");
            }
            resolutions.add(resolution);
        }
        return resolutions;
    }
}
