package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON an analysis report writes beside its page, in the one shape the twelve share (ADR-362 section 9): {@code
 * format}, {@code analysisReport}, {@code title}, {@code writtenAt}, {@code headline} and {@code sections}. It builds
 * the tree from the values the page is drawn from, writes it through {@code .part} and {@link ReplacingMove}, and reads
 * a headline back for {@code report.json} (section 3), which copies the headline and no row. It holds no rule: what a
 * report says is its writer's.
 */
final class AnalysisReportJson {

    /** The format every analysis report's JSON is written in so far; a reader refuses another (ADR-362 section 3). */
    static final long FORMAT = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AnalysisReportJson() {}

    /** One figure of a headline: numbers alone, in one of {@code count}, {@code share}, {@code bytes}, {@code seconds} and {@code number}. */
    static Map<String, Object> figure(String key, String label, Number value, String unit) {
        return InvocationReport.object("key", key, "label", label, "value", value, "unit", unit);
    }

    /** One column of a table, of a kind the schemas list. */
    static Map<String, Object> column(String id, String label, String kind) {
        return InvocationReport.object("id", id, "label", label, "kind", kind);
    }

    /** A table of {@code columns} over {@code rows}, each row a list of cells in the columns' order. */
    static Map<String, Object> table(List<Map<String, Object>> columns, List<List<Object>> rows) {
        return InvocationReport.object("columns", new ArrayList<Object>(columns), "rows", new ArrayList<Object>(rows));
    }

    /** One bar chart over a column of the table, which draws the columns {@code series}. */
    static Map<String, Object> bars(String x, List<String> series) {
        return InvocationReport.object("kind", "bars", "x", x, "series", new ArrayList<Object>(series));
    }

    /** One section; {@code chart} and {@code table} are null where it has none. */
    static Map<String, Object> section(
            String id, String title, List<String> text, Map<String, Object> chart, Map<String, Object> table) {
        return InvocationReport.object(
                "id", id, "title", title, "text", new ArrayList<Object>(text), "chart", chart, "table", table);
    }

    /** The whole tree of one analysis report. */
    static Map<String, Object> tree(
            String name,
            String title,
            Instant writtenAt,
            List<Map<String, Object>> headline,
            List<Map<String, Object>> sections) {
        return InvocationReport.object(
                "format", FORMAT,
                "analysisReport", name,
                "title", title,
                "writtenAt", writtenAt.truncatedTo(ChronoUnit.MILLIS).toString(),
                "headline", new ArrayList<Object>(headline),
                "sections", new ArrayList<Object>(sections));
    }

    /**
     * Writes {@code tree} as {@code target}, whole, through {@code .part} and a replacing move, so that a reader
     * never sees it half written (ADR-223 section 3).
     *
     * @throws IOException where it cannot be written or moved
     */
    static void write(Path target, Map<String, Object> tree) throws IOException {
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.writeString(part, InvocationReport.Json.render(tree) + "\n", StandardCharsets.UTF_8);
        ReplacingMove.ATOMIC.intoPlace(part, target);
    }

    /**
     * The headline of the JSON at {@code json}, each figure as {@code report.json} copies it, or null where there is
     * no such file, it cannot be read, is not of the report {@code name} or is of a format the jar does not know.
     */
    static List<Object> headlineOf(Path json, String name) {
        try {
            if (!Files.isRegularFile(json)) {
                return null;
            }
            JsonNode tree = MAPPER.readTree(Files.readString(json, StandardCharsets.UTF_8));
            if (!tree.isObject()
                    || !tree.path("format").isIntegralNumber()
                    || tree.get("format").asLong() != FORMAT
                    || !name.equals(tree.path("analysisReport").asString(null))
                    || !tree.path("headline").isArray()) {
                return null;
            }
            List<Object> figures = new ArrayList<>();
            for (JsonNode figure : tree.get("headline")) {
                JsonNode value = figure.path("value");
                if (!value.isNumber()) {
                    return null;
                }
                figures.add(InvocationReport.object(
                        "key", figure.path("key").asString(""),
                        "label", figure.path("label").asString(""),
                        "value", value.isIntegralNumber() ? (Object) value.asLong() : (Object) value.asDouble(),
                        "unit", figure.path("unit").asString("")));
            }
            return figures;
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }
}
