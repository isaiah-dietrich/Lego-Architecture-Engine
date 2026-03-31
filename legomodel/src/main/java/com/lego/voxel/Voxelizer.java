package com.lego.voxel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import com.lego.model.Mesh;

/**
 * Facade for voxelization strategy selection.
 *
 * Default behavior remains the legacy ray-parity implementation to preserve
 * backward compatibility while new voxelizers are introduced incrementally.
 */
public final class Voxelizer {

    /** Non-instantiable utility class. */
    private Voxelizer() {
        // Utility class, prevent instantiation
    }

    /**
     * Backward-compatible entry point that uses the legacy implementation.
     */
    public static VoxelGrid voxelize(Mesh mesh, int resolution) {
        return voxelize(mesh, resolution, VoxelizationStrategy.LEGACY);
    }

    /**
     * Strategy-aware voxelization entry point.
     *
     * @param mesh input mesh (must be non-null)
     * @param resolution voxel grid resolution (must be >= 2)
     * @param strategy voxelization strategy to use
     * @return voxelized grid
     */
    public static VoxelGrid voxelize(Mesh mesh, int resolution, VoxelizationStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        return switch (strategy) {
            case LEGACY -> legacyVoxelize(mesh, resolution);
            case TOPOLOGICAL_SURFACE -> TopologicalVoxelizer.voxelizeSurface(mesh, resolution);
        };
    }

    private static VoxelGrid legacyVoxelize(Mesh mesh, int resolution) {
        try {
            Class<?> legacyClass = Class.forName("com.lego.voxel.LegacyVoxelizer");
            Method method = legacyClass.getDeclaredMethod("voxelize", Mesh.class, int.class);
            Object out = method.invoke(null, mesh, resolution);
            return (VoxelGrid) out;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Legacy voxelizer is not available in this build. Rebuild with -Plegacy.");
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Legacy voxelizer is present but incompatible: " + e.getMessage(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Legacy voxelizer failed: " + cause.getMessage(), cause);
        }
    }
}
