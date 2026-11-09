package io.algernon.vespera.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An analysis report's JSON, {@code <name>.json} in the working directory (ADR-362 §9), as the window reads it to draw
 * the report's tab (ADR-362 §10): the title, when it was written, the headline numbers and the sections in the page's
 * order, each with its paragraphs, its chart and its table. The window draws what it reads and decides none of it; it
 * reads with {@link JsonReader}, in {@code java.base} alone, and links no class of the jar (ADR-285 §3). A cell is a
 * {@link String}, a {@link BigDecimal} or null, as its column's {@code kind} says; a {@code share} is a fraction of one.
 */
public final class AnalysisReportFile {

    /**
     * The {@code format} the window reads for each analysis report, by name: an entry is added with each report's
     * native view (ADR-362 §13 step 7), and its names are the reports the window draws natively. A report with no
     * entry is read as {@link #FIRST_FORMAT}.
     */
    public static final Map<String, Integer> FORMATS = Map.ofEntries(
            Map.entry("extraction-failures", 1),
            Map.entry("extraction-survivors", 1),
            Map.entry("format-mix", 1),
            Map.entry("confidence-distribution", 1),
            Map.entry("word-count-distribution", 1),
            Map.entry("document-frequency", 1),
            Map.entry("seed-corpus-comparison", 1),
            Map.entry("translations", 1));

    /** The format every report's JSON is first written in. */
    static final int FIRST_FORMAT = 1;

    /** A report's JSON of a {@code format} other than the one the window reads for it. */
    public static final class FormatNotKnownException extends IllegalArgumentException {

        private final long format;

        FormatNotKnownException(long format) {
            super("the report is of format " + format + ", which this window does not read");
            this.format = format;
        }

        public long format() {
            return format;
        }
    }

    /** One of the few numbers a page states first: {@code unit} is one of count, share, bytes, seconds and number. */
    public record Headline(String key, String label, BigDecimal value, String unit) {
    }

    /** A table's column: {@code kind} is text, count, share, number, bytes, seconds, instant, path or analysisReport. */
    public record Column(String id, String label, String kind) {
    }

    public record Table(List<Column> columns, List<List<Object>> rows) {
    }

    /** {@code kind} is bars or lines; {@code x} is the column of the categories, {@code series} the columns drawn. */
    public record Chart(String kind, String x, List<String> series) {
    }

    public record Section(String id, String title, List<String> text, Chart chart, Table table) {
    }

    public record Report(int format, String analysisReport, String title, Instant writtenAt, List<Headline> headline,
            List<Section> sections) {
    }

    private AnalysisReportFile() {
    }

    /** The format the window reads for {@code name}. */
    public static int formatOf(String name) {
        return FORMATS.getOrDefault(name, FIRST_FORMAT);
    }

    /**
     * What {@code json}, the file of the analysis report {@code name}, holds.
     *
     * @throws FormatNotKnownException where it is JSON of another {@code format} than {@link #formatOf} gives
     * @throws IllegalArgumentException where it is not that JSON
     */
    public static Report read(String name, String json) {
        try {
            Map<?, ?> root = (Map<?, ?>) JsonReader.read(json);
            long format = ((BigDecimal) root.get("format")).longValueExact();
            if (format != formatOf(name)) {
                throw new FormatNotKnownException(format);
            }
            List<Headline> headline = new ArrayList<>();
            for (Object each : list(root.get("headline"))) {
                Map<?, ?> number = (Map<?, ?>) each;
                headline.add(new Headline((String) number.get("key"), (String) number.get("label"),
                        (BigDecimal) number.get("value"), (String) number.get("unit")));
            }
            List<Section> sections = new ArrayList<>();
            for (Object each : list(root.get("sections"))) {
                sections.add(section((Map<?, ?>) each));
            }
            return new Report((int) format, (String) root.get("analysisReport"), (String) root.get("title"),
                    Instant.parse((String) root.get("writtenAt")), List.copyOf(headline), List.copyOf(sections));
        } catch (ClassCastException | NullPointerException | ArithmeticException | DateTimeParseException notThatJson) {
            throw new IllegalArgumentException(name + ".json is not in the shape the jar writes it", notThatJson);
        }
    }

    private static Section section(Map<?, ?> section) {
        Map<?, ?> chart = (Map<?, ?>) section.get("chart");
        Map<?, ?> table = (Map<?, ?>) section.get("table");
        return new Section((String) section.get("id"), (String) section.get("title"), strings(section.get("text")),
                chart == null ? null
                        : new Chart((String) chart.get("kind"), (String) chart.get("x"), strings(chart.get("series"))),
                table == null ? null : table(table));
    }

    private static Table table(Map<?, ?> table) {
        List<Column> columns = new ArrayList<>();
        for (Object each : list(table.get("columns"))) {
            Map<?, ?> column = (Map<?, ?>) each;
            columns.add(new Column((String) column.get("id"), (String) column.get("label"), (String) column.get("kind")));
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Object each : list(table.get("rows"))) {
            List<Object> row = new ArrayList<>();
            for (Object cell : (List<?>) each) {
                if (cell != null && !(cell instanceof String) && !(cell instanceof BigDecimal)) {
                    throw new ClassCastException("a cell is a text or a number");
                }
                row.add(cell);
            }
            rows.add(java.util.Collections.unmodifiableList(row));
        }
        return new Table(List.copyOf(columns), List.copyOf(rows));
    }

    private static List<?> list(Object array) {
        return array == null ? List.of() : (List<?>) array;
    }

    private static List<String> strings(Object array) {
        List<String> strings = new ArrayList<>();
        for (Object each : list(array)) {
            strings.add((String) each);
        }
        return List.copyOf(strings);
    }
}
