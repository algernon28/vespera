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
 * What is kept about a group whose answer was rejected (ADR-111, #183).
 *
 * <p><b>A rejected answer leaves a row, not an absence.</b> A group with nothing written over it and
 * nothing saying why is indistinguishable from one the run never reached, so the reason the answer
 * was turned down is kept beside the group it was about — which is what lets somebody find out what
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

    /** Which group of its partition this is: its identity, which never moves. */
    private static final int CLUSTER_ORDINAL = 3;

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
    void keepsTheReasonAgainstTheGroupItWasAbout() {
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

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.startRun("generation", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
