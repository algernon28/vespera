package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What is kept about a cluster whose answer was rejected (ADR-111, #183).
 *
 * <p><b>A rejected answer leaves a row, not an absence.</b> A cluster with nothing written over it and
 * nothing saying why is indistinguishable from one the run never reached, so the reason the answer
 * was turned down is kept beside the cluster it was about — which is what lets somebody find out what
 * went wrong without asking the model again. The precedents are the two tables this project already
 * has for a recorded fault that judges nobody: the walk anomaly and the unusable seed.
 *
 * <p><b>The ways an answer can be turned down are a closed set.</b> Four of them, fixed in the code
 * rather than written into the row as free text, so a fifth cannot be added by whoever writes the
 * next caller — it takes a change to this type, which is a change somebody reviews.
 *
 * <p><b>Nothing here is a judgement.</b> A rejected answer says nothing about the documents it was
 * about: they are exactly as relevant as they were, none of them is removed, and a reader of the
 * judgements must not be able to tell this happened at all.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("183")
@Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class ClusterFaultsTest {

    /** The topic the repaired cluster lies in (ADR-409 section 1). */
    private static final TopicKey THE_TOPIC = new TopicKey("seeds/safety.docx", 0);

    /** Another topic, whose cluster sits at the same place in its own order. */
    private static final TopicKey ANOTHER_TOPIC = new TopicKey("seeds/handling.docx", 0);

    /** Both topics are undivided (ADR-409 section 3). */
    private static final OptionalInt NO_SUBTOPIC = OptionalInt.empty();

    /** Which cluster of its topic this is: its identity, which never moves. */
    private static final int CLUSTER_ORDINAL = 3;

    /** A second cluster of the same topic's own clusters, so dropping one reason has another to spare. */
    private static final int ANOTHER_CLUSTER_ORDINAL = 4;

    /**
     * How many reasons stand under one record before anything is dropped: one for the cluster repaired,
     * one for the other cluster of the same topic, and one for a cluster under a different topic that sits at
     * the same place in that topic's order.
     */
    private static final int THREE_REASONS_KEPT_UNDER_ONE_RECORD = 3;

    /** What those three leave behind once the reason for one of the three clusters is dropped. */
    private static final int TWO_REASONS_LEFT = 2;

    /** The number that failed, kept in the row so nobody has to ask the model again to learn it. */
    private static final String DETAIL = "citation 7 against 2 document(s) sent";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A reason is dropped for one group under one record, and for nothing else")
    @DisplayName("Dropping the reason kept for a group leaves the other group's reason and the same group's reason under another record standing")
    @Issue("185")
    @Link(name = "ADR-077", url = Adr.A_REGENERATED_MEASUREMENT_IS_KEYED_PER_RUN, type = "adr")
    void dropsTheReasonForOneClusterUnderOneRecordAndNoOther() {
        ClusterFaults faults = new ClusterFaults(jdbcTemplate);
        OccurrenceId anyOccurrence = anOccurrence("seeds/safety.docx");
        RunId run = aRun(anyOccurrence);
        RunId anotherRun = aRun(anyOccurrence);
        ClusterSlot repaired = new ClusterSlot(THE_TOPIC, NO_SUBTOPIC, CLUSTER_ORDINAL);
        ClusterSlot theOtherOfTheSameTopic = new ClusterSlot(THE_TOPIC, NO_SUBTOPIC, ANOTHER_CLUSTER_ORDINAL);
        ClusterSlot atTheSamePlaceInAnotherTopic = new ClusterSlot(ANOTHER_TOPIC, NO_SUBTOPIC, CLUSTER_ORDINAL);
        faults.record(run, repaired, new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, DETAIL));
        faults.record(
                run, atTheSamePlaceInAnotherTopic, new ClusterFault(ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM, DETAIL));
        faults.record(run, theOtherOfTheSameTopic, new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, DETAIL));
        faults.record(anotherRun, repaired, new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, DETAIL));

        faults.delete(run, repaired);

        claim(
                "the other two groups under the same record still keep their own reasons -- "
                        + TWO_REASONS_LEFT + " of the " + THREE_REASONS_KEPT_UNDER_ONE_RECORD
                        + " kept there. A later answer was believed about one group and says nothing about"
                        + " any other, so dropping a reason for more than the one group would lose why the"
                        + " rest were left unwritten",
                () -> assertThat(WholeRun.clusterFaults(jdbcTemplate,run)).hasSize(TWO_REASONS_LEFT));
        claim(
                "one of the two is the other group of the same topic's own groups, told apart from the"
                        + " repaired one by its place in that topic's order and nothing else",
                () -> assertThat(WholeRun.clusterFaults(jdbcTemplate,run)).anySatisfy(recorded -> {
                    assertThat(recorded.cluster()).isEqualTo(theOtherOfTheSameTopic);
                    assertThat(recorded.fault().kind()).isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION);
                }));
        claim(
                "and the other is a group under a different topic that sits at the same place in its own"
                        + " topic's order. A group's place is counted afresh for each topic, so the number"
                        + " alone names no group: dropping a reason by that number would take the reason"
                        + " away from one group in every topic's set at once, and the run's account of why"
                        + " those groups are holes would be gone with no sign that it ever existed",
                () -> assertThat(WholeRun.clusterFaults(jdbcTemplate,run)).anySatisfy(recorded -> {
                    assertThat(recorded.cluster()).isEqualTo(atTheSamePlaceInAnotherTopic);
                    assertThat(recorded.fault().kind()).isEqualTo(ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM);
                }));
        claim(
                "and the same group keeps the reason standing against it under the other record, which is"
                        + " a separate piece of work over the same documents: a second one writes beside the"
                        + " first rather than over it, so an answer believed under one record cannot drop"
                        + " what another recorded about the same group",
                () -> assertThat(WholeRun.clusterFaults(jdbcTemplate,anotherRun)).singleElement().satisfies(recorded -> {
                    assertThat(recorded.cluster()).isEqualTo(repaired);
                    assertThat(recorded.fault().kind()).isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE);
                }));
    }

    private OccurrenceId anOccurrence(String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        ledger.occurrences().fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-09-15T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        return ledger.runs().startRun(
                "generation", "abc" + System.nanoTime(), "{}", theWalkOf(anyOccurrenceInTheWalk), List.of());
    }

    private WalkId theWalkOf(OccurrenceId occurrence) {
        return new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, occurrence.value()));
    }
}
