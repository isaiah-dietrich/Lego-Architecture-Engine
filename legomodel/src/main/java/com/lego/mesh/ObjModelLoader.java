package com.lego.mesh;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ModelLoader implementation for .obj files.
 *
 * Delegates to the existing ObjLoader for geometry parsing. OBJ files carry
 * no color information, so LoadedModel#colorMap() is always empty.
 *
 * Rejects .gltf inputs with an explicit error message directing users to
 * convert to .glb first.
 */
public final class ObjModelLoader implements ModelLoader {

    @Override
    /** Loads an OBJ file and returns the model with geometry only (no color data). */
    public LoadedModel load(Path path) throws IOException {
        String filename = path.getFileName().toString().toLowerCase();

        if (filename.endsWith(".gltf")) {
            throw new IllegalArgumentException(
                "Unsupported format: .gltf files are not accepted. Convert to .glb first."
            );
        }

        if (!filename.endsWith(".obj")) {
            throw new IllegalArgumentException(
                "ObjModelLoader only supports .obj files, got: " + path.getFileName()
            );
        }

        return LoadedModel.geometryOnly(ObjLoader.load(path));
    }
}
