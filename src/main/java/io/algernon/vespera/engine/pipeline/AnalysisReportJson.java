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
 * The shape the analysis reports share when they write what their pages show as JSON, {@code <name>.json} beside
 * {@code <name>.html} (ADR-362 section 9): {@code format}, {@code analysisReport}, {@code title}, {@code writtenAt},
 * {@code headline} and {@code sections}. Each report's writer builds its {@link Document} from the values it draws its
 * page from, and {@link #write} puts it in place through {@code .part} and {@code ReplacingMove}; {@link #headlineOf}
 * is what {@code report.json} copies of it, the headline alone and nothing of a section.
 *
 * <p>It holds no rule: what a report says, and under which sections, is its writer's, and each report's schema in
 * {@code docs/report} names its headline's keys, its sections' ids and each table's columns.
 */
final class AnalysisReportJson {

    /** What a headline figure's value is counted in (ADR-362 section 9). */
    static final List<String> UNITS = List.of("count", "share", "bytes", "seconds", "number");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final DateTimeFormatter INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private AnalysisReportJson() {}

    /** One number the page states first: its {@code key}, {@code label}, {@code value} and {@code unit} from {@link #UNITS}. */
    record Figure(String key, String label, Number value, String unit) {}

    /** A column of a table: its {@code id}, {@code label} and {@code kind}, one of the kinds ADR-362 section 9 lists. */
    record Column(String id, String label, String kind) {}

    /** A table: its columns and its rows, each a list of cells in the columns' order. */
    record Table(List<Column> columns, List<List<Object>> rows) {}

    /** A chart drawn from the columns of its section's table: {@code kind} {@code bars} or {@code lines}, {@code x} a column's id. */
    record Chart(String kind, String x, List<String> series) {}

    /** A part of the page: its paragraphs, then its chart and its table where it has them. */
    record Section(String id, String title, List<String> text, Chart chart, Table table) {}

    /** One report's JSON, before it is written. */
    record Document(
            int format, String analysisReport, String title, Instant writtenAt, List<Figure> headline, List<Section> sections) {}

    /**
     * Writes {@code document} whole into {@code <file>.part} and moves it over {@code file}. It throws where either
     * fails, which is the caller's to handle as it handles its page's failure (ADR-362 section 9).
     */
    static void write(Path file, Document document) throws IOException {
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.createDirectories(file.toAbsolutePath().getParent());
        try {
            Files.writeString(part, MAPPER.writeValueAsString(tree(document)), StandardCharsets.UTF_8);
            ReplacingMove.ATOMIC.intoPlace(part, file);
        } catch (IOException | RuntimeException failed) {
            Files.deleteIfExists(part);
            throw failed;
        }
    }

    /**
     * The headline the JSON at {@code file} holds, as the trees {@code report.json} is written from: a list of
     * objects, {@code null} where the file is not there, cannot be read, is not that report's or is of a format other
     * than {@code format}.
     */
    static List<Object> headlineOf(Path file, String analysisReport, int format) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonNode report = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (!report.has("format") || report.get("format").asLong() != format
                    || !analysisReport.equals(report.path("analysisReport").asString(""))
                    || !report.get("headline").isArray()) {
                return null;
            }
            List<Object> headline = new ArrayList<>();
            for (JsonNode figure : report.get("headline")) {
                Object value = figure.get("value").isIntegralNumber()
                        ? (Object) figure.get("value").asLong()
                        : (Object) figure.get("value").asDouble();
                headline.add(InvocationReport.object(
                        "key", figure.get("key").asString(),
                        "label", figure.get("label").asString(),
                        "value", value,
                        "unit", figure.get("unit").asString()));
            }
            return headline;
        } catch (IOException | RuntimeException cannotBeRead) {
            return null;
        }
    }

    private static Map<String, Object> tree(Document document) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("format", document.format());
        tree.put("analysisReport", document.analysisReport());
        tree.put("title", document.title());
        tree.put("writtenAt", INSTANT.format(document.writtenAt().truncatedTo(ChronoUnit.MILLIS)));
        List<Object> headline = new ArrayList<>();
        for (Figure figure : document.headline()) {
            headline.add(InvocationReport.object(
                    "key", figure.key(), "label", figure.label(), "value", figure.value(), "unit", figure.unit()));
        }
        tree.put("headline", headline);
        List<Object> sections = new ArrayList<>();
        for (Section section : document.sections()) {
            sections.add(InvocationReport.object(
                    "id", section.id(),
                    "title", section.title(),
                    "text", section.text(),
                    "chart", section.chart() == null ? null : InvocationReport.object(
                            "kind", section.chart().kind(),
                            "x", section.chart().x(),
                            "series", section.chart().series()),
                    "table", section.table() == null ? null : tableTree(section.table())));
        }
        tree.put("sections", sections);
        return tree;
    }

    private static Map<String, Object> tableTree(Table table) {
        List<Object> columns = new ArrayList<>();
        for (Column column : table.columns()) {
            columns.add(InvocationReport.object("id", column.id(), "label", column.label(), "kind", column.kind()));
        }
        return InvocationReport.object("columns", columns, "rows", table.rows());
    }
}
