package com.lego.ldraw;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic local-path locator for the LDraw parts library.
 */
public final class DefaultPartLibraryLocator implements PartLibraryLocator {

    @Override
    public Path locate(Path overridePath) {
        List<Path> candidates = new ArrayList<>();
        if (overridePath != null) {
            Path normalized = overridePath.toAbsolutePath().normalize();
            validateLibraryRoot(normalized, true);
            return normalized;
        }

        candidates.add(Path.of("/Applications/Studio 2.0/ldraw"));
        Path cwd = Path.of("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve("ldraw"));
        candidates.add(cwd.resolve("legomodel/ldraw"));
        Path home = Path.of(System.getProperty("user.home", ""));
        if (home != null && !home.toString().isBlank()) {
            candidates.add(home.resolve("ldraw"));
        }

        for (Path candidate : candidates) {
            if (isValidLibraryRoot(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        StringBuilder msg = new StringBuilder("Could not locate local LDraw library. Checked:\n");
        for (Path candidate : candidates) {
            msg.append("  - ").append(candidate.toAbsolutePath()).append('\n');
        }
        msg.append("Provide --ldraw-library-dir=<path> pointing to a folder containing parts/ or p/.");
        throw new LDrawException(msg.toString());
    }

    private static void validateLibraryRoot(Path root, boolean explicit) {
        if (!isValidLibraryRoot(root)) {
            String prefix = explicit ? "Invalid --ldraw-library-dir: " : "Invalid LDraw library root: ";
            throw new LDrawException(prefix + root.toAbsolutePath()
                + " (expected directory containing parts/ or p/)");
        }
    }

    private static boolean isValidLibraryRoot(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return false;
        }
        return Files.isDirectory(root.resolve("parts"))
            || Files.isDirectory(root.resolve("p"))
            || Files.isDirectory(root.resolve("UnOfficial/parts"))
            || Files.isDirectory(root.resolve("UnOfficial/p"));
    }
}
