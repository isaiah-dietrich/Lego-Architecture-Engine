package com.lego.model;

/**
 * Immutable 3D coordinate representation.
 *
 * Vector3 is the foundational data model for all geometry operations.
 * Each component may range from negative infinity to positive infinity
 * (e.g., for normalized or scaled coordinates).
 *
 * No validation is applied—the caller is responsible for ensuring
 * valid coordinate values for their use case.
 */
public record Vector3(double x, double y, double z) {

    public static final Vector3 ZERO = new Vector3(0, 0, 0);

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 scale(double s) {
        return new Vector3(x * s, y * s, z * s);
    }

    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3 normalize() {
        double len = length();
        if (len < 1e-12) {
            return ZERO;
        }
        return new Vector3(x / len, y / len, z / len);
    }
}
