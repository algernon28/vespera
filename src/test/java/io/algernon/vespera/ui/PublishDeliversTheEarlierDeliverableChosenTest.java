package io.algernon.vespera.ui;

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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The window's Earlier deliverables, in the Working directory section, and Publish (ADR-439 §6, §7): the list shows
 * each earlier deliverable of the working directory chosen, nothing chosen as it is read, and Publish's boxes are those
 * of the latest deliverable's tree, not of a tree no kept run reached; a row chosen offers its tree's boxes, and
 * Publish's command line names its invocation before the destinations; publishing it to the website, it being earlier
 * than the latest, asks once before anything starts, and Cancel starts nothing; Clear the choice puts the latest back.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("923")
@Link(name = "ADR-439", url = Adr.A_PUBLISH_MAY_NAME_AN_EARLIER_RUN_BY_ITS_INVOCATION, type = "adr")
@Link(name = "ADR-345", url = Adr.THE_COCKPIT_REPLACES_THE_BROWSER_CONTROL_PANEL_AND_ITS_TREE_WRITES_THE_FOLDERS_NOT_WALKED, type = "adr")
class PublishDeliversTheEarlierDeliverableChosenTest {

    /** The latest run, which reached the first generation, and an earlier one, which reached the second. */
    private static final String LATEST = "20261112T080000Z";

    private static final String EARLIER = "20261111T080000Z";

    /** Three generation runs, by the folder names the unnamed root gives their trees; the third no kept run reached. */
    private static final String FIRST = "aaaa000000000001";

    private static final String SECOND = "bbbb000000000002";

    private static final String THIRD = "cccc000000000003";

    /** The confirmation's title (ADR-439 §7). */
    private static final String TITLE = "Publish an earlier deliverable";

    @Test
    @Story("Publish delivers the earlier deliverable chosen")
    @DisplayName("The working folder's earlier deliverables are listed with none chosen, and Publish offers what the latest one drew")
    void theListShowsTheEarlierDeliverablesNoneChosenAndTheBoxesAreTheLatests(@TempDir Path folder) throws Exception {
        Path workingDirectory = aWorkingDirectoryWithThreeTrees(folder);
        ControlPanelState state = aStateOver(folder, workingDirectory);

        record Read(List<String> rows, boolean chosen, List<String> boxes) {}
        Read read;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.awaiting(PublishDeliversTheEarlierDeliverableChosenTest::rows, rows -> rows.size() == 2);
            read = window.read(root -> new Read(rows(root), list(root).getSelectionModel().getSelectedItem() != null,
                    boxes(root)));
        }
        List<String> expected = EarlierDeliverable.of(workingDirectory, ZoneId.systemDefault()).stream()
                .map(EarlierDeliverable::words).toList();

