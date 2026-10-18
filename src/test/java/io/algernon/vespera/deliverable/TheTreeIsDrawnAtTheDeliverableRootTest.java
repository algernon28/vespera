package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.columnByPath;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tree drawn at its deliverable root: a named root is the tree, every tree carries the marker naming its working
 * directory and generation run, written before the first copy, the checks accept the tree the writer leaves, a
 * redrawing deletes nothing, and the gibibyte kept free is read on the drive the tree is written to (ADR-276 §1, §3,
 * §4).
 *
 * <p>Each test lays out {@code <base>/archive} as the corpus root, holding two survivors of one cluster, occurrence 42
 * at {@code reports/q3.pdf} and occurrence 43 at {@code notes/risers.txt}, and {@code <base>/work} as the working
 * directory; a named root is {@code <base>/handed over}, beside both.
 */
@Epic("The deliverable")
@Feature("Where the tree is drawn")
@Issue("557")
@Link(name = "ADR-276", url = Adr.THE_DELIVERABLE_IS_DRAWN_AT_A_ROOT_THE_OPERATOR_MAY_NAME, type = "adr")
class TheTreeIsDrawnAtTheDeliverableRootTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/fire-safety.docx";

    /** The one cluster both survivors are in, written over from both. */
    private static final RecordedCluster THE_CLUSTER = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    private static final String FIRST_PATH = "reports/q3.pdf";

    private static final byte[] FIRST_BYTES = "the first survivor".getBytes(StandardCharsets.UTF_8);

    /** The first survivor's copy, from the top of the tree. */
    private static final String FIRST_COPY = "documents/42-q3.pdf";

    private static final String SECOND_PATH = "notes/risers.txt";

    private static final byte[] SECOND_BYTES = "the second survivor".getBytes(StandardCharsets.UTF_8);

    private static final String SECOND_COPY = "documents/43-risers.txt";

    /** What the tree's marker is called, at the top of every tree. */
    private static final String MARKER = "vespera-deliverable.txt";

    /** A generation run other than the one {@link ListedTree#provenance} names. */
    private static final String ANOTHER_GENERATION_RUN = "d".repeat(48) + "0123456789abcdef";

    /** No seed folder is set in the profile. */
    private static final Optional<Path> NO_SEED_FOLDER = Optional.empty();

    @Test
    @Story("Every tree says which working directory and which generation drew it")
    @DisplayName("A tree drawn in the working directory carries a marker naming that working directory and its generation")
    void everyTreeCarriesAMarkerNamingItsWorkingDirectoryAndItsGenerationRun(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Path work = Files.createDirectories(base.resolve("work"));

        Path tree = drawAt(work, DeliverableRoot.UNNAMED, archive);

        claim(
                "the tree is drawn where it was before a root could be named",
                () -> assertThat(tree).isEqualTo(work.resolve("deliverable").resolve(ListedTree.RUN_ID)));
        claim(
                "and at its top is the marker: two lines, the working directory's real path and the generation run",
                () -> assertThat(Files.readString(tree.resolve(MARKER), StandardCharsets.UTF_8))
                        .isEqualTo("working-directory=" + work.toRealPath() + "\n"
                                + "generation-run=" + ListedTree.RUN_ID + "\n"));
    }

    @Test
    @Story("The tree is drawn at the folder the operator names, or in the working directory")
    @DisplayName("At a folder the operator names, the tree is drawn with its index at the top and nothing in the working directory")
    void aNamedRootIsTheTreeWithItsIndexAtTheTop(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Path work = Files.createDirectories(base.resolve("work"));
        Path root = base.resolve("handed over");

        Path tree = drawAt(work, DeliverableRoot.named(root), archive);

        claim("the tree is the root itself", () -> assertThat(tree).isEqualTo(root.toAbsolutePath().normalize()));
        claim(
                "the index and the listing are at the top of the root",
                () -> {
                    assertThat(root.resolve("index.html")).isRegularFile();
                    assertThat(root.resolve("documents.csv")).isRegularFile();
                });
        claim(
                "each copy is under the root's documents folder, holding the bytes of its file in the archive",
                () -> {
                    assertThat(root.resolve(FIRST_COPY)).hasBinaryContent(FIRST_BYTES);
                    assertThat(root.resolve(SECOND_COPY)).hasBinaryContent(SECOND_BYTES);
                });
        claim(
                "the marker at the root names the working directory and the generation that drew it",
                () -> assertThat(Files.readString(root.resolve(MARKER), StandardCharsets.UTF_8))
                        .isEqualTo("working-directory=" + work.toRealPath() + "\n"
                                + "generation-run=" + ListedTree.RUN_ID + "\n"));
        claim(
                "and nothing was drawn under the working directory's deliverable folder",
                () -> assertThat(work.resolve("deliverable")).doesNotExist());
    }

    @Test
    @Story("Every tree says which working directory and which generation drew it")
    @DisplayName("The marker is in place before the first document is copied, so a drawing stopped partway is resumed")
    void theMarkerIsInPlaceBeforeTheFirstCopy(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Path work = Files.createDirectories(base.resolve("work"));
        Path root = base.resolve("handed over");
        List<Boolean> markerStood = new ArrayList<>();
        List<Boolean> aCopyStood = new ArrayList<>();
        DeliverableProgress whenTheCopiesAreAnnounced = new DeliverableProgress() {
            @Override
            public void toCopySurvivors(long survivors, long bytes, long usableBytes) {
                markerStood.add(Files.isRegularFile(root.resolve(MARKER)));
                aCopyStood.add(Files.exists(root.resolve(FIRST_COPY)) || Files.exists(root.resolve(SECOND_COPY)));
            }
        };

        Deliverable.writeTo(
                work,
                DeliverableRoot.named(root),
                ListedTree.provenance(archive),
                ListedTree.of(List.of(THE_CLUSTER), writtenFromBoth(), bothSurvivors(), Map.of()),
                SurvivorPictures.none(),
                whenTheCopiesAreAnnounced);

        claim(
                "when the writer announced the copies it was about to make, once, the marker already stood at the root",
                () -> assertThat(markerStood).containsExactly(true));
        claim(
                "and no copy did yet",
                () -> assertThat(aCopyStood).containsExactly(false));
    }

    @Test
    @Story("A folder that holds anything but this tree is refused")
    @DisplayName("The tree the writer leaves at a folder is accepted for its working directory and generation, and for no other")
    void theTreeTheWriterLeavesIsTheOneTheChecksAccept(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Path work = Files.createDirectories(base.resolve("work"));
        Path elsewhere = Files.createDirectories(base.resolve("elsewhere"));
        Path root = base.resolve("handed over");
        DeliverableRoot named = DeliverableRoot.named(root);

        drawAt(work, named, archive);

        claim(
                "before the job, the root is accepted for the working directory that drew it",
                () -> assertThat(named.refusalAtTheStart(work, archive, NO_SEED_FOLDER)).isEmpty());
        claim(
                "at the drawing, it is accepted for that working directory and that generation, to be drawn again in"
                        + " place",
                () -> assertThat(named.refusalAtTheDrawing(work, ListedTree.RUN_ID)).isEmpty());
        claim(
                "for another generation of the same working directory it is refused",
                () -> assertThat(named.refusalAtTheDrawing(work, ANOTHER_GENERATION_RUN))
                        .contains("the deliverable root " + root.toRealPath() + " holds the tree of another generation"
                                + " run, so no tree was drawn. Name an empty folder in vespera.deliverable-root, and"
                                + " run again."));
        claim(
                "and for another working directory it is refused, at the drawing and before the job",
                () -> {
                    assertThat(named.refusalAtTheDrawing(elsewhere, ListedTree.RUN_ID))
                            .contains("the deliverable root " + root.toRealPath() + " is not empty and holds no tree of"
                                    + " this working directory, so no tree was drawn. Name an empty folder in"
                                    + " vespera.deliverable-root, and run again.");
                    assertThat(named.refusalAtTheStart(elsewhere, archive, NO_SEED_FOLDER))
                            .contains("vespera run refused the deliverable root " + root.toRealPath() + ": it is not"
                                    + " empty and holds no tree of this working directory. Name an empty folder in"
                                    + " vespera.deliverable-root.");
                });
    }

    @Test
    @Story("Nothing beneath the folder is deleted")
    @DisplayName("A tree drawn again at its folder is put back in place, and every other file beneath the folder stays")
    void aTreeOfTheSameRunIsRedrawnInPlaceAndNothingBeneathItIsDeleted(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Path work = Files.createDirectories(base.resolve("work"));
        Path root = base.resolve("handed over");
        drawAt(work, DeliverableRoot.named(root), archive);
        Path anEarlierLayoutsCopy = root.resolve("documents").resolve("7-a-copy-an-earlier-layout-left.pdf");
        Files.writeString(anEarlierLayoutsCopy, "a copy no survivor of this tree is named by", StandardCharsets.UTF_8);
        Path theOperatorsNotes = root.resolve("notes.txt");
        Files.writeString(theOperatorsNotes, "the operator's notes", StandardCharsets.UTF_8);
        Files.delete(root.resolve("index.html"));

        drawAt(work, DeliverableRoot.named(root), archive);

        claim("the index is drawn again at the top of the root", () -> assertThat(root.resolve("index.html")).isRegularFile());
        claim(
                "both copies stand, listed as copied",
                () -> assertThat(columnByPath(root, "copy_outcome"))
                        .containsExactly(Map.entry(FIRST_PATH, "copied"), Map.entry(SECOND_PATH, "copied")));
        claim(
                "a copy no survivor of this tree is named by, as an earlier layout would leave, is still there as it was",
                () -> assertThat(anEarlierLayoutsCopy).hasContent("a copy no survivor of this tree is named by"));
        claim(
                "and so is a file the operator put in the root",
                () -> assertThat(theOperatorsNotes).hasContent("the operator's notes"));
    }

    /**
     * The shipped reading of the drive's free space, through the form of the writer that reads it itself: the working
     * directory lies beneath a file, so its drive's free space cannot be read, and the root lies beside it.
     */
    @Test
    @Story("Copying never fills the drive the tree is drawn on")
    @DisplayName("The free space a copy must leave is read on the drive the tree is drawn on, not the working directory's")
    void theGibibyteIsReadOnTheDriveTheTreeIsWrittenTo(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Path aFile = Files.writeString(base.resolve("a-file"), "not a folder", StandardCharsets.UTF_8);
        Path workBeneathAFile = aFile.resolve("work");
        Path root = base.resolve("handed over");

        claim(
                "the precondition: the working directory's free space is read as none, and the drive the root lies on"
                        + " as having room for both documents and the gibibyte a copy must leave",
                () -> {
                    assertThat(Deliverable.usableBytesAt(workBeneathAFile)).isZero();
                    assertThat(Deliverable.usableBytesAt(base))
                            .isGreaterThan(Deliverable.COPY_HEADROOM_BYTES + FIRST_BYTES.length + SECOND_BYTES.length);
                });

        Path tree = Deliverable.writeTo(
                workBeneathAFile,
                DeliverableRoot.named(root),
                ListedTree.provenance(archive),
                ListedTree.of(List.of(THE_CLUSTER), writtenFromBoth(), bothSurvivors(), Map.of()),
                SurvivorPictures.none(),
                DeliverableProgress.NONE,
                ReplacingMove.ATOMIC);

        claim(
                "both documents are copied: the room was read on the root's drive, where reading the working"
                        + " directory's would have left every copy unmade for want of room",
                () -> assertThat(columnByPath(tree, "copy_outcome"))
                        .containsExactly(Map.entry(FIRST_PATH, "copied"), Map.entry(SECOND_PATH, "copied")));
    }

    /** The two survivors' files beneath {@code <base>/archive}, which is returned. */
    private static Path anArchive(Path base) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(archive.resolve("reports"));
        Files.createDirectories(archive.resolve("notes"));
        Files.write(archive.resolve(FIRST_PATH), FIRST_BYTES);
        Files.write(archive.resolve(SECOND_PATH), SECOND_BYTES);
        return archive;
    }

    /** The tree drawn at {@code root} for {@code work}, over {@code archive}, through the shipped public form. */
    private static Path drawAt(Path work, DeliverableRoot root, Path archive) {
        return Deliverable.writeTo(
                work,
                root,
                ListedTree.provenance(archive),
                ListedTree.of(List.of(THE_CLUSTER), writtenFromBoth(), bothSurvivors(), Map.of()),
                SurvivorPictures.none(),
                DeliverableProgress.NONE);
    }

    private static Map<ClusterSlot, SynthesisDoc> writtenFromBoth() {
        return Map.of(slotOf(THE_CLUSTER), writing("Fire doors", "Both [1] and [2].", 42, 43));
    }

    private static List<ListedSurvivor> bothSurvivors() {
        return List.of(
                survivor(42, FIRST_PATH, sha256Of(FIRST_BYTES), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(43, SECOND_PATH, sha256Of(SECOND_BYTES), THE_SEED, SEED_PATH, 0, 0.8));
    }
}
