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
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * Utility class to clean data from rebrickable.com.
 */
public class Cleaning {

    private static final Pattern DIMENSION_PATTERN = Pattern.compile(
        "(?i)(?<![A-Za-z0-9])(\\d+)\\s*x\\s*(\\d+)(?:\\s*x\\s*(\\d+/\\d+|\\d+))?(?!\\s*[xX])(?![A-Za-z0-9/])"
    );
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^[1-9]\\d*$");
    private static final Pattern FRACTION_PATTERN = Pattern.compile("^([1-9]\\d*)/([1-9]\\d*)$");

    private static final int PREVIEW_LIMIT = 10;
    private static final String REJECT_REASON = "reject_reason";

    private static final String[] CATALOG_HEADERS = {
        "part_id",
        "name",
        "category",
        "stud_x",
        "stud_y",
        "height_units",
        "material",
        "active"
    };

    static final class ParsedDimensions {
        private final int studX;
        private final int studY;
        private final String heightUnits;

        ParsedDimensions(int studX, int studY, String heightUnits) {
            this.studX = studX;
            this.studY = studY;
            this.heightUnits = heightUnits;
        }

        int studX() {
            return studX;
        }

        int studY() {
            return studY;
        }

        String heightUnits() {
            return heightUnits;
        }
    }

    public static void main(String[] args) {
        Cleaning cleaning = new Cleaning();

        Path inputPath = resolveFilteredInputPath();
        Path catalogPath = resolveCatalogOutputPath(inputPath);
        Path rejectedPath = resolveRejectedOutputPath(inputPath);

        if (inputPath == null || catalogPath == null || rejectedPath == null) {
            System.err.println("Could not resolve input/output paths from working directory.");
            System.exit(1);
            return;
        }

        try {
            cleaning.buildCatalog(inputPath.toFile(), catalogPath.toFile(), rejectedPath.toFile());
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("CSV cleaning failed: " + e.getMessage());
            System.exit(1);
        }
    }

    public void buildCatalog(File input, File catalogOutput, File rejectedOutput) throws IOException {
        Path inputPath = input.toPath();
        Path catalogPath = catalogOutput.toPath();
        Path rejectedPath = rejectedOutput.toPath();

        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input CSV does not exist: " + inputPath.toAbsolutePath());
        }

        if (catalogPath.getParent() != null) {
            Files.createDirectories(catalogPath.getParent());
        }
        if (rejectedPath.getParent() != null) {
            Files.createDirectories(rejectedPath.getParent());
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

        int totalRows = 0;
        int parsedRows = 0;
        int rejectedRows = 0;

        List<String> keptNames = new ArrayList<>();
        List<String> rejectedNames = new ArrayList<>();

        try (
            Reader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
            CSVParser parser = format.parse(reader);
            Writer catalogWriter = Files.newBufferedWriter(catalogPath, StandardCharsets.UTF_8);
            Writer rejectedWriter = Files.newBufferedWriter(rejectedPath, StandardCharsets.UTF_8);
            CSVPrinter catalogPrinter = new CSVPrinter(
                catalogWriter,
                CSVFormat.DEFAULT.builder().setHeader(CATALOG_HEADERS).build()
            );
            CSVPrinter rejectedPrinter = new CSVPrinter(
                rejectedWriter,
                CSVFormat.DEFAULT.builder().setHeader(buildRejectedHeaders(parser.getHeaderNames())).build()
            )
        ) {
            List<String> headers = parser.getHeaderNames();
            String partNumHeader = resolveHeader(headers, "part_num");
            String nameHeader = resolveHeader(headers, "name");
            String categoryHeader = resolveHeader(headers, "part_cat_id");
            String materialHeader = resolveHeader(headers, "part_material");

            for (CSVRecord record : parser) {
                totalRows++;
                String name = record.get(nameHeader);

                ParsedDimensions parsed = parseDimensions(name);
                if (parsed != null) {
                    parsedRows++;
                    keptNames.add(name);

                    catalogPrinter.printRecord(
                        record.get(partNumHeader),
                        name,
                        record.get(categoryHeader),
                        parsed.studX(),
                        parsed.studY(),
                        parsed.heightUnits(),
                        record.get(materialHeader),
                        true
                    );
                } else {
                    rejectedRows++;
                    rejectedNames.add(name);
                    rejectedPrinter.printRecord(buildRejectedRecord(headers, record, "invalid or missing dimensions"));
                }
            }
        }

        boolean valid = parsedRows + rejectedRows == totalRows;
        double successPercent = totalRows == 0 ? 0.0 : (parsedRows * 100.0 / totalRows);

        System.out.println("Summary:");
        System.out.println("- total input rows: " + totalRows);
        System.out.println("- parsed successfully: " + parsedRows);
        System.out.println("- rejected rows: " + rejectedRows);
        System.out.println("- success %: " + String.format("%.2f", successPercent));
        System.out.println("- parts catalog path: " + catalogPath.toAbsolutePath());
        System.out.println("- rejected rows path: " + rejectedPath.toAbsolutePath());
        System.out.println("- verification (parsed + rejected == total): " + valid);
        printPreview("First 10 kept names:", keptNames);
        printPreview("First 10 rejected names:", rejectedNames);

        if (!valid) {
            throw new IllegalStateException("Row count mismatch: parsed + rejected != total");
        }
    }

