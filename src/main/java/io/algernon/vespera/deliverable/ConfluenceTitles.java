package io.algernon.vespera.deliverable;

/**
 * The titles of the Confluence rendering's pages (ADR-310): a cluster's {@code TEXT (P.C · MARKER)} or {@code TEXT
 * (P.C page K · MARKER)}, a seed partition's {@code TEXT (P · MARKER)}, and the root page's {@code Vespera MARKER}.
 *
 * <p><b>The suffix is never cut</b> (ADR-310 §4): every control character of the text becomes a space, runs of
 * whitespace one space, the text is trimmed, and where the whole would pass {@value #LONGEST_TITLE} UTF-16 code units
 * the text is cut at a code point's boundary and {@code …} appended. Nothing in a title is escaped; that is the
 * renderer's, where a title is written into a body.
 */
final class ConfluenceTitles {

    /** The most UTF-16 code units a title has, Confluence's own limit (ADR-310 §4). */
    static final int LONGEST_TITLE = 255;

    /** How many hexadecimal characters of the run's id its marker is (ADR-310 §1). */
    static final int MARKER_LENGTH = 12;

    /** What stands for the text a title's cut leaves out. */
    private static final String CUT = "…";

    private ConfluenceTitles() {}

    /** The generation run's marker: the first {@value #MARKER_LENGTH} characters of its id (ADR-310 §1). */
    static String markerOf(String runId) {
        return runId.substring(0, Math.min(MARKER_LENGTH, runId.length()));
    }

    /** The root page's title (ADR-310 §3). */
    static String root(String marker) {
        return "Vespera " + marker;
    }

    /** A seed partition's page's title, {@code place} its padded order (ADR-310 §3). */
    static String partition(String stem, String place, String marker) {
        return titled(stem, " (" + place + " · " + marker + ")");
    }

    /**
     * A cluster's page's title, {@code place} being {@code P.C} and {@code page} counted from 1: the first page carries no
     * page number (ADR-310 §2).
     */
    static String cluster(String text, String place, int page, String marker) {
        return titled(text, " (" + place + (page == 1 ? "" : " page " + page) + " · " + marker + ")");
    }

    /** {@code text} folded and, where the whole would pass the limit, cut, then {@code suffix} whole (ADR-310 §4). */
    private static String titled(String text, String suffix) {
        StringBuilder spaced = new StringBuilder();
        text.codePoints().forEach(point -> spaced.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        String folded = spaced.toString().replaceAll("\\s+", " ").strip();
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
