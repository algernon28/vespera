package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.extraction.WordCountDistribution;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders {@link WordCountDistribution.Distribution} as {@code word-count-distribution.html}, the page the word
 * floor is read off (ADR-282 section 5), through {@link ReportPage}. It holds no rule: the bands, the candidate
 * floors and what a floor removes are {@code extraction}'s, and each format is named as {@link FormatMixReport}
 * names it. The page holds counts, a run id and the labels of detected formats, and no path, name or text of a
 * document.
 */
final class WordCountDistributionReport {

    private WordCountDistributionReport() {}

    /** What a row of the first table is (ADR-275 section 2): the same for every distribution. */
    private static final String SURVIVORS_HOVER =
            "Each row is a count of words, and each cell the number of files of that kind holding that many words.";

    /** What a row of the second table is (ADR-275 section 2): the same for every distribution. */
    private static final String FLOORS_HOVER =
            "Each row is a word floor, and each cell the number of files of that kind it would remove, those with"
                    + " fewer words than the floor.";

    static String render(WordCountDistribution.Distribution distribution, RunId contentCensusRunId) {
        List<DetectedFormat> formats = distribution.formats();
        List<String> columns = new ArrayList<>();
        formats.forEach(format -> columns.add(FormatMixReport.labelOf(format)));
        columns.add("All formats");

        StringBuilder bandRows = new StringBuilder();
        long[] totals = new long[formats.size() + 1];
        for (WordCountDistribution.Band band : WordCountDistribution.BANDS) {
            List<String> cells = new ArrayList<>();
            cells.add(ReportPage.textCell(label(band)));
            for (int column = 0; column < formats.size(); column++) {
                long survivors = distribution.survivors(formats.get(column), band);
                totals[column] += survivors;
                cells.add(ReportPage.numberCell(survivors));
            }
            long all = distribution.survivors(band);
            totals[formats.size()] += all;
            cells.add(ReportPage.numberCell(all));
            bandRows.append(ReportPage.row(cells.toArray(String[]::new)));
        }
        List<String> totalCells = new ArrayList<>();
        totalCells.add(ReportPage.textCell("All"));
        for (long total : totals) {
            totalCells.add(ReportPage.numberCell(total));
        }
        bandRows.append(ReportPage.row(totalCells.toArray(String[]::new)));

        StringBuilder floorRows = new StringBuilder();
        for (long floor : WordCountDistribution.CANDIDATE_FLOORS) {
            List<String> cells = new ArrayList<>();
            cells.add(ReportPage.numberCell(floor));
            for (DetectedFormat format : formats) {
                cells.add(ReportPage.numberCell(distribution.removedBy(floor, format)));
            }
            cells.add(ReportPage.numberCell(distribution.removedBy(floor)));
            floorRows.append(ReportPage.row(cells.toArray(String[]::new)));
        }

        String body = ReportPage.heading(1, "Word counts")
                + ReportPage.paragraph("Content census run <code>" + ReportPage.escape(contentCensusRunId.value())
                        + "</code>. The words of every file text extraction kept, by the kind of file stage 1 found."
                        + " A word is whatever stands between two spaces of the extracted text, so <code>2026</code>"
                        + " is one word; the words of page headers and footers count.")
                + ReportPage.heading(2, "Files by their words")
                + ReportPage.tableCard(
                        SURVIVORS_HOVER,
                        "<thead>" + ReportPage.headerRow(headers("Words", columns)) + "</thead>\n<tbody>\n",
                        bandRows + "</tbody>\n")
                + ReportPage.heading(2, "What a word floor would remove")
                + ReportPage.tableCard(
                        FLOORS_HOVER,
                        "<thead>" + ReportPage.headerRow(headers("Word floor", columns)) + "</thead>\n<tbody>\n",
                        floorRows + "</tbody>\n")
                + ReportPage.paragraph("A word floor removes every file with fewer words than it, whatever its kind:"
                        + " a one-line text note, a shortcut and a picture of one word fall under a floor of 2"
                        + " alike. Write the number you choose into <code>" + DegenerateOutputWordFloor.KEY
                        + "</code> in the profile, and say in its <code>provenance</code> how you arrived at it."
                        + " The key ships unset, and while it is unset no file is removed for having too few words.");
        return ReportPage.render("Word counts", body);
    }

    private static String[] headers(String first, List<String> columns) {
        List<String> all = new ArrayList<>();
        all.add(first);
        all.addAll(columns);
        return all.toArray(String[]::new);
    }

    private static String label(WordCountDistribution.Band band) {
        if (band.fewestWords() == 0) {
            return band.mostWords() + " or fewer";
        }
        if (band.mostWords() == Long.MAX_VALUE) {
            return band.fewestWords() + " or more";
        }
        if (band.fewestWords() == band.mostWords()) {
            return Long.toString(band.fewestWords());
        }
        return band.fewestWords() + " to " + band.mostWords();
    }
}
