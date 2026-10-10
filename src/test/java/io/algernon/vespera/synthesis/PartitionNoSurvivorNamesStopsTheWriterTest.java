package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where the tree's writer stops for a partition whose seed no survivor names (ADR-213 §6, ADR-223 §6): before
 * the tree's directory, any partition directory, any page or any total is written or announced.
 *
 * <p><b>What refuses it moved, and the stop moved earlier with it.</b> Until ADR-223 the index was composed
 * whole and first, and the index refused such a partition, after the tree's directory had been made and the
 * survivors asked for their pictures. Since ADR-223 the writer is handed its partitions one row each, a
 * partition's seed path arrives with it, and {@code ListedPartition} refuses one that has none. The writer
 * asks for its partitions before it does anything else, so nothing at all is on disk and nothing was
 * reported when it stops.
 *
 * <p>The partition nobody names is the second of two here, as it was: a writer that laid out the first
 * before asking about the second would leave the first one's directory behind.
 *
 * <p>Pure: the tree goes into a temporary directory and no database is used.
 */
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("351")
@Issue("472")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
class PartitionNoSurvivorNamesStopsTheWriterTest {

    private static final String RUN_ID = "d".repeat(48) + "0123456789abcdef";
    private static final long WALK = 7L;

    /** The seed of the first partition, which the one survivor names. */
    private static final OccurrenceId THE_NAMED_SEED = new OccurrenceId(1);

    /** The seed of the second partition, which no survivor names. */
    private static final OccurrenceId THE_SEED_NOBODY_NAMES = new OccurrenceId(2);

    @Test
    @Story("A partition whose seed no survivor names stops the writer")
    @DisplayName("The writer stops before anything is written, the tree's own directory included, and before it reports anything at all")
    void stopsBeforeAnyPartitionIsWrittenOrAnnounced(@TempDir Path workingDirectory) {
        List<String> events = new ArrayList<>();
        ListedSurvivor theOneSurvivor = new ListedSurvivor(
                new OccurrenceId(10),
                new OccurrencePath("reports/document-10.docx"),
                "hash-10",
                THE_NAMED_SEED,
                "seeds/First Seed.docx",
                0,
                0.5);

        Throwable thrown = catchThrowable(() -> ListedArrangement.writeTo(
                workingDirectory,
                new DeliverableProvenance(
                        RUN_ID, WALK, workingDirectory.resolveSibling("archive-that-is-not-there").toString(), List.of()),
                List.of(
                        cluster(THE_NAMED_SEED, 1, "First group"),
                        cluster(THE_SEED_NOBODY_NAMES, 2, "Second group")),
                List.of(),
                List.of(theOneSurvivor),
                SurvivorPictures.none(),
                Map.of(),
                recording(events)));

        Path tree = workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(RUN_ID);

        claim(
                "the second partition's directory is named from its seed's path, and it was handed over with"
                        + " none, so it is refused, naming the seed it is about",
                () -> assertThat(thrown)
                        .as("what writing a tree with a partition no survivor names threw")
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("seed partition 2 has no seed path; a partition directory cannot be named"
                                + " without it"));
        claim(
                "nothing was written: the tree's own directory was never made, so there is no directory for the"
                        + " first partition, which is named and comes before the refused one, no page, no"
                        + " index.md, no documents.csv and no file left half written beside either",
                () -> assertThat(tree)
                        .as("the tree's directory after the writer stopped")
                        .doesNotExist());
        claim(
                "and the working directory holds nothing else the writer could have left behind",
                () -> assertThat(workingDirectory)
                        .as("the working directory after the writer stopped")
                        .isEmptyDirectory());
        claim(
                "nothing was reported either: the partitions are asked for before the survivors are asked for"
                        + " their pictures, so no count of survivors, partitions, files or entries was announced",
                () -> assertThat(events)
                        .as("what the writer reported before it stopped")
                        .isEmpty());
    }

    /** A cluster of one document, the only cluster of the partition at {@code partitionOrder}. */
    private static RecordedCluster cluster(OccurrenceId seed, int partitionOrder, String label) {
        return new RecordedCluster(new ArrangedCluster(seed, 0, 1, partitionOrder, 1), new ClusterLabel(label));
    }

    private static DeliverableProgress recording(List<String> events) {
        return new DeliverableProgress() {
            @Override
            public void toListPictures(long survivors) {
                events.add("to-list " + survivors);
            }

            @Override
            public void picturesListed() {
                events.add("listed");
            }

            @Override
            public void toWritePartitions(long partitions) {
                events.add("to-partitions " + partitions);
            }

            @Override
            public void partitionWritten() {
                events.add("partition");
            }

            @Override
            public void toWriteClusterFiles(long clusters) {
                events.add("to-files " + clusters);
            }

            @Override
            public void clusterFileWritten() {
                events.add("file");
            }

            @Override
            public void toWriteMembershipEntries(long entries) {
                events.add("to-entries " + entries);
            }

            @Override
            public void membershipEntryWritten() {
                events.add("entry");
            }
        };
    }
}
