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
import java.util.List;
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
class ClusterFaultsTest {

    /** Which cluster of its partition this is: its identity, which never moves. */
    private static final int CLUSTER_ORDINAL = 3;

    /** A second cluster of the same seed's own clusters, so dropping one reason has another to spare. */
    private static final int ANOTHER_CLUSTER_ORDINAL = 4;

    /**
     * How many reasons stand under one record before anything is dropped: one for the cluster repaired,
     * one for the other cluster of the same seed, and one for a cluster under a different seed that sits at
     * the same place in that seed's order.
     */
    private static final int THREE_REASONS_KEPT_UNDER_ONE_RECORD = 3;

    /** What those three leave behind once the reason for one of the three clusters is dropped. */
    private static final int TWO_REASONS_LEFT = 2;

    /** The number that failed, kept in the row so nobody has to ask the model again to learn it. */
    private static final String DETAIL = "citation 7 against 2 document(s) sent";

    /**
     * How many ways an answer can be turned down: the question arriving cut short, the answer stopping
     * because it ran out of room, an answer nothing can read back into a heading and its writing, and
     * a citation pointing at no document the call carried. Four, and a fifth is a change to the code
     * rather than a value somebody passes in.
     */
    private static final int FOUR_WAYS_AN_ANSWER_CAN_BE_TURNED_DOWN = 4;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A group left unwritten keeps the reason it was left unwritten")
    @DisplayName("The reason an answer was turned down is kept against the group it was about")
    void keepsTheReasonAgainstTheClusterItWasAbout() {
        ClusterFaults faults = new ClusterFaults(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        RunId run = aRun(seed);

        faults.record(
                run,
                seed,
                CLUSTER_ORDINAL,
                new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, DETAIL));

        claim(
                "what comes back names the group and says which check turned the answer down, so the"
                        + " group with nothing written over it is a group somebody can account for rather"
                        + " than a hole with no explanation anywhere",
                () -> assertThat(faults.forRun(run)).singleElement().satisfies(recorded -> {
                    assertThat(recorded.winningSeed()).isEqualTo(seed);
                    assertThat(recorded.clusterOrdinal()).isEqualTo(CLUSTER_ORDINAL);
                    assertThat(recorded.fault().kind()).isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE);
                }));
        claim(
                "and it carries the number that failed, word for word as it was recorded: which check"
                        + " turned the answer down says what kind of thing went wrong, and the number is"
                        + " what distinguishes an answer that pointed at a document that was never sent"
                        + " from one that was cut off -- without it, learning that costs the most"
                        + " expensive call this system makes, made a second time",
                () -> assertThat(faults.forRun(run))
                        .singleElement()
                        .satisfies(recorded -> assertThat(recorded.fault().detail()).isEqualTo(DETAIL)));
    }

    @Test
    @Story("The ways an answer can be turned down are fixed, and a new one is a decision somebody takes")
    @DisplayName("There are four ways an answer can be turned down, and no way to record a fifth")
    void namesFourWaysAndNoFifth() {
        claim(
                "there are exactly " + FOUR_WAYS_AN_ANSWER_CAN_BE_TURNED_DOWN + " of them -- the question"
                        + " arriving cut short, the answer running out of room, an answer nothing can read,"
                        + " and a citation pointing at a document the call never carried -- and they are"
                        + " fixed in the code rather than free text a caller supplies, so a fifth cannot be"
                        + " introduced by somebody writing the next caller and meaning well. A set that"
                        + " anyone may add to is a set whose meaning stops living in one place",
                () -> assertThat(ClusterFaultKind.values())
                        .hasSize(FOUR_WAYS_AN_ANSWER_CAN_BE_TURNED_DOWN)
                        .containsExactlyInAnyOrder(
                                ClusterFaultKind.PROMPT_EVALUATION_CEILING,
                                ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM,
                                ClusterFaultKind.SCHEMA_VIOLATION,
                                ClusterFaultKind.CITATION_NOT_IN_RANGE));
    }

    @Test
    @Story("A reason is dropped once a later answer about the same group is believed")
    @DisplayName("Dropping the reason kept for a group removes it, and dropping one for a group that has none does nothing")
    @Issue("185")
    void dropsTheReasonKeptAndDoesNothingWhenNoneIsKept() {
        ClusterFaults faults = new ClusterFaults(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        RunId run = aRun(seed);
        faults.record(
                run, seed, CLUSTER_ORDINAL, new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, DETAIL));

        faults.delete(run, seed, CLUSTER_ORDINAL);

        claim(
                "nothing is kept against the group any more. A later answer about it was believed and has"
                        + " been written over the group, so a reason still standing here would have the"
                        + " record saying one group under one run was both written over and left unwritten",
                () -> assertThat(faults.forRun(run)).isEmpty());
        claim(
                "and dropping a reason for a group that has none does nothing and fails nothing, which is"
                        + " the ordinary case: almost every group is answered well the first time and never"
                        + " had a reason kept against it to drop",
                () -> {
                    faults.delete(run, seed, CLUSTER_ORDINAL);
                    assertThat(faults.forRun(run)).isEmpty();
                });
    }

    @Test
    @Story("A reason is dropped for one group under one record, and for nothing else")
    @DisplayName("Dropping the reason kept for a group leaves the other group's reason and the same group's reason under another record standing")
    @Issue("185")
    @Link(name = "ADR-077", url = Adr.A_REGENERATED_MEASUREMENT_IS_KEYED_PER_RUN, type = "adr")
    void dropsTheReasonForOneClusterUnderOneRecordAndNoOther() {
        ClusterFaults faults = new ClusterFaults(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        RunId run = aRun(seed);
        OccurrenceId anotherSeed = anotherOccurrenceInTheWalkOf(seed, "seeds/handling.docx");
        RunId anotherRun = aRun(seed);
        faults.record(
                run, seed, CLUSTER_ORDINAL, new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, DETAIL));
        faults.record(
                run,
                anotherSeed,
                CLUSTER_ORDINAL,
                new ClusterFault(ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM, DETAIL));
        faults.record(
                run,
                seed,
                ANOTHER_CLUSTER_ORDINAL,
                new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, DETAIL));
        faults.record(
                anotherRun,
                seed,
                CLUSTER_ORDINAL,
                new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, DETAIL));

        faults.delete(run, seed, CLUSTER_ORDINAL);

        claim(
                "the other two groups under the same record still keep their own reasons -- "
                        + TWO_REASONS_LEFT + " of the " + THREE_REASONS_KEPT_UNDER_ONE_RECORD
                        + " kept there. A later answer was believed about one group and says nothing about"
                        + " any other, so dropping a reason for more than the one group would lose why the"
                        + " rest were left unwritten",
                () -> assertThat(faults.forRun(run)).hasSize(TWO_REASONS_LEFT));
        claim(
                "one of the two is the other group of the same seed's own groups, told apart from the"
                        + " repaired one by its place in that seed's order and nothing else",
                () -> assertThat(faults.forRun(run)).anySatisfy(recorded -> {
                    assertThat(recorded.winningSeed()).isEqualTo(seed);
                    assertThat(recorded.clusterOrdinal()).isEqualTo(ANOTHER_CLUSTER_ORDINAL);
                    assertThat(recorded.fault().kind()).isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION);
                }));
        claim(
                "and the other is a group under a different seed that sits at the same place in its own"
                        + " seed's order. A group's place is counted afresh for each seed, so the number"
                        + " alone names no group: dropping a reason by that number would take the reason"
                        + " away from one group in every seed's set at once, and the run's account of why"
                        + " those groups are holes would be gone with no sign that it ever existed",
                () -> assertThat(faults.forRun(run)).anySatisfy(recorded -> {
                    assertThat(recorded.winningSeed()).isEqualTo(anotherSeed);
                    assertThat(recorded.clusterOrdinal()).isEqualTo(CLUSTER_ORDINAL);
                    assertThat(recorded.fault().kind()).isEqualTo(ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM);
                }));
        claim(
                "and the same group keeps the reason standing against it under the other record, which is"
                        + " a separate piece of work over the same documents: a second one writes beside the"
                        + " first rather than over it, so an answer believed under one record cannot drop"
                        + " what another recorded about the same group",
                () -> assertThat(faults.forRun(anotherRun)).singleElement().satisfies(recorded -> {
                    assertThat(recorded.clusterOrdinal()).isEqualTo(CLUSTER_ORDINAL);
                    assertThat(recorded.fault().kind()).isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE);
                }));
    }

    @Test
    @Story("A rejected answer takes nothing out of the archive")
    @DisplayName("A group left unwritten records no judgement against any of its documents")
    void writesNoJudgement() {
        ClusterFaults faults = new ClusterFaults(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        RunId run = aRun(seed);

        faults.record(
                run,
                seed,
                CLUSTER_ORDINAL,
                new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, DETAIL));

        claim(
                "no judgement is recorded against any document because the answer about its group was"
                        + " turned down: every judgement this system records exists to take a document out"
                        + " of what gets published, and nothing is wrong with these documents -- what was"
                        + " rejected is what was said about them",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verdict", Integer.class))
                        .isZero());
    }

    private OccurrenceId anOccurrence(String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-09-15T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    /**
     * A second seed observed by the same walk as {@code sibling}, so that both can be winning seeds of
     * one run. {@link #anOccurrence} starts a walk of its own each time it is called, and two seeds in
     * two walks could never be partitions of the same run.
     */
    private OccurrenceId anotherOccurrenceInTheWalkOf(OccurrenceId sibling, String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = theWalkOf(sibling);
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-09-15T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        return ledger.startRun(
                "generation", "abc" + System.nanoTime(), "{}", theWalkOf(anyOccurrenceInTheWalk), List.of());
    }

    private WalkId theWalkOf(OccurrenceId occurrence) {
        return new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, occurrence.value()));
    }
}
