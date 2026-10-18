package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestLinks;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where the tree is drawn, and which roots are refused before the job and at the drawing (ADR-276 §1 to §3).
 *
 * <p>Pure: no database and no job. Each test lays out {@code <base>/archive} as the corpus root, {@code
 * <base>/library/seeds} as the seed folder and {@code <base>/work} as the working directory, all three made. A
 * path the record compares is its real path, and for a path not made yet the real path of the nearest folder above
 * it with the names below appended, so each expected line is built from the real path of a folder the test made.
 */
@Epic("The deliverable")
@Feature("Where the tree is drawn")
@Issue("557")
@Link(name = "ADR-276", url = Adr.THE_DELIVERABLE_IS_DRAWN_AT_A_ROOT_THE_OPERATOR_MAY_NAME, type = "adr")
class DeliverableRootTest {

    /** A generation run's id, of the shape the ledger mints. */
    private static final String A_GENERATION_RUN = "c".repeat(48) + "0123456789abcdef";

    /** Another generation run's id, differing from the first in every character but its last sixteen. */
    private static final String ANOTHER_GENERATION_RUN = "d".repeat(48) + "0123456789abcdef";

    /** What the tree's marker is called, at the top of every tree. */
    private static final String MARKER = "vespera-deliverable.txt";

    /** No seed folder is set in the profile. */
    private static final Optional<Path> NO_SEED_FOLDER = Optional.empty();

    /** What a refusal at the start ends with, where the root lies over the corpus root or the seed folder. */
    private static final String NAME_A_FOLDER_OUTSIDE =
            ". Name a folder outside the corpus root and the seed folder in vespera.deliverable-root.";

    @TempDir
    Path base;

    private Path archive;

    private Path library;

    private Path seeds;

    private Path work;

    @BeforeEach
    void layOutTheCorpusTheSeedsAndTheWorkingDirectory() throws IOException {
        archive = Files.createDirectories(base.resolve("archive"));
        library = Files.createDirectories(base.resolve("library"));
        seeds = Files.createDirectories(library.resolve("seeds"));
        work = Files.createDirectories(base.resolve("work"));
    }

    @Test
    @Story("The tree is drawn at the folder the operator names, or in the working directory")
    @DisplayName("With no folder named, the tree is the generation's own folder under the working directory's deliverable folder")
    void anUnnamedRootIsTheGenerationRunsFolderInTheWorkingDirectory() {
        Path theDefault = work.resolve("deliverable").resolve(A_GENERATION_RUN);

        claim(
                "with no root named, the tree is <working directory>/deliverable/<generation run>, as it was before a root"
                        + " could be named",
                () -> assertThat(DeliverableRoot.UNNAMED.treeOf(work, A_GENERATION_RUN)).isEqualTo(theDefault));
        claim(
                "and a property left empty, blank or absent names no root",
                () -> {
                    assertThat(DeliverableRoot.of("").treeOf(work, A_GENERATION_RUN)).isEqualTo(theDefault);
                    assertThat(DeliverableRoot.of("   ").treeOf(work, A_GENERATION_RUN)).isEqualTo(theDefault);
                    assertThat(DeliverableRoot.of(null).treeOf(work, A_GENERATION_RUN)).isEqualTo(theDefault);
                });
    }

    @Test
    @Story("The tree is drawn at the folder the operator names, or in the working directory")
    @DisplayName("A folder the operator names is the tree itself, whatever generation drew it")
    void aNamedRootIsTheTreeItself() {
        Path root = base.resolve("handed over");

        claim(
                "a named root is the tree, absolute, with no folder of the working directory's or of the run's added",
                () -> assertThat(DeliverableRoot.named(root).treeOf(work, A_GENERATION_RUN))
                        .isEqualTo(root.toAbsolutePath().normalize()));
        claim(
                "the property's value is read with the spaces around it stripped",
                () -> assertThat(DeliverableRoot.of("  " + root + "  ").treeOf(work, A_GENERATION_RUN))
                        .isEqualTo(root.toAbsolutePath().normalize()));
        claim(
                "and another generation's tree is drawn at the same root, since the root names no run",
                () -> assertThat(DeliverableRoot.named(root).treeOf(work, ANOTHER_GENERATION_RUN))
                        .isEqualTo(root.toAbsolutePath().normalize()));
    }

