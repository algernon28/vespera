package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.ListedDoc;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The contract of {@code DeliverableProgress} (ADR-192 sections 3 and 5, #412): the loops of {@code
 * Deliverable.writeTo} that read or write something, each announced once with its total before its first
 * item, in the order the tree is written, and each item reported after it. The survivors asked for their
 * pictures come first (the furniture pass); then, before the loop over partitions, the partitions, the cluster
 * files and the membership entries are announced, the last two summed across the partitions; then each
 * membership entry with a document is reported after its pictures, each cluster file after its entries, and
 * each partition after its files. An entry for a document the call carried that the cluster no longer holds
 * reads no picture and is not counted. Since ADR-274 §2.5 the copies about to be made are announced once the
 * pictures are listed, and a partition's copies are made, each reported by its outcome, before its pages.
 *
 * <p>The furniture rules compute over pictures already in memory and are not counted (ADR-192 section 7).
 *
 * <p>Pure: the tree is written into a temporary directory, over an archive that is not there, and no database is
 * used.
 */
@Epic("The deliverable")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class DeliverableProgressTest {

    private static final String RUN_ID = "c".repeat(48) + "0123456789abcdef";
    private static final long WALK = 7L;
    private static final String PNG = "image/png";

    private static final OccurrenceId FIRST_SEED = new OccurrenceId(1);
    private static final OccurrenceId SECOND_SEED = new OccurrenceId(2);
    private static final String FIRST_SEED_PATH = "seeds/First Seed.docx";
    private static final String SECOND_SEED_PATH = "seeds/Second Seed.docx";

    @Test
    @Story("The tree tells its caller how many survivors, partitions, files and entries it will go through")
    @DisplayName("Two partitions, three cluster files and four entries are each announced once, summed, and each reported")
    void announcesEachLoopOnceAndSumsTheFilesAndEntries(@TempDir Path workingDirectory) {
        List<String> events = new ArrayList<>();

        ListedArrangement.writeTo(
                workingDirectory,
                provenance(workingDirectory),
                List.of(
                        cluster(FIRST_SEED, 0, 1, 1, 1, "First group"),
                        cluster(FIRST_SEED, 1, 2, 1, 2, "Second group"),
                        cluster(SECOND_SEED, 0, 1, 2, 1, "Third group")),
                List.of(),
                List.of(
                        member(10, FIRST_SEED, FIRST_SEED_PATH, 0),
                        member(11, FIRST_SEED, FIRST_SEED_PATH, 1),
                        member(12, FIRST_SEED, FIRST_SEED_PATH, 1),
                        member(20, SECOND_SEED, SECOND_SEED_PATH, 0)),
                survivor -> survivor.occurrence().value() == 10
                        ? List.of(new ListedPicture(PNG, "a diagram".getBytes(StandardCharsets.UTF_8), false, ""))
                        : List.of(),
                Map.of(),
                recording(events));

        claim(
                "the four survivors are asked for their pictures once each, before anything else is written",
                () -> assertThat(events.subList(0, 5)).containsExactly("to-list 4", "listed", "listed", "listed", "listed"));
        claim(
                "then the copies about to be made are announced once: all four, none standing, whose originals'"
                        + " sizes could not be read and so count as no bytes",
                () -> assertThat(events.subList(5, 6)).containsExactly("to-copy 4 of 0 bytes"));
        claim(
                "then the partitions, the cluster files and the entries are each announced once, the files and the"
                        + " entries as their sums across the two partitions, three and four",
                () -> assertThat(events.subList(6, 9)).containsExactly("to-partitions 2", "to-files 3", "to-entries 4"));
        claim(
                "and within each partition its copies come before its pages, each gone since the archive is not"
                        + " there, each entry is reported before its file, and each file before its partition",
                () -> assertThat(events.subList(9, events.size()))
                        .containsExactly(
                                "gone", "gone", "gone", "entry", "file", "entry", "entry", "file", "partition",
                                "gone", "entry", "file", "partition"));
    }

    @Test
    @Story("The tree tells its caller how many survivors, partitions, files and entries it will go through")
    @DisplayName("An entry for a document the group no longer holds is neither in the total nor reported")
    void anEntryWithNoDocumentIsNotCounted(@TempDir Path workingDirectory) {
        List<String> events = new ArrayList<>();
        OccurrenceId noLongerHeld = new OccurrenceId(99);

        ListedArrangement.writeTo(
                workingDirectory,
                provenance(workingDirectory),
                List.of(cluster(FIRST_SEED, 0, 1, 1, 1, "First group")),
                List.of(new ListedDoc(
                        FIRST_SEED, 0, new SynthesisDoc("A title", "It says [1] and [2].", List.of(noLongerHeld, new OccurrenceId(10))))),
                List.of(member(10, FIRST_SEED, FIRST_SEED_PATH, 0)),
                SurvivorPictures.none(),
                Map.of(),
                recording(events));

        claim(
                "the page numbers two entries, one of them for the document the group no longer holds; only the"
                        + " entry with a document is announced and reported",
                () -> assertThat(events)
                        .containsExactly(
                                "to-list 1", "listed", "to-copy 1 of 0 bytes", "to-partitions 1", "to-files 1",
                                "to-entries 1", "gone", "entry", "file", "partition"));
    }

    private static RecordedCluster cluster(
            OccurrenceId seed, int ordinal, int documentCount, int partitionOrder, int clusterOrder, String label) {
        return new RecordedCluster(
                new ArrangedCluster(seed, ordinal, documentCount, partitionOrder, clusterOrder), new ClusterLabel(label));
    }

    private static ListedSurvivor member(long occurrence, OccurrenceId seed, String seedPath, int ordinal) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath("reports/document-" + occurrence + ".docx"),
                "hash-" + occurrence,
                seed,
                seedPath,
                ordinal,
                0.5);
    }

    private static DeliverableProvenance provenance(Path workingDirectory) {
        return new DeliverableProvenance(
                RUN_ID, WALK, workingDirectory.resolveSibling("archive-that-is-not-there").toString(), List.of());
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
            public void toCopySurvivors(long survivors, long bytes, long usableBytes) {
                events.add("to-copy " + survivors + " of " + bytes + " bytes");
            }

            @Override
            public void survivorCopied(long bytes) {
                events.add("copied");
            }

            @Override
            public void survivorAlreadyCopied() {
                events.add("already copied");
            }

            @Override
            public void survivorChanged() {
                events.add("changed");
            }

            @Override
            public void survivorGone() {
                events.add("gone");
            }

            @Override
            public void survivorWithNoRoom() {
                events.add("no room");
            }

            @Override
            public void survivorNotCopied() {
                events.add("not copied");
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
