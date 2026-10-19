package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.ReplacingMove;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
     * Writes the report beside its place in {@code workingDirectory} and moves it there. A report that cannot be written
     * fails nothing and leaves no file under the name it was written with: it informs a value the operator may choose,
     * and the tree it states is whole without it.
     */
    void writeTo(Path workingDirectory, ReplacingMove moves) {
        Path part = workingDirectory.resolve(FILE_NAME + Deliverable.PART_SUFFIX);
        try {
            try (Writer report = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                report.write(page());
            }
            moves.intoPlace(part, workingDirectory.resolve(FILE_NAME));
        } catch (IOException | RuntimeException unwritten) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException | RuntimeException stays) {
                // A file nothing reads stays where it was written.
            }
        }
    }

    private String page() {
        long[] ascending = clusters.stream().mapToLong(cluster -> cluster[SIZE]).sorted().toArray();
        StringBuilder page = new StringBuilder(Html.head("Group page sizes"));
        page.append("<h1>Group page sizes</h1>\n")
                .append("<p>This page states how large the page of each group is when it is written whole, in bytes: the")
                .append(" file as it stands on disk, its pictures inlined. Writing a whole number of bytes into ")
                .append(ClusterPageCeiling.KEY)
                .append(" in profile.yaml splits every page larger than it, at the end of a document's entry, into")
                .append(" several pages; left unset, every group keeps one page. No number is suggested here.</p>\n");
        page.append("<h2>The ceiling</h2>\n<table>\n")
                .append(row(ClusterPageCeiling.KEY, ceilingReading()))
                .append("</table>\n");
        page.append("<h2>The pages written whole</h2>\n<table>\n")
                .append(row("Groups", Integer.toString(ascending.length)))
                .append(row("Largest, in bytes", ascending.length == 0 ? "none" : Long.toString(ascending[ascending.length - 1])))
                .append(row("95th percentile, in bytes", nearestRank(ascending, 95)))
                .append(row("Median, in bytes", nearestRank(ascending, 50)))
                .append("</table>\n");
        page.append("<h2>The groups larger than a mark</h2>\n<table>\n");
        for (long mark : MARKS) {
            page.append(row("Larger than " + mark + " bytes", Long.toString(largerThan(ascending, mark))));
        }
        page.append("</table>\n");
        if (ceiling instanceof ClusterPageCeiling.Bytes) {
            page.append("<h2>What the ceiling split</h2>\n<table>\n")
                    .append(row("Groups split", Long.toString(clustersSplit)))
                    .append(row("Pages written", Long.toString(pagesWritten)))
                    .append(row("Pages larger than the ceiling", Long.toString(pagesOverTheCeiling)))
                    .append("</table>\n");
        }
        page.append("<h2>The largest</h2>\n<table>\n")
                .append("<tr><th>Rank</th><th>Bytes</th><th>Documents listed</th><th>Pages written</th></tr>\n");
        List<long[]> largestFirst = new ArrayList<>(clusters);
        largestFirst.sort(Comparator.comparingLong((long[] cluster) -> cluster[SIZE]).reversed());
        for (int rank = 1; rank <= Math.min(RANKED, largestFirst.size()); rank++) {
            long[] cluster = largestFirst.get(rank - 1);
            page.append("<tr>")
                    .append(cell(rank))
                    .append(cell(cluster[SIZE]))
                    .append(cell(cluster[ENTRIES]))
                    .append(cell(cluster[PAGES]))
                    .append("</tr>\n");
        }
        page.append("</table>\n").append(Html.tail());
        return page.toString();
    }

    /** The key as it reads: its bytes, {@code unset}, or {@code ignored} with the text never quoted. */
    private String ceilingReading() {
        return switch (ceiling) {
            case ClusterPageCeiling.Bytes bytes -> Long.toString(bytes.bytes());
            case ClusterPageCeiling.Unset unset -> "unset";
            case ClusterPageCeiling.Ignored ignored -> "ignored";
        };
    }

    /** The value at rank ceil(p x n / 100) of {@code ascending}, counted from 1, or {@code none} where there is no size. */
    private static String nearestRank(long[] ascending, int percentile) {
        if (ascending.length == 0) {
            return "none";
        }
        int rank = (int) Math.ceilDiv((long) percentile * ascending.length, 100L);
        return Long.toString(ascending[rank - 1]);
    }

    private static long largerThan(long[] ascending, long mark) {
        return java.util.Arrays.stream(ascending).filter(size -> size > mark).count();
    }

    private static String row(String label, String value) {
        return "<tr><th>" + label + "</th><td class=\"count\">" + value + "</td></tr>\n";
    }

    private static String cell(long number) {
        return "<td class=\"count\">" + number + "</td>";
    }
}
