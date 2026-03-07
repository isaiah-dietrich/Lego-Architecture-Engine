package com.lego.mesh;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link ModelLoader} implementation for {@code .obj} files.
 *
 * <p>Delegates to the existing {@link ObjLoader} for geometry parsing. OBJ files carry
 * no color information, so {@link LoadedModel#colorMap()} is always empty.
 *
 * <p>Rejects {@code .gltf} inputs with an explicit error message directing users to
 * convert to {@code .glb} first.
 */
public final class ObjModelLoader implements ModelLoader {

    @Override
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
