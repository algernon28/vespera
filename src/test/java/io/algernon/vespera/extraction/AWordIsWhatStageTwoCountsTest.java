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
 * The word floor reads {@code extraction_metric.word_count} as stage 2 stores it, and nothing is subtracted from it
 * (ADR-282 section 2): not the vowelless words, among which a year or a word of another alphabet is counted, and
 * not the one-character words. Asked through {@link ExtractionMetrics#writeAndJudge}, the one place stage 2 judges a
 * conversion, so that what is judged is what is stored.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Text with nothing in it to score")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
class AWordIsWhatStageTwoCountsTest {

    /** A word floor an operator has set: two words or more clear it. */
    private static final long A_WORD_FLOOR_OF_TWO = 2;

    /** A year and nothing else: one word, holding none of the Latin vowels. */
    private static final String A_YEAR_ALONE = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"2026\",\"label\":\"text\"}]}}}";

    /** Two words of Cyrillic: neither holds a Latin vowel. */
    private static final String TWO_WORDS_OF_CYRILLIC = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"Привет мир\",\"label\":\"text\"}]}}}";

    /** "Quarterly report 2026" over a body of one word, "OK". */
    private static final String ONE_WORD_UNDER_A_PAGE_HEADER = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"Quarterly report 2026\",\"label\":\"page_header\"},"
            + "{\"text\":\"OK\",\"label\":\"text\"}]}}}";

    /** The words of {@link #TWO_WORDS_OF_CYRILLIC}. */
    private static final long TWO_WORDS = 2;

    /** The three words of the page header and the one of the body of {@link #ONE_WORD_UNDER_A_PAGE_HEADER}. */
    private static final long FOUR_WORDS = 3 + 1;

    /** Tier 3's reason for one word under a floor of two, word for word (ADR-282 section 1). */
    private static final String ONE_WORD_BELOW_A_FLOOR_OF_TWO = "1 word(s), below the configured word floor 2";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A word is what stage 2 counts")
    @DisplayName("A year alone is one word, and is removed under a word floor of two")
    void aYearAloneIsOneWord() {
        OccurrenceId occurrence = anOccurrence("a-year.txt");
        RunId run = aRun("a-year");

        DegeneracyVerdict verdict =
                metrics().writeAndJudge(occurrence, run, converted(A_YEAR_ALONE), null, A_WORD_FLOOR_OF_TWO);

        claim(
                "the year is stored as one word",
                () -> assertThat(storedWords(occurrence, run)).isEqualTo(1L));
        claim(
                "and judged as one word: below the floor of " + A_WORD_FLOOR_OF_TWO + ", for the reason that says so",
                () -> assertThat(verdict).isEqualTo(new DegeneracyVerdict(true, ONE_WORD_BELOW_A_FLOOR_OF_TWO)));
    }

    @Test
    @Story("A word is what stage 2 counts")
    @DisplayName("Two words of an alphabet with no Latin vowel are two words, and clear a word floor of two")
    void wordsWithoutALatinVowelAreWords() {
        OccurrenceId occurrence = anOccurrence("cyrillic.txt");
        RunId run = aRun("cyrillic");

        DegeneracyVerdict verdict =
                metrics().writeAndJudge(occurrence, run, converted(TWO_WORDS_OF_CYRILLIC), null, A_WORD_FLOOR_OF_TWO);

        claim(
                "the measurement counts both as words with no Latin vowel, so words net of that count would be"
                        + " none",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT vowelless_word_count FROM extraction_metric WHERE occurrence_id = ? AND run_id = ?",
                                Long.class,
                                occurrence.value(),
                                run.value()))
                        .isEqualTo(TWO_WORDS));
        claim(
                "the floor reads the " + TWO_WORDS + " words as stored, and they meet the floor of "
                        + A_WORD_FLOOR_OF_TWO,
                () -> assertThat(verdict).isEqualTo(DegeneracyVerdict.notDegenerate()));
    }

    @Test
    @Story("A word is what stage 2 counts")
    @DisplayName("The words of a page header count, so one word under a three-word header clears a word floor of two")
    void theWordsOfPageHeadersAndFootersCount() {
        OccurrenceId occurrence = anOccurrence("one-word-under-a-header.pdf");
        RunId run = aRun("one-word-under-a-header");

        DegeneracyVerdict verdict = metrics()
                .writeAndJudge(occurrence, run, converted(ONE_WORD_UNDER_A_PAGE_HEADER), null, A_WORD_FLOOR_OF_TWO);

        claim(
                "the stored count is every item's words, the page header's included: " + FOUR_WORDS,
                () -> assertThat(storedWords(occurrence, run)).isEqualTo(FOUR_WORDS));
        claim(
                "and the floor reads that count, so the conversion clears a floor of " + A_WORD_FLOOR_OF_TWO,
                () -> assertThat(verdict).isEqualTo(DegeneracyVerdict.notDegenerate()));
    }

    private long storedWords(OccurrenceId occurrence, RunId run) {
        Long words = jdbcTemplate.queryForObject(
                "SELECT word_count FROM extraction_metric WHERE occurrence_id = ? AND run_id = ?",
                Long.class,
                occurrence.value(),
                run.value());
        return words == null ? -1 : words;
    }

    private ExtractionMetrics metrics() {
        return new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
    }

    private static DoclingResponse converted(String rawResponse) {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 1.0, null, rawResponse);
    }

    private OccurrenceId anOccurrence(String name) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/words-corpus-of-" + name));
        Instant then = Instant.parse("2026-01-01T00:00:00Z");
        ledger.occurrences().fileOccurrence(walkId, new OccurrencePath(name), 10, then, then);
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(name)).orElseThrow();
    }

    private RunId aRun(String name) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/words-run-of-" + name));
        return ledger.runs().startRun("extraction", "abc123", "{}", walkId, List.of());
    }
}
