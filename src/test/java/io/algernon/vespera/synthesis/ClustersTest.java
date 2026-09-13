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
 * Stage 6a's own row per cluster (ADR-105, ADR-110) — the level stage 5 left unbuilt.
 *
 * <p>Stage 5 records which documents share a cluster, and records it without the cluster having a row
 * anywhere, which is what keeps membership from falling out of step with itself. What was missing is
 * the cluster as something addressable: a name, a size and a place. This is that, and it restates
 * none of the membership.
 *
 * <p><b>What must be shown not to happen.</b> Nothing here is a verdict: stage 6a removes no document
 * from anything, so no row it writes may appear in the verdict table, and a reader of that table must
 * not be able to tell that this stage ran.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Arrangement")
@Feature("Recording the arrangement")
@Issue("175")
@Link(name = "ADR-105", url = Adr.STAGE_6A_NAMES_THE_ARRANGEMENT_STAGE_5_BUILT, type = "adr")
class ClustersTest {

    /** The name the cluster below was given, and the one it has to come back carrying. */
    private static final String LABEL = "2019 Site Safety Audit";

    /** How many survivors that cluster holds. */
    private static final int DOCUMENT_COUNT = 47;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A group of documents becomes something with a name and a place")
    @DisplayName("A group is recorded with its name, its size and where it sits, and reads back whole")
    void recordsAClusterWithItsNameSizeAndPlace() {
        Clusters clusters = new Clusters(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        RunId run = aRun(seed);

        clusters.record(run, new ArrangedCluster(seed, 3, DOCUMENT_COUNT, 1, 2), new ClusterLabel(LABEL));

        claim(
                "the group comes back carrying everything a reader of the arrangement is shown -- what it"
                        + " is called, how many documents are under it, and where it sits at both levels --"
                        + " so the page that renders it never has to work any of that out a second time",
                () -> assertThat(clusters.forRun(run))
                        .singleElement()
                        .satisfies(recorded -> {
                            assertThat(recorded.label().value()).isEqualTo(LABEL);
                            assertThat(recorded.cluster().documentCount()).isEqualTo(DOCUMENT_COUNT);
                            assertThat(recorded.cluster().partitionOrder()).isEqualTo(1);
                            assertThat(recorded.cluster().clusterOrder()).isEqualTo(2);
                            assertThat(recorded.cluster().ordinal()).isEqualTo(3);
                            assertThat(recorded.cluster().winningSeed()).isEqualTo(seed);
                        }));
    }

    @Test
    @Story("Arranging documents takes nothing out of the archive")
    @DisplayName("Arranging documents records no judgement against any of them")
    void writesNoVerdict() {
        Clusters clusters = new Clusters(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/safety.docx");
        RunId run = aRun(seed);

        clusters.record(run, new ArrangedCluster(seed, 0, DOCUMENT_COUNT, 1, 1), new ClusterLabel(LABEL));

        claim(
                "no judgement is recorded against any document because a group was named: every kind of"
                        + " judgement this system records exists to take a document out of what gets"
                        + " published, and naming a group takes nothing out of anything",
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
                Instant.parse("2026-09-13T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.startRun("arrangement", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
