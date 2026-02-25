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
}
