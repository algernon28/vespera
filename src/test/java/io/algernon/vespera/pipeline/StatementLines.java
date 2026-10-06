package io.algernon.vespera.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The lines a stage writes around its statements, read out of what an invocation logged, and the lines
 * ADR-193 sections 4.1 and 4.3 and ADR-199 section 3 give a statement, for the whole-job tests to compare
 * them with.
 *
 * <p>A statement line opens with the stage's own name and one of four verbs: {@code is reading}, {@code
 * read}, {@code is counting}, {@code counted}. {@link #of} keeps those lines of one stage, in the order they
 * were written, and puts {@value #SECONDS} where an after-line states its seconds to one decimal place, so a
 * test can hold the whole sequence to an exact list: every line once, in the order the step issues its
 * statements, and nothing else under those verbs. A duration written any other way is left as it was
 * written, and so matches no expected line.
 *
 * <p>It reads a line from where the stage's name begins, so it takes the messages of a list appender and
 * the console's lines alike.
 */
final class StatementLines {

    /** What stands for the seconds of an after-line. */
    static final String SECONDS = "<S>";

    private static final List<String> VERBS = List.of(" is reading ", " read ", " is counting ", " counted ");

    /** What stands for the total of a counted read, where a test does not work it out. */
    static final String ROWS = "<N>";

    private static final Pattern SECONDS_TO_ONE_DECIMAL = Pattern.compile(" in \\d+\\.\\d s$");

    private static final Pattern OVER_UP_TO = Pattern.compile(", over up to [\\d,]+ rows$");

    private StatementLines() {}

    /** The statement lines of {@code stage} among {@code lines}, in order, each from the stage's name on. */
    static List<String> of(List<String> lines, String stage) {
        List<String> said = new ArrayList<>();
        for (String line : lines) {
            for (String verb : VERBS) {
                int at = line.indexOf(stage + verb);
                if (at >= 0) {
                    said.add(SECONDS_TO_ONE_DECIMAL
                            .matcher(line.substring(at).stripTrailing())
                            .replaceFirst(" in " + SECONDS + " s"));
                    break;
                }
            }
        }
        return said;
    }

    /** The two lines of a timed read: {@code <stage> is reading <what>}, then {@code <stage> read <what> in <S> s}. */
    static List<String> timedRead(String stage, String what) {
        return List.of(stage + " is reading " + what, stage + " read " + what + " in " + SECONDS + " s");
    }

    /** The two lines of a timed count: {@code <stage> is counting <what>}, then {@code <stage> counted <what> in <S> s}. */
    static List<String> timedCount(String stage, String what) {
        return List.of(stage + " is counting " + what, stage + " counted " + what + " in " + SECONDS + " s");
    }

    /**
     * The two lines of a counted read over a run that holds a row: {@code <stage> is reading <what>, over up
     * to <N> rows}, N grouped in threes, then {@code <stage> read <what> in <S> s}.
     */
    static List<String> countedRead(String stage, String what, long rowsUpTo) {
        return List.of(
                stage + " is reading " + what + ", over up to " + String.format(Locale.ROOT, "%,d", rowsUpTo) + " rows",
                stage + " read " + what + " in " + SECONDS + " s");
    }

    /**
     * The same two lines with {@value #ROWS} for the total, to compare with {@link #withoutTotals}: for a
     * read whose total a whole-job test cannot work out without knowing which run wrote which row. The total
     * handed to {@code pipeline} is pinned in the module's own contract test.
     */
    static List<String> countedRead(String stage, String what) {
        return List.of(
                stage + " is reading " + what + ", over up to " + ROWS + " rows",
                stage + " read " + what + " in " + SECONDS + " s");
    }

    /** {@code said} with {@value #ROWS} where a counted read's line before states its total. */
    static List<String> withoutTotals(List<String> said) {
        return said.stream()
                .map(line -> OVER_UP_TO.matcher(line).replaceFirst(", over up to " + ROWS + " rows"))
                .toList();
    }

    /** Several statements' lines, one after the other, as a step that issues them in that order writes them. */
    @SafeVarargs
    static List<String> inOrder(List<String>... statements) {
        List<String> all = new ArrayList<>();
        for (List<String> statement : statements) {
            all.addAll(statement);
        }
        return all;
    }
}
