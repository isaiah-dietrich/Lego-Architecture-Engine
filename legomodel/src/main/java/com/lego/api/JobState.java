package com.lego.api;

import java.nio.file.Path;

import com.lego.cli.PipelineResult;

/**
 * Mutable state for a single conversion job.
 * Fields written by the pipeline thread, read by HTTP handler threads.
 * Volatile fields are sufficient — no field is read-modify-written outside
 * the single worker thread that owns this job.
 */
public final class JobState {

    public enum Status { QUEUED, RUNNING, DONE, ERROR }

    public final String jobId;
    public final Path inputFile;
    public final Path outputFile;
    public final String outputExtension;   // "ldr" or "obj"
    public final long createdAtMs;

    // Pipeline parameters (immutable after construction)
    public final int resolution;
    public final String algorithm;
    public final String outputType;
    public final String colorMode;
    public final String colorAlgorithm;

    // Mutable job progress (written by worker, read by handler)
    public volatile Status status = Status.QUEUED;
    public volatile String stage = "queued";
    public volatile PipelineResult result = null;
    public volatile String error = null;

    public JobState(
        String jobId,
        Path inputFile,
        Path outputFile,
        String outputExtension,
        int resolution,
        String algorithm,
        String outputType,
        String colorMode,
        String colorAlgorithm
    ) {
        this.jobId = jobId;
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.outputExtension = outputExtension;
        this.createdAtMs = System.currentTimeMillis();
        this.resolution = resolution;
        this.algorithm = algorithm;
        this.outputType = outputType;
        this.colorMode = colorMode;
        this.colorAlgorithm = colorAlgorithm;
    }
}
