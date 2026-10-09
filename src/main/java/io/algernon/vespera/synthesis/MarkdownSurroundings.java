package io.algernon.vespera.synthesis;

/**
 * Every Markdown text surrounding a value lands in, and the one rule each is escaped by (ADR-134,
 * ADR-136, ADR-138, ADR-148; ADR-212 §2).
 *
 * <p><b>A value is only ever dangerous with respect to the structure it lands in</b>, which is why there
 * is one constant per surrounding and not one rule: a table cell, an ATX heading and a membership entry
 * each fold or do not, and escape the characters their own structure makes live. What a surrounding
 * answers to decides the form its rule takes, never whether it is one (ADR-137 §4): these three answer
 * to a reader and escape with a backslash; a link destination answers to a resolver and is
 * percent-encoded by {@link ArchiveLink}; the CSV answers to a parser and doubles a quote in {@link
 * ManifestCsv}. The two are not constants here for that reason.
 *
 * <p><b>The backslash goes first, on a ground that is not about the data (ADR-134).</b> Every rule
 * inserts escape characters of its own, so a literal backslash already present must be escaped before
 * it can merge with what is added after it. A label reading {@code Retrofits \| Phase 2} would otherwise
 * become {@code Retrofits \\| Phase 2}, an escaped backslash followed by a live pipe, which is #246's
 * defect reintroduced by the rule written to prevent it.
 *
 * <p><b>{@code <} and {@code &} are the hazard every surrounding shares (ADR-136).</b> Written through, a
 * tag goes live where a renderer honours HTML, is deleted outright by GitHub's sanitiser with nothing
 * left to say a word was removed, and a run such as {@code &copy;} is decoded to {@code ©}. The escape is
 * the backslash escape {@code \<} and {@code \&}, not the entities {@code &lt;} and {@code &amp;}, which
 * are a different operation and damage the rendered page and the plain-text reader alike.
 *
 * <p><b>{@code [} and {@code ]} compose a link or an image the value never asked for (ADR-138)</b>, in a
 * cell and a heading exactly as in a membership entry, so they are escaped in all three, and the index's
 * link text is a cell that happens to carry a link rather than a rule of its own: escaping a bracket a
 * second time there turns {@code \[} into {@code \\[}, an escaped backslash followed by a live bracket.
 *
 * <p><b>A backtick is escaped in all three, unconditionally (ADR-148)</b>: two of them around any text
 * form a code span in every renderer measured.
 *
 * <p><b>The pipe is the cell's alone</b>, because a table row ends at the first unescaped one. A heading
 * and a list item have no structure a pipe closes.
 */
enum MarkdownSurroundings {

    /** One cell of the index: folded to one line, and the pipe escaped as well. */
    TABLE_CELL(true, "\\<&[]|`"),

    /** An ATX heading: folded to one line, since a line break ends the heading. */
    ATX_HEADING(true, "\\<&[]`"),

    /** A line of a membership list, and a picture's alt text: not folded, a path being one line already. */
    MEMBERSHIP_ENTRY(false, "\\<&[]`");

    private final boolean folds;

    private final String escaped;

    MarkdownSurroundings(boolean folds, String escaped) {
        this.folds = folds;
        this.escaped = escaped;
    }

    /**
     * {@code value} as this surrounding carries it: folded onto one line where {@link #folds()}, then
     * each of {@link #escaped()} written behind a backslash, in that order.
     */
    String escape(String value) {
        String result = folds ? onOneLine(value) : value;
        for (char markup : escaped.toCharArray()) {
            result = result.replace(String.valueOf(markup), "\\" + markup);
        }
        return result;
    }

    /** Whether a line break in a value is folded to a space before it is escaped. */
    boolean folds() {
        return folds;
    }

    /** The characters this surrounding escapes, in the order it does, the backslash first. */
    String escaped() {
        return escaped;
    }

    /**
     * {@code value} as a single line (#246).
     *
     * <p>A table row ends at a line break and so does a heading, so a value carrying one does not merely
     * look wrong — it ends the thing it was written into and turns what follows into prose. The break is
     * folded into a space rather than escaped, because Markdown has no escape for it in either place,
     * and a title that wrapped in the document it came from means one line here.
     */
    static String onOneLine(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }
}
