package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What one stage is told when it asks which run of the stage before it to read, once that answer is a
 * lookup rather than a recomputation (ADR-099).
 *
 * <p>The decision names this test outright: "the refusal is pinned by a test that inserts two rows for
 * one stage over one walk directly through {@link Ledger}, since the pipeline cannot produce them." A
 * walk has held at most one run per stage only because every ordinary invocation mints a fresh walk;
 * two runs over one walk is the situation the glossary calls normal and lookup cannot resolve, so the
 * refusal is built before walk reuse can reach it.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Ledger")
@Issue("114")
@Link(name = "ADR-099", url = Adr.AN_UPSTREAM_IS_LOOKED_UP_AND_TWO_CANDIDATES_STOP_THE_RUN, type = "adr")
class UpstreamRunsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** A stage name the lookup is asked about — any stage before another would do. */
    private static final String A_STAGE = "content-redundancy";

    @Test
    @Story("Which run of the stage before it a stage reads")
    @DisplayName("A stage with no run over this walk leaves its successor nothing to name")
    void refusesWhenTheStageHasNoRunOverThisWalk() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));

        claim(
                "asking for stage " + A_STAGE + "'s run over a walk it never ran against is a fault rather"
                        + " than an empty answer: nothing is recorded to look up, and a stage that named"
                        + " nothing upstream would fail later at the foreign key instead",
                () -> assertThatThrownBy(() -> new UpstreamRuns(ledger).runOf(A_STAGE, walkId))
                        .isInstanceOf(NoUpstreamRunException.class)
                        .hasMessageContaining(A_STAGE));
    }

    @Test
    @Story("Which run of the stage before it a stage reads")
    @DisplayName("The one run of a stage over this walk is the id its successor reads")
    void readsBackTheOneRunOfTheStage() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId recorded = ledger.startRun(A_STAGE, "a version", "{\"floor\":0.4}", walkId, List.of());

        claim(
                "the single recorded run of stage " + A_STAGE + " over this walk comes back as its own id,"
                        + " so a successor reads exactly the row that exists rather than one re-derived from"
                        + " inputs that could drift from what was written",
                () -> assertThat(new UpstreamRuns(ledger).runOf(A_STAGE, walkId)).isEqualTo(recorded));
    }

    @Test
    @Story("Which run of the stage before it a stage reads")
    @DisplayName("Two runs of one stage over one walk stop the successor rather than letting it guess")
    void refusesToChooseBetweenTwoRunsOfOneStageOverOneWalk() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId atFortyPercent = ledger.startRun(A_STAGE, "a version", "{\"floor\":0.4}", walkId, List.of());
        RunId atSixtyPercent = ledger.startRun(A_STAGE, "a version", "{\"floor\":0.6}", walkId, List.of());

        claim(
                "two runs of stage " + A_STAGE + " over this one walk stop the run with both ids and both"
                        + " configurations named, because a run is minted whenever the configuration changes,"
                        + " so both are legitimate and nothing in the data says which one was meant",
                () -> assertThatThrownBy(() -> new UpstreamRuns(ledger).runOf(A_STAGE, walkId))
                        .isInstanceOf(AmbiguousUpstreamRunException.class)
                        .hasMessageContaining(atFortyPercent.value())
                        .hasMessageContaining(atSixtyPercent.value())
                        .hasMessageContaining("0.4")
                        .hasMessageContaining("0.6"));
    }
}