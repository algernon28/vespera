package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.corpus.CodeSets;
import io.algernon.vespera.engine.corpus.ConfigurationFiles;
import io.algernon.vespera.engine.corpus.DetectedFormat;
import io.algernon.vespera.engine.corpus.DetectedSubtype;
import io.algernon.vespera.engine.corpus.FormatMix;
import io.algernon.vespera.engine.extraction.DoclingClient;
import io.algernon.vespera.engine.extraction.TextParts;
import java.time.Instant;
import java.util.ArrayList;
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
     * {@code codeNameEndings} lists, empty where it lists none. {@code codeSets} is what the set pass found
     * (ADR-284 section 10): the page counts the sets and their members by role, and names no folder.
     * {@code configuration} is what the configuration pass found and {@code configurationFloor} the floor it ran
     * under, {@code null} where none is set (ADR-287 section 8); {@code outOfScope} already counts the
     * configuration files left out.
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
            List<String> codeEndings,
            CodeSets.Tally codeSets,
            ConfigurationFiles.Tally configuration,
            Double configurationFloor) {}

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

    /** How {@code format} is named on {@code format-mix.html}, for the pages that name a detected format as it does. */
    static String labelOf(DetectedFormat format) {
        return LABELS.get(format);
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

    /** The page's title, and the JSON's. */
    static final String TITLE = "What the files turned out to be";

    /** The name of the report, as its JSON and the invocation report name it. */
    static final String NAME = "format-mix";

    /** The format of the JSON, raised when a field is removed, renamed or changed in meaning or type (ADR-362 section 3). */
    static final int FORMAT = 1;

    static String render(Mix mix) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, TITLE));
        introTexts(mix).forEach(text -> body.append(prose(text)));

        body.append(pageTable(kindsTable(mix), false))
                .append(ReportPage.paragraph(ReportPage.escape(KINDS_NOTE)
                        .replace("Nothing could be read", "<em>Nothing could be read</em>")
                        .replace("of no known kind", "<em>of no known kind</em>")));

        body.append(ReportPage.heading(2, "Finer labels, taken from filenames")).append(prose(FINER_INTRO));
        if (mix.bySubtype().isEmpty()) {
            body.append(prose(FINER_NONE));
        } else {
            body.append(pageTable(finerTable(mix), false));
        }

        body.append(ReportPage.heading(2, "Kept as code"));
        if (keptNothingAsCode(mix)) {
            body.append(prose(CODE_NONE));
        } else {
            body.append(pageTable(keptAsCodeTable(mix), false));
        }

        body.append(ReportPage.heading(2, "Name endings of text files and files of no known kind"))
                .append(prose(ENDINGS_INTRO))
                .append(pageTable(endingsTable(mix), false))
                .append(prose(ENDINGS_CAUTION));

        body.append(ReportPage.heading(2, "How much of each text file begins with a timestamp"));
        timestampTexts(mix).forEach(text -> body.append(prose(text)));
        body.append(pageTable(timestampTable(mix), false));

        body.append(ReportPage.heading(2, "How much of each text file is settings"));
        settingsTexts(mix).forEach(text -> body.append(prose(text)));
        body.append(pageTable(settingsShareTable(mix), false));
        body.append(prose(rootsText(mix)));
        if (rootFiles(mix) != 0) {
            body.append(pageTable(rootsTable(mix), false));
        }
        body.append(prose(definitionsText(mix)));
        if (definitions(mix) != 0) {
            body.append(pageTable(definitionsTable(mix), false));
        }

        body.append(ReportPage.heading(2, "What could not be recognised"));
        unrecognisedTexts(mix).forEach(text -> body.append(prose(text)));
        if (!mix.unrecognisedLeadingBytes().isEmpty()) {
            body.append(pageTable(unrecognisedTable(mix), true));
        }

        body.append(ReportPage.heading(2, "What this page cannot see"))
                .append(ReportPage.paragraph(ReportPage.escape(CANNOT_SEE)
                        .replace("of no known kind", "<em>of no known kind</em>")));

        return ReportPage.render(TITLE, body.toString());
    }

    /**
     * What the page shows as JSON, from the same values and the same texts (ADR-362 section 9): the headline is the
     * few numbers the page states first, and each section is a part of the page in its order, by the id
     * {@code docs/report/format-mix.schema.json} names.
     */
    static AnalysisReportJson.Document document(Mix mix, Instant writtenAt) {
        long examined = LABELS.keySet().stream()
                .mapToLong(format -> mix.byFormat().getOrDefault(format, 0))
                .sum();
        List<AnalysisReportJson.Figure> headline = List.of(
                figure("files", "Files examined", examined),
                figure("out-of-scope", "Files left out as out of scope", mix.outOfScope()),
                figure("logs", "Of those, logs", mix.logs()),
                figure("too-large", "Of those, text files left out for their size", mix.tooLarge()),
                figure("code", "Files kept as code", mix.code()));
        List<AnalysisReportJson.Section> sections = new ArrayList<>();
        sections.add(section("what-the-files-were", TITLE, introTexts(mix), null, null));
        sections.add(section(
                "by-kind",
                "What the files were found to be",
                List.of(),
                new AnalysisReportJson.Chart("bars", "kind", List.of("files")),
                kindsTable(mix)));
        sections.add(section("nothing-could-be-read", "Nothing could be read", List.of(KINDS_NOTE), null, null));
        sections.add(section(
                "finer-labels",
                "Finer labels, taken from filenames",
                List.of(FINER_INTRO, mix.bySubtype().isEmpty() ? FINER_NONE : ""),
                null,
                mix.bySubtype().isEmpty() ? null : finerTable(mix)));
        sections.add(section(
                "kept-as-code",
                "Kept as code",
                keptNothingAsCode(mix) ? List.of(CODE_NONE) : List.of(),
                null,
                keptNothingAsCode(mix) ? null : keptAsCodeTable(mix)));
        sections.add(section(
                "name-endings",
                "Name endings of text files and files of no known kind",
                List.of(ENDINGS_INTRO),
                null,
                endingsTable(mix)));
        sections.add(section("name-endings-caution", "Which endings never to list", List.of(ENDINGS_CAUTION), null, null));
        sections.add(section(
                "timestamp-share",
                "How much of each text file begins with a timestamp",
                timestampTexts(mix),
                new AnalysisReportJson.Chart("bars", "band", List.of("files")),
                timestampTable(mix)));
        sections.add(section(
                "settings-share",
                "How much of each text file is settings",
                settingsTexts(mix),
                new AnalysisReportJson.Chart("bars", "band", List.of("files")),
                settingsShareTable(mix)));
        sections.add(section(
                "settings-roots",
                "Files whose first element is a program's configuration",
                List.of(rootsText(mix)),
                null,
                rootFiles(mix) == 0 ? null : rootsTable(mix)));
        sections.add(section(
                "settings-interface-definitions",
                "Interface definitions",
                List.of(definitionsText(mix)),
                null,
                definitions(mix) == 0 ? null : definitionsTable(mix)));
        sections.add(section(
                "unrecognised",
                "What could not be recognised",
                unrecognisedTexts(mix),
                null,
                mix.unrecognisedLeadingBytes().isEmpty() ? null : unrecognisedTable(mix)));
        sections.add(section("cannot-see", "What this page cannot see", List.of(CANNOT_SEE), null, null));
        return new AnalysisReportJson.Document(FORMAT, NAME, TITLE, writtenAt, headline, sections);
    }

    private static AnalysisReportJson.Figure figure(String key, String label, long value) {
        return new AnalysisReportJson.Figure(key, label, value, "count");
    }

    /** A section whose paragraphs are the texts given, a text that is empty left out. */
    private static AnalysisReportJson.Section section(
            String id,
            String title,
            List<String> texts,
            AnalysisReportJson.Chart chart,
            AnalysisReportJson.Table table) {
        return new AnalysisReportJson.Section(
                id, title, texts.stream().filter(text -> !text.isEmpty()).toList(), chart, table);
    }

    // ---- the texts, which the page escapes and the JSON holds as they are

    private static final String KINDS_NOTE = "Nothing could be read counts files that were empty"
            + " or would not open, so nothing in them was ever examined. Those are"
            + " deliberately not counted as of no known kind, which means the"
            + " opposite: the file was read, and what was inside it matched nothing"
            + " known.";

    private static final String FINER_INTRO = "The classes above come from what is inside each file. The"
            + " labels below come from the filename instead, and only where what is inside"
            + " cannot say: an older Word document and a thumbnail cache are built the same"
            + " way, and one piece of text reads like any other. A name can be wrong, so"
            + " these deserve less trust than the counts above.";

    private static final String FINER_NONE = "No file carried a name that added anything.";

    private static final String CODE_NONE = "No file was kept as code.";

    private static final String ENDINGS_INTRO = "Every file found to be text, or of no known kind, counted by the ending of"
            + " its name from its last dot, in lower case, whatever became of it. A name with no dot, or"
            + " whose only dot begins it, has no ending.";

    private static final String ENDINGS_CAUTION = "Never list .html .htm .xhtml .md .txt .tex .rst .adoc in codeNameEndings:"
            + " those are where the documents are. Take care with .sql .inc .in, which hold documents as"
            + " often as code.";

    private static final String CANNOT_SEE = "A file can be perfectly readable text and still not be a"
            + " document: an editor's leftover backup. Those are"
            + " counted as text above, and this page cannot tell them apart from a written"
            + " note, because nothing inside them says which is which. So a small of no"
            + " known kind count does not mean an archive is free of clutter. It means"
            + " the clutter that is left is the kind this measurement does not reach.";

    /** A paragraph of plain text, escaped. */
    private static String prose(String plain) {
        return ReportPage.paragraph(ReportPage.escape(plain));
    }

    /** The paragraphs the page opens with, after its title. */
    private static List<String> introTexts(Mix mix) {
        List<String> texts = new ArrayList<>();
        texts.add("Every file was read far enough to recognise what it is."
                + " What follows is what was found. Counting removes nothing: it exists so that a"
                + " decision about what to leave out has a measurement behind it rather than a"
                + " guess.");
        texts.add("Spreadsheets, BMP images, videos, emails, compiled code, fonts,"
                + " certificates and detached signatures, and databases are out of scope, whatever"
                + " they hold. So is a text file over "
                + grouped(TextParts.LARGEST_TEXT_BYTES)
                + " bytes, because the converter's answer for one would be too large to keep, and so is"
                + " an HTML, CSV or AsciiDoc file, or a text file written in UTF-16 or UTF-32, over "
                + grouped(DoclingClient.TEXT_SIZE_CEILING_BYTES)
                + " bytes, because the converter cannot finish one in time and such a file is not cut"
                + " into parts, and so is a log, once logTimestampShareFloor is set in profile.yaml."
                + " So is a configuration file that lies in no code set, once"
                + " configurationSettingShareFloor is set in profile.yaml."
                + " Any other text file over "
                + grouped(DoclingClient.TEXT_SIZE_CEILING_BYTES)
                + " bytes is converted in parts. Files left out as out of scope,"
                + " and not read any further: "
                + mix.outOfScope()
                + ". Of those, logs: "
                + mix.logs()
                + ", text files left out for their size: "
                + mix.tooLarge()
                + ", files left out for the ending of their name: "
                + mix.leftOutByName()
                + ", and configuration files: "
                + mix.configuration().leftOut()
                + ". They are still counted in the table below, with everything else.");
        texts.add(mix.nameEndings().isEmpty()
                ? "No name endings are listed, so no file was left out for the ending of its name."
                        + " Listing them in outOfScopeNameEndings in profile.yaml leaves out every file of no"
                        + " known kind, and every text file, whose name ends with one."
                : "outOfScopeNameEndings in profile.yaml lists these name endings: "
                        + String.join(", ", mix.nameEndings())
                        + ". A file of no known kind, or a text file, whose name ends with one of them, in"
                        + " any case, is out of scope whatever it holds. A file whose content says what it"
                        + " is, a PDF for one, is kept whatever its name.");
        texts.add("Code is text written to be compiled or interpreted, or a page of reference"
                + " that a documentation generator wrote from it. It is kept apart from the documents,"
                + " whatever it holds, and is not hashed. "
                + "Files kept as code, and not read any further: "
                + mix.code()
                + ". Of those, kept for the ending of their name: "
                + mix.codeByEnding()
                + ", and kept for the mark of a documentation generator: "
                + mix.codeByMark()
                + ". They are still counted in the table below, with everything else.");
        texts.add("Code sets: " + mix.codeSets().sets()
                + ", whose members are files of code: " + mix.codeSets().code()
                + ", build files: " + mix.codeSets().build()
                + ", configuration files: " + mix.configuration().keptWithCodeSet()
                + ", and files of generated reference trees: " + mix.codeSets().companion() + ".");
        texts.add(mix.codeEndings().isEmpty()
                ? "No name endings are listed as code. Listing them in codeNameEndings in profile.yaml keeps as"
                        + " code every file of no known kind, and every text file, whose name ends with one and"
                        + " whose start and end read as text. A page that carries the mark of a documentation"
                        + " generator is kept as code all the same."
                : "codeNameEndings in profile.yaml lists these name endings: "
                        + String.join(", ", mix.codeEndings())
                        + ". A file of no known kind, or a text file, whose name ends with one of them, in any"
                        + " case, and whose start and end read as text, is kept as code. A file whose content"
                        + " says what it is, a PDF for one, is kept whatever its name.");
        List<String> shared = mix.nameEndings().stream().filter(mix.codeEndings()::contains).toList();
        if (!shared.isEmpty()) {
            texts.add("outOfScopeNameEndings and codeNameEndings both list "
                    + String.join(", ", shared)
                    + "; a file whose name ends with " + (shared.size() == 1 ? "it" : "one of them")
                    + " is out of scope, and not code.");
        }
        return texts;
    }

    private static List<String> timestampTexts(Mix mix) {
        return List.of(
                "Each text file, and each file of no known kind whose start and end read as"
                        + " text, not left out for its kind or for the ending of its name, and not kept as code, was"
                        + " read from its start and its end, and the lines that begin with a date or a time were"
                        + " counted against the lines read that are neither blank nor the continuation of the line"
                        + " above. The share is that count in whole percent, rounded down.",
                mix.logFloor() == null
                        ? "No floor is set, so nothing was left out as a log. Setting logTimestampShareFloor"
                                + " in profile.yaml turns the rule on."
                        : "The floor is " + floorText(mix.logFloor()) + ": a text file of ten lines or more"
                                + " at or above it was left out as a log.");
    }

    /** The section on the share of settings (ADR-287 section 8). */
    private static List<String> settingsTexts(Mix mix) {
        ConfigurationFiles.Tally found = mix.configuration();
        return List.of(
                "Each text file of no finer kind, and each file of no known kind whose start"
                        + " and end read as text, still kept after what was kept as code and with the code sets, was"
                        + " read from its start and its end, and its lines shaped as a setting, a name followed by = or"
                        + " by a colon and a space, or a [section] heading, were counted against the lines read that"
                        + " are neither blank nor a comment. A line ending with a backslash is read with the line"
                        + " after it. The share is that count in whole percent, rounded down.",
                mix.configurationFloor() == null
                        ? "No floor is set, so no file was found to be a configuration file. Setting"
                                + " configurationSettingShareFloor in profile.yaml turns the rule on: a configuration"
                                + " file is then kept with its code set where it lies in one, and left out otherwise."
                        : "The floor is " + floorText(mix.configurationFloor()) + ": a file of ten lines or more at or"
                                + " above it, or whose first element is one of those below, is a configuration file."
                                + " Configuration files: " + found.configurationFiles()
                                + ", of which kept with their code set: " + found.keptWithCodeSet()
                                + ", and left out: " + found.leftOut() + ".");
    }

    private static String rootsText(Mix mix) {
        return "Files whose first element is a program's configuration, which makes each one a"
                + " configuration file whatever its share once the floor is set: " + rootFiles(mix) + ".";
    }

    private static String definitionsText(Mix mix) {
        return "Interface definitions, kept as documents whatever their share: " + definitions(mix) + ".";
    }

    private static List<String> unrecognisedTexts(Mix mix) {
        return mix.unrecognisedLeadingBytes().isEmpty()
                ? List.of("Every file that was read was recognised.")
                : List.of("Grouped by the four bytes each one begins with, so that a"
                        + " single kind of file arriving in bulk shows as one large group rather"
                        + " than as a total nobody can act on.");
    }

    // ---- the tables, which the page draws and the JSON holds as rows

    private static final String FILES = "files";

    private static AnalysisReportJson.Column kindColumn(String id, String label) {
        return new AnalysisReportJson.Column(id, label, "text");
    }

    private static AnalysisReportJson.Column filesColumn(String label) {
        return new AnalysisReportJson.Column(FILES, label, "count");
    }

    private static boolean keptNothingAsCode(Mix mix) {
        return mix.code() == 0 && mix.configuration().keptWithCodeSet() == 0;
    }

    private static int rootFiles(Mix mix) {
        return mix.configuration().roots().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int definitions(Mix mix) {
        return mix.configuration().interfaceDefinitions().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static AnalysisReportJson.Table kindsTable(Mix mix) {
        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<DetectedFormat, String> label : LABELS.entrySet()) {
            rows.add(List.of(label.getValue(), mix.byFormat().getOrDefault(label.getKey(), 0)));
        }
        return new AnalysisReportJson.Table(
                List.of(kindColumn("kind", "What it was found to be"), filesColumn("Files")), rows);
    }

    private static AnalysisReportJson.Table finerTable(Mix mix) {
        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<DetectedFormat, Map<DetectedSubtype, Integer>> within : mix.bySubtype().entrySet()) {
            for (Map.Entry<DetectedSubtype, Integer> named : within.getValue().entrySet()) {
                rows.add(List.of(LABELS.get(within.getKey()), SUBTYPE_LABELS.get(named.getKey()), named.getValue()));
            }
            rows.add(List.of(LABELS.get(within.getKey()), "nothing the name could add", unlabelled(mix, within.getKey())));
        }
        return new AnalysisReportJson.Table(
                List.of(kindColumn("within", "Within"), kindColumn("named-as", "Named as"), filesColumn("Files")), rows);
    }

    private static AnalysisReportJson.Table keptAsCodeTable(Mix mix) {
        List<List<Object>> rows = new ArrayList<>();
        mix.codeEndingCounts().forEach((ending, files) -> rows.add(List.of(ending, files)));
        mix.codeGeneratorCounts().forEach((generator, files) -> rows.add(List.of(generator, files)));
        rows.add(List.of("build file", mix.codeSets().build()));
        rows.add(List.of("configuration file", mix.configuration().keptWithCodeSet()));
        mix.codeSets().companionsByGenerator().forEach(
                (generator, files) -> rows.add(List.of("file of a tree " + generator + " generated", files)));
        return new AnalysisReportJson.Table(
                List.of(
                        kindColumn(
                                "source",
                                "Name ending listed, generator whose mark the page carries, build file, or tree of a generator"),
                        filesColumn("Files")),
                rows);
    }

    private static AnalysisReportJson.Table endingsTable(Mix mix) {
        List<List<Object>> rows = new ArrayList<>();
        mix.textEndingCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> rows.add(List.of(entry.getKey(), entry.getValue())));
        return new AnalysisReportJson.Table(List.of(kindColumn("ending", "Ending"), filesColumn("Files")), rows);
    }

    private static AnalysisReportJson.Table timestampTable(Mix mix) {
        return new AnalysisReportJson.Table(
                List.of(kindColumn("band", "Share beginning with a timestamp"),
                        new AnalysisReportJson.Column(FILES, "Text files", "count")),
                bandRows(mix.fewerThanTenLines(), mix.byTimestampBand()));
    }

    private static AnalysisReportJson.Table settingsShareTable(Mix mix) {
        return new AnalysisReportJson.Table(
                List.of(kindColumn("band", "Share of lines that are settings"), filesColumn("Files")),
                bandRows(mix.configuration().fewerThanTenLines(), mix.configuration().byShareBand()));
    }

    private static List<List<Object>> bandRows(int fewerThanTen, int[] bands) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("fewer than ten lines", fewerThanTen));
        for (int band = 0; band < BANDS; band++) {
            String label = band == 0
                    ? "under 10%"
                    : band == BANDS - 1 ? "90% to 100%" : band * 10 + "% to " + (band * 10 + 9) + "%";
            rows.add(List.of(label, bands[band]));
        }
        return rows;
    }

    private static AnalysisReportJson.Table rootsTable(Mix mix) {
        List<List<Object>> rows = new ArrayList<>();
        mix.configuration().roots().forEach((root, files) -> rows.add(List.of(root, files)));
        return new AnalysisReportJson.Table(List.of(kindColumn("first-element", "First element"), filesColumn("Files")), rows);
    }

    private static AnalysisReportJson.Table definitionsTable(Mix mix) {
        List<List<Object>> rows = new ArrayList<>();
        mix.configuration().interfaceDefinitions().forEach((definition, files) -> rows.add(List.of(definition, files)));
        return new AnalysisReportJson.Table(
                List.of(kindColumn("definition", "Interface definition"), filesColumn("Files")), rows);
    }

    private static AnalysisReportJson.Table unrecognisedTable(Mix mix) {
        List<List<Object>> rows = new ArrayList<>();
        mix.unrecognisedLeadingBytes().forEach((leading, files) -> rows.add(List.of(leading, files)));
        return new AnalysisReportJson.Table(List.of(kindColumn("begins-with", "Begins with"), filesColumn("Files")), rows);
    }

    /** A table of the page from a table of the JSON: a number in a number cell, any other cell text, or code where asked. */
    private static String pageTable(AnalysisReportJson.Table table, boolean firstCellIsCode) {
        String[] headers =
                table.columns().stream().map(AnalysisReportJson.Column::label).toArray(String[]::new);
        StringBuilder rows = new StringBuilder();
        for (List<Object> cells : table.rows()) {
            String[] drawn = new String[cells.size()];
            for (int i = 0; i < drawn.length; i++) {
                drawn[i] = cells.get(i) instanceof Number number
                        ? ReportPage.numberCell(number.longValue())
                        : i == 0 && firstCellIsCode
                                ? ReportPage.codeCell(cells.get(i).toString())
                                : ReportPage.textCell(cells.get(i).toString());
            }
            rows.append(ReportPage.row(drawn));
        }
        return ReportPage.table(ReportPage.headerRow(headers), rows.toString());
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
