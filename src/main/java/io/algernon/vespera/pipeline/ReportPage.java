package io.algernon.vespera.pipeline;

import java.util.List;

/**
 * The one module that assembles a report page's HTML (ADR-130): the document skeleton, the
 * stylesheet, escaping and the table/row scaffolding all live here, so the eight reports written
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
 * leverage at eight call sites and locality for the next change to any of those rules.
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
     * What the progress pages add (ADR-267 section 4): every colour is a custom property, one set for each colour
     * scheme. Text colours are at least 4.5:1 and state and series colours at least 3:1 on the page's background.
     */
    private static final String DRAWN_STYLES =
            ":root { color-scheme: light dark; --page: #f9f9f7; --surface: #ffffff; --text: #0b0b0b; --muted: #52514e;"
            + " --border: #c3c2b7; --grid: #e1e0d9; --series-1: #236fcc; --series-2: #c04a1a;"
            + " --finished: #1a7f37; --running: #236fcc; --gate: #9a6700; --failed: #cf222e; }\n"
            + "@media (prefers-color-scheme: dark) { :root { --page: #0d0d0d; --surface: #1a1a19; --text: #ffffff;"
            + " --muted: #c3c2b7; --border: #383835; --grid: #2c2c2a; --series-1: #3987e5; --series-2: #e8743b;"
            + " --finished: #3fb950; --running: #3987e5; --gate: #d29922; --failed: #f85149; } }\n"
            + "body { background: var(--page); color: var(--text); }\n"
            + "a { color: var(--series-1); }\n"
            + "td, th { border-color: var(--border); }\n"
            + ".tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px;"
            + " margin: 1em 0; }\n"
            + ".tile { background: var(--surface); border: 1px solid var(--border); border-radius: 10px;"
            + " padding: 14px 16px; font-size: 1.15em; font-weight: 600; }\n"
            + "progress { width: 100%; max-width: 600px; }\n"
            + "svg { display: block; max-width: 100%; height: auto; margin: 0.6em 0 1.2em; background: var(--surface);"
            + " border: 1px solid var(--border); border-radius: 10px; }\n"
            + "svg text { fill: var(--text); font: 12px sans-serif; }\n"
            + "svg .tick { fill: var(--muted); }\n"
            + "svg .axis { stroke: var(--border); }\n"
            + "svg .line { fill: none; stroke-width: 2; }\n"
            + ".s1 { stroke: var(--series-1); } .s2 { stroke: var(--series-2); }\n"
            + "circle.s1 { fill: var(--series-1); } circle.s2 { fill: var(--series-2); }\n"
            + "text.s1 { fill: var(--series-1); stroke: none; font-weight: 600; }\n"
            + "text.s2 { fill: var(--series-2); stroke: none; font-weight: 600; }\n"
            + "g[data-state=\"finished\"] rect { fill: var(--finished); }\n"
            + "g[data-state=\"running\"] rect { fill: var(--running); }\n"
            + "g[data-state=\"stopped at a gate\"] rect { fill: var(--gate); }\n"
            + "g[data-state=\"failed\"] rect { fill: var(--failed); }\n"
            + "g[data-state=\"not started\"] text { fill: var(--muted); }\n"
            + ".liveness { font-weight: 600; color: var(--finished); }\n"
            + ".liveness.stale { color: var(--failed); }\n";

    /**
     * Wraps a report's title and body in the self-contained document every page is (ADR-075,
     * ADR-086, ADR-087, ADR-095, ADR-088, ADR-107): a doctype, a language, a character set, a title,
     * the shared stylesheet, and the body between {@code <body>} and {@code </body>}.
     *
     * @param title what the browser tab and the document title carry; escaped here
     * @param body everything between {@code <body>} and {@code </body>}, already assembled
     */
    static String render(String title, String body) {
        return head(title) + body + tail();
    }

    /**
     * Everything of a page before its body: what {@link #render} writes first. Apart from {@link #render}
     * for a page written as its rows come, which never holds its body whole (ADR-220 section 6).
     */
    static String head(String title) {
        return head(title, "");
    }

    /**
     * Like {@link #render}, for a page a browser reloads every {@code seconds} (ADR-265 section 1): a refresh
     * in the head and nothing else different.
     */
    static String reloading(String title, String body, int seconds) {
        return head(title, "<meta http-equiv=\"refresh\" content=\"" + seconds + "\">\n") + body + tail();
    }

    /** Like {@link #render}, for the progress pages (ADR-267 section 4): their stylesheet follows the shared one. */
    static String drawn(String title, String body) {
        return head(title, "", DRAWN_STYLES) + body + tail();
    }

    /** Like {@link #reloading}, for the progress pages: their stylesheet follows the shared one. */
    static String drawnReloading(String title, String body, int seconds) {
        return head(title, "<meta http-equiv=\"refresh\" content=\"" + seconds + "\">\n", DRAWN_STYLES) + body + tail();
    }

    private static String head(String title, String extraMeta) {
        return head(title, extraMeta, "");
    }

    private static String head(String title, String extraMeta, String moreStyles) {
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
                + extraMeta
                + "<title>" + escape(title) + "</title>\n<style>\n"
                + STYLES
                + moreStyles
                + "</style>\n</head>\n<body>\n";
    }

    /** Everything of a page after its body: what {@link #render} writes last. */
    static String tail() {
        return "</body>\n</html>\n";
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
        return tableOpened() + headers + rows + tableClosed();
    }

    /** What opens a table, for a page that streams its rows between this and {@link #tableClosed()}. */
    static String tableOpened() {
        return "<table>\n";
    }

    /** What closes a table {@link #tableOpened()} opened. */
    static String tableClosed() {
        return "</table>\n";
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