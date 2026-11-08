package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
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
}
