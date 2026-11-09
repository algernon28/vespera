package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shape the analysis reports of {@code pipeline} share when they write what their page shows as JSON beside it
 * (ADR-362 section 9): {@code format}, {@code analysisReport}, {@code title}, {@code writtenAt}, {@code headline}
 * and {@code sections}, built here from typed values, written whole into {@code .part} and moved into place, and
 * read back, by the invocation report, for its {@code headline} alone.
 *
 * <p>It holds no rule of what a report says: each report's class supplies its own numbers, sections and rows.
 */
final class AnalysisReportJson {

    /** The ending of a report's JSON, beside its page. */
    static final String ENDING = ".json";

    private static final DateTimeFormatter INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AnalysisReportJson() {}

    /** One number of a headline: a {@code count}, {@code share}, {@code bytes}, {@code seconds} or {@code number}. */
    static Map<String, Object> figure(String key, String label, Number value, String unit) {
        Map<String, Object> figure = new LinkedHashMap<>();
        figure.put("key", key);
        figure.put("label", label);
        figure.put("value", value);
        figure.put("unit", unit);
        return figure;
    }

    /** One column of a table: its id, its label and its kind. */
    static Map<String, Object> column(String id, String label, String kind) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("id", id);
        column.put("label", label);
        column.put("kind", kind);
        return column;
    }

    /** A table of {@code columns} and {@code rows}, each row a list of cells in the columns' order. */
    static Map<String, Object> table(List<Map<String, Object>> columns, List<List<Object>> rows) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("columns", new ArrayList<Object>(columns));
        table.put("rows", new ArrayList<Object>(rows));
        return table;
    }

    /** One part of the page: its id, title and paragraphs, and its chart and table where it has them, else {@code null}. */
    static Map<String, Object> section(
            String id, String title, List<String> text, Map<String, Object> chart, Map<String, Object> table) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("id", id);
        section.put("title", title);
        section.put("text", new ArrayList<Object>(text));
        section.put("chart", chart);
        section.put("table", table);
        return section;
    }

    /** The JSON text of one report. */
    static String render(
            String analysisReport,
            int format,
            String title,
            Instant writtenAt,
            List<Map<String, Object>> headline,
            List<Map<String, Object>> sections) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("format", (long) format);
        report.put("analysisReport", analysisReport);
        report.put("title", title);
        report.put("writtenAt", INSTANT.format(writtenAt.truncatedTo(ChronoUnit.MILLIS)));
        report.put("headline", new ArrayList<Object>(headline));
        report.put("sections", new ArrayList<Object>(sections));
        return InvocationReport.Json.render(report) + "\n";
    }

    /**
     * Writes {@code text} whole into {@code place}'s {@code .part} and moves it over {@code place} (ADR-223 section 3);
     * where either fails the {@code .part} is taken away and the failure is the caller's.
     */
    static void write(Path place, String text) throws IOException {
        Path part = place.resolveSibling(place.getFileName() + ".part");
        try {
            Files.writeString(part, text, StandardCharsets.UTF_8);
            ReplacingMove.ATOMIC.intoPlace(part, place);
        } catch (IOException | RuntimeException failed) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException | RuntimeException ignored) {
                // the next write overwrites it
            }
            throw failed;
        }
    }

    /**
     * The {@code headline} of the JSON at {@code json}, as the invocation report copies it, or {@code null} where it
     * is not there, cannot be read or is not of {@code knownFormat}.
     */
    static List<Object> headlineIn(Path json, int knownFormat) {
        try {
            if (!Files.isRegularFile(json)) {
                return null;
            }
            JsonNode tree = MAPPER.readTree(Files.readString(json, StandardCharsets.UTF_8));
            if (!tree.isObject() || !tree.has("format") || tree.get("format").asLong(-1) != knownFormat
                    || !tree.has("headline") || !tree.get("headline").isArray()) {
                return null;
            }
            List<Object> headline = new ArrayList<>();
            for (JsonNode each : tree.get("headline")) {
                Map<String, Object> figure = new LinkedHashMap<>();
                figure.put("key", each.get("key").asString());
                figure.put("label", each.get("label").asString());
                JsonNode value = each.get("value");
                figure.put("value", value.isIntegralNumber() ? (Object) value.asLong() : (Object) value.asDouble());
                figure.put("unit", each.get("unit").asString());
                headline.add(figure);
            }
            return headline;
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }
}
