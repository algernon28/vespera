package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrencePath.Result;
import io.algernon.vespera.ledger.OccurrencePath.Stored;
import io.algernon.vespera.ledger.OccurrencePath.Unstorable;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
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
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    /** The legacy Windows path ceiling, which the stored form is not bound by. */
    private static final int MAX_PATH = 260;

    /** Segments of this length, repeated, take the test path past {@link #MAX_PATH}. */
    private static final int SEGMENT_LENGTH = 60;

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
    @Story("The stored form")
    @DisplayName("A path is stored relative to the corpus root")
    void storesThePathRelativeToTheCorpusRoot() {
        claim(
                "the root D:/Archive is dropped, leaving Reports/Q1.PDF as what identifies the occurrence in the corpus",
                () -> assertThat(stored("Reports", "Q1.PDF")).isEqualTo("Reports/Q1.PDF"));
    }

    @Test
    @Story("The stored form")
    @DisplayName("Separators are rewritten so the stored form is platform-neutral")
    void rewritesSeparatorsSoTheStoredFormIsPlatformNeutral() {
        claim(
                "the Windows separators of a/b/c/d.txt are stored as forward slashes, whatever platform observed it",
                () -> assertThat(stored("a", "b", "c", "d.txt")).isEqualTo("a/b/c/d.txt"));
    }

    /** No schema cap needed: java.nio applies the extended-length prefix itself. */
    @Test
    @Story("The stored form")
    @DisplayName("A path past the legacy Windows limit is stored, not truncated")
    void acceptsAPathLongerThanTheLegacyWindowsLimit() {
        String segment = "x".repeat(SEGMENT_LENGTH);
        String value = stored(segment, segment, segment, segment, segment, "leaf.txt");
        claim(
                "five " + SEGMENT_LENGTH + "-character segments and a leaf exceed the " + MAX_PATH
                        + "-character legacy MAX_PATH, and are stored whole",
                () -> assertThat(value.length()).isGreaterThan(MAX_PATH));
    }

    @Test
    @Story("Distinct files stay distinct")
    @DisplayName("Case is preserved exactly as observed")
    void preservesCaseExactlyAsObserved() {
        claim(
                "the upper-case name Reports/Q1.PDF is stored with its case untouched",
                () -> assertThat(stored("Reports", "Q1.PDF")).isEqualTo("Reports/Q1.PDF"));
        claim(
                "the lower-case name reports/q1.pdf is stored with its case untouched",
                () -> assertThat(stored("reports", "q1.pdf")).isEqualTo("reports/q1.pdf"));
    }

    /**
     * Why comparison is byte-exact rather than case-folded: the JDK's folding table disagrees
     * with NTFS. These pairs compare equal as {@link Path}s and are distinct files on disk, so
     * folding would merge two different files into one occurrence.
     */
    @Test
    @Story("Distinct files stay distinct")
    @DisplayName("Case pairs the JDK wrongly folds are kept apart")
    void keepsApartTheCasePairsTheJdkWronglyFolds() {
        assumeTrue(WINDOWS, "Path.equals only folds case on Windows");

        claim(
                "precondition: the JDK treats U+0131 (dotless i) and I as the same path, though NTFS holds two files",
                () -> assertThat(resolve(DOTLESS_I + ".txt")).isEqualTo(resolve("I.txt")));
        claim(
                "precondition: the JDK treats U+00B5 (micro sign) and U+03BC (greek mu) as the same path",
                () -> assertThat(resolve(MICRO_SIGN + ".txt")).isEqualTo(resolve(GREEK_MU + ".txt")));

        claim(
                "U+0131 and I are stored as two different occurrences, because NTFS holds two different files",
                () -> assertThat(readable(stored(DOTLESS_I + ".txt"))).isNotEqualTo(readable(stored("I.txt"))));
        claim(
                "U+00B5 and U+03BC are stored as two different occurrences, for the same reason",
                () -> assertThat(readable(stored(MICRO_SIGN + ".txt")))
                        .isNotEqualTo(readable(stored(GREEK_MU + ".txt"))));
    }

    /** NTFS normalises nothing, so a composed and a decomposed name are two different files. */
    @Test
    @Story("Distinct files stay distinct")
    @DisplayName("Composed and decomposed forms of a name are kept apart")
    void keepsComposedAndDecomposedFormsApart() {
        String composed = "caf" + E_ACUTE + ".txt";
        String decomposed = "cafe" + COMBINING_ACUTE + ".txt";

        claim(
                "precondition: cafe with U+00E9 and cafe with e + U+0301 are different strings, though they look alike",
                () -> assertThat(readable(composed)).isNotEqualTo(readable(decomposed)));
        claim(
                "the two forms are stored as two different occurrences, because NTFS holds two different files",
                () -> assertThat(readable(stored(composed))).isNotEqualTo(readable(stored(decomposed))));
        claim(
                "neither form is normalised on the way in: what was observed is what is stored",
                () -> assertThat(readable(stored(composed))).isEqualTo(readable(composed)));
    }

    @Test
    @Story("Distinct files stay distinct")
    @DisplayName("A well-formed astral character survives the round trip")
    void acceptsWellFormedAstralCharacters() {
        String name = "pair-" + GRINNING_FACE + ".txt";

        claim(
                "U+1F600 is a well-formed surrogate pair, so it encodes in UTF-8 and is stored unchanged",
                () -> assertThat(readable(stored(name))).isEqualTo(readable(name)));
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
        Result result = OccurrencePath.relativize(ROOT, resolve("orphan-" + LONE_HIGH_SURROGATE + ".txt"));

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
    @Story("Names that cannot survive UTF-8")
    @DisplayName("An unpaired low surrogate is refused on the same grounds")
    void refusesAnUnpairedLowSurrogateToo() {
        Result result = OccurrencePath.relativize(ROOT, resolve("orphan-" + LONE_LOW_SURROGATE + ".txt"));

        claim(
                "the low half of a pair, U+DC00, is as unstorable alone as the high half",
                () -> assertThat(result).isInstanceOf(Unstorable.class));
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

    @Test
    @Story("The corpus root bounds what is recorded")
    @DisplayName("The root itself is rejected, being no entry beneath it")
    void rejectsTheRootItselfBecauseItIsNotAnEntryBeneathIt() {
        claim(
                "the root is not a file occurrence, so relativizing it against itself is refused",
                () -> assertThatThrownBy(() -> OccurrencePath.relativize(ROOT, ROOT))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
