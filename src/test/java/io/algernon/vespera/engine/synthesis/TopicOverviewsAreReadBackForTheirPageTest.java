package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link TopicOverviews#writtenFor} and {@link TopicOverviews#faultOf} over rows {@link TopicOverviews} recorded
 * (ADR-415 §11, ADR-402 §7): a topic's writing with how many numbers its call sent, the kind of a topic's fault, and
 * nothing of another topic or of another generation run.
 *
 * <p>Under one generation run: a topic on sluices with an overview over groups 1 and 3 of three, and a topic on pumps
 * whose overview was turned down for running out of room. Under another run of the same walk: the topic on sluices
 * with an overview over one group, and a fault for a third topic, on gates.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Generation")
@Feature("Topic overview")
@Issue("833")
@Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class TopicOverviewsAreReadBackForTheirPageTest {

    private static final TopicKey SLUICES = new TopicKey("seeds/sluices.docx", 0);

    private static final TopicKey PUMPS = new TopicKey("seeds/pumps.docx", 2);

    private static final TopicKey GATES = new TopicKey("seeds/gates.docx", 1);

    /** The three clusters of the topic on sluices, by their number, from 1. */
    private static final List<ClusterSlot> SLUICES_CLUSTERS = List.of(
            new ClusterSlot(SLUICES, OptionalInt.empty(), 0),
            new ClusterSlot(SLUICES, OptionalInt.empty(), 1),
            new ClusterSlot(SLUICES, OptionalInt.empty(), 2));

    private static final String THE_WRITING = "Sluices silt up [1], and their gates warp [3].";

    private static final String ANOTHER_RUNS_WRITING = "Sluices silt up [1].";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A topic's page carries its overview, each citation leading to the group it cites")
    @DisplayName("A topic's overview is read back with how many groups its call sent, a turned-down one by its fault's kind, and nothing of another topic or another writing run")
    void anOverviewIsReadWithItsCountAndAFaultByItsKindUnderItsRunAlone() {
        TopicOverviews overviews = new TopicOverviews(jdbcTemplate);
        RunId generation = aGenerationRun();
        RunId another = aGenerationRun();
        overviews.recordWritten(generation, SLUICES,
                new TopicOverviewOutcome.Written(THE_WRITING, List.of(1, 3), 1), SLUICES_CLUSTERS);
        overviews.recordTurnedDown(generation, PUMPS, new TopicOverviewOutcome.TurnedDown(
                new ClusterFault(ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM, "the answer reached its limit"), 1));
        overviews.recordWritten(another, SLUICES,
                new TopicOverviewOutcome.Written(ANOTHER_RUNS_WRITING, List.of(1), 2), SLUICES_CLUSTERS);
        overviews.recordTurnedDown(another, GATES, new TopicOverviewOutcome.TurnedDown(
                new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "not the shape asked for"), 2));

        claim("the topic on sluices' overview under the run asked about: its writing, and the two numbers its call sent"
                        + " of the topic's three groups",
                () -> assertThat(overviews.writtenFor(generation, SLUICES))
                        .contains(new TopicOverviews.RecordedOverview(THE_WRITING, 2)));
        claim("and no fault for it there",
                () -> assertThat(overviews.faultOf(generation, SLUICES)).isEmpty());
        claim("the topic on pumps has no overview under the run, and its fault's kind is read back",
                () -> {
                    assertThat(overviews.writtenFor(generation, PUMPS)).isEmpty();
                    assertThat(overviews.faultOf(generation, PUMPS))
                            .contains(ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM);
                });
        claim("the other run's rows are not read under the run asked about: neither its overview of the topic on"
                        + " sluices, with its one number, nor its fault of the topic on gates",
                () -> {
                    assertThat(overviews.writtenFor(another, SLUICES))
                            .contains(new TopicOverviews.RecordedOverview(ANOTHER_RUNS_WRITING, 1));
                    assertThat(overviews.faultOf(generation, GATES)).isEqualTo(Optional.empty());
                    assertThat(overviews.writtenFor(generation, GATES)).isEmpty();
                });
    }

    private RunId aGenerationRun() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus"));
        return ledger.runs().startRun("generation", "abc" + System.nanoTime(), "{}", walk, List.of());
    }
}