    @Test
    @Story("A folder over the archive is refused before anything is walked")
    @DisplayName("A folder that is the corpus root, inside it or above it is refused, each in one line")
    void refusesARootThatIsTheCorpusRootInsideItOrAboveIt() throws IOException {
        Path archiveReal = archive.toRealPath();
        Path inside = archive.resolve("tree");

        claim(
                "a root inside the corpus root, not made yet, is refused by its place, compared where it would be made",
                () -> assertThat(DeliverableRoot.named(inside).refusalAtTheStart(work, archive, NO_SEED_FOLDER))
                        .contains(overlap(archiveReal.resolve("tree"), "inside the corpus root", archiveReal)));
        claim(
                "the corpus root itself is refused",
                () -> assertThat(DeliverableRoot.named(archive).refusalAtTheStart(work, archive, NO_SEED_FOLDER))
                        .contains(overlap(archiveReal, "the corpus root", archiveReal)));
        claim(
                "a folder holding the corpus root is refused",
                () -> assertThat(DeliverableRoot.named(base).refusalAtTheStart(work, archive, NO_SEED_FOLDER))
                        .contains(overlap(base.toRealPath(), "above the corpus root", archiveReal)));
        claim(
                "and checking made no folder: the root inside the corpus root is still not there",
                () -> assertThat(inside).doesNotExist());
    }

    @Test
    @Story("A folder over the archive is refused before anything is walked")
    @DisplayName("A folder that is the seed folder, inside it or above it is refused, each in one line")
    void refusesARootThatIsTheSeedFolderInsideItOrAboveIt() throws IOException {
        Path seedsReal = seeds.toRealPath();
        Optional<Path> theSeedFolder = Optional.of(seeds);

        claim(
                "a root inside the seed folder is refused",
                () -> assertThat(DeliverableRoot.named(seeds.resolve("tree")).refusalAtTheStart(work, archive, theSeedFolder))
                        .contains(overlap(seedsReal.resolve("tree"), "inside the seed folder", seedsReal)));
        claim(
                "the seed folder itself is refused",
                () -> assertThat(DeliverableRoot.named(seeds).refusalAtTheStart(work, archive, theSeedFolder))
                        .contains(overlap(seedsReal, "the seed folder", seedsReal)));
        claim(
                "and a folder holding the seed folder is refused",
                () -> assertThat(DeliverableRoot.named(library).refusalAtTheStart(work, archive, theSeedFolder))
                        .contains(overlap(library.toRealPath(), "above the seed folder", seedsReal)));
    }

    @Test
    @Story("A folder over the archive is refused before anything is walked")
    @DisplayName("A folder beside the corpus root or the seed folder, or inside the working directory, is accepted")
    void acceptsARootBesideTheCorpusRootOrTheSeedFolderOrInsideTheWorkingDirectory() {
        Optional<Path> theSeedFolder = Optional.of(seeds);
        Path besideTheArchive = base.resolve("archive-copies");
        Path besideTheSeeds = library.resolve("seeds-copies");
        Path insideTheWorkingDirectory = work.resolve("tree");

        claim(
                "a root whose name begins with the corpus root's name, beside it, is not inside it: names are compared"
                        + " whole",
                () -> assertThat(DeliverableRoot.named(besideTheArchive).refusalAtTheStart(work, archive, theSeedFolder))
                        .isEmpty());
        claim(
                "a root whose name begins with the seed folder's name, beside it, is not inside it either",
                () -> assertThat(DeliverableRoot.named(besideTheSeeds).refusalAtTheStart(work, archive, theSeedFolder))
                        .isEmpty());
        claim(
                "a root inside the working directory is accepted: nothing walks the working directory",
                () -> assertThat(
                                DeliverableRoot.named(insideTheWorkingDirectory).refusalAtTheStart(work, archive, theSeedFolder))
                        .isEmpty());
        claim(
                "and none of the three was made by being checked",
                () -> {
                    assertThat(besideTheArchive).doesNotExist();
                    assertThat(besideTheSeeds).doesNotExist();
                    assertThat(insideTheWorkingDirectory).doesNotExist();
                });
    }

