package io.algernon.vespera.engine.deliverable;

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

    /**
     * ADR-333 §7: a tree drawn at or inside {@code <working directory>/website/} would be carried by the operator's sync
     * with its code sets, so such a root is refused at the start of {@code vespera run}, after ADR-276 §2's comparisons
     * and by the same reading of paths, in one line naming the relation. The website's marker, one line {@code
     * website} and no path, is read as the marker of no tree.
     */
    @Test
    @Story("A folder over the archive is refused before anything is walked")
    @DisplayName("A folder that is the working directory's website folder, or inside it, is refused, each in one line")
    @Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
    void refusesARootAtOrInsideTheWebsiteFolder() throws IOException {
        Path website = work.resolve("website");
        Path insideIt = website.resolve("tree");
        Path workReal = work.toRealPath();

        claim(
                "the website folder itself, not made yet, is refused by its place, its whole line naming the relation",
                () -> assertThat(DeliverableRoot.named(website).refusalAtTheStart(work, archive, NO_SEED_FOLDER))
                        .contains(website(workReal.resolve("website"), "the website folder")));
        claim(
                "a folder inside it is refused as inside the website folder",
                () -> assertThat(DeliverableRoot.named(insideIt).refusalAtTheStart(work, archive, NO_SEED_FOLDER))
                        .contains(website(workReal.resolve("website").resolve("tree"), "inside the website folder")));
        claim(
                "a folder whose name begins with the website folder's, beside it, is not inside it: names are compared"
                        + " whole",
                () -> assertThat(DeliverableRoot.named(work.resolve("websites")).refusalAtTheStart(work, archive,
                                NO_SEED_FOLDER))
                        .isEmpty());
        claim(
                "and checking made neither folder",
                () -> assertThat(website).doesNotExist());

        Path workInsideTheArchive = Files.createDirectories(archive.resolve("work"));
        Path archiveReal = archive.toRealPath();
        claim(
                "ADR-276 §2's comparisons come first: a website folder of a working directory inside the corpus root"
                        + " is refused as inside the corpus root",
                () -> assertThat(DeliverableRoot.named(workInsideTheArchive.resolve("website"))
                                .refusalAtTheStart(workInsideTheArchive, archive, NO_SEED_FOLDER))
                        .contains(overlap(archiveReal.resolve("work").resolve("website"), "inside the corpus root",
                                archiveReal)));

        Path holdingTheWebsitesMarker = Files.createDirectories(base.resolve("synced"));
        Files.writeString(holdingTheWebsitesMarker.resolve(MARKER), "website\n", StandardCharsets.UTF_8);
        claim(
                "a folder holding the website's marker, one line and no path, is read as holding no tree: it is"
                        + " refused as a folder not empty, never drawn over",
                () -> assertThat(DeliverableRoot.named(holdingTheWebsitesMarker).refusalAtTheStart(work, archive,
                                NO_SEED_FOLDER))
                        .contains("vespera run refused the deliverable root " + holdingTheWebsitesMarker.toRealPath()
                                + ": it is not empty and holds no tree of this working directory. Name an empty"
                                + " folder in vespera.deliverable-root."));
    }

    /** The line refusing {@code root} standing in {@code relation} to the working directory's website folder. */
    private static String website(Path root, String relation) {
        return "vespera run refused the deliverable root " + root + ": it is " + relation + " of this working"
                + " directory. Name a folder outside it in vespera.deliverable-root.";
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

    /** The answer at the drawing of {@link #A_GENERATION_RUN}'s tree for {@code root} named. */
    private Optional<String> drawingOf(Path root) {
        return DeliverableRoot.named(root).refusalAtTheDrawing(work, A_GENERATION_RUN);
    }

    /** The line refusing {@code root}, which stands in {@code relation} to {@code folder}. */
    private static String overlap(Path root, String relation, Path folder) {
        return "vespera run refused the deliverable root " + root + ": it is " + relation + " " + folder
                + NAME_A_FOLDER_OUTSIDE;
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
