package io.algernon.vespera.synthesis;

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
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What {@code synthesis} tells its caller around the one announced statement of {@code ClusterGeneration.write},
 * and in which order (ADR-193 sections 6 and 7, ADR-204 section 4, #411): the read of the standing faults,
 * started and ended after the last cluster is gone through. It is timed, so it is started with no total and
 * reports no step.
 *
 * <p><b>Nothing is read before the walk since ADR-223.</b> The walk used to open with a timed read of every
 * cluster already written. Whether a cluster is written is now asked by its key as the walk reaches it, a
 * lookup of one row that is announced by nobody, so the walk's announcement is the first thing the caller
 * hears, and a cluster already written is walked past with no read reported for it.
 *
 * <p>No answer is asked for here: one test walks nothing and the other walks a cluster already written, so
 * the serving engine is one that fails if it is called. A walk that stops on five answers turned down
 * returns before the second read, and that is held where a whole job plays it ({@code
 * GenerationReportsItsProgressInvocationTest}). A read that throws is {@code
 * SynthesisStatementThatThrowsTest}'s: it drops a table, so it runs on a database of its own and not in
 * this class's context, whose database every class of its kind shares.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Synthesis")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-204", url = Adr.PART_B_OF_THE_STATEMENTS_WRITTEN_OUT, type = "adr")
class SynthesisStatementProgressOrderTest {

    private static final String MODEL_NAME = "qwen3:8b";
    private static final int THE_WINDOW = 8192;
    private static final String SEED_PATH = "seeds/safety.docx";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SynthesisDocs synthesisDocs;
    private ClusterGeneration generation;
    private OccurrenceId seed;
    private OccurrenceId document;
    private RunId run;
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void aFreshRunAndAnEngineNobodyMayCall() {
        synthesisDocs = new SynthesisDocs(jdbcTemplate);
        ChatModel neverAsked = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("no answer is asked for in this test");
            }
        };
        generation =
                new ClusterGeneration(new ClusterSynthesis(neverAsked), synthesisDocs, new ClusterFaults(jdbcTemplate));
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        ledger.occurrences().fileOccurrence(walk, new OccurrencePath(SEED_PATH), 1, Instant.EPOCH, Instant.EPOCH);
        seed = ledger.occurrences().occurrenceId(walk, new OccurrencePath(SEED_PATH)).orElseThrow();
        OccurrencePath documentPath = new OccurrencePath("corpus/document.txt");
        ledger.occurrences().fileOccurrence(walk, documentPath, 1, Instant.EPOCH, Instant.EPOCH);
        document = ledger.occurrences().occurrenceId(walk, documentPath).orElseThrow();
        run = ledger.runs().startRun("generation", "g" + System.nanoTime(), "{}", walk, List.of());
        calls.clear();
    }

    @Test
    @Story("Writing over the groups says what it is reading")
    @DisplayName("Over no group, the walk is announced first, with nothing read before it, and the standing faults are still read after")
    void overAnEmptyArrangementBothReadsAreReportedAroundTheAnnouncement() {
        write(List.of());

        claim(
                "the walk is announced, with zero, before anything is read; then the read of the standing"
                        + " faults is started with no total and ended",
                () -> assertThat(calls)
                        .containsExactly(
                                "toGoThrough 0",
                                starting(SynthesisStatement.STANDING_FAULTS),
                                ended(SynthesisStatement.STANDING_FAULTS)));
    }

    @Test
    @Story("Writing over the groups says what it is reading")
    @DisplayName("The standing faults are read after the last group is gone through")
    void theStandingFaultsAreReadAfterTheLastCluster() {
        synthesisDocs.record(run, seed, 0, new SynthesisDoc("Written before", "Written before [1].", List.of(document)));

        write(List.of(new RecordedCluster(new ArrangedCluster(seed, 0, 1, 1, 1), new ClusterLabel("Cluster 00"))));

        claim(
                "the one group, already written, is walked past with no read reported for it, the engine never"
                        + " asked, and the standing faults are read once the walk is over",
                () -> assertThat(calls)
                        .containsExactly(
                                "toGoThrough 1",
                                "clusterGoneThrough",
                                starting(SynthesisStatement.STANDING_FAULTS),
                                ended(SynthesisStatement.STANDING_FAULTS)));
    }

    private static String starting(SynthesisStatement statement) {
        return "statementStarting(" + statement + ", " + OptionalLong.empty() + ")";
    }

    private static String ended(SynthesisStatement statement) {
        return "statementEnded(" + statement + ")";
    }

    private void write(List<RecordedCluster> clusters) {
        generation.write(
                run,
                clusters.size(),
                clusters,
                recorded -> new ClusterMaterial(SEED_PATH, List.of()),
                MODEL_NAME,
                THE_WINDOW,
                new RecordingProgress());
    }

    /** Every callback, in the order it came, written into {@link #calls} as the call it was. */
    private class RecordingProgress implements GenerationProgress {

        @Override
        public void statementStarting(SynthesisStatement statement, OptionalLong rowsUpTo) {
            calls.add("statementStarting(" + statement + ", " + rowsUpTo + ")");
        }

        @Override
        public void stepsTaken(SynthesisStatement statement, long steps) {
            calls.add("stepsTaken(" + statement + ", " + steps + ")");
        }

        @Override
        public void statementEnded(SynthesisStatement statement) {
            calls.add(ended(statement));
        }

        @Override
        public void noSendableDocument(RecordedCluster cluster) {
            calls.add("noSendableDocument");
        }

        @Override
        public void nothingFitsTheWindow(RecordedCluster cluster, int documentCount, int contextWindow) {
            calls.add("nothingFitsTheWindow");
        }

        @Override
        public void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault) {
            calls.add("noDocumentCountedInsideTheRoom");
        }

        @Override
        public void answerTurnedDown(RecordedCluster cluster, ClusterFault fault) {
            calls.add("answerTurnedDown");
        }

        @Override
        public void toGoThrough(long clusterCount) {
            calls.add("toGoThrough " + clusterCount);
        }

        @Override
        public void clusterGoneThrough() {
            calls.add("clusterGoneThrough");
        }
    }
}
