package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.corpus.FormatMix;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.TextParts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
     * out as out of scope (ADR-146), of which {@code logs} were logs and {@code tooLarge} were text
     * files left out for their size, by any of ADR-178's three rules (ADR-171). {@code byTimestampBand} counts the text files of ten or
     * more non-blank lines by their timestamped share, ten percent to a band, the last band being 90% to 100%;
     * {@code fewerThanTenLines} counts the rest. {@code leftOutByName} counts the files left out for the ending
     * of their name (ADR-256), and {@code nameEndings} are the canonical endings the profile lists, empty
     * where it lists none. {@code logFloor} is {@code null} where none is set.
     *
     * <p>{@code code} counts the files kept as code (ADR-277), {@code codeByEnding} of them for the ending of their
     * name and {@code codeByMark} for a generator's mark; {@code codeEndingCounts} and {@code codeGeneratorCounts}
     * count them by the ending listed and by the generator named; {@code textEndingCounts} counts every file detected
     * as text or as of no known kind by the ending of its name; and {@code codeEndings} are the canonical endings
     * {@code codeNameEndings} lists, empty where it lists none.
     */
    record Mix(
            Map<DetectedFormat, Integer> byFormat,
            Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype,
            Map<String, Integer> unrecognisedLeadingBytes,
            int outOfScope,
            int logs,
            int tooLarge,
            int leftOutByName,
            int[] byTimestampBand,
            int fewerThanTenLines,
            int code,
            int codeByEnding,
            int codeByMark,
            Map<String, Integer> codeEndingCounts,
            Map<String, Integer> codeGeneratorCounts,
            Map<String, Integer> textEndingCounts,
            Double logFloor,
            List<String> nameEndings,
            List<String> codeEndings) {}

    /** How many bands of a tenth the timestamped share is counted in. */
    static final int BANDS = FormatMix.TIMESTAMP_BANDS;

    /**
     * How each class is named for a reader who has never seen this project (ADR-052): report-visible
     * text carries no vocabulary the reader would have to look up.
     */
    private static final Map<DetectedFormat, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(DetectedFormat.PDF, "PDF documents");
        LABELS.put(DetectedFormat.IMAGE, "Images");
        LABELS.put(DetectedFormat.BMP, "BMP images");
        LABELS.put(DetectedFormat.VIDEO, "Videos");
        LABELS.put(DetectedFormat.WORDPROCESSING, "Word processing documents");
        LABELS.put(DetectedFormat.SPREADSHEET, "Spreadsheets");
        LABELS.put(DetectedFormat.ZIP_CONTAINER, "Archives of another kind");
        LABELS.put(DetectedFormat.COMPILED_CODE, "Compiled code");
        LABELS.put(DetectedFormat.FONT, "Fonts");
        LABELS.put(DetectedFormat.CERTIFICATE, "Certificates and detached signatures");
        LABELS.put(DetectedFormat.DATABASE, "Databases");
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
        SUBTYPE_LABELS.put(DetectedSubtype.OUTLOOK_MESSAGE, "Outlook messages");
        SUBTYPE_LABELS.put(DetectedSubtype.RFC822_MESSAGE, "internet messages");
    }

    static String render(Mix mix) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, "What the files turned out to be"))
                .append(ReportPage.paragraph("Every file was read far enough to recognise what it is."
                        + " What follows is what was found. Counting removes nothing: it exists so that a"
                        + " decision about what to leave out has a measurement behind it rather than a"
                        + " guess."))
                .append(ReportPage.paragraph("Spreadsheets, BMP images, videos, emails, compiled code, fonts,"
                        + " certificates and detached signatures, and databases are out of scope, whatever"
                        + " they hold. So is a text file over "
                        + grouped(TextParts.LARGEST_TEXT_BYTES)
                        + " bytes, because the converter's answer for one would be too large to keep, and so is"
                        + " an HTML, CSV or AsciiDoc file, or a text file written in UTF-16 or UTF-32, over "
                        + grouped(DoclingClient.TEXT_SIZE_CEILING_BYTES)
                        + " bytes, because the converter cannot finish one in time and such a file is not cut"
                        + " into parts, and so is a log, once logTimestampShareFloor is set in profile.yaml."
                        + " Any other text file over "
                        + grouped(DoclingClient.TEXT_SIZE_CEILING_BYTES)
                        + " bytes is converted in parts. Files left out as out of scope,"
                        + " and not read any further: "
                        + mix.outOfScope()
                        + ". Of those, logs: "
                        + mix.logs()
                        + ", text files left out for their size: "
                        + mix.tooLarge()
                        + ", and files left out for the ending of their name: "
                        + mix.leftOutByName()
                        + ". They are still counted in the table below, with everything else."))
                .append(ReportPage.paragraph(mix.nameEndings().isEmpty()
                        ? "No name endings are listed, so no file was left out for the ending of its name."
                                + " Listing them in outOfScopeNameEndings in profile.yaml leaves out every file of no"
                                + " known kind, and every text file, whose name ends with one."
                        : "outOfScopeNameEndings in profile.yaml lists these name endings: "
                                + ReportPage.escape(String.join(", ", mix.nameEndings()))
                                + ". A file of no known kind, or a text file, whose name ends with one of them, in"
                                + " any case, is out of scope whatever it holds. A file whose content says what it"
                                + " is, a PDF for one, is kept whatever its name."))
                .append(ReportPage.paragraph("Code is text written to be compiled or interpreted, or a page of reference"
                        + " that a documentation generator wrote from it. It is kept apart from the documents,"
                        + " whatever it holds, and is not hashed. "
                        + "Files kept as code, and not read any further: "
                        + mix.code()
                        + ". Of those, kept for the ending of their name: "
                        + mix.codeByEnding()
                        + ", and kept for the mark of a documentation generator: "
                        + mix.codeByMark()
                        + ". They are still counted in the table below, with everything else."))
                .append(ReportPage.paragraph(mix.codeEndings().isEmpty()
                        ? "No name endings are listed as code. Listing them in codeNameEndings in profile.yaml keeps as"
                                + " code every file of no known kind, and every text file, whose name ends with one and"
                                + " whose start and end read as text. A page that carries the mark of a documentation"
                                + " generator is kept as code all the same."
                        : "codeNameEndings in profile.yaml lists these name endings: "
                                + ReportPage.escape(String.join(", ", mix.codeEndings()))
                                + ". A file of no known kind, or a text file, whose name ends with one of them, in any"
                                + " case, and whose start and end read as text, is kept as code. A file whose content"
                                + " says what it is, a PDF for one, is kept whatever its name."));
        List<String> shared = mix.nameEndings().stream().filter(mix.codeEndings()::contains).toList();
        if (!shared.isEmpty()) {
            body.append(ReportPage.paragraph("outOfScopeNameEndings and codeNameEndings both list "
                    + ReportPage.escape(String.join(", ", shared))
                    + "; a file whose name ends with " + (shared.size() == 1 ? "it" : "one of them")
                    + " is out of scope, and not code."));
        }

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

        body.append(ReportPage.heading(2, "Kept as code"));
        if (mix.code() == 0) {
            body.append(ReportPage.paragraph("No file was kept as code."));
        } else {
            StringBuilder codeRows = new StringBuilder();
            mix.codeEndingCounts().forEach((ending, files) ->
                    codeRows.append(ReportPage.row(ReportPage.textCell(ending), ReportPage.numberCell(files))));
            mix.codeGeneratorCounts().forEach((generator, files) ->
                    codeRows.append(ReportPage.row(ReportPage.textCell(generator), ReportPage.numberCell(files))));
            body.append(ReportPage.table(
                    ReportPage.headerRow("Name ending listed, or generator whose mark the page carries", "Files"),
                    codeRows.toString()));
        }

        body.append(ReportPage.heading(2, "Name endings of text files and files of no known kind"))
                .append(ReportPage.paragraph("Every file found to be text, or of no known kind, counted by the ending of"
                        + " its name from its last dot, in lower case, whatever became of it. A name with no dot, or"
                        + " whose only dot begins it, has no ending."));
        StringBuilder endingRows = new StringBuilder();
        mix.textEndingCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> endingRows.append(
                        ReportPage.row(ReportPage.textCell(entry.getKey()), ReportPage.numberCell(entry.getValue()))));
        body.append(ReportPage.table(ReportPage.headerRow("Ending", "Files"), endingRows.toString()))
                .append(ReportPage.paragraph("Never list .html .htm .xhtml .md .txt .tex .rst .adoc in codeNameEndings:"
                        + " those are where the documents are. Take care with .sql .inc .in, which hold documents as"
                        + " often as code."));

        body.append(ReportPage.heading(2, "How much of each text file begins with a timestamp"))
                .append(ReportPage.paragraph("Each text file not left out for its kind or for the ending of its name, and not kept as code, was read from its start and its end, and the"
                        + " lines that begin with a date or a time were counted against the lines read that"
                        + " are not blank. The share is that count in whole percent, rounded down."))
                .append(ReportPage.paragraph(mix.logFloor() == null
                        ? "No floor is set, so nothing was left out as a log. Setting logTimestampShareFloor"
                                + " in profile.yaml turns the rule on."
                        : "The floor is " + floorText(mix.logFloor()) + ": a text file of ten lines or more"
                                + " at or above it was left out as a log."));
        StringBuilder bandRows = new StringBuilder();
        bandRows.append(ReportPage.row(
                ReportPage.textCell("fewer than ten lines"), ReportPage.numberCell(mix.fewerThanTenLines())));
        for (int band = 0; band < BANDS; band++) {
            String label = band == 0
                    ? "under 10%"
                    : band == BANDS - 1 ? "90% to 100%" : band * 10 + "% to " + (band * 10 + 9) + "%";
            bandRows.append(ReportPage.row(ReportPage.textCell(label), ReportPage.numberCell(mix.byTimestampBand()[band])));
        }
        body.append(ReportPage.table(ReportPage.headerRow("Share beginning with a timestamp", "Text files"), bandRows.toString()));

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

    /** A number with {@code ,} as the grouping separator, whatever the locale. */
    private static String grouped(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }

    /** The floor as a whole percent where it is one, and as the number otherwise. */
    private static String floorText(double floor) {
        double percent = floor * 100;
        return Math.abs(percent - Math.rint(percent)) < 1e-9
                ? (long) Math.rint(percent) + "%"
                : String.valueOf(floor);
    }

    /** The remainder within a class: examined, but carrying a name that named no member of it. */
    private static int unlabelled(Mix mix, DetectedFormat format) {
        int named = mix.bySubtype().getOrDefault(format, Map.of()).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return mix.byFormat().getOrDefault(format, 0) - named;
    }
}
