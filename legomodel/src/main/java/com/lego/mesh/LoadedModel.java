package com.lego.mesh;

import java.util.Map;
import java.util.Optional;

import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.model.Triangle;

/**
 * The result of loading a 3D model file.
 *
 * <p>{@code mesh} always contains the geometry. {@code colorMap} is present when the
 * source file carried color information (e.g. GLB vertex colors or material base-color
 * factor); it is empty for formats that have no color channel (e.g. OBJ).
 *
 * <p>Color data flows as a side-channel alongside geometry. {@code Triangle}, {@code Mesh},
 * and the voxelizer pipeline are never modified to carry color.
 */
public record LoadedModel(
    Mesh mesh,
    Optional<Map<Triangle, ColorRgb>> colorMap
) {
    public LoadedModel {
        java.util.Objects.requireNonNull(mesh, "mesh");
        java.util.Objects.requireNonNull(colorMap, "colorMap");
    }

    /** Convenience factory: geometry only, no color. */
    public static LoadedModel geometryOnly(Mesh mesh) {
        return new LoadedModel(mesh, Optional.empty());
    }

    /** Convenience factory: geometry with color. */
    public static LoadedModel withColor(Mesh mesh, Map<Triangle, ColorRgb> colorMap) {
        java.util.Objects.requireNonNull(colorMap, "colorMap");
        return new LoadedModel(mesh, Optional.of(colorMap));
    }
}
