package com.lego.optimize;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.lego.ldraw.CachingPartGeometryRepository;
import com.lego.ldraw.DatParser;
import com.lego.ldraw.DefaultMaskCacheKeyStrategy;
import com.lego.ldraw.DefaultPartLibraryLocator;
import com.lego.ldraw.DiskMaskCacheStore;
import com.lego.ldraw.FilesystemPartFileResolver;
import com.lego.ldraw.GeometryRasterizer;
import com.lego.ldraw.LayeredMaskCacheStore;
import com.lego.ldraw.MaskCacheKeyStrategy;
import com.lego.ldraw.MaskCacheStore;
import com.lego.ldraw.MemoryMaskCacheStore;
import com.lego.ldraw.PartGeometry;
import com.lego.ldraw.PartGeometryRepository;
import com.lego.ldraw.PartLibraryLocator;
import com.lego.ldraw.PartFileResolver;
import com.lego.ldraw.RaycastGeometryRasterizer;
import com.lego.ldraw.StrictDatParser;
import com.lego.ldraw.DependencyFingerprint;
import com.lego.model.Facing;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * Geometry-backed part mask provider using local LDraw DAT geometry.
 */
public final class GeometryPartMaskProvider implements PartMaskProvider {

    private final PartGeometryRepository geometryRepository;
    private final GeometryRasterizer rasterizer;
    private final MaskCacheStore cacheStore;
    private final MaskCacheKeyStrategy keyStrategy;
    private final Path libraryRoot;
    private final Map<String, String> dependencyFingerprintByReference;

    public GeometryPartMaskProvider() {
        this(null, null);
    }

    public GeometryPartMaskProvider(Path ldrawLibraryDir, Path geometryMaskCacheDir) {
        PartLibraryLocator locator = new DefaultPartLibraryLocator();
        Path root = locator.locate(ldrawLibraryDir);
        Path cacheDir = geometryMaskCacheDir != null
            ? geometryMaskCacheDir.toAbsolutePath().normalize()
            : Path.of("output", "geometry-mask-cache").toAbsolutePath().normalize();

        PartFileResolver resolver = new FilesystemPartFileResolver(root);
        DatParser parser = new StrictDatParser(resolver);
        PartGeometryRepository repository = new CachingPartGeometryRepository(parser);
        GeometryRasterizer geometryRasterizer = new RaycastGeometryRasterizer();
        MaskCacheStore layered = new LayeredMaskCacheStore(
            new MemoryMaskCacheStore(),
            new DiskMaskCacheStore(cacheDir)
        );

        this.geometryRepository = repository;
        this.rasterizer = geometryRasterizer;
        this.cacheStore = layered;
        this.keyStrategy = new DefaultMaskCacheKeyStrategy();
        this.libraryRoot = root;
        this.dependencyFingerprintByReference = new HashMap<>();
    }

    GeometryPartMaskProvider(PartGeometryRepository geometryRepository,
                             GeometryRasterizer rasterizer,
                             MaskCacheStore cacheStore,
                             MaskCacheKeyStrategy keyStrategy,
                             Path libraryRoot) {
        this.geometryRepository = geometryRepository;
        this.rasterizer = rasterizer;
        this.cacheStore = cacheStore;
        this.keyStrategy = keyStrategy;
        this.libraryRoot = libraryRoot;
        this.dependencyFingerprintByReference = new HashMap<>();
    }

    @Override
    public PartMask getMask(BrickSpec spec, Facing facing, int studX, int studY) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (facing == null) {
            throw new IllegalArgumentException("facing must not be null");
        }
        if (studX <= 0 || studY <= 0) {
            throw new IllegalArgumentException("studX/studY must be positive");
        }

        // Rectangular bricks/plates are accurately represented as a cuboid mask and
        // do not require expensive geometry loading/rasterization.
        if (!spec.isSlope()) {
            return PartMask.cuboid(studX, studY, spec.heightUnits());
        }

        String reference = spec.partId() + ".dat";
        PartGeometry geometry = geometryRepository.load(reference);
        String dependencyFingerprint = dependencyFingerprintByReference.computeIfAbsent(
            reference,
            ignored -> DependencyFingerprint.compute(geometry)
        );
        String key = keyStrategy.keyFor(spec, facing, studX, studY, dependencyFingerprint);

        return cacheStore.get(key).orElseGet(() -> {
            PartMask generated = rasterizer.rasterize(geometry, spec, facing, studX, studY);
            cacheStore.put(key, generated);
            return generated;
        });
    }

    public Path libraryRoot() {
        return libraryRoot;
    }

    @Override
    public boolean isGeometryBacked() {
        return true;
    }
}
