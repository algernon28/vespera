package io.algernon.vespera.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.ledger.OccurrencePath.Result;
import io.algernon.vespera.ledger.OccurrencePath.Stored;
import io.algernon.vespera.ledger.OccurrencePath.Unstorable;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * ADR-051 as tests. Every case here was measured against Windows 11 + NTFS + OpenJDK 26 while
 * the decision was being made; see issue #4. Nothing touches the filesystem: the stored form is
 * derived from two paths and a string, which is the whole point of it being a value.
 *
 * <p>Characters are written as code points rather than literals so that no editor, encoding or
 * copy-paste can quietly normalise the very distinction under test.
 */
class OccurrencePathTest {

    private static final Path ROOT = Path.of("D:", "Archive");
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private static final String DOTLESS_I = ch(0x0131);   // ı — the JDK upper-cases this to I
    private static final String MICRO_SIGN = ch(0x00B5);  // µ — the JDK folds this onto greek mu
    private static final String GREEK_MU = ch(0x03BC);    // μ
    private static final String E_ACUTE = ch(0x00E9);     // é, composed
    private static final String COMBINING_ACUTE = ch(0x0301);
    private static final String GRINNING_FACE = new String(Character.toChars(0x1F600));
    private static final String LONE_HIGH_SURROGATE = ch(0xD800);
    private static final String LONE_LOW_SURROGATE = ch(0xDC00);

    private static String ch(int codePoint) {
        return String.valueOf((char) codePoint);
    }

    /**
     * Non-ASCII rendered as code points. Assertions compare these renderings so that a failure
     * message is legible in a terminal or a report: an emoji or a lone surrogate printed raw is
     * either unreadable or invisible, and "expected X but was X" tells nobody anything.
     */
    private static String readable(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints()
                .forEach(cp -> out.append(cp < 128 ? String.valueOf((char) cp) : String.format("[U+%04X]", cp)));
        return out.toString();
    }

    private static Path resolve(String... more) {
        Path p = ROOT;
        for (String name : more) {
            p = p.resolve(name);
        }
        return p;
    }

    private static String stored(String... more) {
        Result result = OccurrencePath.relativize(ROOT, resolve(more));
        assertThat(result).isInstanceOf(Stored.class);
        return ((Stored) result).path().value();
    }

    @Test
    void storesThePathRelativeToTheCorpusRoot() {
        assertThat(stored("Reports", "Q1.PDF")).isEqualTo("Reports/Q1.PDF");
    }

    @Test
    void rewritesSeparatorsSoTheStoredFormIsPlatformNeutral() {
        assertThat(stored("a", "b", "c", "d.txt")).isEqualTo("a/b/c/d.txt");
    }

    @Test
    void preservesCaseExactlyAsObserved() {
        assertThat(stored("Reports", "Q1.PDF")).isEqualTo("Reports/Q1.PDF");
        assertThat(stored("reports", "q1.pdf")).isEqualTo("reports/q1.pdf");
    }

    /**
     * Why comparison is byte-exact rather than case-folded: the JDK's folding table disagrees
     * with NTFS. These pairs compare equal as {@link Path}s and are distinct files on disk, so
     * folding would merge two different files into one occurrence.
     */
    @Test
    void keepsApartTheCasePairsTheJdkWronglyFolds() {
        assumeTrue(WINDOWS, "Path.equals only folds case on Windows");
        assertThat(resolve(DOTLESS_I + ".txt"))
                .as("precondition: the JDK upper-cases U+0131 to I")
                .isEqualTo(resolve("I.txt"));
        assertThat(resolve(MICRO_SIGN + ".txt"))
                .as("precondition: the JDK folds U+00B5 onto U+03BC")
                .isEqualTo(resolve(GREEK_MU + ".txt"));

        assertThat(readable(stored(DOTLESS_I + ".txt"))).isNotEqualTo(readable(stored("I.txt")));
        assertThat(readable(stored(MICRO_SIGN + ".txt"))).isNotEqualTo(readable(stored(GREEK_MU + ".txt")));
    }

    /** NTFS normalises nothing, so a composed and a decomposed name are two different files. */
    @Test
    void keepsComposedAndDecomposedFormsApart() {
        String composed = "caf" + E_ACUTE + ".txt";
        String decomposed = "cafe" + COMBINING_ACUTE + ".txt";
        assertThat(readable(composed))
                .as("precondition: U+00E9 and e + U+0301 are different strings")
                .isNotEqualTo(readable(decomposed));
        assertThat(readable(stored(composed))).isNotEqualTo(readable(stored(decomposed)));
        assertThat(readable(stored(composed))).isEqualTo(readable(composed));
    }

    @Test
    void acceptsWellFormedAstralCharacters() {
        String name = "pair-" + GRINNING_FACE + ".txt";
        assertThat(readable(stored(name))).isEqualTo(readable(name));
    }

    /**
     * An unpaired surrogate is a valid NTFS filename with no UTF-8 encoding, so it cannot be
     * stored in a TEXT column. It yields no occurrence at all — a walk anomaly instead —
     * carrying a lossy rendering so the operator can still locate the file.
     */
    @Test
    void refusesAFilenameThatCannotSurviveUtf8() {
        Result result = OccurrencePath.relativize(ROOT, resolve("orphan-" + LONE_HIGH_SURROGATE + ".txt"));
        assertThat(result).isInstanceOf(Unstorable.class);
        Unstorable unstorable = (Unstorable) result;
        assertThat(unstorable.lossyRendering()).contains("orphan-");
        assertThat(unstorable.reason().toLowerCase()).contains("utf-8");
    }

    @Test
    void refusesAnUnpairedLowSurrogateToo() {
        Result result = OccurrencePath.relativize(ROOT, resolve("orphan-" + LONE_LOW_SURROGATE + ".txt"));
        assertThat(result).isInstanceOf(Unstorable.class);
    }

    /** No schema cap needed: java.nio applies the extended-length prefix itself. */
    @Test
    void acceptsAPathLongerThanTheLegacyWindowsLimit() {
        String segment = "x".repeat(60);
        String value = stored(segment, segment, segment, segment, segment, "leaf.txt");
        assertThat(value.length()).as("expected a path past MAX_PATH").isGreaterThan(260);
    }

    @Test
    void rejectsAnEntryOutsideTheCorpusRoot() {
        assertThatThrownBy(() -> OccurrencePath.relativize(ROOT, Path.of("D:", "Elsewhere", "file.txt")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTheRootItselfBecauseItIsNotAnEntryBeneathIt() {
        assertThatThrownBy(() -> OccurrencePath.relativize(ROOT, ROOT)).isInstanceOf(IllegalArgumentException.class);
    }
}
