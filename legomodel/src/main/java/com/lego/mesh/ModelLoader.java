package com.lego.mesh;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction over model file loaders.
 *
 * <p>Implementations load a model file from the given {@code path} and return a
 * {@link LoadedModel} containing the parsed geometry and, where available, color data.
 *
 * <p>Current implementations:
 * <ul>
 *   <li>{@link ObjModelLoader} — loads {@code .obj} files; no color output.</li>
 *   <li>{@code GlbLoader} (Phase 1) — loads {@code .glb} files; color output in Phase 2.</li>
 * </ul>
 *
 * <p>The CLI selects the appropriate loader by file extension at runtime.
 * Unsupported extensions must throw {@link IllegalArgumentException} with a clear message.
 */
@FunctionalInterface
public interface ModelLoader {

    /**
     * Loads a model from the given path.
     *
     * @param path path to the model file
     * @return the loaded model
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file format is not supported by this loader
     */
    LoadedModel load(Path path) throws IOException;
}
