package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders what stage 1 found across a corpus as one self-contained HTML file (ADR-095) — plain,
 * hand-assembled HTML, no templating library, the shared {@link ReportPage} module the reports
 * beside the database all supply their title, prose and rows to (ADR-046, ADR-130).
 *
 * <p>The page exists because nobody has walked a real archive. It is what turns "should intact
 * non-documents be removed" from an argument into a decision with a source, so two things about it
 * are load-bearing rather than presentational: files nothing was read from are counted apart from
 * files that were read and matched nothing, and the page states in its own words what it cannot
 * see.
 */
final class FormatMixReport {

    private FormatMixReport() {}

    /**
     * What stage 1 found, accumulated over the occurrences it examined, and how many of them it left
     * out as out of scope (ADR-146).
     */
    record Mix(
            Map<DetectedFormat, Integer> byFormat,
            Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype,
            Map<String, Integer> unrecognisedLeadingBytes,
            int outOfScope) {}

    /**
     * How each class is named for a reader who has never seen this project (ADR-052): report-visible
     * text carries no vocabulary the reader would have to look up.
     */
    private static final Map<DetectedFormat, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(DetectedFormat.PDF, "PDF documents");
        LABELS.put(DetectedFormat.IMAGE, "Images");
        LABELS.put(DetectedFormat.WORDPROCESSING, "Word processing documents");
        LABELS.put(DetectedFormat.SPREADSHEET, "Spreadsheets");
        LABELS.put(DetectedFormat.ZIP_CONTAINER, "Archives of another kind");
        LABELS.put(DetectedFormat.OLE_COMPOUND, "Older Office containers");
        LABELS.put(DetectedFormat.PLAIN_TEXT, "Text");
        LABELS.put(DetectedFormat.UNRECOGNISED, "Of no known kind");
        LABELS.put(DetectedFormat.FLOOR_STOPPED, "Nothing could be read");
    }

    private static final Map<DetectedSubtype, String> SUBTYPE_LABELS = new LinkedHashMap<>();

    static {
        SUBTYPE_LABELS.put(DetectedSubtype.LEGACY_WORD, "older Word documents");
        SUBTYPE_LABELS.put(DetectedSubtype.LEGACY_SPREADSHEET, "older spreadsheets");
        SUBTYPE_LABELS.put(DetectedSubtype.LEGACY_PRESENTATION, "older presentations");
        SUBTYPE_LABELS.put(DetectedSubtype.HTML, "web pages");
        SUBTYPE_LABELS.put(DetectedSubtype.MARKDOWN, "Markdown");
        SUBTYPE_LABELS.put(DetectedSubtype.CSV, "comma-separated values");
        SUBTYPE_LABELS.put(DetectedSubtype.ASCIIDOC, "AsciiDoc");
    }

    static String render(Mix mix) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, "What the files turned out to be"))
                .append(ReportPage.paragraph("Every file was read far enough to recognise what it is."
                        + " What follows is what was found. Counting removes nothing: it exists so that a"
                        + " decision about what to leave out has a measurement behind it rather than a"
                        + " guess."))
                .append(ReportPage.paragraph("Spreadsheets are out of scope, whatever they hold. Files"
                        + " left out as out of scope, and not read any further: " + mix.outOfScope()
                        + ". They are still counted in the table below, with everything else."));

        StringBuilder formatRows = new StringBuilder();
        for (Map.Entry<DetectedFormat, String> label : LABELS.entrySet()) {
            formatRows.append(ReportPage.row(
                    ReportPage.textCell(label.getValue()),
                    ReportPage.numberCell(mix.byFormat().getOrDefault(label.getKey(), 0))));
        }
        body.append(ReportPage.table(
                        ReportPage.headerRow("What it was found to be", "Files"), formatRows.toString()))
                .append(ReportPage.paragraph("<em>Nothing could be read</em> counts files that were empty"
                        + " or would not open, so nothing in them was ever examined. Those are"
                        + " deliberately not counted as <em>of no known kind</em>, which means the"
                        + " opposite: the file was read, and what was inside it matched nothing"
                        + " known."));

        body.append(ReportPage.heading(2, "Finer labels, taken from filenames"))
                .append(ReportPage.paragraph("The classes above come from what is inside each file. The"
                        + " labels below come from the filename instead, and only where what is inside"
                        + " cannot say: an older Word document and a thumbnail cache are built the same"
                        + " way, and one piece of text reads like any other. A name can be wrong, so"
                        + " these deserve less trust than the counts above."));
        if (mix.bySubtype().isEmpty()) {
            body.append(ReportPage.paragraph("No file carried a name that added anything."));
        } else {
            StringBuilder subtypeRows = new StringBuilder();
            for (Map.Entry<DetectedFormat, Map<DetectedSubtype, Integer>> within :
                    mix.bySubtype().entrySet()) {
                for (Map.Entry<DetectedSubtype, Integer> named :
                        within.getValue().entrySet()) {
                    subtypeRows.append(ReportPage.row(
                            ReportPage.textCell(LABELS.get(within.getKey())),
                            ReportPage.textCell(SUBTYPE_LABELS.get(named.getKey())),
                            ReportPage.numberCell(named.getValue())));
                }
                subtypeRows.append(ReportPage.row(
                        ReportPage.textCell(LABELS.get(within.getKey())),
                        ReportPage.textCell("nothing the name could add"),
                        ReportPage.numberCell(unlabelled(mix, within.getKey()))));
            }
            body.append(ReportPage.table(
                    ReportPage.headerRow("Within", "Named as", "Files"), subtypeRows.toString()));
        }

        body.append(ReportPage.heading(2, "What could not be recognised"));
        if (mix.unrecognisedLeadingBytes().isEmpty()) {
            body.append(ReportPage.paragraph("Every file that was read was recognised."));
        } else {
            StringBuilder leadingRows = new StringBuilder();
            for (Map.Entry<String, Integer> leading :
                    mix.unrecognisedLeadingBytes().entrySet()) {
                leadingRows.append(ReportPage.row(
                        ReportPage.codeCell(leading.getKey()),
                        ReportPage.numberCell(leading.getValue())));
            }
            body.append(ReportPage.paragraph("Grouped by the four bytes each one begins with, so that a"
                            + " single kind of file arriving in bulk shows as one large group rather"
                            + " than as a total nobody can act on."))
                    .append(ReportPage.table(
                            ReportPage.headerRow("Begins with", "Files"), leadingRows.toString()));
        }

        body.append(ReportPage.heading(2, "What this page cannot see"))
                .append(ReportPage.paragraph("A file can be perfectly readable text and still not be a"
                        + " document: a folder settings file, an editor's leftover backup. Those are"
                        + " counted as text above, and this page cannot tell them apart from a written"
                        + " note, because nothing inside them says which is which. So a small <em>of no"
                        + " known kind</em> count does not mean an archive is free of clutter. It means"
                        + " the clutter that is left is the kind this measurement does not reach."));

        return ReportPage.render("What the files turned out to be", body.toString());
    }

    /** The remainder within a class: examined, but carrying a name that named no member of it. */
    private static int unlabelled(Mix mix, DetectedFormat format) {
        int named = mix.bySubtype().getOrDefault(format, Map.of()).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return mix.byFormat().getOrDefault(format, 0) - named;
    }
}
