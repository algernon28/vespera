package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The marker at the top of every tree names the version of Vespera that drew it, in a third line (ADR-431 §5,
 * amending ADR-276 §3), and a marker is still read by its two keys, so a tree drawn before, whose marker has two lines,
 * is this working directory's as it was.
 *
 * <p>The classes under test lie in no jar, so no manifest names a version and the third line reads {@code unknown}
 * (ADR-431 §4).
 */
@Epic("The deliverable")
@Feature("Where the tree is drawn")
@Issue("904")
@Link(name = "ADR-431", url = Adr.THE_RELEASE_VERSION_IS_THE_TAGS_AND_ENTERS_NO_RUN_ID, type = "adr")
class TheMarkerNamesTheVersionThatDrewTheTreeTest {

    /** A generation run's id, made up: the marker records it and nothing here looks it up. */
    private static final String A_GENERATION_RUN = "generation-run-of-the-test";

    /** The third line for classes that lie in no jar: the key and the word for no version named. */
    private static final String THE_VERSION_LINE_OUTSIDE_A_JAR = "vespera=unknown\n";

    @Test
    @Story("A tree says which version of Vespera drew it")
    @DisplayName("A tree's marker names the folder it was made for, the work that drew it and Vespera's version")
    void theMarkersThirdLineIsTheReleaseVersion(@TempDir Path folder) throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path tree = Files.createDirectories(folder.resolve("tree"));

        DeliverableRoot.markTree(tree, work, A_GENERATION_RUN);
        String marker = Files.readString(tree.resolve(DeliverableRoot.MARKER_FILE_NAME), StandardCharsets.UTF_8);

        claim("the marker is three lines, each ending in a line feed: the working directory, the generation run and"
                        + " the version, unknown for classes that lie in no jar",
                () -> assertThat(marker).isEqualTo("working-directory=" + work.toRealPath() + "\n"
                        + "generation-run=" + A_GENERATION_RUN + "\n"
                        + THE_VERSION_LINE_OUTSIDE_A_JAR));
    }

    @Test
    @Story("A tree says which version of Vespera drew it")
    @DisplayName("A tree drawn before, whose marker has two lines, is drawn in again, as one with three is")
    void aMarkerOfTwoLinesIsStillThisWorkingDirectorysTree(@TempDir Path folder) throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path other = Files.createDirectories(folder.resolve("other"));
        Path twoLines = marked(folder.resolve("two-lines"), markerOf(work, ""));
        Path threeLines = marked(folder.resolve("three-lines"), markerOf(work, "vespera=2.0.0\n"));
        Path anotherWorkingDirectory = marked(folder.resolve("another"), markerOf(other, "vespera=2.0.0\n"));

        claim("a named root whose marker has two lines naming this working directory and run is drawn in",
                () -> assertThat(DeliverableRoot.named(twoLines).refusalAtTheDrawing(work, A_GENERATION_RUN))
                        .isEmpty());
        claim("one whose marker has the version's third line too is drawn in alike",
                () -> assertThat(DeliverableRoot.named(threeLines).refusalAtTheDrawing(work, A_GENERATION_RUN))
                        .isEmpty());
        claim("one whose three lines name another working directory is still refused",
                () -> assertThat(DeliverableRoot.named(anotherWorkingDirectory)
                        .refusalAtTheDrawing(work, A_GENERATION_RUN)).isPresent());
    }

    /** A marker naming {@code workingDirectory} and the test's generation run, followed by {@code more}. */
    private static String markerOf(Path workingDirectory, String more) throws IOException {
        return "working-directory=" + workingDirectory.toRealPath() + "\n"
                + "generation-run=" + A_GENERATION_RUN + "\n" + more;
    }

    /** A folder at {@code root} holding a page and {@code marker}, as a tree drawn there leaves it. */
    private static Path marked(Path root, String marker) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("index.html"), "<html></html>\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve(DeliverableRoot.MARKER_FILE_NAME), marker, StandardCharsets.UTF_8);
        return root;
    }
}
