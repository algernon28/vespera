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
 * The shape the analysis reports share when they write their numbers as JSON beside their page (ADR-362 section 9):
 * {@code format}, {@code analysisReport}, {@code title}, {@code writtenAt}, {@code headline} and {@code sections}.
 * It builds the tree from typed values, writes it through {@code .part} and {@link ReplacingMove}, and reads back the
 * headline {@code report.json} copies. It decides no number: each writer hands it the values its page is drawn from.
 */
final class AnalysisReportJson {

    private AnalysisReportJson() {}

    /** The format of every analysis report's JSON, raised by a change that removes, renames or retypes a field. */
    static final int FORMAT = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** One headline number: {@code unit} is one of count, share, bytes, seconds and number. */
    record Figure(String key, String label, Number value, String unit) {}

    /** One column of a table: {@code kind} is one of text, count, share, number, bytes, seconds, instant, path and analysisReport. */
    record Column(String id, String label, String kind) {}

    /** A chart drawn from a table's columns: {@code kind} is bars or lines, {@code x} a column's id, {@code series} column ids. */
    record Chart(String kind, String x, List<String> series) {}

    /** One part of the page, in the page's order; {@code chart} and the table's {@code columns} are null and empty where it has none. */
    record Section(String id, String title, List<String> text, Chart chart, List<Column> columns, List<List<Object>> rows) {

        /** A part that is prose alone. */
        static Section prose(String id, String title, List<String> text) {
            return new Section(id, title, text, null, List.of(), List.of());
        }
    }

    /** The tree of one report, ready to render. */
    static Map<String, Object> tree(
            String name, String title, Instant writtenAt, List<Figure> headline, List<Section> sections) {
        List<Object> figures = new ArrayList<>();
        for (Figure figure : headline) {
            figures.add(InvocationReport.object(
                    "key", figure.key(), "label", figure.label(), "value", figure.value(), "unit", figure.unit()));
        }
        List<Object> parts = new ArrayList<>();
        for (Section section : sections) {
            Object chart = section.chart() == null
                    ? null
                    : InvocationReport.object(
                            "kind", section.chart().kind(),
                            "x", section.chart().x(),
                            "series", new ArrayList<Object>(section.chart().series()));
            Object table = null;
            if (!section.columns().isEmpty()) {
                List<Object> columns = new ArrayList<>();
                for (Column column : section.columns()) {
                    columns.add(InvocationReport.object("id", column.id(), "label", column.label(), "kind", column.kind()));
                }
                List<Object> rows = new ArrayList<>();
                for (List<Object> row : section.rows()) {
                    rows.add(new ArrayList<Object>(row));
                }
                table = InvocationReport.object("columns", columns, "rows", rows);
            }
            parts.add(InvocationReport.object(
                    "id", section.id(),
                    "title", section.title(),
                    "text", new ArrayList<Object>(section.text()),
                    "chart", chart,
                    "table", table));
        }
        return InvocationReport.object(
                "format", FORMAT,
                "analysisReport", name,
                "title", title,
                "writtenAt", writtenAt.truncatedTo(ChronoUnit.MILLIS).toString(),
                "headline", figures,
                "sections", parts);
    }

    /**
     * Writes {@code <name>.json} in {@code directory} through {@code .part}, moved over the earlier one whole, so that
     * a reader sees the earlier file or this one. Throws where it cannot, leaving the earlier file as it was.
     */
    static void write(Path directory, String name, Map<String, Object> tree) throws IOException {
        Files.createDirectories(directory);
        Path part = directory.resolve(name + ".json.part");
        try {
            Files.writeString(part, InvocationReport.Json.render(tree) + "\n", StandardCharsets.UTF_8);
            ReplacingMove.ATOMIC.intoPlace(part, directory.resolve(name + ".json"));
        } catch (IOException | RuntimeException refused) {
            Files.deleteIfExists(part);
            throw refused;
        }
    }

    /**
     * The headline of {@code <name>.json} in {@code directory}, as a list of {@code key}, {@code label}, {@code value}
     * and {@code unit} maps; null where the file is not there, cannot be read or is of a format this jar does not know.
     */
    static List<Object> headlineOf(Path directory, String name) {
        try {
            JsonNode root = MAPPER.readTree(Files.readString(directory.resolve(name + ".json"), StandardCharsets.UTF_8));
            if (root.path("format").asInt(-1) != FORMAT || !root.path("headline").isArray()) {
                return null;
            }
            List<Object> figures = new ArrayList<>();
            for (JsonNode figure : root.get("headline")) {
                JsonNode value = figure.get("value");
                figures.add(InvocationReport.object(
                        "key", figure.path("key").asString(),
                        "label", figure.path("label").asString(),
                        "value", value.isIntegralNumber() ? (Number) value.longValue() : (Number) value.doubleValue(),
                        "unit", figure.path("unit").asString()));
            }
            return figures;
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }
}
