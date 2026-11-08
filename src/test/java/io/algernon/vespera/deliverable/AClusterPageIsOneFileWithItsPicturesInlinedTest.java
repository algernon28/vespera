package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A cluster's page is one file (ADR-286): a picture, and an image survivor's copy that is a PNG or a JPEG, are shown
 * by a base64 {@code data:} URI on an {@code <img>}'s {@code src}, and no picture file is written (§1, §4, §6); the page
 * is written to a stream an entry at a time, the bytes handed on counted at each entry's end (§2); it is written
 * through {@code index.html.part} and moved into place (§3); and the files an earlier drawing left in a cluster folder
 * are counted and none deleted (§4).
 *
 * <p>Each test that draws a tree lays out {@code <base>/archive} as the corpus root and {@code <base>/work} as the
 * working directory, the tree drawn at the working directory's {@code deliverable/} folder. The pictures and images
 * are real PNGs, JPEGs and GIFs, written by the JDK, so the bytes carry the signature of their kind. The rules of
 * furniture and the budget are {@code DeliverablePicturesTest}'s; every page's sources and links, {@code
 * TheTreeIsHtmlTest}'s; the names a copy yields to, {@code CopyNamesTest}'s; the closing line, {@code
 * pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest}'s.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("560")
@Link(name = "ADR-286", url = Adr.A_CLUSTERS_PAGE_IS_ONE_FILE_ITS_PICTURES_INLINED, type = "adr")
class AClusterPageIsOneFileWithItsPicturesInlinedTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The cluster every drawing here arranges. */
    private static final RecordedCluster THE_CLUSTER = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    /** That cluster's folder, from the tree's root: the partition's place and slug, then the cluster's. */
    private static final String THE_FOLDER = "1-fire-safety/1-fire-doors";

    /** The page, in its folder. */
    private static final String THE_PAGE = THE_FOLDER + "/index.html";

    /** What a page is written under in its folder before it is moved over its place. */
    private static final String THE_PAGES_PART = "index.html.part";

    /** The title the cluster is written over under. */
    private static final String THE_WRITING_TITLE = "Fire doors and their inspection";

    /** A title the redrawing is handed, so a page written again differs from the first. */
    private static final String ANOTHER_TITLE = "Fire doors, inspected again";

    @Test
    @Story("A group's page is written beside its place and moved over it")
    @DisplayName("A first drawing whose group page cannot be moved into place leaves no page in the group's folder")
    void aFirstDrawingWhosePageCannotBeMovedLeavesNoPageInItsFolder(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of("reports/q3.pdf", bytes("the first survivor")));
        Path tree = base.resolve("work").resolve("deliverable").resolve(ListedTree.RUN_ID);

        Throwable thrown = catchThrowable(() -> drawWith(base, archive, THE_WRITING_TITLE, refusingEveryClusterPage()));

        claim(
                "the drawing fails, as it does when the tree's index cannot be moved",
                () -> assertThat(thrown).isInstanceOf(UncheckedIOException.class));
        claim(
                "no page stands at index.html in the group's folder: a page is put there only by its move, whole",
                () -> assertThat(tree.resolve(THE_PAGE)).doesNotExist());
        claim(
                "and the tree's index, moved only after every page, is not put in place either",
                () -> assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME)).doesNotExist());
    }

    @Test
    @Story("A group's page is written beside its place and moved over it")
    @DisplayName("A redrawing whose group page cannot be moved into place leaves the earlier page whole")
    void aRedrawingWhosePageCannotBeMovedLeavesTheEarlierPageWhole(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of("reports/q3.pdf", bytes("the first survivor")));
        Path tree = drawWith(base, archive, THE_WRITING_TITLE, ReplacingMove.ATOMIC);
        byte[] theEarlierPage = Files.readAllBytes(tree.resolve(THE_PAGE));

        claim(
                "a finished drawing leaves no part of any page behind, and its page carries its title",
                () -> {
                    assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(".part"));
                    assertThat(new String(theEarlierPage, StandardCharsets.UTF_8)).contains(THE_WRITING_TITLE);
                });

        Throwable thrown = catchThrowable(() -> drawWith(base, archive, ANOTHER_TITLE, refusingEveryClusterPage()));

        claim(
                "the redrawing, handed another title, fails at its page's move",
                () -> assertThat(thrown).isInstanceOf(UncheckedIOException.class));
        claim(
                "and the group's page is the earlier drawing's, byte for byte: the new page was written beside it"
                        + " and never over it",
                () -> assertThat(tree.resolve(THE_PAGE)).hasBinaryContent(theEarlierPage));
    }

    /** A move that refuses every cluster page's, as a file system refusing it would, and makes every other. */
    private static ReplacingMove refusingEveryClusterPage() {
        return new ReplacingMove(
                (part, target) -> {
                    if (target.getFileName().toString().equals(Deliverable.INDEX_FILE_NAME)
                            && part.getFileName().toString().equals(THE_PAGES_PART)
                            && !target.getParent().getFileName().toString().equals(ListedTree.RUN_ID)) {
                        throw new IOException("a group's page was refused its place");
                    }
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});
    }

    private static Path drawWith(Path base, Path archive, String title, ReplacingMove moves) {
        return drawWith(base, archive, title, moves, DeliverableProgress.NONE);
    }

    /** The one-survivor tree of {@link #THE_CLUSTER}, written over under {@code title}, moved by {@code moves}. */
    private static Path drawWith(
            Path base, Path archive, String title, ReplacingMove moves, DeliverableProgress progress) {
        Map<ClusterSlot, SynthesisDoc> written = Map.of(slotOf(THE_CLUSTER), writing(title, "It says [1].", 42));
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(
                        List.of(THE_CLUSTER),
                        written,
                        List.of(aSurvivor(42, "reports/q3.pdf", bytes("the first survivor"))),
                        Map.of()),
                SurvivorPictures.none(),
                progress,
                moves);
    }

    /** {@code <base>/archive}, holding each path's bytes. */
    private static Path anArchive(Path base, Map<String, byte[]> files) throws IOException {
        Path archive = base.resolve("archive");
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path original = archive.resolve(file.getKey());
            Files.createDirectories(original.getParent());
            Files.write(original, file.getValue());
        }
        return archive;
    }

    /** A survivor of {@link #THE_CLUSTER}, its recorded key that of {@code content}, scored by its occurrence. */
    private static ListedSurvivor aSurvivor(long occurrence, String path, byte[] content) {
        return survivor(occurrence, path, sha256Of(content), THE_SEED, SEED_PATH, 0, 1.0 - occurrence / 1000.0);
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
