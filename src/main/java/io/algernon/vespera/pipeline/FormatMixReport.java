package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders what stage 1 found across a corpus as one self-contained HTML file (ADR-095) — plain,
 * hand-assembled HTML, no templating library, the same shape {@link ConfidenceDistributionReport}
 * and {@link SeedCorpusComparisonReport} already established (ADR-046).
 *
 * <p>The page exists because nobody has walked a real archive. It is what turns "should intact
 * non-documents be removed" from an argument into a decision with a source, so two things about it
 * are load-bearing rather than presentational: files nothing was read from are counted apart from
 * files that were read and matched nothing, and the page states in its own words what it cannot
 * see.
 */
final class FormatMixReport {

    private FormatMixReport() {}

    /** What stage 1 found, accumulated over the occurrences it examined. */
    record Mix(
            Map<DetectedFormat, Integer> byFormat,
            Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype,
            Map<String, Integer> unrecognisedLeadingBytes) {}

    /**
     * How each class is named for a reader who has never seen this project (ADR-052): report-visible
     * text carries no vocabulary the reader would have to look up.
     */
    private static final Map<DetectedFormat, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(DetectedFormat.PDF, "PDF documents");
        LABELS.put(DetectedFormat.IMAGE, "Images");
        LABELS.put(DetectedFormat.WORDPROCESSING, "Word processing documents");
        LABELS.put(DetectedFormat.JAVA_ARCHIVE, "Java archives");
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
        StringBuilder page = new StringBuilder();
        page.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<title>What the files turned out to be</title>\n<style>\n")
                .append("body { font-family: sans-serif; margin: 2em; max-width: 900px; }\n")
                .append("table { border-collapse: collapse; margin-bottom: 1.5em; }\n")
                .append("td, th { border: 1px solid #ccc; padding: 0.3em 0.8em; text-align: left; }\n")
                .append("td.count { text-align: right; }\n")
                .append("</style>\n</head>\n<body>\n")
                .append("<h1>What the files turned out to be</h1>\n")
                .append("<p>Every file was read far enough to recognise what it is. What follows is what"
                        + " was found, and it removes nothing: it exists so that a later decision about"
                        + " what to leave out has a measurement behind it rather than a guess.</p>\n");

        page.append("<table>\n<tr><th>What it was found to be</th><th>Files</th></tr>\n");
        for (Map.Entry<DetectedFormat, String> label : LABELS.entrySet()) {
            page.append("<tr><td>")
                    .append(escape(label.getValue()))
                    .append("</td><td class=\"count\">")
                    .append(mix.byFormat().getOrDefault(label.getKey(), 0))
                    .append("</td></tr>\n");
        }
        page.append("</table>\n");

        page.append("<p><em>Nothing could be read</em> counts files that were empty or would not open, so"
                + " nothing in them was ever examined. Those are deliberately not counted as <em>of no"
                + " known kind</em>, which means the opposite: the file was read, and what was inside it"
                + " matched nothing known.</p>\n");

        page.append("<h2>Finer labels, taken from filenames</h2>\n")
                .append("<p>The classes above come from what is inside each file. The labels below come"
                        + " from the filename instead, and only where what is inside cannot say: an older"
                        + " Word document and a thumbnail cache are built the same way, and one piece of"
                        + " text reads like any other. A name can be wrong, so these deserve less trust"
                        + " than the counts above.</p>\n");
        if (mix.bySubtype().isEmpty()) {
            page.append("<p>No file carried a name that added anything.</p>\n");
        } else {
            page.append("<table>\n<tr><th>Within</th><th>Named as</th><th>Files</th></tr>\n");
            for (Map.Entry<DetectedFormat, Map<DetectedSubtype, Integer>> within :
                    mix.bySubtype().entrySet()) {
                for (Map.Entry<DetectedSubtype, Integer> named :
                        within.getValue().entrySet()) {
                    page.append("<tr><td>")
                            .append(escape(LABELS.get(within.getKey())))
                            .append("</td><td>")
                            .append(escape(SUBTYPE_LABELS.get(named.getKey())))
                            .append("</td><td class=\"count\">")
                            .append(named.getValue())
                            .append("</td></tr>\n");
                }
                page.append("<tr><td>")
                        .append(escape(LABELS.get(within.getKey())))
                        .append("</td><td>nothing the name could add</td><td class=\"count\">")
                        .append(unlabelled(mix, within.getKey()))
                        .append("</td></tr>\n");
            }
            page.append("</table>\n");
        }

        page.append("<h2>What could not be recognised</h2>\n");
        if (mix.unrecognisedLeadingBytes().isEmpty()) {
            page.append("<p>Every file that was read was recognised.</p>\n");
        } else {
            page.append("<p>Grouped by the four bytes each one begins with, so that a single kind of file"
                            + " arriving in bulk shows as one large group rather than as a total nobody can"
                            + " act on.</p>\n")
                    .append("<table>\n<tr><th>Begins with</th><th>Files</th></tr>\n");
            for (Map.Entry<String, Integer> leading :
                    mix.unrecognisedLeadingBytes().entrySet()) {
                page.append("<tr><td><code>")
                        .append(escape(leading.getKey()))
                        .append("</code></td><td class=\"count\">")
                        .append(leading.getValue())
                        .append("</td></tr>\n");
            }
            page.append("</table>\n");
        }

        page.append("<h2>What this page cannot see</h2>\n")
                .append("<p>A file can be perfectly readable text and still not be a document: a folder"
                        + " settings file, an editor's leftover backup. Those are counted as text above,"
                        + " and this page cannot tell them apart from a written note, because nothing"
                        + " inside them says which is which. So a small <em>of no known kind</em> count"
                        + " does not mean an archive is free of clutter. It means the clutter that is"
                        + " left is the kind this measurement does not reach.</p>\n");

        page.append("</body>\n</html>\n");
        return page.toString();
    }

    /** The remainder within a class: examined, but carrying a name that named no member of it. */
    private static int unlabelled(Mix mix, DetectedFormat format) {
        int named = mix.bySubtype().getOrDefault(format, Map.of()).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return mix.byFormat().getOrDefault(format, 0) - named;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
