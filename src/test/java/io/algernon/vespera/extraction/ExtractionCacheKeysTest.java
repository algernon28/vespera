package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The reader and writer of the keys stage 2 records (ADR-206 sections 2 and 4): a key recorded for a
 * document under a run is read back under that run and no other, a run's keys are removed together, and
 * a document with no key is an error that names the document and the run.
 *
 * <p>The last is the one a whole invocation cannot reach by any state a run leaves: a key is written
 * wherever a document is measured, so a surviving document without one is the record disagreeing with
 * itself. Here the document simply has none recorded.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("The extraction cache")
@Issue("349")
@Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
class ExtractionCacheKeysTest {

    /** A value of the shape every key has: a SHA-256 as 64 lowercase hexadecimal characters. */
    private static final String A_KEY = "0123456789abcdef".repeat(4);

    /** One document with a key recorded, and one with none. */
    private static final int TWO_DOCUMENTS = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Each document's cache key is on record")
    @DisplayName("A key recorded for a document under a run is read back under that run, and under no other")
    void readsBackTheKeyRecordedUnderARun() {
        OccurrencesUnderARun documents = OccurrencesUnderARun.of(jdbcTemplate, TWO_DOCUMENTS);
        OccurrencesUnderARun anotherRun = OccurrencesUnderARun.of(jdbcTemplate, TWO_DOCUMENTS);
        ExtractionCacheKeys keys = new ExtractionCacheKeys(jdbcTemplate);

        keys.record(documents.get(0), documents.run(), A_KEY);

        claim(
                "the key is read back for that document under the run that recorded it, character for"
                        + " character",
                () -> assertThat(keys.forOccurrence(documents.get(0), documents.run())).contains(A_KEY));
        claim(
                "and nothing is read for the same document under another run, or for another document"
                        + " under the same run: a key belongs to one document and one run",
                () -> {
                    assertThat(keys.forOccurrence(documents.get(0), anotherRun.run())).isEmpty();
                    assertThat(keys.forOccurrence(documents.get(1), documents.run())).isEmpty();
                });
    }

    @Test
    @Story("Each document's cache key is on record")
    @DisplayName("A document with no key on record is an error that names the document and the run")
    void aDocumentWithNoKeyIsAnErrorNamingItAndTheRun() {
        OccurrencesUnderARun documents = OccurrencesUnderARun.of(jdbcTemplate, TWO_DOCUMENTS);
        ExtractionCacheKeys keys = new ExtractionCacheKeys(jdbcTemplate);
        keys.record(documents.get(0), documents.run(), A_KEY);

        claim(
                "asking for the key a step must have, of a document that has none, fails and says which"
                        + " document and which run: every document that was measured has a key, so this"
                        + " is the record disagreeing with itself, and a step that went on would do so"
                        + " without the document or by reading its file again",
                () -> assertThatThrownBy(() -> keys.requireForOccurrence(documents.get(1), documents.run()))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("occurrence " + documents.get(1).value())
                        .hasMessageContaining(documents.run().value()));
        claim(
                "while the document that has one gets it",
                () -> assertThat(keys.requireForOccurrence(documents.get(0), documents.run())).isEqualTo(A_KEY));
    }

    @Test
    @Story("Each document's cache key is on record")
    @DisplayName("Removing a run's keys removes all of them and none of another run's")
    void discardsARunsKeysAndNoOthers() {
        OccurrencesUnderARun documents = OccurrencesUnderARun.of(jdbcTemplate, TWO_DOCUMENTS);
        OccurrencesUnderARun anotherRun = OccurrencesUnderARun.of(jdbcTemplate, TWO_DOCUMENTS);
        ExtractionCacheKeys keys = new ExtractionCacheKeys(jdbcTemplate);
        keys.record(documents.get(0), documents.run(), A_KEY);
        keys.record(documents.get(1), documents.run(), A_KEY);
        keys.record(anotherRun.get(0), anotherRun.run(), A_KEY);

        keys.discardForRun(documents.run());

        claim(
                "both of the run's " + TWO_DOCUMENTS + " keys are gone, so a step that does its work again"
                        + " under the same run can record them again without meeting its earlier rows",
                () -> {
                    assertThat(keys.forOccurrence(documents.get(0), documents.run())).isEmpty();
                    assertThat(keys.forOccurrence(documents.get(1), documents.run())).isEmpty();
                });
        claim(
                "and the other run's key is still there",
                () -> assertThat(keys.forOccurrence(anotherRun.get(0), anotherRun.run())).contains(A_KEY));
    }
}