    @Test
    @Story("A folder over the archive is refused before anything is walked")
    @DisplayName("A folder reached through a link into the corpus root is refused as lying inside it")
    void aRootReachedThroughALinkIsComparedWhereTheLinkLeads() throws IOException {
        Path link = base.resolve("a link to the archive");
        if (!TestLinks.make(link, archive.toAbsolutePath())) {
            abort("this machine can make neither a symbolic link nor a junction, so a root beneath one cannot be laid out");
        }
        Path archiveReal = archive.toRealPath();

        claim(
                "a root beneath a link to the corpus root is inside the corpus root, where the link leads, and the line"
                        + " names it there",
                () -> assertThat(DeliverableRoot.named(link.resolve("tree")).refusalAtTheStart(work, archive, NO_SEED_FOLDER))
                        .contains(overlap(archiveReal.resolve("tree"), "inside the corpus root", archiveReal)));
    }

    /** Windows only: NTFS opens a name in any case, and elsewhere two spellings in two cases are two folders. */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Story("A folder over the archive is refused before anything is walked")
    @DisplayName("On Windows a folder spelled in another case than the seed folder is compared as the same name")
    void onWindowsANameIsComparedWithoutRegardToCase() throws IOException {
        Path seedFolderNotMadeYet = base.resolve("Unmade");
        Path rootSpelledInCapitals = base.resolve("UNMADE").resolve("tree");
        Path baseReal = base.toRealPath();

        claim(
                "the precondition: neither the seed folder nor the root is there, so no real path settles their case",
                () -> {
                    assertThat(seedFolderNotMadeYet).doesNotExist();
                    assertThat(rootSpelledInCapitals).doesNotExist();
                });
        claim(
                "a root under the seed folder's name in capitals is inside the seed folder",
                () -> assertThat(DeliverableRoot.named(rootSpelledInCapitals)
                                .refusalAtTheStart(work, archive, Optional.of(seedFolderNotMadeYet)))
                        .contains(overlap(
                                baseReal.resolve("UNMADE").resolve("tree"),
                                "inside the seed folder",
                                baseReal.resolve("Unmade"))));
    }

    @Test
    @Story("A folder over the archive is refused before anything is walked")
    @DisplayName("With no folder named, a working directory inside the corpus root is refused by its deliverable folder")
    void anUnnamedRootIsCheckedAsTheWorkingDirectorysDeliverableFolder() throws IOException {
        Path workInsideTheArchive = Files.createDirectories(archive.resolve("work"));

        claim(
                "the working directory's deliverable folder, inside the corpus root, is refused, named as the folder"
                        + " every unnamed tree lies in",
                () -> assertThat(DeliverableRoot.UNNAMED.refusalAtTheStart(workInsideTheArchive, archive, NO_SEED_FOLDER))
                        .contains(overlap(
                                workInsideTheArchive.toRealPath().resolve("deliverable"),
                                "inside the corpus root",
                                archive.toRealPath())));
        claim(
                "and a working directory beside the corpus root is accepted",
                () -> assertThat(DeliverableRoot.UNNAMED.refusalAtTheStart(work, archive, Optional.of(seeds))).isEmpty());
    }

