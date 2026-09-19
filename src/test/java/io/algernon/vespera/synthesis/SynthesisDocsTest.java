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

    /** How many documents that call was written from, which the deliverable states in the document. */
    private static final int DOCUMENTS_SENT = 2;

    /** Which cluster of its partition this is: its identity, which never moves. */
    private static final int CLUSTER_ORDINAL = 3;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What was written over a group is kept, so the deliverable can be rebuilt without asking again")
    @DisplayName("The writing is kept against the group it was written over, and reads back whole")
    void keepsTheWritingAgainstTheClusterItWasWrittenOver() {
        SynthesisDocs docs = new SynthesisDocs(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        RunId run = aRun(seed);

        docs.record(run, seed, CLUSTER_ORDINAL, new SynthesisDoc(TITLE, PROSE, DOCUMENTS_SENT));

        claim(
                "what comes back is what the model said and what it said it about: the heading, the text"
                        + " with every marker exactly where it was, the number of documents behind it, and"
                        + " the group itself -- because the page a reader opens is built from this row, and"
                        + " a row that had been tidied would describe writing nobody produced",
                () -> assertThat(docs.forRun(run)).singleElement().satisfies(recorded -> {
                    assertThat(recorded.winningSeed()).isEqualTo(seed);
                    assertThat(recorded.clusterOrdinal()).isEqualTo(CLUSTER_ORDINAL);
                    assertThat(recorded.doc().title()).isEqualTo(TITLE);
                    assertThat(recorded.doc().prose()).isEqualTo(PROSE);
                    assertThat(recorded.doc().documentsSent()).isEqualTo(DOCUMENTS_SENT);
                }));
    }

    @Test
    @Story("Writing over a group takes nothing out of the archive")
    @DisplayName("Writing over a group records no judgement against any document")
    void writesNoJudgement() {
        SynthesisDocs docs = new SynthesisDocs(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        RunId run = aRun(seed);

        docs.record(run, seed, CLUSTER_ORDINAL, new SynthesisDoc(TITLE, PROSE, DOCUMENTS_SENT));

        claim(
                "no judgement is recorded against any document because something was written over its"
                        + " group: every judgement this system records exists to take a document out of what"
                        + " gets published, and writing connecting text over what survived takes nothing out"
                        + " of anything",
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
