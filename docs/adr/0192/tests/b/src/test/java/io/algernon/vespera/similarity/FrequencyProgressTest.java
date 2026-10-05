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
 * The contract of {@code FrequencyProgress} (ADR-192 section 5, #412): {@code DocumentFrequency.measure(RunId,
 * RunId, FrequencyProgress)} calls {@code toGoThrough(long)} once, with the distinct (granularity, hash) pairs
 * it counted in memory, before the first frequency row, and {@code hashGoneThrough()} after each, whether or
 * not a row was written for it: a hash seen in one surviving document earns none (ADR-074's omission rule).
 *
 * <p><b>Part (b) of ADR-192.</b> Does not compile until the interface and the overload exist; part (b) moves it
 * into {@code src/test}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class FrequencyProgressTest {

    /** Hashes 0 to 9 and 5 to 14: fifteen distinct, five of them in both documents. */
    private static final int FIFTEEN_DISTINCT_HASHES = 15;

    private static final int FIVE_SHARED_HASHES = 5;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Measuring document frequency tells its caller how many hashes it will go through")
    @DisplayName("The loop is announced once with the number of distinct hashes, and each is reported, a row written or not")
    void announcesTheDistinctHashesOnceAndReportsEach() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.startWalk(Path.of("C:/corpus-frequency"));
        RunId stage2 = ledger.startRun("extraction", "abc123", "{}", walk, List.of());
        RunId stage3 = ledger.startRun("content-census", "def456", "{}", walk, List.of(stage2));
        document(ledger, walk, stage2, "a.txt", 0, 10);
        document(ledger, walk, stage2, "b.txt", 5, 10);
        List<String> events = new ArrayList<>();

        new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2, recording(events));

        claim(
                "the loop is announced once with " + FIFTEEN_DISTINCT_HASHES + ", then each hash is reported once",
                () -> {
                    assertThat(events.getFirst()).isEqualTo("to-go-through " + FIFTEEN_DISTINCT_HASHES);
                    assertThat(events.subList(1, events.size()))
                            .hasSize(FIFTEEN_DISTINCT_HASHES)
                            .allMatch("gone-through"::equals);
                });
        claim(
                "though only the " + FIVE_SHARED_HASHES + " hashes both documents hold earned a row",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM shingle_document_frequency WHERE run_id = ?",
                                Integer.class,
                                stage3.value()))
                        .isEqualTo(FIVE_SHARED_HASHES));
    }

    @Test
    @Story("Measuring document frequency tells its caller how many hashes it will go through")
    @DisplayName("With no shingle row the loop is still announced, with zero, and reports nothing")
    void aLoopWithNothingToGoThroughIsAnnouncedWithZero() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.startWalk(Path.of("C:/corpus-empty"));
        RunId stage2 = ledger.startRun("extraction", "abc124", "{}", walk, List.of());
        RunId stage3 = ledger.startRun("content-census", "def457", "{}", walk, List.of(stage2));
        List<String> events = new ArrayList<>();

        new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2, recording(events));

        claim(
                "measurement has no early return before this loop, so a run with no shingle row announces zero and"
                        + " reports nothing",
                () -> assertThat(events).containsExactly("to-go-through 0"));
    }

    private static FrequencyProgress recording(List<String> events) {
        return new FrequencyProgress() {
            @Override
            public void toGoThrough(long hashes) {
                events.add("to-go-through " + hashes);
            }

            @Override
            public void hashGoneThrough() {
                events.add("gone-through");
            }
        };
    }

    private void document(Ledger ledger, WalkId walk, RunId stage2, String path, long from, int count) {
        ledger.fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
        long occurrence = ledger.occurrenceId(walk, new OccurrencePath(path)).orElseThrow().value();
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
