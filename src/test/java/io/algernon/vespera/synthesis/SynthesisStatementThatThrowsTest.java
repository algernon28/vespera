package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ADR-204 section 4, "on every path but one that throws", for {@code synthesis} (ADR-193 section 7, #411):
 * the one statement of {@code ClusterGeneration.write} that is announced, the timed count of the standing
 * faults, is started and never said to have ended where it throws. The table the read goes to is dropped once
 * the read has been announced, so the read itself is what fails.
 *
 * <p><b>There were two such statements until ADR-223.</b> The read of the clusters already written is gone:
 * whether a cluster is written is asked by its key, one row at a time, which is a lookup and is announced by
 * nobody (ADR-193 section 1), so the test that dropped {@code synthesis_doc} under it went with it.
 *
 * <p><b>No Spring context, and a database file of its own</b> ({@link PoolOfTwo}, in a folder JUnit removes).
 * A test that drops a table, and commits what its fixture writes, may not do either in the in-memory
 * database every {@code @JdbcTest} class shares: other classes read {@code walk} and {@code run} there and
 * expect to find one row. Nothing here reaches that database.
 */
@Epic("Synthesis")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-204", url = Adr.PART_B_OF_THE_STATEMENTS_WRITTEN_OUT, type = "adr")
class SynthesisStatementThatThrowsTest {

    private static final String MODEL_NAME = "qwen3:8b";
    private static final int THE_WINDOW = 8192;
    private static final String SEED_PATH = "seeds/safety.docx";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private JdbcTemplate jdbcTemplate;
    private RunId run;
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void aPoolOfThisTestsOwnAndARun() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-statements"));
        run = ledger.runs().startRun("generation", "g-statements", "{}", walk, List.of());
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Writing over the groups says what it is reading")
    @DisplayName("A read of the standing faults that throws is not said to have ended")
    void theReadOfTheStandingFaultsThatThrowsIsNotSaidToHaveEnded() {
        claim(
                "writing fails as the template reports any statement's failure, once the table is gone",
                () -> assertThatThrownBy(() -> writeDroppingATable(SynthesisStatement.STANDING_FAULTS, "cluster_fault"))
                        .isInstanceOf(DataAccessException.class));
        claim(
                "the caller was told the walk over no group was announced, with no read before it, and then"
                        + " that the read of the standing faults started, with no total, and never that it ended",
                () -> assertThat(calls)
                        .containsExactly("toGoThrough 0", starting(SynthesisStatement.STANDING_FAULTS)));
    }

    private static String starting(SynthesisStatement statement) {
        return "statementStarting(" + statement + ", " + OptionalLong.empty() + ")";
    }

    /** Writes over no cluster, with a caller that drops {@code table} when {@code statement} is announced. */
    private void writeDroppingATable(SynthesisStatement statement, String table) {
        ChatModel neverAsked = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("no answer is asked for in this test");
            }
        };
        new ClusterGeneration(
                        new ClusterSynthesis(neverAsked), new SynthesisDocs(jdbcTemplate), new ClusterFaults(jdbcTemplate))
                .write(
                        run,
                        0,
                        List.of(),
                        recorded -> new ClusterMaterial(SEED_PATH, List.of()),
                        MODEL_NAME,
                        THE_WINDOW,
                        new GenerationProgress() {
                            @Override
                            public void statementStarting(SynthesisStatement started, OptionalLong rowsUpTo) {
                                calls.add("statementStarting(" + started + ", " + rowsUpTo + ")");
                                if (started == statement) {
                                    jdbcTemplate.execute("DROP TABLE " + table);
                                }
                            }

                            @Override
                            public void statementEnded(SynthesisStatement ended) {
                                calls.add("statementEnded(" + ended + ")");
                            }

                            @Override
                            public void toGoThrough(long clusterCount) {
                                calls.add("toGoThrough " + clusterCount);
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
                        });
    }
}