        claim("the list shows the two deliverables kept runs reached, the latest first, each in its words",
                () -> assertThat(read.rows()).hasSize(2).isEqualTo(expected));
        claim("none is chosen as the window opens",
                () -> assertThat(read.chosen()).isFalse());
        claim("and Publish offers Confluence and the website, which the latest drew, and not the retrieval records of a"
                        + " tree no kept run reached",
                () -> assertThat(read.boxes()).containsExactly("confluence", "website"));
    }

    @Test
    @Story("Publish delivers the earlier deliverable chosen")
    @DisplayName("An earlier deliverable chosen offers what it drew, names its run on Publish's command line, asks once before replacing what the website holds, and Clear the choice puts the latest back")
    void aRowChosenOffersItsBoxesNamesItsInvocationAndAsksBeforePublishing(@TempDir Path folder) throws Exception {
        Path workingDirectory = aWorkingDirectoryWithThreeTrees(folder);
        ControlPanelState state = aStateOver(folder, workingDirectory);
        String started = EarlierDeliverable.of(workingDirectory, ZoneId.systemDefault()).get(1).started();

        List<String> boxesChosen;
        String lineChosen;
        List<AnsweringDialogs.Asked> asked;
        boolean childStarted;
        List<String> boxesCleared;
        String lineCleared;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.awaiting(PublishDeliversTheEarlierDeliverableChosenTest::rows, rows -> rows.size() == 2);
            window.act(root -> list(root).getSelectionModel().select(1));
            boxesChosen = window.awaiting(PublishDeliversTheEarlierDeliverableChosenTest::boxes,
                    boxes -> boxes.equals(List.of("website")));
            window.act(root -> WindowForTests.node(root, "publish-to-website", CheckBox.class).setSelected(true));
            lineChosen = window.read(root -> commandLine(root));
            window.dialogs.answer = false;
            window.act(root -> WindowForTests.node(root, "publish", Button.class).fire());
            asked = List.copyOf(window.dialogs.asked);
            childStarted = state.childRuns() || state.ending().isPresent();
            window.act(root -> WindowForTests.node(root, "earlier-deliverables-clear", Button.class).fire());
            boxesCleared = window.awaiting(PublishDeliversTheEarlierDeliverableChosenTest::boxes,
                    boxes -> boxes.equals(List.of("confluence", "website")));
            lineCleared = window.read(root -> commandLine(root));
        }

        claim("the earlier deliverable chosen offers the website alone, which is all its tree drew",
                () -> assertThat(boxesChosen).containsExactly("website"));
        claim("with the website ticked, Publish's command line names the earlier run before the destination",
                () -> assertThat(lineChosen).startsWith("vespera publish --invocation " + EARLIER + " --to website "));
        claim("pressed, Publish asked once, before anything started, in the confirmation's title, sentence and answers",
                () -> assertThat(asked).containsExactly(new AnsweringDialogs.Asked(TITLE, "The deliverable of " + started
                        + " replaces what is published to the website, and what it does not hold is taken off there.",
                        "Publish", "Cancel")));
        claim("and Cancel started nothing",
                () -> assertThat(childStarted).isFalse());
        claim("cleared, the boxes are the latest deliverable's again, and the command line names no run",
                () -> {
                    assertThat(boxesCleared).containsExactly("confluence", "website");
                    assertThat(lineCleared).startsWith("vespera publish --to ").doesNotContain("--invocation");
                });
    }

    /**
     * A working directory under {@code folder} whose kept runs reached two generations, the latest's tree drawing
     * Confluence and the website and the earlier's the website alone, beside a third tree no kept run reached that drew
     * the retrieval records.
     */
    private static Path aWorkingDirectoryWithThreeTrees(Path folder) throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Path trees = workingDirectory.resolve("deliverable");
        listsIn(trees.resolve(FIRST), "confluence", "website");
        listsIn(trees.resolve(SECOND), "website");
        listsIn(trees.resolve(THIRD), "retrieval");
        String root = trees.toAbsolutePath().normalize().toString();
        copy(workingDirectory, EARLIER, "2026-11-11T08:00:00.000Z", SECOND, root);
        copy(workingDirectory, LATEST, "2026-11-12T08:00:00.000Z", FIRST, root);
        return workingDirectory;
    }

    private static void listsIn(Path tree, String... destinations) throws IOException {
        for (String destination : destinations) {
            Files.writeString(Files.createDirectories(tree.resolve(destination))
                    .resolve(ChildCommand.DESTINATION_LISTS.get(destination)), "{}", StandardCharsets.UTF_8);
        }
    }

    /** The copy {@code logs/report-<id>.json} of an ended {@code vespera run} that reached {@code generation}. */
    private static void copy(Path workingDirectory, String id, String started, String generation, String root)
            throws IOException {
        String report = "{\"format\":" + InvocationReportFile.FORMAT + ",\"invocation\":{\"id\":\"" + id
                + "\",\"pid\":4242,\"command\":\"run\",\"started\":\"" + started + "\",\"ended\":\"" + started
                + "\",\"state\":\"ended\",\"outcome\":\"completed\",\"exitCode\":0,\"writtenAt\":\"" + started
                + "\",\"interval\":1},\"places\":{\"workingDirectory\":" + json(workingDirectory.toString())
                + ",\"corpusRoot\":\"/archives/Letters\",\"deliverableRoot\":" + json(root) + "},\"steps\":[],"
                + "\"legs\":[],\"arrivedAt\":[{\"stage\":\"generation\",\"run\":\"" + generation + "\"}],\"next\":null}";
        Path logs = Files.createDirectories(workingDirectory.resolve("logs"));
        Files.writeString(logs.resolve("report-" + id + ".json"), report, StandardCharsets.UTF_8);
    }

    /** A state holding a corpus root and {@code workingDirectory} under {@code folder}, its child never started. */
    private static ControlPanelState aStateOver(Path folder, Path workingDirectory) throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("no-java"), folder.resolve("no-vespera.jar")));
        state.hold(new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "", "", "", ""));
        return state;
    }

    @SuppressWarnings("unchecked")
    private static ListView<EarlierDeliverable> list(Parent root) {
        return WindowForTests.node(root, "earlier-deliverables", ListView.class);
    }

    /** The words of each row the list shows, in order; none where the list is not drawn. */
    private static List<String> rows(Parent root) {
        return HeadlessToolkit.nodeWithId(root, "earlier-deliverables").isEmpty() ? List.of()
                : list(root).getItems().stream().map(EarlierDeliverable::words).toList();
    }

    /** The destinations of the boxes {@code publish-to} holds, ticked or not, in order. */
    private static List<String> boxes(Parent root) {
        return HeadlessToolkit.nodeWithId(root, "publish-to")
                .map(HeadlessToolkit::everyNodeUnder)
                .orElse(List.of())
                .stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .map(box -> box.getId().substring("publish-to-".length()))
                .toList();
    }

    /** The line {@code command-line-publish} shows (ADR-344 §4). */
    private static String commandLine(Parent root) {
        Optional<Node> shown = HeadlessToolkit.nodeWithId(root, "command-line-publish");
        return shown.map(node -> node instanceof TextInputControl text ? text.getText()
                : node instanceof Labeled labeled ? labeled.getText() : "").orElse("");
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
