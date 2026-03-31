package com.lego.ldraw;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.lego.model.Facing;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * SHA-256 based deterministic cache key builder.
 */
public final class DefaultMaskCacheKeyStrategy implements MaskCacheKeyStrategy {

    private static final String RASTERIZER_VERSION = "rasterizer-v1";
    private static final String PARSER_VERSION = "parser-v1";

    @Override
    public String keyFor(BrickSpec spec, Facing facing, int studX, int studY, String dependencyFingerprint) {
        String raw = spec.partId() + "|"
            + spec.heightUnits() + "|"
            + facing + "|"
            + studX + "x" + studY + "|"
            + PARSER_VERSION + "|"
            + RASTERIZER_VERSION + "|"
            + dependencyFingerprint;
        return sha256(raw);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
