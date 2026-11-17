package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.ClusterLabel;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.TopicKey;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where the tree's writer stops for a topic that arrives with no name (ADR-213 §6, ADR-223 §6, ADR-409 section 10):
 * before the tree's directory, any topic's folder, any page or any total is written or announced.
 *
 * <p><b>What refuses it moved, and the stop moved earlier with it.</b> Until ADR-223 the index was composed
 * whole and first, and the index refused such a top level, after the tree's directory had been made and the
 * survivors asked for their pictures. Since ADR-223 the writer is handed its topics one row each, a topic's
 * name arrives with it, since its folder is named from it (ADR-409 section 10), and {@link ListedTopic} refuses one
 * that has none. The writer asks for its topics before it does anything else, so nothing at all is on disk and
 * nothing was reported when it stops. The fixture names a topic as the first listed survivor under it names it,
 * so a topic no survivor is listed under arrives with none.
 *
 * <p>The topic nobody names is the second of two here, as it was: a writer that laid out the first
 * before asking about the second would leave the first one's directory behind.
 *
 * <p>Pure: the tree goes into a temporary directory and no database is used.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("351")
@Issue("472")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class PartitionNoSurvivorNamesStopsTheWriterTest {

    private static final String RUN_ID = "d".repeat(48) + "0123456789abcdef";
    private static final long WALK = 7L;

    /** The first topic, which the one survivor is listed under and names. */
    private static final TopicKey THE_NAMED_TOPIC = new TopicKey("seeds/First Seed.docx", 0);

    /** The second topic, which no survivor is listed under, so it arrives with no name. */
    private static final TopicKey THE_TOPIC_NOBODY_NAMES = new TopicKey("seeds/Second Seed.docx", 4);

    @Test
    @Story("A topic that arrives with no name stops the writer")
    @DisplayName("The writer stops before anything is written, the tree's own directory included, and before it reports anything at all")
    void stopsBeforeAnyPartitionIsWrittenOrAnnounced(@TempDir Path workingDirectory) {
        List<String> events = new ArrayList<>();
        ListedSurvivor theOneSurvivor = new ListedSurvivor(
                new OccurrenceId(10),
                new OccurrencePath("reports/document-10.docx"),
                "hash-10",
                new OccurrenceId(1),
                THE_NAMED_TOPIC,
                "First Seed",
                OptionalInt.empty(),
                0,
                0.5);

        Throwable thrown = catchThrowable(() -> ListedArrangement.writeTo(
                workingDirectory,
                new DeliverableProvenance(
                        RUN_ID, WALK, workingDirectory.resolveSibling("archive-that-is-not-there").toString(), List.of()),
                List.of(
                        cluster(THE_NAMED_TOPIC, 1, "First group"),
                        cluster(THE_TOPIC_NOBODY_NAMES, 2, "Second group")),
                List.of(),
                List.of(theOneSurvivor),
                SurvivorPictures.none(),
                Map.of(),
                recording(events)));

        Path tree = workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(RUN_ID);

        claim(
                "the second topic's folder is named from its name, and it was handed over with none, so it is"
                        + " refused, naming the topic it is about by its first passage",
                () -> assertThat(thrown)
                        .as("what writing a tree with a topic that has no name threw")
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("topic seeds/Second Seed.docx chunk 4 has no name; a topic's folder cannot be"
                                + " named without it"));
        claim(
                "nothing was written: the tree's own directory was never made, so there is no directory for the"
                        + " first topic, which is named and comes before the refused one, no page, no"
                        + " index.html, no documents.csv, no copy and no file left half written beside either",
                () -> assertThat(tree)
                        .as("the tree's directory after the writer stopped")
                        .doesNotExist());
        claim(
                "and the working directory holds nothing else the writer could have left behind",
                () -> assertThat(workingDirectory)
                        .as("the working directory after the writer stopped")
                        .isEmptyDirectory());
        claim(
                "nothing was reported either: the topics are asked for before the survivors are asked for"
                        + " their pictures, so no count of survivors, topics, files or entries was announced",
                () -> assertThat(events)
                        .as("what the writer reported before it stopped")
                        .isEmpty());
    }

    /** A cluster of one document, the only cluster of {@code topic}, undivided, placed at {@code topicOrder}. */
    private static RecordedCluster cluster(TopicKey topic, int topicOrder, String label) {
        return new RecordedCluster(
                new ArrangedCluster(topic, OptionalInt.empty(), 0, 1, topicOrder, 0, 1), new ClusterLabel(label));
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
            public void toDrawClusters(long clusters) {
                events.add("to-files " + clusters);
            }

            @Override
            public void clusterDrawn() {
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
