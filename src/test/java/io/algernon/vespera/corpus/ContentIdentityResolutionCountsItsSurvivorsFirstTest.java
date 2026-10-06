package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.SizedOccurrence;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What stands between a caller's call of {@code ContentIdentityResolution.resolve} and the first thing it
 * is told (ADR-199 section 2, ADR-200 section 3, #429): the count of the survivors, and nothing else the
 * ledger is asked for. {@code toSize} then hands the caller that count.
 *
 * <p>{@code pipeline} times that count from outside {@code corpus} on the strength of this: it writes one
 * line immediately before it calls {@code resolve} and one when {@code toSize} is called, and the seconds
 * between them are the count's. {@code corpus} writes no line and has no statement interface (ADR-200
 * section 7), so this order is the whole of what it owes. A change that puts a read of the survivors, a
 * lookup of an occurrence's facts or a verdict ahead of {@code toSize} would make the timed line state
 * more than the count, and fails here.
 *
 * <p>Green from the start: {@code corpus} already works in this order. The test exists so that it goes
 * on doing so.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Progress reporting")
@Issue("429")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
@Link(name = "ADR-200", url = Adr.STAGE_1_HOLDS_ONE_SIZE_AT_A_TIME, type = "adr")
class ContentIdentityResolutionCountsItsSurvivorsFirstTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    /** Three survivors: two that share a size, so one size is hashed, and one whose size is its own. */
    private static final int SURVIVORS = 3;

    /** How often the survivors are counted in one resolution. */
    private static final int ONCE = 1;

    private static final String COUNTED = "the ledger is asked how many survivors there are";
    private static final String TOLD = "the caller is told how many survivors will be sized: ";
    private static final String READ_BY_SIZE = "the ledger is asked for the survivors by size";
    private static final String READ_BY_ID = "the ledger is asked for the survivors";
    private static final String FACTS = "the ledger is asked for an occurrence's facts";
    private static final String VERDICT = "the ledger is given a verdict";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The survivor count that sizes a stage's counter says how long it took")
    @DisplayName("Content identity counts its survivors before it asks for anything else, and tells its caller the count first")
    void theCountIsAllThatComesBeforeTheCallerIsTold(@TempDir Path root) throws Exception {
        List<String> events = new ArrayList<>();
        RunId run = threeSurvivorsUnder(root);

        new ContentIdentityResolution(new RecordingLedger(jdbcTemplate, events), new ContentIdentity(jdbcTemplate))
                .resolve(run, root, new RecordingProgress(events));

        claim(
                "the first thing the ledger is asked is how many survivors there are, and the next thing that"
                        + " happens is the caller being told that number, " + SURVIVORS + ": nothing else is asked"
                        + " of the ledger between the call and that answer, so the time between them is the count's",
                () -> assertThat(events).startsWith(COUNTED, TOLD + SURVIVORS));
        claim(
                "the survivors are counted " + ONCE + " time in the whole resolution, so that wait happens once",
                () -> assertThat(events).filteredOn(COUNTED::equals).hasSize(ONCE));
        claim(
                "and the resolution did go on to read the survivors by size, so the order above is of a"
                        + " resolution that did its work",
                () -> assertThat(events).contains(READ_BY_SIZE));
    }

    @Test
    @Story("The survivor count that sizes a stage's counter says how long it took")
    @DisplayName("A count of the survivors that fails tells the caller nothing, so nothing is said to have been counted")
    void aCountThatThrowsTellsTheCallerNothing(@TempDir Path root) throws Exception {
        List<String> events = new ArrayList<>();
        RunId run = threeSurvivorsUnder(root);
        IllegalStateException failure = new IllegalStateException("the count could not be taken");
        Ledger failing = new RecordingLedger(jdbcTemplate, events) {
            @Override
            public long survivorCount(RunId runId) {
                super.survivorCount(runId);
                throw failure;
            }
        };

        claim(
                "the failure of the count reaches the caller as it was thrown",
                () -> assertThatThrownBy(() -> new ContentIdentityResolution(failing, new ContentIdentity(jdbcTemplate))
                                .resolve(run, root, new RecordingProgress(events)))
                        .isSameAs(failure));
        claim(
                "and the count is all that happened: the caller was told no number, and no survivor was read",
                () -> assertThat(events).containsExactly(COUNTED));
    }

    /** Two occurrences of eleven bytes and one of five, recorded under a walk of {@code root}, and its run. */
    private RunId threeSurvivorsUnder(Path root) throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.startWalk(root);
        record(ledger, walk, root, "first.txt", "a".repeat(11));
        record(ledger, walk, root, "second.txt", "b".repeat(11));
        record(ledger, walk, root, "alone.txt", "c".repeat(5));
        return ledger.startRun("byte-level-reduction", "corpus-under-test", "{}", walk, List.of());
    }

    private static void record(Ledger ledger, WalkId walk, Path root, String name, String content) throws IOException {
        Path written = root.resolve(name);
        Files.writeString(written, content);
        ledger.fileOccurrence(walk, new OccurrencePath(name), Files.size(written), CREATED, CREATED);
    }

    /** The real ledger, writing down each thing content identity asks of it before answering. */
    private static class RecordingLedger extends Ledger {
        private final List<String> events;

        RecordingLedger(JdbcTemplate jdbcTemplate, List<String> events) {
            super(jdbcTemplate);
            this.events = events;
        }

        @Override
        public long survivorCount(RunId runId) {
            events.add(COUNTED);
            return super.survivorCount(runId);
        }

        @Override
        public ItemStreamReader<SizedOccurrence> survivorsBySize(RunId runId) {
            events.add(READ_BY_SIZE);
            return super.survivorsBySize(runId);
        }

        @Override
        public ItemStreamReader<OccurrenceId> survivors(RunId runId) {
            events.add(READ_BY_ID);
            return super.survivors(runId);
        }

        @Override
        public Optional<OccurrenceFacts> factsFor(OccurrenceId occurrenceId) {
            events.add(FACTS);
            return super.factsFor(occurrenceId);
        }

        @Override
        public void verdict(OccurrenceId occurrenceId, RunId runId, VerdictKind kind, String reason) {
            events.add(VERDICT);
            super.verdict(occurrenceId, runId, kind, reason);
        }
    }

    /** Writes down the one call this test is about, in the same list the ledger writes into. */
    private static final class RecordingProgress implements HashingProgress {
        private final List<String> events;

        RecordingProgress(List<String> events) {
            this.events = events;
        }

        @Override
        public void toSize(long survivors) {
            events.add(TOLD + survivors);
        }

        @Override
        public void toHash(long occurrences) {}

        @Override
        public void hashed(OccurrenceId occurrence, String sha256) {}
    }
}
