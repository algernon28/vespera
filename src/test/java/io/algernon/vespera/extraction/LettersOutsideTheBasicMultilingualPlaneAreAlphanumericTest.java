package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tier 1's letters and digits are characters, not UTF-16 units (ADR-070, ADR-083, ADR-232): a letter whose
 * code point lies outside the Basic Multilingual Plane, a mathematical alphanumeric symbol or an ideograph of
 * CJK Extension B, is held in a Java string as two surrogate units, neither of which is a letter on its own.
 * A body made only of such letters is text a person can read and the chunker cuts chunks from, so stage 2
 * does not judge it {@code degenerate-output}, a seed made of it is not an unusable seed, and {@code
 * alphanumeric_char_count} counts each such letter once.
 *
 * <p>Asked through {@link ExtractionMetrics#writeAndJudge}, the one place stage 2 judges a conversion, and
 * through {@link UsableText#whyUnusable}, the seed set's bar.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Text with nothing in it to score")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
@Link(name = "ADR-232", url = Adr.A_FILE_WHOSE_ONLY_TEXT_IS_IN_PAGE_HEADERS_AND_FOOTERS_IS_DEGENERATE_OUTPUT_AND_SUCH_A_SEED_IS_UNUSABLE, type = "adr")
class LettersOutsideTheBasicMultilingualPlaneAreAlphanumericTest {

    /** MATHEMATICAL BOLD CAPITAL A, B, C and D, U+1D400 to U+1D403: four letters, eight UTF-16 units. */
    private static final String FOUR_MATHEMATICAL_BOLD_LETTERS =
            new String(Character.toChars(0x1D400)) + new String(Character.toChars(0x1D401))
                    + new String(Character.toChars(0x1D402)) + new String(Character.toChars(0x1D403));

    /** Two ideographs of CJK Unified Ideographs Extension B, U+20000 and U+20001: two letters, four units. */
    private static final String TWO_IDEOGRAPHS_OF_EXTENSION_B =
            new String(Character.toChars(0x20000)) + new String(Character.toChars(0x20001));

    /** The letters of {@link #FOUR_MATHEMATICAL_BOLD_LETTERS}, each counted once. */
    private static final long FOUR_LETTERS = 4;

    private static final String A_BODY_OF_MATHEMATICAL_LETTERS = body(FOUR_MATHEMATICAL_BOLD_LETTERS);

    private static final String A_BODY_OF_IDEOGRAPHS = body(TWO_IDEOGRAPHS_OF_EXTENSION_B);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A document whose text is all letters outside the common plane of Unicode has text")
    @DisplayName("A conversion whose only text is mathematical bold letters is not judged to have no usable content")
    void aBodyOfMathematicalLettersIsNotDegenerate() {
        OccurrenceId occurrence = anOccurrence("mathematical-letters.pdf");
        RunId run = aRun("mathematical-letters");

        DegeneracyVerdict verdict =
                metrics().writeAndJudge(occurrence, run, converted(A_BODY_OF_MATHEMATICAL_LETTERS), null);

        claim(
                "the conversion has usable content: its text is " + FOUR_LETTERS + " letters a person can read",
                () -> assertThat(verdict).isEqualTo(DegeneracyVerdict.notDegenerate()));
        claim(
                "and its count of letters and digits is " + FOUR_LETTERS + ", one for each letter, though each"
                        + " is held as two units of the string",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT alphanumeric_char_count FROM extraction_metric WHERE occurrence_id = ? AND run_id = ?",
                                Long.class,
                                occurrence.value(),
                                run.value()))
                        .isEqualTo(FOUR_LETTERS));
    }

    @Test
    @Story("A document whose text is all letters outside the common plane of Unicode has text")
    @DisplayName("A conversion whose only text is two rare ideographs is not judged to have no usable content")
    void aBodyOfIdeographsOfExtensionBIsNotDegenerate() {
        DegeneracyVerdict verdict = metrics()
                .writeAndJudge(anOccurrence("ideographs.pdf"), aRun("ideographs"), converted(A_BODY_OF_IDEOGRAPHS), null);

        claim(
                "the conversion has usable content: its text is two ideographs, each a letter",
                () -> assertThat(verdict).isEqualTo(DegeneracyVerdict.notDegenerate()));
    }

    @Test
    @Story("A document whose text is all letters outside the common plane of Unicode has text")
    @DisplayName("A seed whose only text is mathematical bold letters or rare ideographs is usable")
    void aSeedOfSuchLettersIsUsable() {
        claim(
                "a seed whose text is " + FOUR_LETTERS + " mathematical bold letters is given no reason to be"
                        + " unusable",
                () -> assertThat(UsableText.whyUnusable(A_BODY_OF_MATHEMATICAL_LETTERS)).isEmpty());
        claim(
                "and neither is a seed whose text is two ideographs",
                () -> assertThat(UsableText.whyUnusable(A_BODY_OF_IDEOGRAPHS)).isEmpty());
    }

    private static String body(String text) {
        return "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"" + text + "\",\"label\":\"text\"}]}}}";
    }

    private ExtractionMetrics metrics() {
        return new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
    }

    private static DoclingResponse converted(String rawResponse) {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 1.0, null, rawResponse);
    }

    private OccurrenceId anOccurrence(String name) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus-of-" + name));
        Instant then = Instant.parse("2026-01-01T00:00:00Z");
        ledger.occurrences().fileOccurrence(walkId, new OccurrencePath(name), 10, then, then);
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(name)).orElseThrow();
    }

    private RunId aRun(String name) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/run-of-" + name));
        return ledger.runs().startRun("extraction", "abc123", "{}", walkId, List.of());
    }
}
