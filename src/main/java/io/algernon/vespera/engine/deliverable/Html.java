package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.DeliverableLanguage;

/**
 * The skeleton every page of the tree stands on, and the one escaping rule (ADR-274 §1.1, §1.3).
 *
 * <p><b>One rule, wherever a value lands.</b> {@link #escape} writes {@code & < > " '} as entities and is the only
 * thing that makes a value safe, in text and in an attribute alike. Written here again, and not read from {@code
 * pipeline}'s {@code ReportPage}, because a capability module cannot name {@code pipeline} (ADR-274 §1.1); the
 * stylesheet is that page's shared rules.
 */
public final class Html {

    /**
     * {@code ReportPage}'s shared rules (font, margins, table and cell borders), and a picture held to its page.
     * Contract (ADR-380): drawn dark and only dark, whatever the system's setting or the control panel's theme; one
     * {@code :root} rule declares {@code color-scheme: dark} and every colour, and no other rule writes one.
     */
    private static final String STYLES = ":root { color-scheme: dark; --ground: #1a1922; --surface: #25232f; --ink: #ecebf3; --dim: #aeaac2; --rule: #3b3949; --accent: #b0a6ff; }\n"
            + "body { background: var(--ground); color: var(--ink); font-family: sans-serif; margin: 2em; max-width: 900px; }\n"
            + "a { color: var(--accent); }\n"
            + "table { border-collapse: collapse; margin-bottom: 1.5em; width: 100%; }\n"
            + "td, th { border: 1px solid var(--rule); padding: 0.3em 0.8em; text-align: left; }\n"
            + "td.count { text-align: right; }\n"
            + "code.name { font-size: 1.2em; padding: 0.2em 0.4em; background: var(--surface); }\n"
            + "blockquote { color: var(--dim); font-style: italic; margin: 0.4em 0 0 1em; }\n"
            + "li { margin-bottom: 0.6em; }\n"
            + "img { max-width: 100%; }\n";

    private Html() {}

    /** {@code text} with each of {@code & < > " '} written as its entity, wherever the text lands. */
    public static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Everything of a page before its body: the document type, the language the page names (ADR-321 §4), the character
     * set, the title and the stylesheet, which draws the page dark alone (ADR-380).
     */
    static String head(String title, DeliverableLanguage language) {
        return "<!DOCTYPE html>\n<html lang=\"" + language.code() + "\">\n<head>\n<meta charset=\"UTF-8\">\n<title>" + escape(title)
                + "</title>\n<style>\n" + STYLES + "</style>\n</head>\n<body>\n";
    }

    /** Everything of a page after its body. */
    static String tail() {
        return "</body>\n</html>\n";
    }
}
