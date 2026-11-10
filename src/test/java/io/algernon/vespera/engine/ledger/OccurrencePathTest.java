package io.algernon.vespera.engine.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrencePath.Result;
import io.algernon.vespera.engine.ledger.OccurrencePath.Stored;
import io.algernon.vespera.engine.ledger.OccurrencePath.Unstorable;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-051 as tests. Every case here was measured against Windows 11 + NTFS + OpenJDK 26 while
 * the decision was being made; see issue #4. Nothing touches the filesystem: the stored form is
 * derived from two paths and a string, which is the whole point of it being a value.
 *
 * <p>Characters are written as code points rather than literals so that no editor, encoding or
 * copy-paste can quietly normalise the very distinction under test.
 *
 * <p>Every assertion sits inside a {@code claim(...)}, which names it in the report. The claim has
 * to carry the whole point: these assertions compare renderings of code points, so a report step
 * showing only the values would be two identical-looking strings and no reason to care.
 */
@Epic("Census")
@Feature("File occurrence identity")
@Issue("4")
@Link(name = "ADR-051", url = Adr.OCCURRENCE_IDENTIFIED_BY_RELATIVE_PATH, type = "adr")
class OccurrencePathTest {

    private static final Path ROOT = Path.of("D:", "Archive");

    /** The legacy Windows path ceiling, which the stored form is not bound by. */
    private static final int MAX_PATH = 260;

    /** Segments of this length, repeated, take the test path past {@link #MAX_PATH}. */
    private static final int SEGMENT_LENGTH = 60;

    private static final String DOTLESS_I = ch(0x0131);   // dotless i -- the JDK upper-cases this to I
    private static final String MICRO_SIGN = ch(0x00B5);  // micro sign -- the JDK folds this onto greek mu
    private static final String GREEK_MU = ch(0x03BC);
    private static final String E_ACUTE = ch(0x00E9);     // e acute, composed
    private static final String COMBINING_ACUTE = ch(0x0301);
    private static final String LONE_HIGH_SURROGATE = ch(0xD800);

    private static String ch(int codePoint) {
        return String.valueOf((char) codePoint);
    }

    /**
     * Non-ASCII rendered as code points, so that a failure message is legible: "expected X but was X"
     * over two names that look alike tells nobody anything.
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

    /**
     * The same, for a name this filesystem may refuse to make a {@link Path} of at all.
     *
     * <p>An unpaired surrogate is a valid NTFS name, and on NTFS these cases are real. Elsewhere the
     * JDK throws before any file is touched, as it does for any name outside the platform's encoding
     * where that encoding is not Unicode (a POSIX locale's ASCII). A test that cannot even name its
     * subject has nothing to say about how the subject is relativized. Aborting keeps that honest: the
     * case is checked where it exists (ADR-065) and skipped, loudly, where it cannot be built.
     */
    private static Path resolveOrAbort(String... more) {
        try {
            return resolve(more);
        } catch (InvalidPathException e) {
            return abort("this filesystem will not name " + readable(String.join("/", more)) + ": "
                    + e.getMessage());
        }
    }

    private static String stored(String... more) {
        return stored(resolve(more));
    }

    private static String stored(Path path) {
        Result result = OccurrencePath.relativize(ROOT, path);
        assertThat(result).isInstanceOf(Stored.class);
        return ((Stored) result).path().value();
    }

    @Test
    @Story("The stored form")
    @DisplayName("Separators are rewritten so the stored form is platform-neutral")
    void rewritesSeparatorsSoTheStoredFormIsPlatformNeutral() {
        claim(
                "the root D:/Archive is dropped and the Windows separators of a/b/c/d.txt are stored as forward"
                        + " slashes, whatever platform observed it",
                () -> assertThat(stored("a", "b", "c", "d.txt")).isEqualTo("a/b/c/d.txt"));
    }

    /** No schema cap needed: java.nio applies the extended-length prefix itself. */
    @Test
    @Story("The stored form")
    @DisplayName("A path past the legacy Windows limit is stored, not truncated")
    void acceptsAPathLongerThanTheLegacyWindowsLimit() {
        String segment = "x".repeat(SEGMENT_LENGTH);
        String whole = String.join("/", segment, segment, segment, segment, segment, "leaf.txt");
        String value = stored(segment, segment, segment, segment, segment, "leaf.txt");
        claim(
                "five " + SEGMENT_LENGTH + "-character segments and a leaf exceed the " + MAX_PATH
                        + "-character legacy MAX_PATH, and are stored whole, every one of their " + whole.length()
                        + " characters",
                () -> {
                    assertThat(whole.length()).isGreaterThan(MAX_PATH);
                    assertThat(value).isEqualTo(whole);
                });
    }

