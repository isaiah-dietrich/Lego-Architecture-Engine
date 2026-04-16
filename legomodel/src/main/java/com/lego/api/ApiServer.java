package com.lego.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.lego.cli.PipelineRequest;
import com.lego.cli.PipelineResult;
import com.lego.cli.PipelineRunner;
import com.lego.color.ColorStrategyRegistry;
import com.lego.data.CsvCatalogPartRepository;
import com.lego.data.CsvPaletteRepository;
import com.lego.mesh.GlbLoader;
import com.lego.mesh.ModelLoader;
import com.lego.mesh.ObjModelLoader;
import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelizationStrategy;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

/**
 * HTTP API server wrapping the LEGO pipeline.
 *
 * Endpoints:
 *   GET  /api/health
 *   POST /api/convert
 *   GET  /api/jobs/{jobId}
 *   GET  /api/jobs/{jobId}/download
 *
 * Run with: mvn compile exec:java -Dexec.mainClass=com.lego.api.ApiServer
 */
public final class ApiServer {

    private static final int DEFAULT_PORT = 7070;
    private static final long JOB_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    private static final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private static final ExecutorService pipelinePool = Executors.newFixedThreadPool(2);
    private static final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private static final Path TEMP_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "lego-api");

    private ApiServer() {}

    public static void main(String[] args) {
        start(DEFAULT_PORT);
    }

    public static void start(int port) {
        try {
            Files.createDirectories(TEMP_ROOT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory: " + TEMP_ROOT, e);
        }

        cleaner.scheduleAtFixedRate(ApiServer::cleanupExpiredJobs, 5, 5, TimeUnit.MINUTES);

        Javalin app = Javalin.create(config ->
            config.plugins.enableCors(cors ->
                cors.add(rule -> rule.anyHost())
            )
        );

        app.get("/api/health",                  ctx -> ctx.json(Map.of("status", "ok")));
        app.post("/api/convert",                ApiServer::handleConvert);
        app.get("/api/jobs/{jobId}",            ApiServer::handleJobStatus);
        app.get("/api/jobs/{jobId}/download",   ApiServer::handleDownload);

        app.start(port);
        System.out.println("Lego API server running on http://localhost:" + port);
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private static void handleConvert(Context ctx) throws IOException {
        UploadedFile upload = ctx.uploadedFile("file");
        if (upload == null) {
            ctx.status(400).json(Map.of("error", "No file uploaded"));
            return;
        }

        String filename = upload.filename().toLowerCase();
        if (!filename.endsWith(".obj") && !filename.endsWith(".glb")) {
            ctx.status(400).json(Map.of("error", "Only .obj and .glb files are accepted"));
            return;
        }
        String ext = filename.endsWith(".glb") ? "glb" : "obj";

        String resParam = ctx.formParam("resolution");
        String algorithm = ctx.formParam("algorithm");
        String outputType = ctx.formParam("outputType");
        String colorMode = ctx.formParam("colorMode");
        String colorAlgorithm = ctx.formParam("colorAlgorithm");

        // Validate resolution
        int resolution;
        try {
            resolution = Integer.parseInt(resParam == null ? "" : resParam);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "resolution must be an integer"));
            return;
        }
        if (resolution < 2) {
            ctx.status(400).json(Map.of("error", "resolution must be >= 2"));
            return;
        }

        // Defaults
        if (algorithm == null || algorithm.isBlank()) algorithm = "topological";
        if (outputType == null || outputType.isBlank()) outputType = "ldraw";
        if (colorMode == null || colorMode.isBlank()) colorMode = ext.equals("glb") ? "glb-color" : "none";
        if (colorAlgorithm == null || colorAlgorithm.isBlank()) colorAlgorithm = "direct";

        // Force color off for OBJ inputs
        if ("obj".equals(ext)) colorMode = "none";

        // Set up job temp directory
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Path jobDir = TEMP_ROOT.resolve(jobId);
        Files.createDirectories(jobDir);

        // Save uploaded file
        Path inputFile = jobDir.resolve("input." + ext);
        try (InputStream is = upload.content()) {
            Files.copy(is, inputFile, StandardCopyOption.REPLACE_EXISTING);
        }

        String outputExt = "ldraw".equals(outputType) ? "ldr" : "obj";
        Path outputFile = jobDir.resolve("output." + outputExt);

        JobState state = new JobState(
            jobId, inputFile, outputFile, outputExt,
            resolution, algorithm, outputType, colorMode, colorAlgorithm
        );
        jobs.put(jobId, state);

        pipelinePool.submit(() -> runPipeline(state));

        ctx.status(202).json(Map.of("jobId", jobId));
    }

    private static void handleJobStatus(Context ctx) {
        String jobId = ctx.pathParam("jobId");
        JobState state = jobs.get(jobId);
        if (state == null) {
            ctx.status(404).json(Map.of("error", "Job not found"));
            return;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", state.jobId);
        response.put("status", state.status.name().toLowerCase());
        response.put("stage", state.stage);

        if (state.status == JobState.Status.DONE && state.result != null) {
            response.put("stats", buildStats(state.result));
            response.put("outputFilename", "output." + state.outputExtension);
        } else {
            response.put("stats", null);
            response.put("outputFilename", null);
        }

        if (state.status == JobState.Status.ERROR) {
            response.put("error", state.error);
        }

        ctx.json(response);
    }

    private static void handleDownload(Context ctx) throws IOException {
        String jobId = ctx.pathParam("jobId");
        JobState state = jobs.get(jobId);
        if (state == null || state.status != JobState.Status.DONE) {
            ctx.status(404).json(Map.of("error", "Job not found or not complete"));
            return;
        }
        if (!Files.exists(state.outputFile)) {
            ctx.status(404).json(Map.of("error", "Output file not found"));
            return;
        }

        String contentType = "ldr".equals(state.outputExtension)
            ? "text/plain"
            : "application/octet-stream";
        String disposition = "attachment; filename=\"output." + state.outputExtension + "\"";

        ctx.contentType(contentType);
        ctx.header("Content-Disposition", disposition);
        ctx.result(Files.newInputStream(state.outputFile));
    }

    // -------------------------------------------------------------------------
    // Pipeline execution
    // -------------------------------------------------------------------------

    private static void runPipeline(JobState state) {
        state.status = JobState.Status.RUNNING;
        state.stage = "loading";

        try {
            ModelLoader loader = state.inputFile.toString().endsWith(".glb")
                ? new GlbLoader()
                : new ObjModelLoader();

            VoxelizationStrategy strategy = VoxelizationStrategy.fromCliValue(state.algorithm);

            PipelineRequest request = new PipelineRequest(
                state.inputFile,
                state.resolution,
                state.outputFile,
                state.outputType,
                strategy,
                state.colorMode,
                16,       // colorFallback: default LDraw color
                false,    // colorList
                state.colorAlgorithm,
                false,    // analyzeStepping
                null,     // analysisDir
                25,       // largeJumpThreshold
                Collections.emptyList(), // sweepResolutions
                false,    // benchmarkAb
                null,     // benchmarkDir
                null,     // ldrawLibraryDir
                null      // geometryMaskCacheDir
            );

            ColorStrategyRegistry strategyRegistry = ColorStrategyRegistry.createDefault();
            CsvCatalogPartRepository catalog = new CsvCatalogPartRepository();
            CsvPaletteRepository palette = new CsvPaletteRepository();

            PipelineResult result = PipelineRunner.runForApi(
                request, loader, strategyRegistry, catalog, palette,
                stage -> state.stage = stage
            );

            state.result = result;
            state.stage = "complete";
            state.status = JobState.Status.DONE;

        } catch (IOException e) {
            state.error = "File error: " + e.getMessage();
            state.stage = "error";
            state.status = JobState.Status.ERROR;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            state.error = e.getMessage();
            state.stage = "error";
            state.status = JobState.Status.ERROR;
        } catch (Exception e) {
            state.error = "Unexpected error: " + e.getMessage();
            state.stage = "error";
            state.status = JobState.Status.ERROR;
        }
    }

    // -------------------------------------------------------------------------
    // Response helpers
    // -------------------------------------------------------------------------

    private static Map<String, Object> buildStats(PipelineResult result) {
        int brickCount = result.bricks().size();
        int surfaceVoxels = result.surfaceVoxels();
        double reduction = surfaceVoxels > 0
            ? Math.round((1.0 - (double) brickCount / surfaceVoxels) * 1000.0) / 10.0
            : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("triangleCount", result.triangleCount());
        stats.put("resolution", result.resolution());
        stats.put("totalVoxels", result.totalVoxels());
        stats.put("solidVoxels", result.solidVoxels());
        stats.put("surfaceVoxels", surfaceVoxels);
        stats.put("brickCount", brickCount);
        stats.put("reductionPercent", reduction);
        stats.put("placementPolicy", result.placementPolicyName());
        stats.put("brickTypes", buildBrickTypes(result));
        stats.put("colorInfo", buildColorInfo(result));
        return stats;
    }

    private static List<Map<String, Object>> buildBrickTypes(PipelineResult result) {
        // Count bricks by partId
        Map<String, Long> counts = result.bricks().stream()
            .collect(Collectors.groupingBy(Brick::partId, Collectors.counting()));

        // Build partId → name lookup from allowedSpecs
        Map<String, String> names = result.allowedSpecs().stream()
            .collect(Collectors.toMap(BrickSpec::partId, BrickSpec::name, (a, b) -> a));

        List<Map<String, Object>> rows = new ArrayList<>();
        counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("partId", entry.getKey());
                row.put("name", names.getOrDefault(entry.getKey(), entry.getKey()));
                row.put("count", entry.getValue().intValue());
                rows.add(row);
            });
        return rows;
    }

    private static Map<String, Object> buildColorInfo(PipelineResult result) {
        if (result.brickColorCodes() == null) return null;

        Map<String, Object> color = new LinkedHashMap<>();
        color.put("coloredBrickCount", result.coloredBrickCount());
        color.put("totalBrickCount", result.bricks().size());
        color.put("opaquePaletteEntries", result.opaquePaletteEntries());
        color.put("colorAlgorithm", result.colorAlgorithmName());
        color.put("smoothedCount", result.smoothedCount());
        return color;
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    private static void cleanupExpiredJobs() {
        long cutoff = System.currentTimeMillis() - JOB_TTL_MS;
        jobs.entrySet().removeIf(entry -> {
            JobState state = entry.getValue();
            if (state.createdAtMs < cutoff) {
                deleteJobDir(TEMP_ROOT.resolve(state.jobId));
                return true;
            }
            return false;
        });
    }

    private static void deleteJobDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a)) // deepest first
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }
}
