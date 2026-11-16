package io.algernon.vespera.engine.synthesis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a sentence of a topic's overview ends (ADR-402 section 2), so that the check can ask each for a citation: at
 * {@code .}, {@code !}, {@code ?} or {@code …}, with any closing quotation marks or bracket after it, followed by a
 * blank and then a capital letter, a quotation mark, {@code (} or {@code [}, or by the end of the writing.
 *
 * <p>It does not end after a run of single letters each followed by a full stop, a single capital letter, or one of the
 * record's titles, nor before a digit or a lower-case letter. A citation that opens a sentence belongs to the one
 * before it. The rule errs towards fewer sentences.
 */
public final class OverviewSentences {

    private static final String ENDS = ".!?…";

    private static final String CLOSERS = "\"'”’»)]";

    private static final String OPENERS = "\"'“‘«([";

    private static final Set<String> TITLES = Set.of(
            "mr", "mrs", "ms", "dr", "st", "sr", "jr", "sen", "rep", "gov", "gen", "no", "nr", "sig", "sigg", "dott",
            "prof", "ing", "avv", "art", "pag", "pp", "vol", "cap", "fig");

    /** Single letters each followed by a full stop but the last: {@code U.S}, {@code S.p.A}, {@code e.g}. */
    private static final Pattern LETTERS_WITH_STOPS = Pattern.compile("(\\p{L}\\.)+\\p{L}");

    private OverviewSentences() {}

    /** The sentences of {@code writing}, in order, each as written and without the blanks around it. */
    public static List<String> of(String writing) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        int length = writing.length();
        int at = 0;
        while (at < length) {
            if (ENDS.indexOf(writing.charAt(at)) < 0) {
                at++;
                continue;
            }
            int terminators = at;
            while (terminators < length && ENDS.indexOf(writing.charAt(terminators)) >= 0) {
                terminators++;
            }
            int end = terminators;
            while (end < length && CLOSERS.indexOf(writing.charAt(end)) >= 0) {
                end++;
            }
            if (endsASentence(writing, at, terminators, end)) {
                end = withTheCitationsThatFollow(writing, end);
                addIfNotBlank(sentences, writing.substring(start, end));
                start = end;
                at = end;
            } else {
                at = end;
            }
        }
        if (start < length) {
            addIfNotBlank(sentences, writing.substring(start));
        }
        return List.copyOf(sentences);
    }

    private static boolean endsASentence(String writing, int firstTerminator, int terminators, int end) {
        if (end < writing.length()) {
            if (!Character.isWhitespace(writing.charAt(end))) {
                return false;
            }
            int next = end;
            while (next < writing.length() && Character.isWhitespace(writing.charAt(next))) {
                next++;
            }
            if (next < writing.length()) {
                int codePoint = writing.codePointAt(next);
                if (!Character.isUpperCase(codePoint) && OPENERS.indexOf(codePoint) < 0) {
                    return false;
                }
            }
        }
        boolean onlyAFullStop = terminators - firstTerminator == 1 && writing.charAt(firstTerminator) == '.';
        return !(onlyAFullStop && isAnAbbreviation(writing, firstTerminator));
    }

    /**
     * Whether the word before the full stop at {@code stop} is one of the letters, the single capital or the titles.
     */
    private static boolean isAnAbbreviation(String writing, int stop) {
        int from = stop;
        while (from > 0) {
            char before = writing.charAt(from - 1);
            if (Character.isLetter(before) || before == '.' || before == '\'' || before == '’') {
                from--;
            } else {
                break;
            }
        }
        String token = writing.substring(from, stop);
        if (token.isEmpty()) {
            return false;
        }
        if (LETTERS_WITH_STOPS.matcher(token).matches()) {
            return true;
        }
        int apostrophe = Math.max(token.lastIndexOf('\''), token.lastIndexOf('’'));
        String word = token.substring(apostrophe + 1);
        if (word.length() == 1 && Character.isUpperCase(word.charAt(0))) {
            return true;
        }
        return TITLES.contains(word.toLowerCase(Locale.ROOT));
    }

    /** {@code end} moved past any citations that open what follows: they belong to the sentence just ended. */
    private static int withTheCitationsThatFollow(String writing, int end) {
        int taken = end;
        while (true) {
            int next = taken;
            while (next < writing.length() && Character.isWhitespace(writing.charAt(next))) {
                next++;
            }
            Matcher citation = Citation.AS_WRITTEN.matcher(writing).region(next, writing.length());
            if (next < writing.length() && citation.lookingAt()) {
                taken = citation.end();
            } else {
                return taken;
            }
        }
    }

    private static void addIfNotBlank(List<String> sentences, String sentence) {
        String stripped = sentence.strip();
        if (!stripped.isEmpty()) {
            sentences.add(stripped);
        }
    }
}
