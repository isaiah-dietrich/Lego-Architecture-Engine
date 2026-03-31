package com.lego.ldraw;

import com.lego.model.Vector3;

/**
 * Immutable 3x4 affine transform used by LDraw type-1 commands.
 */
public record GeometryTransform(
    double a, double b, double c, double tx,
    double d, double e, double f, double ty,
    double g, double h, double i, double tz
) {

    public static GeometryTransform identity() {
        return new GeometryTransform(
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0
        );
    }

    public Vector3 apply(Vector3 v) {
        return new Vector3(
            a * v.x() + b * v.y() + c * v.z() + tx,
            d * v.x() + e * v.y() + f * v.z() + ty,
            g * v.x() + h * v.y() + i * v.z() + tz
        );
    }

    public GeometryTransform compose(GeometryTransform other) {
        return new GeometryTransform(
            a * other.a + b * other.d + c * other.g,
            a * other.b + b * other.e + c * other.h,
            a * other.c + b * other.f + c * other.i,
            a * other.tx + b * other.ty + c * other.tz + tx,
            d * other.a + e * other.d + f * other.g,
            d * other.b + e * other.e + f * other.h,
            d * other.c + e * other.f + f * other.i,
            d * other.tx + e * other.ty + f * other.tz + ty,
            g * other.a + h * other.d + i * other.g,
            g * other.b + h * other.e + i * other.h,
            g * other.c + h * other.f + i * other.i,
            g * other.tx + h * other.ty + i * other.tz + tz
        );
    }

    public double determinant3x3() {
        return a * (e * i - f * h)
            - b * (d * i - f * g)
            + c * (d * h - e * g);
    }
}
