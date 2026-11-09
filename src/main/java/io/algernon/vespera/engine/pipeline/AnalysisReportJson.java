package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
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
import java.util.function.Consumer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shape the analysis reports share when they write what their pages show as JSON, {@code <name>.json} beside
 * {@code <name>.html} (ADR-362 section 9): {@code format}, {@code analysisReport}, {@code title}, {@code writtenAt},
 * {@code headline} and {@code sections}. Each report's writer builds its {@link Document} from the values it draws its
 * page from, and {@link #write} puts it in place through {@code .part} and {@code ReplacingMove}; {@link #headlineOf}
 * is what {@code report.json} copies of it, the headline alone and nothing of a section.
 *
 * <p>A report whose rows are too many to hold writes them a row at a time instead, through the streaming
 * {@link #write(Writer, String, int, String, Instant, List, StreamedSection, boolean, Rows)} (ADR-220).
 *
 * <p>A report whose sections are written as they come, the headline last because a streamed page knows its numbers only
 * at its end, writes through an instance: {@link #begin}, {@link #section} and {@link #end}. The caller owns the file:
 * it opens the {@code .part}, hands the writer here, and moves the part into place.
 *
 * <p>It holds no rule: what a report says, and under which sections, is its writer's, and each report's schema in
 * {@code docs/report} names its headline's keys, its sections' ids and each table's columns.
 */
final class AnalysisReportJson implements AutoCloseable {

    /** The report's own format (ADR-362 section 9). */
    static final int FORMAT = 1;

    /** What a headline figure's value is counted in (ADR-362 section 9). */
    static final List<String> UNITS = List.of("count", "share", "bytes", "seconds", "number");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final DateTimeFormatter INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final JsonGenerator out;

    private AnalysisReportJson(JsonGenerator out) {
        this.out = out;
    }

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

    /** The one section of a report whose rows are streamed, as its page streams them (ADR-220): its id, title and paragraphs, and its table's columns. */
    record StreamedSection(String id, String title, List<String> paragraphs, List<Column> columns) {}

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
            StreamedSection section,
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
        return MAPPER.writeValueAsString(text);
    }

    /** Begins the report on {@code target}: everything before its first section. */
    static AnalysisReportJson begin(Writer target, String analysisReport, String title, Instant writtenAt)
            throws IOException {
        try {
            AnalysisReportJson json = new AnalysisReportJson(MAPPER.createGenerator(target));
            json.out.writeStartObject();
            json.out.writeNumberProperty("format", FORMAT);
            json.out.writeStringProperty("analysisReport", analysisReport);
            json.out.writeStringProperty("title", title);
            json.out.writeStringProperty("writtenAt", instant(writtenAt));
            json.out.writeName("sections");
            json.out.writeStartArray();
            return json;
        } catch (JacksonException e) {
            throw new IOException(e);
        }
    }

    /** One section; {@code columns} is {@code null} where it holds no table, and each row is a list of cells in the columns' order. */
    void section(String id, String title, List<String> text, List<Column> columns, List<List<Object>> rows)
            throws IOException {
        try {
            out.writeStartObject();
            out.writeStringProperty("id", id);
            out.writeStringProperty("title", title);
            out.writeName("text");
            out.writeStartArray();
            for (String paragraph : text) {
                out.writeString(paragraph);
            }
            out.writeEndArray();
            out.writeNullProperty("chart");
            out.writeName("table");
            if (columns == null) {
                out.writeNull();
            } else {
                out.writeStartObject();
                out.writeName("columns");
                out.writeStartArray();
                for (Column column : columns) {
                    out.writeStartObject();
                    out.writeStringProperty("id", column.id());
                    out.writeStringProperty("label", column.label());
                    out.writeStringProperty("kind", column.kind());
                    out.writeEndObject();
                }
                out.writeEndArray();
                out.writeName("rows");
                out.writeStartArray();
                for (List<Object> row : rows) {
                    out.writeStartArray();
                    for (Object cell : row) {
                        cellOf(cell);
                    }
                    out.writeEndArray();
                }
                out.writeEndArray();
                out.writeEndObject();
            }
            out.writeEndObject();
        } catch (JacksonException e) {
            throw new IOException(e);
        }
    }

    private void cellOf(Object cell) {
        switch (cell) {
            case null -> out.writeNull();
            case String text -> out.writeString(text);
            case Double number -> out.writeNumber(number);
            case Number number -> out.writeNumber(number.longValue());
            default -> throw new IllegalArgumentException("not a cell: " + cell.getClass().getName());
        }
    }

    /** Ends the sections, writes the headline and closes the generator and the writer under it. */
    void end(List<Figure> headline) throws IOException {
        try {
            out.writeEndArray();
            out.writeName("headline");
            out.writeStartArray();
            for (Figure figure : headline) {
                out.writeStartObject();
                out.writeStringProperty("key", figure.key());
                out.writeStringProperty("label", figure.label());
                out.writeName("value");
                if (figure.value() instanceof Double number) {
                    out.writeNumber(number);
                } else {
                    out.writeNumber(figure.value().longValue());
                }
                out.writeStringProperty("unit", figure.unit());
                out.writeEndObject();
            }
            out.writeEndArray();
            out.writeEndObject();
            out.close();
        } catch (JacksonException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() {
        out.close();
    }

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
        tree.put("writtenAt", instant(document.writtenAt().truncatedTo(ChronoUnit.MILLIS)));
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
