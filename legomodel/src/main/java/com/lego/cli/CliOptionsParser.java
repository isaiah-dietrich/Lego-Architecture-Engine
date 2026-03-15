package com.lego.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses command-line arguments into a validated {@link ParsedOptions} record.
 * Extracted from {@code Main} to separate CLI parsing from pipeline orchestration.
 */
final class CliOptionsParser {

    private CliOptionsParser() {}

    /** Parses command-line arguments into a validated {@link ParsedOptions}. */
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
            if ("--analyze-stepping".equals(arg)) {
                analyzeStepping = true;
            } else if (arg.startsWith("--analysis-dir=")) {
                analysisDir = Path.of(arg.substring("--analysis-dir=".length()));
            } else if ("--analysis-dir".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--analysis-dir requires a value");
                }
                analysisDir = Path.of(args[++i]);
            } else if (arg.startsWith("--jump-threshold=")) {
                String value = arg.substring("--jump-threshold=".length());
                jumpThreshold = parseNonNegativeInt(value, "jump-threshold");
            } else if (arg.startsWith("--sweep=")) {
                String value = arg.substring("--sweep=".length());
                sweepResolutions = parseSweepResolutions(value);
            } else if (arg.startsWith("--color-mode=")) {
                colorMode = arg.substring("--color-mode=".length());
                if (!"none".equals(colorMode) && !"glb-color".equals(colorMode)) {
                    throw new IllegalArgumentException(
                        "Invalid --color-mode: " + colorMode + ". Use 'none' or 'glb-color'."
                    );
                }
            } else if (arg.startsWith("--color-fallback=")) {
                colorFallback = parseNonNegativeInt(
                    arg.substring("--color-fallback=".length()), "color-fallback"
                );
            } else if ("--color-list".equals(arg)) {
                colorList = true;
            } else if (arg.startsWith("--color-algorithm=")) {
                colorAlgorithm = arg.substring("--color-algorithm=".length());
            } else if (arg.startsWith("--placement-policy=")) {
                placementPolicy = arg.substring("--placement-policy=".length());
            } else {
                positional.add(arg);
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
