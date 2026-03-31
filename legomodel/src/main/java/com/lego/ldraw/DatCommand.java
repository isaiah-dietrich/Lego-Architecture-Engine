package com.lego.ldraw;

import com.lego.model.Vector3;

/**
 * Typed representation of parsed LDraw commands.
 */
public sealed interface DatCommand permits DatCommand.Comment,
    DatCommand.SubfileRef,
    DatCommand.LineSegment,
    DatCommand.TriangleFace,
    DatCommand.QuadFace,
    DatCommand.OptionalLine {

    record Comment(String text) implements DatCommand {}

    record SubfileRef(String reference, GeometryTransform transform, boolean invertNext) implements DatCommand {}

    record LineSegment(Vector3 v1, Vector3 v2) implements DatCommand {}

    record TriangleFace(Vector3 v1, Vector3 v2, Vector3 v3) implements DatCommand {}

    record QuadFace(Vector3 v1, Vector3 v2, Vector3 v3, Vector3 v4) implements DatCommand {}

    record OptionalLine(Vector3 v1, Vector3 v2, Vector3 c1, Vector3 c2) implements DatCommand {}
}
