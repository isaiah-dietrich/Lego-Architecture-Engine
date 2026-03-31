package com.lego.ldraw;

/**
 * Typed failure surfaced by the strict LDraw geometry pipeline.
 */
public final class LDrawException extends IllegalArgumentException {

    public LDrawException(String message) {
        super(message);
    }

    public LDrawException(String message, Throwable cause) {
        super(message, cause);
    }
}
