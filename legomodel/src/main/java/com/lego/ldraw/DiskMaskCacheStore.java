package com.lego.ldraw;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.lego.optimize.PartMask;
import com.lego.optimize.PartMask.VoxelOffset;

/**
 * Filesystem cache store for geometry masks.
 */
public final class DiskMaskCacheStore implements MaskCacheStore {

    private final Path directory;

    public DiskMaskCacheStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public Optional<PartMask> get(String key) {
        Path file = cacheFile(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() < 3 || !"mask-v1".equals(lines.get(0))) {
                return Optional.empty();
            }
            int split = Integer.parseInt(lines.get(1));
            if (split <= 0 || split >= lines.size()) {
                return Optional.empty();
            }
            List<VoxelOffset> solid = parseOffsets(lines.subList(2, 2 + split));
            List<VoxelOffset> top = parseOffsets(lines.subList(2 + split, lines.size()));
            if (solid.isEmpty() || top.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new PartMask(solid, top));
        } catch (RuntimeException | IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, PartMask mask) {
        if (key == null || key.isBlank() || mask == null) {
            return;
        }
        Path file = cacheFile(key);
        try {
            Files.createDirectories(directory);
            List<String> lines = new ArrayList<>();
            lines.add("mask-v1");
            lines.add(Integer.toString(mask.solidOccupancyMask().size()));
            for (VoxelOffset offset : mask.solidOccupancyMask()) {
                lines.add(offset.dx() + "," + offset.dy() + "," + offset.dz());
            }
            for (VoxelOffset offset : mask.topCoverageMask()) {
                lines.add(offset.dx() + "," + offset.dy() + "," + offset.dz());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Cache write failures should not fail placement.
        }
    }

    private Path cacheFile(String key) {
        return directory.resolve(key + ".mask");
    }

    private static List<VoxelOffset> parseOffsets(List<String> lines) {
        List<VoxelOffset> offsets = new ArrayList<>(lines.size());
        for (String line : lines) {
            String[] parts = line.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid cache line: " + line);
            }
            offsets.add(new VoxelOffset(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            ));
        }
        return offsets;
    }
}
