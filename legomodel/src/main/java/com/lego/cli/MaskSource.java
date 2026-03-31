package com.lego.cli;

/**
 * Effective source of part masks used for placement and benchmark metrics.
 */
enum MaskSource {
    GEOMETRY("geometry"),
    LEGACY_PROCEDURAL("legacy-procedural");

    private final String cliLabel;

    MaskSource(String cliLabel) {
        this.cliLabel = cliLabel;
    }

    String cliLabel() {
        return cliLabel;
    }
}
