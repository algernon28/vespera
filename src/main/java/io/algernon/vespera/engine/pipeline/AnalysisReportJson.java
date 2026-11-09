package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one writer of the shape the analysis reports of {@code pipeline} share (ADR-362 section 9): {@code format},
 * {@code analysisReport}, {@code title}, {@code writtenAt}, {@code sections} and {@code headline}, written as the
 * sections come and the headline last, because a streamed page knows its numbers only at its end.
 *
 * <p>The caller owns the file: it opens the {@code .part}, hands the writer here, and moves the part into place.
 * Nothing here names a document's text; a section holds what its page holds and the caller supplies it.
 */
final class AnalysisReportJson implements AutoCloseable {

    /** The report's own format (ADR-362 section 9). */
    static final int FORMAT = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final DateTimeFormatter INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /** One headline figure: {@code unit} is one of {@code count}, {@code share}, {@code bytes}, {@code seconds}, {@code number}. */
    record Figure(String key, String label, Number value, String unit) {}

    /** One table column: {@code kind} is one of {@code text}, {@code count}, {@code share}, {@code number}, {@code bytes}, {@code seconds}, {@code instant}, {@code path}, {@code analysisReport}. */
    record Column(String id, String label, String kind) {}

    private final JsonGenerator out;

    private AnalysisReportJson(JsonGenerator out) {
        this.out = out;
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
            json.out.writeStringProperty("writtenAt", INSTANT.format(writtenAt));
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
                        cell(cell);
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

    private void cell(Object cell) {
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
}
