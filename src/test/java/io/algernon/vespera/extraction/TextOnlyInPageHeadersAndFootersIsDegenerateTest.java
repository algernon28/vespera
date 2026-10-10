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
import io.qameta.allure.Issue;
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
 * Stage 2's tier 1 counts the text the chunker cuts chunks from (ADR-232, #499, amending ADR-070 and
 * ADR-145 in what tier 1 measures): a conversion with letters and digits only in items labelled {@code
 * page_header} or {@code page_footer} is {@code degenerate-output}, under a reason of its own. Asked through
 * {@link ExtractionMetrics#writeAndJudge}, the one place stage 2 judges a conversion.
 *
 * <p>The measurement is not changed: {@code alphanumeric_char_count} still counts every item, as the other
 * columns do, so a page header is still measured and no column moves.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Text with nothing in it to score")
@Issue("499")
@Link(name = "ADR-232", url = Adr.A_FILE_WHOSE_ONLY_TEXT_IS_IN_PAGE_HEADERS_AND_FOOTERS_IS_DEGENERATE_OUTPUT_AND_SUCH_A_SEED_IS_UNUSABLE, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
class TextOnlyInPageHeadersAndFootersIsDegenerateTest {

    /** The reason of ADR-232 section 1, word for word. */
    private static final String ONLY_IN_PAGE_HEADERS_AND_FOOTERS =
            "zero alphanumeric content outside page headers and footers";

    /** "Quarterly report 2026" and "Page 1 of 1": the letters and digits of the header and the footer below. */
    private static final long LETTERS_AND_DIGITS_OF_THE_HEADER_AND_FOOTER = 19 + 8;

    private static final String A_PAGE_HEADER_AND_A_PAGE_FOOTER = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"Quarterly report 2026\",\"label\":\"page_header\"},"
            + "{\"text\":\"Page 1 of 1\",\"label\":\"page_footer\"}]}}}";

    private static final String A_PAGE_HEADER_AND_A_RULE = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"Quarterly report 2026\",\"label\":\"page_header\"},"
            + "{\"text\":\"- - - - -\",\"label\":\"text\"}]}}}";

    private static final String A_PAGE_HEADER_AND_A_PARAGRAPH = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"Quarterly report 2026\",\"label\":\"page_header\"},"
            + "{\"text\":\"Revenue rose in the third quarter.\",\"label\":\"text\"}]}}}";

    private static final String NO_TEXT_AT_ALL = "{\"document\":{\"json_content\":{\"texts\":[]}}}";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Text only in page headers and footers is output with no usable content")
    @DisplayName("A conversion whose only text is a page header and a page footer is judged to have no usable content, for a reason that says where the text was")
    void aPageHeaderAndAPageFooterAloneAreDegenerate() {
        OccurrenceId occurrence = anOccurrence("headers-and-footers.pdf");
        RunId run = aRun("headers-and-footers");

        DegeneracyVerdict verdict = metrics().writeAndJudge(occurrence, run, converted(A_PAGE_HEADER_AND_A_PAGE_FOOTER), null);

        claim(
                "the conversion is judged to have no usable content: nothing of it is text that would be cut"
                        + " into pieces and scored",
                () -> assertThat(verdict.degenerate()).isTrue());
        claim(
                "and the reason says the text there was is in page headers and footers, so that a reader who"
                        + " opens the file and sees text knows why it was removed",
                () -> assertThat(verdict.reason()).isEqualTo(ONLY_IN_PAGE_HEADERS_AND_FOOTERS));
        claim(
                "what was measured of it is not changed: the " + LETTERS_AND_DIGITS_OF_THE_HEADER_AND_FOOTER
                        + " letters and digits of the header and the footer are still counted",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT alphanumeric_char_count FROM extraction_metric WHERE occurrence_id = ? AND run_id = ?",
                                Long.class,
                                occurrence.value(),
                                run.value()))
                        .isEqualTo(LETTERS_AND_DIGITS_OF_THE_HEADER_AND_FOOTER));
    }

    @Test
    @Story("Text only in page headers and footers is output with no usable content")
    @DisplayName("A conversion with a page header and, outside it, punctuation only is judged the same way")
    void aPageHeaderBesideABodyOfPunctuationIsDegenerate() {
        DegeneracyVerdict verdict = metrics()
                .writeAndJudge(anOccurrence("header-and-rule.pdf"), aRun("header-and-rule"), converted(A_PAGE_HEADER_AND_A_RULE), null);

        claim(
                "the body holds no letter and no digit, and the header's do not count for it",
                () -> assertThat(verdict)
                        .isEqualTo(new DegeneracyVerdict(true, ONLY_IN_PAGE_HEADERS_AND_FOOTERS)));
    }

    @Test
    @Story("A document with a body is not removed for having page headers")
    @DisplayName("A conversion with a page header and a paragraph is not judged to have no usable content")
    void aPageHeaderBesideAParagraphIsNotDegenerate() {
        DegeneracyVerdict verdict = metrics()
                .writeAndJudge(anOccurrence("header-and-body.pdf"), aRun("header-and-body"), converted(A_PAGE_HEADER_AND_A_PARAGRAPH), null);

        claim("the paragraph is text outside the page header", () -> assertThat(verdict.degenerate()).isFalse());
    }

    @Test
    @Story("A document with no text at all keeps the reason it had")
    @DisplayName("A conversion with no text at all is judged to have no usable content for the reason it always was")
    void noTextAtAllKeepsItsReason() {
        DegeneracyVerdict verdict =
                metrics().writeAndJudge(anOccurrence("no-text.pdf"), aRun("no-text"), converted(NO_TEXT_AT_ALL), null);

        claim(
                "no text anywhere is not text in page headers and footers, and is not said to be",
                () -> assertThat(verdict)
                        .isEqualTo(new DegeneracyVerdict(true, UsableText.NO_ALPHANUMERIC_CONTENT)));
    }

    /** A mean confidence the converter reports, below {@link #A_SET_CONFIDENCE_FLOOR}. */
    private static final double A_LOW_MEAN_CONFIDENCE = 0.10;

    /** A confidence floor an operator has set, on the converter's scale of 0 to 1. */
    private static final double A_SET_CONFIDENCE_FLOOR = 0.50;

    @Test
    @Story("Text only in page headers and footers is output with no usable content")
    @DisplayName("Where a conversion of a page header and footer alone also falls below a set confidence floor, the reason given is where the text was, not the confidence")
    void theReasonOfTextOnlyInHeadersAndFootersComesBeforeALowConfidence() {
        DegeneracyVerdict ofHeadersAndFooters = metrics()
                .writeAndJudge(
                        anOccurrence("low-confidence-headers.pdf"),
                        aRun("low-confidence-headers"),
                        convertedWithLowConfidence(A_PAGE_HEADER_AND_A_PAGE_FOOTER),
                        A_SET_CONFIDENCE_FLOOR);
        DegeneracyVerdict ofABody = metrics()
                .writeAndJudge(
                        anOccurrence("low-confidence-body.pdf"),
                        aRun("low-confidence-body"),
                        convertedWithLowConfidence(A_PAGE_HEADER_AND_A_PARAGRAPH),
                        A_SET_CONFIDENCE_FLOOR);

        claim(
                "the floor of " + A_SET_CONFIDENCE_FLOOR + " is in force: a conversion with a paragraph and a"
                        + " mean confidence of " + A_LOW_MEAN_CONFIDENCE + " is removed for its confidence",
                () -> {
                    assertThat(ofABody.degenerate()).isTrue();
                    assertThat(ofABody.reason()).contains("below the configured floor");
                });
        claim(
                "the conversion of a page header and footer alone, with the same confidence under the same"
                        + " floor, is removed for where its text was: that holds whatever the floor is set to",
                () -> assertThat(ofHeadersAndFooters)
                        .isEqualTo(new DegeneracyVerdict(true, ONLY_IN_PAGE_HEADERS_AND_FOOTERS)));
    }

    private static DoclingResponse convertedWithLowConfidence(String rawResponse) {
        return new DoclingResponse(
                ConversionStatus.SUCCESS,
                List.of(),
                1.0,
                new ConfidenceScores(null, null, null, null, A_LOW_MEAN_CONFIDENCE, null, null, null),
                rawResponse);
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
