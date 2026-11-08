package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.columnByPath;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A cluster's folder in the tree (ADR-280): it holds the cluster's page, {@code index.html}, and the copy of each of
 * the cluster's survivors under its original's own name, the page's pictures inlined in it (ADR-286), and {@code
 * documents/} is no longer written. A copy at its name is trusted unread, one whose name is too long in bytes is not copied, the
 * listing names it in its folder, an entry links it by its name percent-encoded, the survey counts the copies past Windows' limit, and the writer reports what an earlier
 * drawing left at the top of the tree and in each partition folder.
 *
 * <p>Each test lays out {@code <base>/archive} as the corpus root and {@code <base>/work} as the working directory,
 * the tree drawn at the working directory's {@code deliverable/} folder. How each name is worked out is {@code
 * CopyNamesTest}'s.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("570")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class TheClusterFolderHoldsItsPageAndItsCopiesTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The cluster most fixtures here arrange, written over from both its survivors. */
    private static final RecordedCluster THE_CLUSTER = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    /** That cluster's folder, from the tree's root: the partition's place and slug, then the cluster's. */
    private static final String THE_FOLDER = "1-fire-safety/1-fire-doors";

    /** A second cluster of the same partition, for the listing's paths. */
    private static final RecordedCluster THE_OTHER_CLUSTER = cluster(THE_SEED, 1, "Smoke dampers", 2, 1);

    /** And its folder. */
    private static final String THE_OTHER_FOLDER = "1-fire-safety/2-smoke-dampers";

    /** The most UTF-8 bytes one name in a folder may hold on ext4, XFS, Btrfs and APFS. */
    private static final int A_NAMES_LONGEST_IN_BYTES = 255;

    /** What a copy is written under, after its name, before it is moved into place. */
    private static final String THE_PART = ".part";

    @Test
    @Story("A group's folder holds its page and its documents")
    @DisplayName("Each row of the listing names its document's copy in its group's folder, and two groups may each hold a document of one name")
    void eachRowOfTheListingNamesItsCopyInItsClustersFolder(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of(
                "a/minutes.docx", "the first minutes",
                "b/minutes.docx", "the second minutes",
                "c/MINUTES.DOCX", "the third minutes"));
        RecordedCluster first = cluster(THE_SEED, 0, "Fire doors", 1, 2);

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(first, THE_OTHER_CLUSTER),
                Map.of(),
                List.of(
                        aSurvivor(11, "a/minutes.docx", "the first minutes", 0),
                        aSurvivor(12, "b/minutes.docx", "the second minutes", 1),
                        aSurvivor(13, "c/MINUTES.DOCX", "the third minutes", 0)),
                Map.of());

        claim(
                "each row's copy is its group's folder and the copy's name: the two groups each keep a"
                        + " minutes.docx, names meeting only within one folder, and the third, meeting the first in"
                        + " its folder without regard to case, takes its number before its extension",
                () -> assertThat(columnByPath(tree, "copy"))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                "a/minutes.docx", THE_FOLDER + "/minutes.docx",
                                "b/minutes.docx", THE_OTHER_FOLDER + "/minutes.docx",
                                "c/MINUTES.DOCX", THE_FOLDER + "/MINUTES~13.DOCX")));
        claim(
                "and each names a copy that stands, holding its own document's bytes",
                () -> {
                    assertThat(tree.resolve(THE_FOLDER).resolve("minutes.docx")).hasBinaryContent(bytes("the first minutes"));
                    assertThat(tree.resolve(THE_OTHER_FOLDER).resolve("minutes.docx"))
                            .hasBinaryContent(bytes("the second minutes"));
                    assertThat(tree.resolve(THE_FOLDER).resolve("MINUTES~13.DOCX"))
                            .hasBinaryContent(bytes("the third minutes"));
                });
    }

    @Test
    @Story("Every file of the tree has a name of bounded length")
    @DisplayName("A copy whose name, with its unfinished copy's ending, would pass what a file system allows is not copied, and nothing is written for it")
    void aNameThatWithItsPartPassesTwoHundredAndFiftyFiveBytesIsNotCopied(@TempDir Path base) throws IOException {
        String oneByteTooLong = "a".repeat(A_NAMES_LONGEST_IN_BYTES - THE_PART.length() - ".pdf".length() + 1) + ".pdf";
        String justShortEnough = "b".repeat(A_NAMES_LONGEST_IN_BYTES - THE_PART.length() - ".pdf".length()) + ".pdf";
        try {
            Files.delete(Files.createFile(base.resolve(justShortEnough + THE_PART)));
        } catch (IOException | RuntimeException refused) {
            abort("the temporary folder's file system does not hold a name of " + A_NAMES_LONGEST_IN_BYTES + " bytes");
        }
        Path archive = anArchive(base, Map.of("kept/b.pdf", "the one that fits"));
        Path tree = base.resolve("work").resolve("deliverable");
        Path folder = tree.resolve(THE_FOLDER);
        ListedSurvivor theTooLongOne = aSurvivor(42, "gone/a.pdf", "never read", 0);
        ListedSurvivor theOneThatFits = aSurvivor(43, "kept/b.pdf", "the one that fits", 0);
        List<String> told = new ArrayList<>();
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void survivorCopied(long bytes) {
                told.add("copied");
            }

            @Override
            public void survivorAlreadyCopied() {
                told.add("already copied");
            }

            @Override
            public void survivorChanged() {
                told.add("changed");
            }

            @Override
            public void survivorGone() {
                told.add("gone");
            }

            @Override
            public void survivorWithNoRoom() {
                told.add("no room");
            }

            @Override
            public void survivorNotCopied() {
                told.add("not copied");
            }
        };
        Copies copies = new Copies(tree, archive.toString(), ReplacingMove.ATOMIC, () -> Long.MAX_VALUE, hearing);

        copies.copyAllAs(
                folder,
                List.of(theTooLongOne, theOneThatFits),
                Map.of(theTooLongOne.occurrence(), oneByteTooLong, theOneThatFits.occurrence(), justShortEnough));

        assumeTrue(
                (oneByteTooLong + THE_PART).getBytes(StandardCharsets.UTF_8).length == A_NAMES_LONGEST_IN_BYTES + 1
                        && (justShortEnough + THE_PART).getBytes(StandardCharsets.UTF_8).length
                                == A_NAMES_LONGEST_IN_BYTES,
                "one name holds one byte past the longest with .part after it, and the other exactly the longest");
        claim(
                "the longest name a copy may have, .part included, is " + A_NAMES_LONGEST_IN_BYTES + " bytes",
                () -> assertThat(CopyNames.LONGEST_NAME_IN_BYTES).isEqualTo(A_NAMES_LONGEST_IN_BYTES));
        claim(
                "the copy whose name is one byte too long is not copied, and is not called gone though its"
                        + " original is: its name is refused before its original is looked for, whatever the disk"
                        + " beneath would take",
                () -> assertThat(copies.outcomeOf(theTooLongOne)).isEqualTo(CopyOutcome.NOT_COPIED));
        claim(
                "and the one beside it, whose name fits exactly, is copied with its bytes, and is the only file"
                        + " in the folder: nothing, not even an unfinished copy, was written for the other",
                () -> {
                    assertThat(copies.outcomeOf(theOneThatFits)).isEqualTo(CopyOutcome.COPIED);
                    assertThat(folder.resolve(justShortEnough)).hasBinaryContent(bytes("the one that fits"));
                    assertThat(filesUnder(folder)).containsExactly(justShortEnough);
                });
        claim(
                "each outcome is told once: one copy made and one not copied, and no copy gone",
                () -> assertThat(told).containsExactlyInAnyOrder("not copied", "copied"));
    }

    @Test
    @Story("Nothing beneath the folder is deleted")
    @DisplayName("The tree reports the documents folder and counts the other folders an earlier drawing left, and deletes none of them")
    void theWriterReportsTheDocumentsFolderAndCountsTheOtherFoldersAnEarlierDrawingLeft(@TempDir Path base)
            throws IOException {
        Path archive = anArchive(base, Map.of("reports/q3.pdf", "the first survivor", "notes/risers.txt", "the second"));
        List<String> reported = new ArrayList<>();
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void leftBehind(boolean documentsFolder, long otherFolders) {
                reported.add("documents folder " + documentsFolder + ", other folders " + otherFolders);
            }
        };
        Path tree = drawWith(base, archive, hearing);
        List<String> afterTheFirst = List.copyOf(reported);
        reported.clear();
        Path anEarlierLayoutsCopy = Files.createDirectories(tree.resolve("documents")).resolve("42-q3.pdf");
        Files.writeString(anEarlierLayoutsCopy, "an earlier layout's copy", StandardCharsets.UTF_8);
        Path anEarlierPartition = Files.createDirectories(tree.resolve("2-an-earlier-partition"));
        Path anEarlierCluster = Files.createDirectories(tree.resolve("1-fire-safety").resolve("9-an-earlier-group"));
        Path anEarlierPage = Files.writeString(
                tree.resolve("1-fire-safety").resolve("1-fire-doors.html"), "an earlier layout's page", StandardCharsets.UTF_8);
        Path aFolderInsideAWrittenOne = Files.createDirectories(tree.resolve(THE_FOLDER).resolve("stray"));
        Path aFileAtTheTop = Files.writeString(tree.resolve("notes.txt"), "the operator's notes", StandardCharsets.UTF_8);

        drawWith(base, archive, hearing);

        claim(
                "the first drawing, over an empty tree, reports once that nothing was left behind",
                () -> assertThat(afterTheFirst).containsExactly("documents folder false, other folders 0"));
        claim(
                "the second reports once the documents folder, and counts 2 other folders: the partition folder"
                        + " at the top and the group's folder in the partition, which this drawing did not write;"
                        + " a file is not counted, and a folder inside a group's folder this drawing wrote is not"
                        + " looked at",
                () -> assertThat(reported).containsExactly("documents folder true, other folders 2"));
        claim(
                "and every one of them is where it was: nothing beneath the tree is deleted",
                () -> {
                    assertThat(anEarlierLayoutsCopy).hasContent("an earlier layout's copy");
                    assertThat(anEarlierPartition).isDirectory();
                    assertThat(anEarlierCluster).isDirectory();
                    assertThat(anEarlierPage).hasContent("an earlier layout's page");
                    assertThat(aFolderInsideAWrittenOne).isDirectory();
                    assertThat(aFileAtTheTop).hasContent("the operator's notes");
                });
    }

    /** The two-survivor tree of {@link #THE_CLUSTER} drawn over {@code archive}, {@code progress} hearing it. */
    private static Path drawWith(Path base, Path archive, DeliverableProgress progress) {
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(
                        List.of(THE_CLUSTER),
                        writtenFrom(THE_CLUSTER, 42, 43),
                        List.of(
                                aSurvivor(42, "reports/q3.pdf", "the first survivor", 0),
                                aSurvivor(43, "notes/risers.txt", "the second", 0)),
                        Map.of()),
                SurvivorPictures.none(),
                progress);
    }

    /** {@code <base>/archive}, holding each path's text as its bytes, which is returned. */
    private static Path anArchive(Path base, Map<String, String> files) throws IOException {
        Path archive = base.resolve("archive");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path original = archive.resolve(file.getKey());
            Files.createDirectories(original.getParent());
            Files.write(original, bytes(file.getValue()));
        }
        return archive;
    }

    /** A survivor of the cluster of {@code ordinal}, its recorded key that of {@code text}'s bytes. */
    private static ListedSurvivor aSurvivor(long occurrence, String path, String text, int ordinal) {
        return survivor(occurrence, path, sha256Of(bytes(text)), THE_SEED, SEED_PATH, ordinal, 1.0 - occurrence / 1000.0);
    }

    /** The writing over {@code recorded}, its call having sent {@code sent} in citation order. */
    private static Map<ClusterSlot, SynthesisDoc> writtenFrom(RecordedCluster recorded, long... sent) {
        return Map.of(slotOf(recorded), writing(recorded.label().value(), "Both [1] and [2].", sent));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
