package com.lego.ldraw;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

/**
 * Computes a deterministic fingerprint from transitive geometry source files.
 */
public final class DependencyFingerprint {

    private DependencyFingerprint() {}

    public static String compute(PartGeometry geometry) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Path> files = geometry.dependencyFiles().stream()
                .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                .toList();
            for (Path file : files) {
                Path normalized = file.toAbsolutePath().normalize();
                digest.update(normalized.toString().getBytes(StandardCharsets.UTF_8));
                if (Files.exists(normalized)) {
                    digest.update(Long.toString(Files.size(normalized)).getBytes(StandardCharsets.UTF_8));
                    digest.update(Long.toString(Files.getLastModifiedTime(normalized).toMillis()).getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] bytes = digest.digest();
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new LDrawException("Failed to fingerprint dependency files", e);
        }
    }
}
