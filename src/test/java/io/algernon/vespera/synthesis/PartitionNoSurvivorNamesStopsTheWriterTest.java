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
 * Where {@code Deliverable.writeTo} stops for a partition whose seed no survivor names (ADR-213 §6): before
 * any partition directory, page or total of partitions, cluster files or membership entries is written,
 * because the index is composed first and the index is what refuses such a partition.
 *
 * <p><b>The one order the split moved.</b> At {@code 4b99a03} the same exception was thrown from inside
 * the loop over partitions, after the partitions, the cluster files and the membership entries had been
 * announced and after every earlier partition's directory and pages were written. The partition nobody
 * names is the second of two here for that reason: against that commit the first one's directory is on
 * disk when the writer stops.
 *
 * <p><b>What did not move</b> is held too: the tree's own directory is made and the survivors are asked
 * for their pictures before the writer stops, and the exception's type and message are the ones {@code
 * IndexPageTest} holds for {@code IndexPage.contents} alone.
 *
 * <p>Pure: the tree goes into a temporary directory and no database is used.
 */
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
class PartitionNoSurvivorNamesStopsTheWriterTest {

    private static final String RUN_ID = "d".repeat(48) + "0123456789abcdef";
    private static final long WALK = 7L;

    /** The seed of the first partition, which the one survivor names. */
    private static final OccurrenceId THE_NAMED_SEED = new OccurrenceId(1);

    /** The seed of the second partition, which no survivor names. */
    private static final OccurrenceId THE_SEED_NOBODY_NAMES = new OccurrenceId(2);

    @Test
    @Story("A partition whose seed no survivor names stops the writer")
    @DisplayName("The writer stops before any partition's directory, any page or the index is written, and before it announces how many partitions, cluster files or membership entries it will write")
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

        Throwable thrown = catchThrowable(() -> Deliverable.writeTo(
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
                "the second partition's directory is named from its seed's path, which only a survivor carries,"
                        + " so the writer refuses it, naming the seed it is about, in the words it always used",
                () -> assertThat(thrown)
                        .as("what writing a tree with a partition no survivor names threw")
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("no survivor names the seed of partition 2; a partition directory cannot be"
                                + " named without it"));
        claim(
                "the tree's own directory was made and holds nothing: no directory for the first partition,"
                        + " which is named and comes before the refused one, no page, and no index.md",
                () -> assertThat(tree)
                        .as("the tree's directory after the writer stopped")
                        .isEmptyDirectory());
        claim(
                "the one survivor was asked for its pictures, which happens before anything is laid out, and"
                        + " then nothing more was reported: no count of partitions, files or entries was announced",
                () -> assertThat(events)
                        .as("what the writer reported before it stopped")
                        .containsExactly("to-list 1", "listed"));
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
