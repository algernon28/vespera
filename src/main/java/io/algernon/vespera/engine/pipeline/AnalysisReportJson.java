package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shape the analysis reports write their numbers in (ADR-362 section 9): {@code format}, {@code analysisReport},
 * {@code title}, {@code writtenAt}, {@code headline} and {@code sections}, written a row at a time where the page
 * streams its rows, so that no class holds more for the JSON than it holds for the page (ADR-220).
 *
 * <p>It also reads a JSON's {@code headline} back for {@code report.json}, which copies the headline and no row
 * (ADR-362 section 3), refusing a name whose format this jar does not know ({@link #FORMATS}).
 */
final class AnalysisReportJson {

    /** The {@code format} each analysis report's JSON is written in, by its name; a ticket of step 5 adds its own. */
    static final Map<String, Integer> FORMATS = Map.of(
            "extraction-failures", 1,
            "extraction-survivors", 1);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final DateTimeFormatter INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private AnalysisReportJson() {}

    /** One number of the headline: its key, its label, its value and its unit, one of count, share, bytes, seconds and number. */
    record Figure(String key, String label, long value, String unit) {}

    /** One column of a table: its id, its label and its kind. */
    record Column(String id, String label, String kind) {}

    /** The one section of a report whose page is a paragraph and a table of rows: its id, title and paragraphs, and its table's columns. */
    record Section(String id, String title, List<String> paragraphs, List<Column> columns) {}

    /** Hands each row, a list of cells in the columns' order, to the consumer it is given, once. */
    @FunctionalInterface
    interface Rows {
        void handOver(Consumer<List<Object>> row) throws IOException;
    }

    /** The instant as the reports write it, ISO 8601 in UTC with {@code Z}, to the millisecond. */
    static String instant(Instant instant) {
        return INSTANT.format(instant);
    }

    /**
     * Writes the report whole to {@code out}: the rows as {@code eachRow} hands them over, each a list of cells in the
     * columns' order, and {@code table} {@code null} where {@code withTable} is false.
     *
     * @throws IOException if {@code out} refuses a write, including while {@code eachRow} is handing over rows
     */
    static void write(
            Writer out,
            String name,
            int format,
            String title,
            Instant writtenAt,
            List<Figure> headline,
            Section section,
            boolean withTable,
            Rows eachRow)
            throws IOException {
        out.write("{\"format\":" + format + ",\"analysisReport\":" + quoted(name) + ",\"title\":" + quoted(title)
                + ",\"writtenAt\":" + quoted(instant(writtenAt)) + ",\"headline\":[");
        for (int i = 0; i < headline.size(); i++) {
            Figure figure = headline.get(i);
            out.write((i == 0 ? "" : ",") + "{\"key\":" + quoted(figure.key()) + ",\"label\":" + quoted(figure.label())
                    + ",\"value\":" + figure.value() + ",\"unit\":" + quoted(figure.unit()) + "}");
        }
        out.write("],\"sections\":[{\"id\":" + quoted(section.id()) + ",\"title\":" + quoted(section.title())
                + ",\"text\":[");
        for (int i = 0; i < section.paragraphs().size(); i++) {
            out.write((i == 0 ? "" : ",") + quoted(section.paragraphs().get(i)));
        }
        out.write("],\"chart\":null,\"table\":");
        if (!withTable) {
            out.write("null");
        } else {
            out.write("{\"columns\":[");
            for (int i = 0; i < section.columns().size(); i++) {
                Column column = section.columns().get(i);
                out.write((i == 0 ? "" : ",") + "{\"id\":" + quoted(column.id()) + ",\"label\":" + quoted(column.label())
                        + ",\"kind\":" + quoted(column.kind()) + "}");
            }
            out.write("],\"rows\":[");
            boolean[] first = {true};
            try {
                eachRow.handOver(row -> {
                    try {
                        out.write(first[0] ? "[" : ",[");
                        first[0] = false;
                        for (int i = 0; i < row.size(); i++) {
                            out.write((i == 0 ? "" : ",") + cell(row.get(i)));
                        }
                        out.write("]");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            out.write("]}");
        }
        out.write("}]}\n");
    }

    private static String cell(Object cell) {
        return switch (cell) {
            case null -> "null";
            case String text -> quoted(text);
            case Number number -> Long.toString(number.longValue());
            default -> throw new IllegalArgumentException("not a cell: " + cell.getClass().getName());
        };
    }

    private static String quoted(String text) {
        return JSON.writeValueAsString(text);
    }

    /**
     * The {@code headline} of {@code json} as a tree for {@code report.json}, or {@code null} where the file is not
     * there, cannot be read, is not that report's or is of a format this jar does not know (ADR-362 section 3).
     */
    static List<Object> headlineOf(String name, Path json) {
        Integer known = FORMATS.get(name);
        if (known == null || !Files.isRegularFile(json)) {
            return null;
        }
        try {
            JsonNode report = JSON.readTree(Files.readString(json, java.nio.charset.StandardCharsets.UTF_8));
            if (!report.path("format").isIntegralNumber() || report.get("format").asInt() != known
                    || !name.equals(report.path("analysisReport").asString(null))
                    || !report.path("headline").isArray()) {
                return null;
            }
            List<Object> headline = new ArrayList<>();
            for (JsonNode figure : report.get("headline")) {
                Map<String, Object> copy = new LinkedHashMap<>();
                copy.put("key", figure.path("key").asString(""));
                copy.put("label", figure.path("label").asString(""));
                JsonNode value = figure.path("value");
                copy.put("value", value.isIntegralNumber() ? (Object) value.asLong() : (Object) value.asDouble());
                copy.put("unit", figure.path("unit").asString(""));
                headline.add(copy);
            }
            return headline;
        } catch (IOException | RuntimeException cannotBeRead) {
            return null;
        }
    }
}