    static ParsedDimensions parseDimensions(String name) {
        if (name == null) {
            return null;
        }

        java.util.regex.Matcher matcher = DIMENSION_PATTERN.matcher(name);
        if (!matcher.find()) {
            return null;
        }

        int studX = Integer.parseInt(matcher.group(1));
        int studY = Integer.parseInt(matcher.group(2));
        String heightUnits = matcher.group(3) == null ? "1" : matcher.group(3);

        if (studX <= 0 || studY <= 0) {
            return null;
        }

        if (!isValidHeightUnits(heightUnits)) {
            return null;
        }

        return new ParsedDimensions(studX, studY, heightUnits);
    }

    static boolean isValidHeightUnits(String heightUnits) {
        if (heightUnits == null || heightUnits.isBlank()) {
            return false;
        }
        if (INTEGER_PATTERN.matcher(heightUnits).matches()) {
            return true;
        }

        java.util.regex.Matcher fractionMatch = FRACTION_PATTERN.matcher(heightUnits);
        if (!fractionMatch.matches()) {
            return false;
        }

        int numerator = Integer.parseInt(fractionMatch.group(1));
        int denominator = Integer.parseInt(fractionMatch.group(2));
        return numerator > 0 && denominator > 0;
    }

    private static String[] buildRejectedHeaders(List<String> sourceHeaders) {
        String[] headers = new String[sourceHeaders.size() + 1];
        for (int i = 0; i < sourceHeaders.size(); i++) {
            headers[i] = sourceHeaders.get(i);
        }
        headers[sourceHeaders.size()] = REJECT_REASON;
        return headers;
    }

    private static List<String> buildRejectedRecord(
        List<String> sourceHeaders,
        CSVRecord record,
        String reason
    ) {
        List<String> values = new ArrayList<>(sourceHeaders.size() + 1);
        for (String header : sourceHeaders) {
            values.add(record.get(header));
        }
        values.add(reason);
        return values;
    }

    private static String resolveHeader(List<String> headers, String expectedName) {
        for (String header : headers) {
            if (expectedName.equalsIgnoreCase(header)) {
                return header;
            }
        }
        throw new IllegalArgumentException("CSV must contain a '" + expectedName + "' header column.");
    }

    private static void printPreview(String label, List<String> names) {
        System.out.println();
        System.out.println(label);
        int limit = Math.min(PREVIEW_LIMIT, names.size());
        for (int i = 0; i < limit; i++) {
            System.out.println((i + 1) + ". " + names.get(i));
        }
    }

    static Path resolvePreferredPath(Path cwd, String directPath, String nestedPath) {
        Path direct = cwd.resolve(directPath);
        if (Files.exists(direct)) {
            return direct;
        }

        Path nested = cwd.resolve(nestedPath);
        if (Files.exists(nested)) {
            return nested;
        }

        return null;
    }

    static Path resolveFilteredInputPath() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return resolvePreferredPath(cwd, "data/parts_dimension_filtered.csv", "legomodel/data/parts_dimension_filtered.csv");
    }

    static Path resolveCatalogOutputPath(Path inputPath) {
        if (inputPath == null) {
            return null;
        }
        return inputPath.getParent().resolve("parts_catalog_v1.csv");
    }

    static Path resolveRejectedOutputPath(Path inputPath) {
        if (inputPath == null) {
            return null;
        }
        return inputPath.getParent().resolve("rejected_rows.csv");
    }
}