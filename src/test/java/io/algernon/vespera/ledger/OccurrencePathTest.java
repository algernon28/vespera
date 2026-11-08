package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrencePath.Result;
import io.algernon.vespera.ledger.OccurrencePath.Stored;
import io.algernon.vespera.ledger.OccurrencePath.Unstorable;
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

    private static final String LONE_HIGH_SURROGATE = ch(0xD800);

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

    /**
     * The same, for a name this filesystem may refuse to make a {@link Path} of at all.
     *
     * <p>An unpaired surrogate is a valid NTFS name, and on NTFS these cases are real. Elsewhere the
     * JDK throws before any file is touched, and a test that cannot even name its subject has
     * nothing to say about how the subject is relativized. Aborting keeps that honest: the case is
     * checked where it exists (ADR-065) and skipped, loudly, where it cannot be built.
     */
    private static Path resolveOrAbort(String... more) {
        try {
            return resolve(more);
        } catch (InvalidPathException e) {
            return abort("this filesystem will not name a file with an unpaired surrogate: " + e.getMessage());
        }
    }

    private static String stored(String... more) {
        Result result = OccurrencePath.relativize(ROOT, resolve(more));
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
