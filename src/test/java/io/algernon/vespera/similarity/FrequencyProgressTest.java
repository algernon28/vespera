package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The contract of {@code FrequencyProgress} (ADR-192 section 5, #412, as ADR-211 section 3 changes it).
 *
 * <p>Since ADR-211 the frequency rows are written by one statement in the database, so there is no loop over
 * the distinct hashes to announce, and {@code toGoThrough} and {@code hashGoneThrough} are gone. What is left
 * that loops is the check of the run's shingled occurrences, a page of 1,000 at a time, against the ledger:
 * {@code shingledOccurrencesChecked(int)} is called once after each page, with that page's number of
 * occurrences, so the calls add up to the shingled occurrences of the run. A run with no shingle row has no
 * page and makes no call.
 *
 * <p>Every method of the recorder is written without {@code @Override}: the two old ones still have to be
 * implemented at {@code 4b99a03} and no longer exist after ADR-211, and the new one exists only after it.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("Progress reporting")
@Issue("456")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class FrequencyProgressTest {

    /** Hashes 0 to 9 and 5 to 14: five of them in both documents. */
    private static final int FIVE_SHARED_HASHES = 5;

    /** The two shingled documents, one page of them. */
    private static final int TWO_SHINGLED_DOCUMENTS = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Measuring document frequency tells its caller how many documents it has checked")
    @DisplayName("The documents carrying passages are reported as checked, a page at a time, and no loop over the distinct passages is announced")
    void reportsTheShingledOccurrencesCheckedAndNoLoopOverHashes() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-frequency"));
        RunId stage2 = ledger.runs().startRun("extraction", "abc123", "{}", walk, List.of());
        RunId stage3 = ledger.runs().startRun("content-census", "def456", "{}", walk, List.of(stage2));
        document(ledger, walk, stage2, "a.txt", 0, 10);
        document(ledger, walk, stage2, "b.txt", 5, 10);
        List<String> events = new ArrayList<>();

        new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2, recording(events));

        claim(
                "the one page of the " + TWO_SHINGLED_DOCUMENTS + " documents that carry passages is reported as"
                        + " checked, once, and nothing is said of the distinct passages",
                () -> assertThat(events).containsExactly("checked " + TWO_SHINGLED_DOCUMENTS));
        claim(
                "and only the " + FIVE_SHARED_HASHES + " passages both documents hold earned a row",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM shingle_document_frequency WHERE run_id = ?",
                                Integer.class,
                                stage3.value()))
                        .isEqualTo(FIVE_SHARED_HASHES));
    }

    @Test
    @Story("Measuring document frequency tells its caller how many documents it has checked")
    @DisplayName("With no passage recorded nothing is reported")
    void aRunWithNoShingleRowReportsNothing() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-empty"));
        RunId stage2 = ledger.runs().startRun("extraction", "abc124", "{}", walk, List.of());
        RunId stage3 = ledger.runs().startRun("content-census", "def457", "{}", walk, List.of(stage2));
        List<String> events = new ArrayList<>();

        new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2, recording(events));

        claim(
                "a run with no shingle row has no page of shingled documents to check, so nothing is reported",
                () -> assertThat(events).isEmpty());
    }

    private static FrequencyProgress recording(List<String> events) {
        return new FrequencyProgress() {
            public void toGoThrough(long hashes) {
                events.add("to-go-through " + hashes);
            }

            public void hashGoneThrough() {
                events.add("gone-through");
            }

            /** ADR-211's callback: after each page of the run's shingled occurrences, with the page's count. */
            public void shingledOccurrencesChecked(int occurrences) {
                events.add("checked " + occurrences);
            }
        };
    }

    private void document(Ledger ledger, WalkId walk, RunId stage2, String path, long from, int count) {
        ledger.occurrences().fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
        long occurrence = ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow().value();
        for (long hash = from; hash < from + count; hash++) {
            jdbcTemplate.update(
                    "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                            + " VALUES (?, ?, ?, ?)",
                    occurrence,
                    stage2.value(),
                    ShingleParameters.DEFAULT.identity(),
                    hash);
        }
    }
}
