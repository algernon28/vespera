package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The survivor set: the occurrences of a run's walk carrying no blocking verdict (ADR-057,
 * ADR-060).
 *
 * <p>Nothing in the census slice reads it — census writes no verdicts, so there is nothing yet to
 * survive — but stage 1 calls it on its first day, and a query nobody has run is a query nobody
 * knows is right.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Issue("10")
@Link(name = "ADR-057", url = Adr.VERDICT_VOCABULARY_IS_EIGHT_VALUES, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
class SurvivorsTest {

    /** Files this test records against the walk: two that survive, one that is ruled out. */
    private static final int OCCURRENCES_RECORDED = 3;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What survives a stage")
    @DisplayName("An occurrence carrying a blocking verdict is not a survivor; one carrying none is")
    void excludesOnlyOccurrencesCarryingABlockingVerdict() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        OccurrenceId broken = record(ledger, walkId, "broken.txt");
        OccurrenceId passed = record(ledger, walkId, "passed.txt");
        OccurrenceId unjudged = record(ledger, walkId, "unjudged.txt");
        RunId runId = ledger.startRun("byte-level-reduction", "abc123", "{}", walkId, List.of());

        ledger.verdict(broken, runId, VerdictKind.BROKEN, "zero bytes");
        ledger.verdict(passed, runId, VerdictKind.PASSED, null);

        List<OccurrenceId> survivors = drain(ledger.survivors(runId));

        claim(
                OCCURRENCES_RECORDED + " occurrences were recorded and one was ruled out, so two survive",
                () -> assertThat(survivors).containsExactly(passed, unjudged));
        claim(
                "the occurrence carrying the blocking verdict is the one that is gone",
                () -> assertThat(survivors).doesNotContain(broken));
    }

    @Test
    @Story("What survives a stage")
    @DisplayName("A blocking verdict from an earlier run still removes an occurrence from a later one")
    void survivalIsCumulativeAcrossRuns() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        OccurrenceId ruledOut = record(ledger, walkId, "broken.txt");
        OccurrenceId survivor = record(ledger, walkId, "fine.txt");

        RunId stageOne = ledger.startRun("byte-level-reduction", "abc123", "{}", walkId, List.of());
        ledger.verdict(ruledOut, stageOne, VerdictKind.BROKEN, "zero bytes");
        RunId stageTwo = ledger.startRun("extraction", "def456", "{}", walkId, List.of(stageOne));

        claim(
                "the later stage does not see what an earlier stage ruled out, which is what makes the"
                        + " cascade cumulative rather than a set of opinions",
                () -> assertThat(drain(ledger.survivors(stageTwo))).containsExactly(survivor));
    }

    @Test
    @Story("What survives a stage")
    @DisplayName("A run records the runs it read, so the chain back to the walk stays queryable")
    @Link(name = "ADR-048", url = Adr.WALK_AND_RUN_IDENTITY, type = "adr")
    void recordsTheRunsARunRead() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId first = ledger.startRun("byte-level-reduction", "abc123", "{}", walkId, List.of());
        RunId second = ledger.startRun("content-census", "def456", "{}", walkId, List.of(first));

        RunId third = ledger.startRun("extraction", "ghi789", "{}", walkId, List.of(first, second));

        claim(
                "both runs the third run read are recorded against it, as rows rather than as one"
                        + " delimited value",
                () -> assertThat(ledger.upstreamRuns(third)).containsExactlyInAnyOrder(first, second));
    }

    private static OccurrenceId record(Ledger ledger, WalkId walkId, String path) {
        ledger.fileOccurrence(walkId, new OccurrencePath(path), 1, Instant.parse("2026-08-29T10:15:30Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    /** Reads the whole reader, the way a step does but without a step. */
    private static List<OccurrenceId> drain(ItemStreamReader<OccurrenceId> reader) {
        List<OccurrenceId> read = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                read.add(id);
            }
        } catch (Exception e) {
            throw new IllegalStateException("the survivors reader failed partway", e);
        } finally {
            reader.close();
        }
        return read;
    }
}
