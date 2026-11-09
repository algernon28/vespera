package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * The sizes of the tree's cluster pages, and the report {@code cluster-page-sizes.html} that states them (ADR-303 §8),
 * which every drawing writes in the working directory, whatever the ceiling.
 *
 * <p><b>Numbers and fixed words only</b>: no cluster, seed partition, document or path is named and no title or label
 * is shown, so the page can be sent to anyone (ADR-274 §8). <b>What is held</b> is three numbers for each cluster of the
 * run, its one-page size, its entries and its pages, bounded by the run's clusters and never by its survivors
 * (ADR-220 §9, ADR-303 §8), and three running counts.
 */
final class ClusterPageSizes {

    /** The report's name, in the working directory beside the other reports. */
    static final String FILE_NAME = "cluster-page-sizes.html";

    /** The sizes the report counts the clusters past, in bytes: powers of ten, none of them a value to set. */
    private static final long[] MARKS = {100_000L, 1_000_000L, 10_000_000L, 100_000_000L};

    /** How many of the largest the report ranks. */
    private static final int RANKED = 10;

    /** The page's title and its JSON's. */
    private static final String TITLE = "Group page sizes";

    /** The report's name in its JSON, and the JSON's format (ADR-362 §9). */
    static final String NAME = "cluster-page-sizes";

    static final int FORMAT = 1;

    /** The JSON beside the page, its name the page's with {@code .json}. */
    static final String JSON_FILE_NAME = "cluster-page-sizes.json";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final DateTimeFormatter INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final int SIZE = 0;
    private static final int ENTRIES = 1;
    private static final int PAGES = 2;

    private final ClusterPageCeiling ceiling;
    private final List<long[]> clusters = new ArrayList<>();
    private long clustersSplit;
    private long pagesWritten;
    private long pagesOverTheCeiling;

    ClusterPageSizes(ClusterPageCeiling ceiling) {
        this.ceiling = ceiling;
    }

    /**
     * Takes in one cluster drawn.
     *
     * @param onePageSize the bytes of its page written whole
     * @param entries how many entries its list holds
     * @param pages how many pages were written for it
     * @param pagesOver how many of those pages are larger than the ceiling
     */
    void drawn(long onePageSize, long entries, int pages, int pagesOver) {
        clusters.add(new long[] {onePageSize, entries, pages});
        if (pages > 1) {
            clustersSplit++;
        }
        pagesWritten += pages;
        pagesOverTheCeiling += pagesOver;
    }

    /**
     * Writes the report beside its place in {@code workingDirectory} and moves it there, its JSON {@code
     * cluster-page-sizes.json} first (ADR-362 §9): the page and its JSON are written from the same parts. A report that
     * cannot be written fails nothing and leaves no file under a name it was written with: it informs a value the
     * operator may choose, and the tree it states is whole without it. A JSON that cannot be moved leaves the earlier
     * JSON and the earlier page as they were, the page not being written; a page that cannot be moved after it leaves
     * the new JSON beside the earlier page.
     */
    void writeTo(Path workingDirectory, ReplacingMove moves) {
        Path part = workingDirectory.resolve(FILE_NAME + Deliverable.PART_SUFFIX);
        Path jsonPart = workingDirectory.resolve(JSON_FILE_NAME + Deliverable.PART_SUFFIX);
        try {
            List<Part> parts = parts();
            Files.writeString(jsonPart, json(parts), StandardCharsets.UTF_8);
            moves.intoPlace(jsonPart, workingDirectory.resolve(JSON_FILE_NAME));
            try (Writer report = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                report.write(page(parts));
            }
            moves.intoPlace(part, workingDirectory.resolve(FILE_NAME));
        } catch (IOException | RuntimeException unwritten) {
            for (Path left : List.of(jsonPart, part)) {
                try {
                    Files.deleteIfExists(left);
                } catch (IOException | RuntimeException stays) {
                    // A file nothing reads stays where it was written.
                }
            }
        }
    }

