package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shape the twelve analysis reports write their numbers in, {@code <name>.json} beside {@code <name>.html}
 * (ADR-362 section 9): {@code format}, {@code analysisReport}, {@code title}, {@code writtenAt}, {@code headline}
 * and {@code sections}, the headline written before the sections so that a reader of the headline alone stops
 * there. The report's class says what its page says; this class holds the shape, writes it whole through {@code
 * .part} and {@link ReplacingMove}, and reads a headline back for {@code report.json} (section 3).
 *
 * <p>It holds no rule of a report: no number is chosen, ranked or rounded here.
 */
final class AnalysisReportJson {

    /** The format every analysis report's JSON is written in (ADR-362 section 9), raised by a change that breaks a field. */
    static final int FORMAT = 1;

    /** The unit of a headline figure. */
    enum Unit {
        COUNT, SHARE, BYTES, SECONDS, NUMBER;

        String word() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One number the page states first. */
    record Figure(String key, String label, Number value, Unit unit) {}

    /** One column of a table: its id, its label and its kind, one of the schema's. */
    record Column(String id, String label, String kind) {}

    /** A table: its columns, and its rows, each a list of cells in the columns' order. */
    record Table(List<Column> columns, List<List<Object>> rows) {}

    /** One part of the page, in the page's order. */
    record Section(String id, String title, List<String> text, Table table) {}

    private static final DateTimeFormatter INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Reads one value out of a stream that goes on after it. */
    private static final ObjectReader READER =
            MAPPER.readerFor(Object.class).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private AnalysisReportJson() {}

    /**
     * Writes the report whole to {@code file}, through {@code file}'s {@code .part} and a replacing move.
     *
     * @throws IOException where the part cannot be written or moved over {@code file}
     */
    static void write(Path file, String name, String title, Instant writtenAt, List<Figure> headline, List<Section> sections)
            throws IOException {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("format", (long) FORMAT);
        tree.put("analysisReport", name);
        tree.put("title", title);
        tree.put("writtenAt", INSTANT.format(writtenAt));
        List<Object> figures = new ArrayList<>();
        for (Figure figure : headline) {
            figures.add(InvocationReport.object(
                    "key", figure.key(), "label", figure.label(), "value", figure.value(), "unit", figure.unit().word()));
        }
        tree.put("headline", figures);
        List<Object> parts = new ArrayList<>();
        for (Section section : sections) {
            parts.add(InvocationReport.object(
                    "id", section.id(), "title", section.title(), "text", new ArrayList<Object>(section.text()),
                    "chart", null, "table", tableOf(section.table())));
        }
        tree.put("sections", parts);
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.writeString(part, InvocationReport.Json.render(tree) + "\n", StandardCharsets.UTF_8);
        ReplacingMove.ATOMIC.intoPlace(part, file);
    }

    private static Object tableOf(Table table) {
        if (table == null) {
            return null;
        }
        List<Object> columns = new ArrayList<>();
        for (Column column : table.columns()) {
            columns.add(InvocationReport.object("id", column.id(), "label", column.label(), "kind", column.kind()));
        }
        List<Object> rows = new ArrayList<>();
        for (List<Object> row : table.rows()) {
            rows.add(new ArrayList<>(row));
        }
        return InvocationReport.object("columns", columns, "rows", rows);
    }

    /**
     * The headline {@code file} holds, as the list of figures it is written as, read without reading the rest of the
     * file; {@code null} where the file is not there, cannot be read, or is of a format this jar does not write.
     */
    static List<Object> headlineOf(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (JsonParser parser = MAPPER.createParser(file)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                if ("format".equals(field)) {
                    if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT || parser.getLongValue() != FORMAT) {
                        return null;
                    }
                } else if ("headline".equals(field)) {
                    Object headline = READER.readValue(parser);
                    return headline instanceof List<?> list ? new ArrayList<Object>(list) : null;
                } else {
                    parser.skipChildren();
                }
            }
            return null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
