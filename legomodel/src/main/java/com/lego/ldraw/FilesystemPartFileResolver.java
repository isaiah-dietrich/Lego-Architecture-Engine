package com.lego.ldraw;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves part names against a local LDraw library root.
 */
public final class FilesystemPartFileResolver implements PartFileResolver {

    private final Path libraryRoot;

    public FilesystemPartFileResolver(Path libraryRoot) {
        if (libraryRoot == null) {
            throw new IllegalArgumentException("libraryRoot must not be null");
        }
        this.libraryRoot = libraryRoot.toAbsolutePath().normalize();
    }

    @Override
    public Path resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new LDrawException("Invalid empty LDraw reference");
        }

        String normalized = normalizeReference(reference);
        List<String> relCandidates = candidateRelativePaths(normalized);
        for (String rel : relCandidates) {
            Path resolved = resolveCaseFlexible(libraryRoot, rel);
            if (resolved != null) {
                return resolved;
            }
        }

        StringBuilder msg = new StringBuilder("Unable to resolve LDraw reference '")
            .append(reference)
            .append("' under ")
            .append(libraryRoot)
            .append(". Tried:\n");
        for (String rel : relCandidates) {
            msg.append("  - ").append(rel).append('\n');
        }
        throw new LDrawException(msg.toString());
    }

    private static String normalizeReference(String value) {
        String out = value.trim().replace('\\', '/');
        if (!out.toLowerCase(Locale.ROOT).endsWith(".dat")) {
            out = out + ".dat";
        }
        while (out.startsWith("./")) {
            out = out.substring(2);
        }
        return out;
    }

    private static List<String> candidateRelativePaths(String ref) {
        String lower = ref.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        if (!lower.contains("/")) {
            out.add("parts/" + ref);
            out.add("Unofficial/parts/" + ref);
            out.add("UnOfficial/parts/" + ref);
            out.add("p/" + ref);
            out.add("Unofficial/p/" + ref);
            out.add("UnOfficial/p/" + ref);
            return out;
        }

        if (lower.startsWith("s/")) {
            out.add("parts/" + ref);
            out.add("UnOfficial/parts/" + ref);
            out.add("Unofficial/parts/" + ref);
            return out;
        }

        if (lower.startsWith("8/") || lower.startsWith("48/") || lower.startsWith("4/")) {
            out.add("p/" + ref);
            out.add("UnOfficial/p/" + ref);
            out.add("Unofficial/p/" + ref);
            return out;
        }

        out.add(ref);
        out.add("parts/" + ref);
        out.add("p/" + ref);
        out.add("UnOfficial/" + ref);
        return out;
    }

    private static Path resolveCaseFlexible(Path root, String relative) {
        Path direct = root.resolve(relative);
        if (Files.isRegularFile(direct)) {
            return direct.toAbsolutePath().normalize();
        }

        String[] segments = relative.split("/");
        Path cursor = root;
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            Path next = cursor.resolve(segment);
            if (Files.exists(next)) {
                cursor = next;
                continue;
            }
            try {
                Path matched = findSegmentIgnoreCase(cursor, segment);
                if (matched == null) {
                    return null;
                }
                cursor = matched;
            } catch (IOException e) {
                return null;
            }
        }

        return Files.isRegularFile(cursor) ? cursor.toAbsolutePath().normalize() : null;
    }

    private static Path findSegmentIgnoreCase(Path directory, String target) throws IOException {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (var stream = Files.list(directory)) {
            return stream
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(target))
                .findFirst()
                .orElse(null);
        }
    }
}
