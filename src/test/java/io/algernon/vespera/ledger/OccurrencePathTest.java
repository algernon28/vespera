package io.algernon.vespera.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private static Path resolve(String... more) {
        Path p = ROOT;
        for (String name : more) {
            p = p.resolve(name);
        }
        return p;
    }

    private static String stored(String... more) {
        Result result = OccurrencePath.relativize(ROOT, resolve(more));
        return assertInstanceOf(Stored.class, result).path().value();
    }

    @Test
    void storesThePathRelativeToTheCorpusRoot() {
        assertEquals("Reports/Q1.PDF", stored("Reports", "Q1.PDF"));
    }

    @Test
    void rewritesSeparatorsSoTheStoredFormIsPlatformNeutral() {
        assertEquals("a/b/c/d.txt", stored("a", "b", "c", "d.txt"));
    }

    @Test
    void preservesCaseExactlyAsObserved() {
        assertEquals("Reports/Q1.PDF", stored("Reports", "Q1.PDF"));
        assertEquals("reports/q1.pdf", stored("reports", "q1.pdf"));
    }

    /**
     * Why comparison is byte-exact rather than case-folded: the JDK's folding table disagrees
     * with NTFS. These pairs compare equal as {@link Path}s and are distinct files on disk, so
     * folding would merge two different files into one occurrence.
     */
    @Test
    void keepsApartTheCasePairsTheJdkWronglyFolds() {
        assumeTrue(WINDOWS, "Path.equals only folds case on Windows");
        assertEquals(resolve(DOTLESS_I + ".txt"), resolve("I.txt"), "precondition: the JDK folds these");
        assertEquals(resolve(MICRO_SIGN + ".txt"), resolve(GREEK_MU + ".txt"), "precondition: the JDK folds these");

        assertNotEquals(stored(DOTLESS_I + ".txt"), stored("I.txt"));
        assertNotEquals(stored(MICRO_SIGN + ".txt"), stored(GREEK_MU + ".txt"));
    }

    /** NTFS normalises nothing, so a composed and a decomposed name are two different files. */
    @Test
    void keepsComposedAndDecomposedFormsApart() {
        String composed = "caf" + E_ACUTE + ".txt";
        String decomposed = "cafe" + COMBINING_ACUTE + ".txt";
        assertNotEquals(composed, decomposed, "precondition: these are different strings");
        assertNotEquals(stored(composed), stored(decomposed));
        assertEquals(composed, stored(composed));
    }

    @Test
    void acceptsWellFormedAstralCharacters() {
        assertEquals("pair-" + GRINNING_FACE + ".txt", stored("pair-" + GRINNING_FACE + ".txt"));
    }

    /**
     * An unpaired surrogate is a valid NTFS filename with no UTF-8 encoding, so it cannot be
     * stored in a TEXT column. It yields no occurrence at all — a walk anomaly instead —
     * carrying a lossy rendering so the operator can still locate the file.
     */
    @Test
    void refusesAFilenameThatCannotSurviveUtf8() {
        Result result = OccurrencePath.relativize(ROOT, resolve("orphan-" + LONE_HIGH_SURROGATE + ".txt"));
        Unstorable unstorable = assertInstanceOf(Unstorable.class, result);
        assertTrue(unstorable.lossyRendering().contains("orphan-"), unstorable.lossyRendering());
        assertTrue(unstorable.reason().toLowerCase().contains("utf-8"), unstorable.reason());
    }

    @Test
    void refusesAnUnpairedLowSurrogateToo() {
        Result result = OccurrencePath.relativize(ROOT, resolve("orphan-" + LONE_LOW_SURROGATE + ".txt"));
        assertInstanceOf(Unstorable.class, result);
    }

    /** No schema cap needed: java.nio applies the extended-length prefix itself. */
    @Test
    void acceptsAPathLongerThanTheLegacyWindowsLimit() {
        String segment = "x".repeat(60);
        String value = stored(segment, segment, segment, segment, segment, "leaf.txt");
        assertTrue(value.length() > 260, "expected a path past MAX_PATH, got " + value.length());
    }

    @Test
    void rejectsAnEntryOutsideTheCorpusRoot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OccurrencePath.relativize(ROOT, Path.of("D:", "Elsewhere", "file.txt")));
    }

    @Test
    void rejectsTheRootItselfBecauseItIsNotAnEntryBeneathIt() {
        assertThrows(IllegalArgumentException.class, () -> OccurrencePath.relativize(ROOT, ROOT));
    }
}
