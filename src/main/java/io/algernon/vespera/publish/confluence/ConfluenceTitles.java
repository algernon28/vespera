package io.algernon.vespera.publish.confluence;

import io.algernon.vespera.engine.deliverable.FixedWords;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;

/**
 * The titles of the Confluence rendering's pages (ADR-378 §1): a cluster's {@code TEXT (P.C · code)} or {@code TEXT
 * (P.C page K · code)}, a seed partition's {@code TEXT (P · code)}, and the root page's {@code Vespera_<code>}. No
 * title carries the run's marker.
 *
 * <p><b>The suffix is never cut</b> (ADR-310 §4): every control character of the text becomes a space, runs of
 * whitespace one space, the text is trimmed, and where the whole would pass {@value #LONGEST_TITLE} UTF-16 code units
 * the text is cut at a code point's boundary and {@code …} appended. Nothing in a title is escaped; that is the
 * renderer's, where a title is written into a body.
 */
final class ConfluenceTitles {

    /** The most UTF-16 code units a title has, Confluence's own limit (ADR-310 §4). */
    static final int LONGEST_TITLE = 255;

    /** What stands for the text a title's cut leaves out. */
    private static final String CUT = "…";

    private ConfluenceTitles() {}

    /** What the root page is titled where the operator names no title (ADR-398 §2). */
    static final String DEFAULT_ROOT_TITLE = "Vespera_{language}";

    /** The language's code, where a root page's title says it (ADR-398 §3). */
    static final String LANGUAGE_PLACEHOLDER = "{language}";

    /** The root page's title, {@code Vespera_<code>}, never cut (ADR-378 §1). */
    static String root(DeliverableLanguage language) {
        return root(language, null);
    }

    /**
     * The root page's title from {@code stated}, as the operator states it, or {@code null} for none (ADR-398 §2, §3):
     * folded as a title's text is, each {@code {language}} replaced by the language's code, and where it holds none an
     * underscore and the code appended. Never cut; a title past {@link #LONGEST_TITLE} is the publish's to refuse.
     */
    static String root(DeliverableLanguage language, String stated) {
        String folded = stated == null ? "" : fold(stated);
        if (folded.isEmpty()) {
            folded = DEFAULT_ROOT_TITLE;
        }
        return folded.contains(LANGUAGE_PLACEHOLDER)
                ? folded.replace(LANGUAGE_PLACEHOLDER, language.code())
                : folded + "_" + language.code();
    }

    private static String fold(String text) {
        StringBuilder spaced = new StringBuilder();
        text.codePoints().forEach(point -> spaced.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return spaced.toString().replaceAll("\\s+", " ").strip();
    }

    /** The suffix's last part: the language's code, after a middle dot with a space on each side (ADR-378 §1). */
    private static String code(DeliverableLanguage language) {
        return " · " + language.code();
    }

    /** A seed partition's page's title, {@code place} its padded order (ADR-378 §1). */
    static String partition(String stem, String place, DeliverableLanguage language) {
        return titled(stem, " (" + place + code(language) + ")");
    }

    /**
     * A cluster's page's title, {@code place} being {@code P.C} and {@code page} counted from 1: the first page carries no
     * page number (ADR-310 §2, ADR-328 §1).
     */
    static String cluster(String text, String place, int page, DeliverableLanguage language) {
        return titled(text, " (" + place + (page == 1 ? "" : FixedWords.pageInATitle(language) + page)
                + code(language) + ")");
    }

    /** {@code text} folded and, where the whole would pass the limit, cut, then {@code suffix} whole (ADR-310 §4). */
    private static String titled(String text, String suffix) {
        String folded = fold(text);
        if (folded.isEmpty()) {
            return suffix.strip();
        }
        if (folded.length() + suffix.length() <= LONGEST_TITLE) {
            return folded + suffix;
        }
        int room = LONGEST_TITLE - suffix.length() - CUT.length();
        int end = room;
        // A cut between the two halves of a surrogate pair is moved before the pair, so no half is left.
        if (end > 0 && Character.isHighSurrogate(folded.charAt(end - 1))) {
            end--;
        }
        return folded.substring(0, end).stripTrailing() + CUT + suffix;
    }
}
