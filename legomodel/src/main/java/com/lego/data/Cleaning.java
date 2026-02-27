package com.lego.data;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * Utility class to clean data from rebrickable.com.
 */
public class Cleaning {

    private static final Pattern SIZE_PATTERN = Pattern.compile(
        "(?i)(?<![A-Za-z0-9])\\d+\\s*x\\s*\\d+(?:\\s*x\\s*(?:\\d+|\\d+/\\d+))?(?![A-Za-z0-9])"
    );

    private static final int PREVIEW_LIMIT = 10;

    public static void main(String[] args) {
        Cleaning cleaning = new Cleaning();

        Path inputPath = resolvePath("data/parts.csv", "legomodel/data/parts.csv");
        Path outputPath = resolvePath(
            "data/parts_dimension_filtered.csv",
            "legomodel/data/parts_dimension_filtered.csv"
        );

        if (inputPath == null || outputPath == null) {
            System.err.println("Could not resolve input/output paths from working directory.");
            System.exit(1);
            return;
        }

        try {
            cleaning.clean(inputPath.toFile(), outputPath.toFile());
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("CSV cleaning failed: " + e.getMessage());
            System.exit(1);
        }
    }

    public void clean(File input, File output) throws IOException {
        Path inputPath = input.toPath();
        Path outputPath = output.toPath();

        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input CSV does not exist: " + inputPath.toAbsolutePath());
        }

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

        List<String> headers;
        List<Map<String, String>> keptRows = new ArrayList<>();
        List<String> keptNames = new ArrayList<>();
        List<String> removedNames = new ArrayList<>();

        int totalRows = 0;

        try (
            Reader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
            CSVParser parser = format.parse(reader)
        ) {
            headers = parser.getHeaderNames();
            String nameHeader = resolveNameHeader(headers);

            for (CSVRecord record : parser) {
                totalRows++;

                String name = record.get(nameHeader);
                if (containsSizeToken(name)) {
                    keptRows.add(copyRecord(headers, record));
                    keptNames.add(name);
                } else {
                    removedNames.add(name);
                }
            }
        }

        writeOutput(outputPath, headers, keptRows);

        int keptRowsCount = keptRows.size();
        int removedRowsCount = removedNames.size();
        boolean valid = keptRowsCount + removedRowsCount == totalRows;

        System.out.println("Summary:");
        System.out.println("- total input rows: " + totalRows);
        System.out.println("- kept rows: " + keptRowsCount);
        System.out.println("- removed rows: " + removedRowsCount);
        System.out.println("- output path: " + outputPath.toAbsolutePath());
        System.out.println("- verification (kept + removed == total): " + valid);

        printPreview("First 10 kept names:", keptNames);
        printPreview("First 10 removed names:", removedNames);

        if (!valid) {
            throw new IllegalStateException("Row count mismatch: kept + removed != total");
        }
    }

    private static void writeOutput(
        Path outputPath,
        List<String> headers,
        List<Map<String, String>> keptRows
    ) throws IOException {
        CSVFormat outputFormat = CSVFormat.DEFAULT.builder()
            .setHeader(headers.toArray(String[]::new))
            .build();

        try (
            Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
            CSVPrinter printer = new CSVPrinter(writer, outputFormat)
        ) {
            for (Map<String, String> row : keptRows) {
                List<String> values = new ArrayList<>(headers.size());
                for (String header : headers) {
                    values.add(row.get(header));
                }
                printer.printRecord(values);
            }
        }
    }

    private static String resolveNameHeader(List<String> headers) {
        for (String header : headers) {
            if ("name".equalsIgnoreCase(header)) {
                return header;
            }
        }
        throw new IllegalArgumentException("CSV must contain a 'name' header column.");
    }

    private static Map<String, String> copyRecord(List<String> headers, CSVRecord record) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String header : headers) {
            row.put(header, record.get(header));
        }
        return row;
    }

    private static boolean containsSizeToken(String name) {
        return name != null && SIZE_PATTERN.matcher(name).find();
    }

    private static void printPreview(String label, List<String> names) {
        System.out.println();
        System.out.println(label);
        int limit = Math.min(PREVIEW_LIMIT, names.size());
        for (int i = 0; i < limit; i++) {
            System.out.println((i + 1) + ". " + names.get(i));
        }
    }

    private static Path resolvePath(String preferred, String fallback) {
        Path preferredPath = Paths.get(preferred);
        if (Files.exists(preferredPath) || startsWithDataDirectory(preferredPath)) {
            return preferredPath;
        }

        Path fallbackPath = Paths.get(fallback);
        if (Files.exists(fallbackPath) || startsWithDataDirectory(fallbackPath)) {
            return fallbackPath;
        }

        return null;
    }

    private static boolean startsWithDataDirectory(Path path) {
        Path normalized = path.normalize();
        if (normalized.getNameCount() == 0) {
            return false;
        }
        String first = normalized.getName(0).toString().toLowerCase(Locale.ROOT);
        return "data".equals(first) || "legomodel".equals(first);
    }
}