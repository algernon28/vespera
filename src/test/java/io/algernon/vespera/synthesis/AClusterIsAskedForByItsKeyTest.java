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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What stage 6b asks of one cluster by its key (ADR-223 sections 5 and 6): whether a synthesis doc is
 * written over it, the doc itself with the documents its call carried, and the fault recorded against it.
 *
 * <p>Until ADR-223 each of these was learnt by reading every row one run left in the table: every synthesis
 * doc with its prose to know which clusters to walk past, and again to write the tree, and every fault to say
 * why a page has no writing. A cluster's key is the run, its winning seed and its ordinal, which is the
 * primary key of both tables, so each question is one row's lookup.
 *
 * <p>The documents a call carried come back in citation order (ADR-133), which is here the reverse of the
 * order the two documents were recorded in the ledger.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
class AClusterIsAskedForByItsKeyTest {

    private static final int THE_WRITTEN_CLUSTER = 0;

    private static final int THE_FAULTED_CLUSTER = 1;

    private static final int A_CLUSTER_NEVER_REACHED = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SynthesisDocs synthesisDocs;
    private ClusterFaults clusterFaults;
    private OccurrenceId seed;
    private OccurrenceId anotherSeed;
    private OccurrenceId recordedFirst;
    private OccurrenceId recordedSecond;
    private RunId generation;
    private RunId anotherGeneration;

    @BeforeEach
    void oneClusterWrittenAndOneFaulted() {
        synthesisDocs = new SynthesisDocs(jdbcTemplate);
        clusterFaults = new ClusterFaults(jdbcTemplate);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-by-key-" + System.nanoTime()));
        seed = anOccurrence(ledger, walk, "seeds/safety.docx");
        anotherSeed = anOccurrence(ledger, walk, "seeds/acoustics.docx");
        recordedFirst = anOccurrence(ledger, walk, "reports/a.docx");
        recordedSecond = anOccurrence(ledger, walk, "reports/b.docx");
        generation = ledger.runs().startRun("generation", "g" + System.nanoTime(), "{}", walk, List.of());
        anotherGeneration = ledger.runs().startRun("generation", "h" + System.nanoTime(), "{}", walk, List.of());

        synthesisDocs.record(
                generation,
                seed,
                THE_WRITTEN_CLUSTER,
                new SynthesisDoc("A title", "It says [1] and then [2].", List.of(recordedSecond, recordedFirst)));
        clusterFaults.record(
                generation, seed, THE_FAULTED_CLUSTER, new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "no closing brace"));
    }

    @Test
    @Story("Whether a group is written is asked of that group alone")
    @DisplayName("A group with a written text answers yes; the same group under another exemplar or another run, and a group with none, answer no")
    void whetherAClusterIsWritten() {
        claim(
                "the group written over under this run answers that it is written",
                () -> assertThat(synthesisDocs.isWritten(generation, seed, THE_WRITTEN_CLUSTER)).isTrue());
        claim(
                "the group that carries a failure, and the one never reached, answer that they are not",
                () -> {
                    assertThat(synthesisDocs.isWritten(generation, seed, THE_FAULTED_CLUSTER)).isFalse();
                    assertThat(synthesisDocs.isWritten(generation, seed, A_CLUSTER_NEVER_REACHED)).isFalse();
                });
        claim(
                "and a group of the same number under another exemplar, or under another run, is another group:"
                        + " neither is written",
                () -> {
                    assertThat(synthesisDocs.isWritten(generation, anotherSeed, THE_WRITTEN_CLUSTER)).isFalse();
                    assertThat(synthesisDocs.isWritten(anotherGeneration, seed, THE_WRITTEN_CLUSTER)).isFalse();
                });
    }

    @Test
    @Story("A group's written text is read for that group alone")
    @DisplayName("A group's written text comes back with its title, its words and the documents it was written from, in the order they were numbered")
    void theDocOfOneCluster() {
        claim(
                "the text comes back whole, and the two documents its call carried come back in the order they"
                        + " were numbered for the model, the second recorded first, so that [1] still lands on"
                        + " the document it was written from",
                () -> assertThat(synthesisDocs.forCluster(generation, seed, THE_WRITTEN_CLUSTER))
                        .contains(new SynthesisDoc(
                                "A title", "It says [1] and then [2].", List.of(recordedSecond, recordedFirst))));
        claim(
                "a group nothing was written over answers nothing, under this run or another",
                () -> {
                    assertThat(synthesisDocs.forCluster(generation, seed, THE_FAULTED_CLUSTER)).isEmpty();
                    assertThat(synthesisDocs.forCluster(anotherGeneration, seed, THE_WRITTEN_CLUSTER)).isEmpty();
                });
    }

    @Test
    @Story("Why a group has no written text is read for that group alone")
    @DisplayName("A group's recorded failure comes back with its kind and its detail, and a group with none answers nothing")
    void theFaultOfOneCluster() {
        claim(
                "the failure kept against the group comes back as it was recorded",
                () -> assertThat(clusterFaults.forCluster(generation, seed, THE_FAULTED_CLUSTER))
                        .contains(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "no closing brace")));
        claim(
                "the written group, the group never reached, and the same group under another run carry none",
                () -> {
                    assertThat(clusterFaults.forCluster(generation, seed, THE_WRITTEN_CLUSTER)).isEmpty();
                    assertThat(clusterFaults.forCluster(generation, seed, A_CLUSTER_NEVER_REACHED)).isEmpty();
                    assertThat(clusterFaults.forCluster(anotherGeneration, seed, THE_FAULTED_CLUSTER)).isEmpty();
                });
    }

    private static OccurrenceId anOccurrence(Ledger ledger, WalkId walk, String path) {
        ledger.occurrences().fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
        return ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
    }
}