    /** One part of the page and of its JSON: the page's heading, its paragraphs and its table, where it has one. */
    private record Part(String id, String title, List<String> text, List<Column> columns, boolean headed, List<List<Object>> rows) {}

    /** A column of a part's table: its JSON {@code id}, {@code label} and {@code kind}. */
    private record Column(String id, String label, String kind) {}

    private static final List<Column> MEASURES = List.of(
            new Column("measure", "Measure", "text"), new Column("value", "Value", "number"));

    private static final List<Column> COUNTED = List.of(
            new Column("measure", "Measure", "text"), new Column("value", "Value", "count"));

    /** The parts of the page, in its order; each number is held once and the page and the JSON are both drawn from it. */
    private List<Part> parts() {
        long[] ascending = clusters.stream().mapToLong(cluster -> cluster[SIZE]).sorted().toArray();
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("about", TITLE, List.of(
                "This page states how large the page of each group is when it is written whole, in bytes: the"
                        + " file as it stands on disk, its pictures inlined. Writing a whole number of bytes into "
                        + ClusterPageCeiling.KEY
                        + " in profile.yaml splits every page larger than it, at the end of a document's entry, into"
                        + " several pages; left unset, every group keeps one page. No number is suggested here."),
                null, false, null));
        parts.add(new Part("ceiling", "The ceiling", List.of(),
                List.of(new Column("setting", "Setting", "text"), new Column("reading", "Reading", "text")), false,
                List.<List<Object>>of(List.of(ClusterPageCeiling.KEY, ceilingReading()))));
        parts.add(new Part("pages-written-whole", "The pages written whole", List.of(), MEASURES, false, List.of(
                row("Groups", (long) ascending.length),
                row("Largest, in bytes", ascending.length == 0 ? null : ascending[ascending.length - 1]),
                row("95th percentile, in bytes", nearestRank(ascending, 95)),
                row("Median, in bytes", nearestRank(ascending, 50)))));
        List<List<Object>> past = new ArrayList<>();
        for (long mark : MARKS) {
            past.add(row("Larger than " + mark + " bytes", largerThan(ascending, mark)));
        }
        parts.add(new Part("groups-larger-than-a-mark", "The groups larger than a mark", List.of(), COUNTED, false, past));
        if (ceiling instanceof ClusterPageCeiling.Bytes) {
            parts.add(new Part("what-the-ceiling-split", "What the ceiling split", List.of(), COUNTED, false, List.of(
                    row("Groups split", clustersSplit),
                    row("Pages written", pagesWritten),
                    row("Pages larger than the ceiling", pagesOverTheCeiling))));
        }
        List<long[]> largestFirst = new ArrayList<>(clusters);
        largestFirst.sort(Comparator.comparingLong((long[] cluster) -> cluster[SIZE]).reversed());
        List<List<Object>> largest = new ArrayList<>();
        for (int rank = 1; rank <= Math.min(RANKED, largestFirst.size()); rank++) {
            long[] cluster = largestFirst.get(rank - 1);
            largest.add(List.of((long) rank, cluster[SIZE], cluster[ENTRIES], cluster[PAGES]));
        }
        parts.add(new Part("the-largest", "The largest", List.of(), List.of(
                new Column("rank", "Rank", "count"), new Column("bytes", "Bytes", "bytes"),
                new Column("documents-listed", "Documents listed", "count"),
                new Column("pages-written", "Pages written", "count")), true, largest));
        return parts;
    }

    /** A row of a table of one number under a label, the number {@code null} where there is none. */
    private static List<Object> row(String label, Long value) {
        List<Object> row = new ArrayList<>();
        row.add(label);
        row.add(value);
        return row;
    }

    private String page(List<Part> parts) {
        StringBuilder page = new StringBuilder(Html.head(TITLE, DeliverableLanguage.ENGLISH));
        for (Part part : parts) {
            if (part.columns() == null) {
                page.append("<h1>").append(part.title()).append("</h1>\n");
                part.text().forEach(paragraph -> page.append("<p>").append(paragraph).append("</p>\n"));
                continue;
            }
            page.append("<h2>").append(part.title()).append("</h2>\n<table>\n");
            if (part.headed()) {
                page.append("<tr>");
                part.columns().forEach(column -> page.append("<th>").append(column.label()).append("</th>"));
                page.append("</tr>\n");
            }
            for (List<Object> cells : part.rows()) {
                if (part.headed()) {
                    page.append("<tr>");
                    cells.forEach(cell -> page.append(cell(cell)));
                    page.append("</tr>\n");
                } else {
                    page.append("<tr><th>").append(cells.get(0)).append("</th>")
                            .append(cell(cells.get(1))).append("</tr>\n");
                }
            }
            page.append("</table>\n");
        }
        return page.append(Html.tail()).toString();
    }

    /** The JSON of the same parts, in the shape the analysis reports share (ADR-362 §9). */
    private String json(List<Part> parts) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("format", FORMAT);
        report.put("analysisReport", NAME);
        report.put("title", TITLE);
        report.put("writtenAt", INSTANT.format(Instant.now().truncatedTo(ChronoUnit.MILLIS)));
        List<Object> headline = new ArrayList<>();
        List<List<Object>> whole = parts.get(2).rows();
        headline.add(figure("groups", "Groups", whole.get(0).get(1), "count"));
        String[] sized = {"largest", "percentile-95", "median"};
        String[] labels = {"Largest page, in bytes", "95th percentile, in bytes", "Median, in bytes"};
        for (int at = 0; at < sized.length; at++) {
            Object value = whole.get(at + 1).get(1);
            if (value != null) {
                headline.add(figure(sized[at], labels[at], value, "bytes"));
            }
        }
        report.put("headline", headline);
        List<Object> sections = new ArrayList<>();
        for (Part part : parts) {
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("id", part.id());
            section.put("title", part.title());
            section.put("text", part.text());
            section.put("chart", null);
            if (part.columns() == null) {
                section.put("table", null);
            } else {
                List<Object> columns = new ArrayList<>();
                for (Column column : part.columns()) {
                    Map<String, Object> described = new LinkedHashMap<>();
                    described.put("id", column.id());
                    described.put("label", column.label());
                    described.put("kind", column.kind());
                    columns.add(described);
                }
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("columns", columns);
                table.put("rows", part.rows());
                section.put("table", table);
            }
            sections.add(section);
        }
        report.put("sections", sections);
        return MAPPER.writeValueAsString(report);
    }

    private static Map<String, Object> figure(String key, String label, Object value, String unit) {
        Map<String, Object> figure = new LinkedHashMap<>();
        figure.put("key", key);
        figure.put("label", label);
        figure.put("value", value);
        figure.put("unit", unit);
        return figure;
    }

    /** The key as it reads: its bytes, {@code unset}, or {@code ignored} with the text never quoted. */
    private String ceilingReading() {
        return switch (ceiling) {
            case ClusterPageCeiling.Bytes bytes -> Long.toString(bytes.bytes());
            case ClusterPageCeiling.Unset unset -> "unset";
            case ClusterPageCeiling.Ignored ignored -> "ignored";
        };
    }

    /** The value at rank ceil(p x n / 100) of {@code ascending}, counted from 1, or {@code null} where there is no size. */
    private static Long nearestRank(long[] ascending, int percentile) {
        if (ascending.length == 0) {
            return null;
        }
        int rank = (int) Math.ceilDiv((long) percentile * ascending.length, 100L);
        return ascending[rank - 1];
    }

    private static Long largerThan(long[] ascending, long mark) {
        return Arrays.stream(ascending).filter(size -> size > mark).count();
    }

    /** A number as the page's table shows it, {@code none} where there is none. */
    private static String cell(Object value) {
        return "<td class=\"count\">" + (value == null ? "none" : value) + "</td>";
    }
}