    @Test
    @Story("A folder that holds anything but this tree is refused")
    @DisplayName("Before the job, a named folder is accepted absent, empty, or holding a tree of this working directory")
    void atTheStartANamedRootIsAcceptedEmptyOrHoldingATreeOfThisWorkingDirectory() throws IOException {
        Path roots = Files.createDirectories(base.resolve("roots"));
        Path absent = roots.resolve("absent");
        Path empty = Files.createDirectories(roots.resolve("empty"));
        Path thisWorkingDirectorysTree = marked(roots.resolve("this working directory's"), work.toRealPath(), ANOTHER_GENERATION_RUN);
        Path strayFile = Files.createDirectories(roots.resolve("a stray file"));
        Files.writeString(strayFile.resolve("notes.txt"), "the operator's notes", StandardCharsets.UTF_8);
        Path elsewhere = Files.createDirectories(base.resolve("elsewhere"));
        Path anotherWorkingDirectorysTree = marked(roots.resolve("another working directory's"), elsewhere.toRealPath(), A_GENERATION_RUN);
        Path unreadableMarker = Files.createDirectories(roots.resolve("an unreadable marker"));
        Files.writeString(unreadableMarker.resolve(MARKER), "not a marker", StandardCharsets.UTF_8);
        Path aFile = Files.writeString(roots.resolve("a file"), "a file where the root would be", StandardCharsets.UTF_8);

        claim(
                "a root not made yet, an empty folder, and a folder holding a tree this working directory drew under"
                        + " another generation are accepted: the generation is compared at the drawing",
                () -> {
                    assertThat(startOf(absent)).isEmpty();
                    assertThat(startOf(empty)).isEmpty();
                    assertThat(startOf(thisWorkingDirectorysTree)).isEmpty();
                });
        claim(
                "a folder holding a file and no tree is refused",
                () -> assertThat(startOf(strayFile)).contains(notEmptyAtTheStart(strayFile.toRealPath())));
        claim(
                "a folder holding another working directory's tree is refused the same way",
                () -> assertThat(startOf(anotherWorkingDirectorysTree))
                        .contains(notEmptyAtTheStart(anotherWorkingDirectorysTree.toRealPath())));
        claim(
                "a folder whose marker cannot be read as one is refused the same way",
                () -> assertThat(startOf(unreadableMarker)).contains(notEmptyAtTheStart(unreadableMarker.toRealPath())));
        claim(
                "a file where the root would be is refused as not a folder",
                () -> assertThat(startOf(aFile))
                        .contains("vespera run refused the deliverable root " + aFile.toRealPath()
                                + ": it is not a folder. Name an empty folder in vespera.deliverable-root."));
        claim(
                "and checking changed nothing: the stray file is as written and the root not made yet is still not there",
                () -> {
                    assertThat(strayFile.resolve("notes.txt")).hasContent("the operator's notes");
                    assertThat(absent).doesNotExist();
                });
    }

    @Test
    @Story("A folder that holds anything but this tree is refused")
    @DisplayName("At the drawing, a named folder is accepted absent, empty, or holding this generation's own tree")
    void atTheDrawingANamedRootIsAcceptedEmptyOrHoldingThisRunsTree() throws IOException {
        Path workReal = work.toRealPath();
        Path roots = Files.createDirectories(base.resolve("roots"));
        Path absent = roots.resolve("absent");
        Path empty = Files.createDirectories(roots.resolve("empty"));
        Path thisRunsTree = marked(roots.resolve("this run's"), workReal, A_GENERATION_RUN);
        Path anotherRunsTree = marked(roots.resolve("another run's"), workReal, ANOTHER_GENERATION_RUN);
        Path strayFile = Files.createDirectories(roots.resolve("a stray file"));
        Files.writeString(strayFile.resolve("notes.txt"), "the operator's notes", StandardCharsets.UTF_8);
        Path anotherWorkingDirectorysTree = marked(
                roots.resolve("another working directory's"),
                Files.createDirectories(base.resolve("elsewhere")).toRealPath(),
                A_GENERATION_RUN);

        claim(
                "a root not made yet, an empty folder, and this working directory's tree of this generation are"
                        + " accepted, the last to be drawn again in place",
                () -> {
                    assertThat(drawingOf(absent)).isEmpty();
                    assertThat(drawingOf(empty)).isEmpty();
                    assertThat(drawingOf(thisRunsTree)).isEmpty();
                });
        claim(
                "this working directory's tree of another generation is refused: a new generation needs a new root",
                () -> assertThat(drawingOf(anotherRunsTree))
                        .contains("the deliverable root " + anotherRunsTree.toRealPath() + " holds the tree of another"
                                + " generation run, so no tree was drawn. Name an empty folder in"
                                + " vespera.deliverable-root, and run again."));
        claim(
                "a folder holding a file and no tree is refused",
                () -> assertThat(drawingOf(strayFile)).contains(notEmptyAtTheDrawing(strayFile.toRealPath())));
        claim(
                "and another working directory's tree of the same generation is refused the same way",
                () -> assertThat(drawingOf(anotherWorkingDirectorysTree))
                        .contains(notEmptyAtTheDrawing(anotherWorkingDirectorysTree.toRealPath())));
    }

