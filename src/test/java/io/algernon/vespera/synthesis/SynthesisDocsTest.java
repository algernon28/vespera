package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
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
 * What one call produced, kept (ADR-108, ADR-110, #180).
 *
 * <p><b>Kept rather than written straight out to a file.</b> Generating is the most expensive thing
 * this system does, and the file a reader opens is a rendering of this row rather than a copy of it:
 * the markers become links and the list of documents is composed at write time. Holding the answer
 * is what lets the deliverable be rebuilt without asking again, and what gives a cluster that could
 * not be written an obvious shape — no row.
 *
 * <p><b>What must be shown not to happen.</b> Nothing here is a judgement: writing over a cluster of
 * survivors takes no document out of anything, so a reader of the judgements must not be able to
 * tell this ran at all.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("180")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-110", url = Adr.PIPELINE_HANDS_SYNTHESIS_ITS_INPUTS, type = "adr")
class SynthesisDocsTest {

    /** The title the model gave the cluster, which is what the deliverable heads the cluster with. */
    private static final String TITLE = "Site Safety Audits, 2018 to 2021";

    /** The writing, with the markers left exactly as they came back rather than turned into anything. */
    private static final String PROSE =
            "The earliest audit [1] sets the pattern the later one [2] is measured against.";

    /** Which cluster of its partition this is: its identity, which never moves. */
    private static final int CLUSTER_ORDINAL = 3;

    /**
     * The citation ordinal a stale row is written under, standing for what an invocation that died
     * between the two statements would have left behind.
     */
    private static final int THE_FIRST_ORDINAL = 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * The walk every occurrence below is observed in, started once so one run can name all of them.
     *
     * <p>An instance field rather than a static one: JUnit builds a test instance per test, so this
     * cannot carry a walk from one method into the next.
     */
    private WalkId walk;

    @Test
    @Story("What was written over a group is kept, so the deliverable can be rebuilt without asking again")
    @DisplayName("A group written again keeps the documents of the call that stands, not an abandoned one's")
    @Link(name = "ADR-133", url = Adr.THE_EXEMPLARS_ONE_CALL_SENT_ARE_RECORDED, type = "adr")
    void clearsWhatAnAbandonedCallLeftUnderTheSameCluster() {
        SynthesisDocs docs = new SynthesisDocs(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        OccurrenceId abandoned = anOccurrence("audits/2018.pdf");
        OccurrenceId earliest = anOccurrence("audits/2019.pdf");
        OccurrenceId later = anOccurrence("audits/2021.pdf");
        RunId run = aRun(seed);
        // What an invocation that died between the two statements leaves: documents recorded as sent
        // for a group that ended up carrying no writing at all. The next invocation asks about that
        // group again, because the row that would have said it was done was never written.
        jdbcTemplate.update(
                "INSERT INTO call_exemplar (run_id, winning_seed_occurrence_id, cluster_ordinal,"
                        + " citation_ordinal, occurrence_id) VALUES (?, ?, ?, ?, ?)",
                run.value(),
                seed.value(),
                CLUSTER_ORDINAL,
                THE_FIRST_ORDINAL,
                abandoned.value());

        docs.record(run, seed, CLUSTER_ORDINAL, new SynthesisDoc(TITLE, PROSE, List.of(earliest, later)));

        claim(
                "the documents kept are the ones the call that stands carried, and the abandoned call's"
                        + " are gone: a row left over from a call nothing was kept from would take a"
                        + " number the writing uses, and every entry after it would move -- which is the"
                        + " wrong link this record exists to make impossible",
                () -> assertThat(WholeRun.synthesisDocs(jdbcTemplate,run))
                        .singleElement()
                        .satisfies(recorded -> assertThat(recorded.doc().sent())
                                .containsExactly(earliest, later)
                                .doesNotContain(abandoned)));
    }

    private OccurrenceId anOccurrence(String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        if (walk == null) {
            walk = ledger.walks().startWalk(Path.of("C:/corpus"));
        }
        ledger.occurrences().fileOccurrence(
                walk,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-09-15T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.runs().startRun("generation", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
