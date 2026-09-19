package io.algernon.vespera.pipeline;

import java.util.List;

/**
 * The one module that assembles a report page's HTML (ADR-130): the document skeleton, the
 * stylesheet, escaping and the table/row scaffolding all live here, so the six reports written
 * beside the database supply only their title, their prose and their rows.
 *
 * <p><b>It is still hand-assembled HTML.</b> There is no templating library and no new dependency
 * (ADR-046): the page is composed by string concatenation exactly as before, and only the place
 * that concatenation happens has moved. ADR-046 forbids a template engine, not a shared module.
 *
 * <p><b>The interface is the seam, and it is small on purpose.</b> A caller learns six names —
 * {@link #render}, {@link #escape}, {@link #heading}, {@link #paragraph}, {@link #table} and
 * {@link #bulletList} — plus the cell constructors that belong to a row ({@link #headerRow},
 * {@link #row}, {@link #textCell}, {@link #numberCell}, {@link #htmlCell}, {@link #linkCell},
 * {@link #codeCell}). Behind it sits every rule that used to be copied six times: the doctype, the
 * character set, the tag the page is titled with, the stylesheet, the escape table, and which cells
 * are right-aligned. That is depth — a whole page per unit of interface learned — and it buys
 * leverage at six call sites and locality for the next change to any of those rules.
 *
 * <p><b>Escaping happens inside the cell constructors, not at the call site.</b> A caller cannot
 * pass unescaped text to {@link #textCell}, {@link #linkCell} or {@link #codeCell}; it has to reach
 * for {@link #htmlCell} to say it means raw markup. The duplication this replaces put the same
 * three {@code replace} calls in six files, which is six places a later escaping rule would have had
 * to be changed and six chances for one to drift.
 *
 * <p>The prose stays in each report. The module owns the scaffolding around it; what a page says is
 * the report's, and only the report can know it.
 */
final class ReportPage {

    private ReportPage() {}

    /**
     * The stylesheet every report page shares. One copy, so the look of the reports changes in one
     * place rather than six; the union of what the six pages used, so none lost a rule it relied on.
     */
    private static final String STYLES =
            "body { font-family: sans-serif; margin: 2em; max-width: 900px; }\n"
            + "table { border-collapse: collapse; margin-bottom: 1.5em; width: 100%; }\n"
            + "td, th { border: 1px solid #ccc; padding: 0.3em 0.8em; text-align: left; }\n"
            + "td.count { text-align: right; }\n"
            + "code.name { font-size: 1.2em; padding: 0.2em 0.4em; background: #eee; }\n"
            + ".bar { background: #4a90d9; height: 1em; }\n"
            + "blockquote { color: #444; font-style: italic; margin: 0.4em 0 0 1em; }\n"
            + "li { margin-bottom: 0.6em; }\n";

    /**
     * Wraps a report's title and body in the self-contained document every page is (ADR-075,
     * ADR-086, ADR-087, ADR-095, ADR-088, ADR-107): a doctype, a language, a character set, a title,
     * the shared stylesheet, and the body between {@code <body>} and {@code </body>}.
     *
     * @param title what the browser tab and the document title carry; escaped here
     * @param body everything between {@code <body>} and {@code </body>}, already assembled
     */
    static String render(String title, String body) {
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<title>" + escape(title) + "</title>\n<style>\n"
                + STYLES
                + "</style>\n</head>\n<body>\n"
                + body
                + "</body>\n</html>\n";
    }

    /**
     * HTML-escapes the three characters that would otherwise be read as markup. The one escape table
     * the reports share; it deliberately leaves quotes alone, because nothing here writes text into
     * an attribute unquoted.
     */
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** A heading of the given level, with its text escaped. */
    static String heading(int level, String text) {
        return "<h" + level + ">" + escape(text) + "</h" + level + ">\n";
    }

    /**
     * A paragraph around already-assembled inline markup. The prose is raw because it is the
     * report's to write: it carries {@code <code>}, {@code <em>} and {@code <strong>} the writer put
     * there, and only the values dropped into it use {@link #escape}.
     */
    static String paragraph(String html) {
        return "<p>" + html + "</p>\n";
    }

    /** A table around a header row and already-assembled body rows. */
    static String table(String headers, String rows) {
        return "<table>\n" + headers + rows + "</table>\n";
    }

    /** A table's header row, one escaped {@code <th>} per label. */
    static String headerRow(String... labels) {
        StringBuilder row = new StringBuilder("<tr>");
        for (String label : labels) {
            row.append("<th>").append(escape(label)).append("</th>");
        }
        return row.append("</tr>\n").toString();
    }

    /** A table's body row from already-assembled cells. */
    static String row(String... cells) {
        StringBuilder row = new StringBuilder("<tr>");
        for (String cell : cells) {
            row.append(cell);
        }
        return row.append("</tr>\n").toString();
    }

    /** A left-aligned cell holding escaped text. */
    static String textCell(String text) {
        return "<td>" + escape(text) + "</td>";
    }

    /**
     * A right-aligned cell holding a number.
     *
     * <p>Right alignment is why this is not {@link #textCell}: it is the one presentational rule
     * every report leans on, and it lives behind the interface rather than at each call site.
     */
    static String numberCell(long value) {
        return numberCell(Long.toString(value));
    }

    /**
     * A right-aligned cell holding an already-formatted display value — a number to two places, or
     * an entity such as {@code &mdash;} where there was no number to show.
     */
    static String numberCell(String display) {
        return "<td class=\"count\">" + display + "</td>";
    }

    /** A cell holding raw markup the report assembled — a bar, a link, a quotation. */
    static String htmlCell(String html) {
        return "<td>" + html + "</td>";
    }

    /** A cell holding a link to {@code href}, labelled {@code label}; both escaped. */
    static String linkCell(String href, String label) {
        return "<td><a href=\"" + escape(href) + "\">" + escape(label) + "</a></td>";
    }

    /** A cell holding escaped text inside {@code <code>}. */
    static String codeCell(String text) {
        return "<td><code>" + escape(text) + "</code></td>";
    }

    /** A bullet list of statements, each escaped. */
    static String bulletList(List<String> statements) {
        StringBuilder list = new StringBuilder("<ul>\n");
        for (String statement : statements) {
            list.append("<li>").append(escape(statement)).append("</li>\n");
        }
        return list.append("</ul>\n").toString();
    }
}