    @Test
    @Story("A folder that holds anything but this tree is refused")
    @DisplayName("With no folder named, the tree under the working directory is never refused at the drawing")
    void anUnnamedRootIsNeverRefusedAtTheDrawing() throws IOException {
        Path anEarlierTree = Files.createDirectories(work.resolve("deliverable").resolve(A_GENERATION_RUN));
        Files.writeString(anEarlierTree.resolve("index.html"), "an index drawn before trees were marked", StandardCharsets.UTF_8);

        claim(
                "the precondition: the tree under the working directory holds an index and no marker, as a tree drawn"
                        + " before trees were marked does",
                () -> {
                    assertThat(anEarlierTree.resolve("index.html")).isRegularFile();
                    assertThat(anEarlierTree.resolve(MARKER)).doesNotExist();
                });
        claim(
                "and it is accepted, its folder being named for the generation that drew it",
                () -> assertThat(DeliverableRoot.UNNAMED.refusalAtTheDrawing(work, A_GENERATION_RUN)).isEmpty());
    }

    /** The answer at the start for {@code root} named, the seed folder set. */
    private Optional<String> startOf(Path root) {
        return DeliverableRoot.named(root).refusalAtTheStart(work, archive, Optional.of(seeds));
    }

    /** The answer at the drawing of {@link #A_GENERATION_RUN}'s tree for {@code root} named. */
    private Optional<String> drawingOf(Path root) {
        return DeliverableRoot.named(root).refusalAtTheDrawing(work, A_GENERATION_RUN);
    }

    /** The line refusing {@code root}, which stands in {@code relation} to {@code folder}. */
    private static String overlap(Path root, String relation, Path folder) {
        return "vespera run refused the deliverable root " + root + ": it is " + relation + " " + folder
                + NAME_A_FOLDER_OUTSIDE;
    }

    private static String notEmptyAtTheStart(Path root) {
        return "vespera run refused the deliverable root " + root + ": it is not empty and holds no tree of this"
                + " working directory. Name an empty folder in vespera.deliverable-root.";
    }

    private static String notEmptyAtTheDrawing(Path root) {
        return "the deliverable root " + root + " is not empty and holds no tree of this working directory, so no tree"
                + " was drawn. Name an empty folder in vespera.deliverable-root, and run again.";
    }

    /** A folder at {@code root} holding a marker naming {@code workingDirectory} and {@code generationRun}, as ADR-276 §3 writes it. */
    private static Path marked(Path root, Path workingDirectory, String generationRun) throws IOException {
        Files.createDirectories(root);
        Files.writeString(
                root.resolve(MARKER),
                "working-directory=" + workingDirectory + "\n" + "generation-run=" + generationRun + "\n",
                StandardCharsets.UTF_8);
        return root;
    }
}