    /**
     * Why comparison is exact rather than case-folded: the JDK's folding table disagrees with NTFS. U+0131
     * and I, and U+00B5 and U+03BC, compare equal as {@link Path}s on Windows and are distinct files on disk,
     * so any fold would merge two different files into one occurrence. The claims hold each name exactly as
     * observed, which no fold leaves standing, on any platform.
     */
    @Test
    @Story("Distinct files stay distinct")
    @DisplayName("Case is preserved exactly as observed, including the case pairs the JDK wrongly folds")
    void preservesCaseExactlyAsObserved() {
        Path dotlessI = resolveOrAbort(DOTLESS_I + ".txt");
        Path microSign = resolveOrAbort(MICRO_SIGN + ".txt");
        Path greekMu = resolveOrAbort(GREEK_MU + ".txt");
        claim(
                "the upper-case name Reports/Q1.PDF and the lower-case name reports/q1.pdf are each stored with"
                        + " their case untouched",
                () -> {
                    assertThat(stored("Reports", "Q1.PDF")).isEqualTo("Reports/Q1.PDF");
                    assertThat(stored("reports", "q1.pdf")).isEqualTo("reports/q1.pdf");
                });
        claim(
                "U+0131 (dotless i) and I are each stored exactly as observed, so they are two occurrences,"
                        + " because NTFS holds two different files",
                () -> {
                    assertThat(readable(stored(dotlessI))).isEqualTo(readable(DOTLESS_I + ".txt"));
                    assertThat(readable(stored("I.txt"))).isEqualTo("I.txt");
                });
        claim(
                "U+00B5 (micro sign) and U+03BC (greek mu) are each stored exactly as observed, for the same"
                        + " reason",
                () -> {
                    assertThat(readable(stored(microSign))).isEqualTo(readable(MICRO_SIGN + ".txt"));
                    assertThat(readable(stored(greekMu))).isEqualTo(readable(GREEK_MU + ".txt"));
                });
    }

    /** NTFS normalises nothing, so a composed and a decomposed name are two different files. */
    @Test
    @Story("Distinct files stay distinct")
    @DisplayName("Composed and decomposed forms of a name are kept apart")
    void keepsComposedAndDecomposedFormsApart() {
        String composed = "caf" + E_ACUTE + ".txt";
        String decomposed = "cafe" + COMBINING_ACUTE + ".txt";
        Path composedPath = resolveOrAbort(composed);
        Path decomposedPath = resolveOrAbort(decomposed);

        claim(
                "neither form is normalised on the way in: cafe with U+00E9 and cafe with e + U+0301 are each"
                        + " stored exactly as observed, so they are two occurrences, because NTFS holds two"
                        + " different files",
                () -> {
                    assertThat(readable(stored(composedPath))).isEqualTo(readable(composed));
                    assertThat(readable(stored(decomposedPath))).isEqualTo(readable(decomposed));
                    assertThat(readable(composed)).isNotEqualTo(readable(decomposed));
                });
    }

    /**
     * An unpaired surrogate is a valid NTFS filename with no UTF-8 encoding, so it cannot be
     * stored in a TEXT column. It yields no occurrence at all — a walk anomaly instead —
     * carrying a lossy rendering so the operator can still locate the file.
     */
    @Test
    @Story("Names that cannot survive UTF-8")
    @DisplayName("An unpaired high surrogate yields no occurrence, and says why")
    void refusesAFilenameThatCannotSurviveUtf8() {
        Result result = OccurrencePath.relativize(ROOT, resolveOrAbort("orphan-" + LONE_HIGH_SURROGATE + ".txt"));

        claim(
                "a name holding the unpaired U+D800 has no UTF-8 encoding, so it yields no occurrence at all",
                () -> assertThat(result).isInstanceOf(Unstorable.class));
        claim(
                "the refusal still renders enough of the name, orphan-, for an operator to find the file",
                () -> assertThat(((Unstorable) result).lossyRendering()).contains("orphan-"));
        claim(
                "and it carries its reason, which names UTF-8",
                () -> assertThat(((Unstorable) result).reason().toLowerCase()).contains("utf-8"));
    }

    @Test
    @Story("The corpus root bounds what is recorded")
    @DisplayName("An entry outside the corpus root is rejected")
    void rejectsAnEntryOutsideTheCorpusRoot() {
        claim(
                "D:/Elsewhere/file.txt lies outside the root D:/Archive, so it has no stored form relative to it",
                () -> assertThatThrownBy(() -> OccurrencePath.relativize(ROOT, Path.of("D:", "Elsewhere", "file.txt")))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
