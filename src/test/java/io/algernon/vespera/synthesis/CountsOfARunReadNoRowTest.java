package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
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
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How many clusters an arrangement holds, how many synthesis docs a generation run wrote, and how many faults
 * stand under it, each counted by the database (ADR-220 section 7). Until ADR-220 the closing line read every
 * cluster of the arrangement and every synthesis doc of the run, its prose included, only to take how many there
 * were, and the generation step read every fault standing to do the same.
 *
 * <p>It names the three methods ADR-220 adds, so the test tree does not compile until they exist.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class CountsOfARunReadNoRowTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    /** Three groups arranged, two written over and one standing with a fault, under the runs asked about. */
    private static final int ARRANGED = 3;

    private static final int WRITTEN = 2;

    private static final int FAULTED = 1;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId arrangement;
    private RunId generation;

    @BeforeEach
    void anArrangementAndAGenerationRunBesideOthers() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        Ledger ledger = new Ledger(pool.jdbcTemplate());
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-counted"));
        OccurrenceId seed = occurrence(ledger, walk, "seeds/one.docx");
        OccurrenceId member = occurrence(ledger, walk, "reports/a.pdf");
        arrangement = ledger.runs().startRun("arrangement", "a214", "{}", walk, List.of());
        RunId otherArrangement = ledger.runs().startRun("arrangement", "b214", "{}", walk, List.of());
        generation = ledger.runs().startRun("generation", "g214", "{}", walk, List.of(arrangement));
        RunId otherGeneration = ledger.runs().startRun("generation", "h214", "{}", walk, List.of(otherArrangement));
        Clusters clusters = new Clusters(pool.jdbcTemplate());
        SynthesisDocs docs = new SynthesisDocs(pool.jdbcTemplate());
        ClusterFaults faults = new ClusterFaults(pool.jdbcTemplate());
        for (int ordinal = 0; ordinal < ARRANGED; ordinal++) {
            clusters.record(
                    arrangement, new ArrangedCluster(seed, ordinal, 1, 1, ordinal + 1), new ClusterLabel("Group " + ordinal));
        }
        clusters.record(otherArrangement, new ArrangedCluster(seed, 0, 1, 1, 1), new ClusterLabel("Another group"));
        for (int ordinal = 0; ordinal < WRITTEN; ordinal++) {
            docs.record(generation, seed, ordinal, new SynthesisDoc("Title " + ordinal, "Prose [1].", List.of(member)));
        }
        docs.record(otherGeneration, seed, 0, new SynthesisDoc("Another title", "Other prose [1].", List.of(member)));
        faults.record(generation, seed, WRITTEN, new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, "cited [9]"));
        faults.record(otherGeneration, seed, 1, new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, "cited [8]"));
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("How much of the deliverable was written is counted, not read")
    @DisplayName("The groups arranged, the writing kept and the faults standing are each counted by the database, under their own run only")
    void eachIsCountedUnderItsOwnRunByTheDatabase() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        int arranged = new Clusters(log.jdbcTemplate()).countForRun(arrangement);
        int written = new SynthesisDocs(log.jdbcTemplate()).countForRun(generation);
        int faulted = new ClusterFaults(log.jdbcTemplate()).countForRun(generation);

        claim(
                "the arrangement holds " + ARRANGED + " groups, " + WRITTEN + " were written over under the generation"
                        + " run and " + FAULTED + " stands with a fault: none of the other runs' rows is counted",
                () -> assertThat(List.of(arranged, written, faulted)).containsExactly(ARRANGED, WRITTEN, FAULTED));
        claim(
                "each is one statement that counts, reading no column of any row, the writing's prose least of all",
                () -> assertThat(log.said())
                        .hasSize(3)
                        .allSatisfy(sql -> {
                            assertThat(sql).contains("COUNT(");
                            assertThat(sql).doesNotContain("prose").doesNotContain("label").doesNotContain("detail");
                        }));
    }

    private static OccurrenceId occurrence(Ledger ledger, WalkId walk, String name) {
        OccurrencePath path = new OccurrencePath(name);
        ledger.occurrences().fileOccurrence(walk, path, 1, WHEN, WHEN);
        return ledger.occurrences().occurrenceId(walk, path).orElseThrow();
    }
}
