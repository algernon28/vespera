package io.algernon.vespera.ui;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.assertj.core.api.Assumptions;

/**
 * The control panel's window shown on a stage of the Headless platform (ADR-342 §6), as {@code ControlPanelWindow}
 * shows it when it starts, with dialogs a test answers: what the window tests that act on it, wait for its clock and read
 * it again share. Every read and every act runs on the toolkit's thread. Closing it stops the child its state started
 * and closes the stage.
 */
final class WindowForTests implements AutoCloseable {

    /** How long a test waits for the window to show what it waits for: a child's start and the window's clock. */
    private static final Duration BOUND = Duration.ofSeconds(20);

    private static final Duration LOOK = Duration.ofMillis(100);

    private static final String USER = System.getProperty("user.name");

    final ControlPanelState state;
    final AnsweringDialogs dialogs;
    final Stage stage;

    private WindowForTests(ControlPanelState state, AnsweringDialogs dialogs, Stage stage) {
        this.state = state;
        this.dialogs = dialogs;
        this.stage = stage;
    }

    /** A control panel's state whose launch values file and lock folder lie under {@code folder}, starting {@code runtime}. */
    static ControlPanelState aState(Path folder, ChildRuntime runtime) throws IOException {
        return new ControlPanelState(runtime,
                LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), USER),
                Files.createDirectories(folder.resolve("root-locks")), USER);
    }

    /** The window of {@code state}, shown on a stage of its own as the control panel shows it, its dialogs answered by the test. */
    static WindowForTests shown(ControlPanelState state) throws Exception {
        AnsweringDialogs dialogs = new AnsweringDialogs();
        Stage stage = HeadlessToolkit.onTheToolkit(() -> {
            Stage made = new Stage();
            ControlPanelWindow.shownOn(made, state, dialogs);
            return made;
        });
        return new WindowForTests(state, dialogs, stage);
    }

    /**
     * The window over a corpus root {@code corpus} and a working directory {@code working} under {@code folder}, its
     * progress interval set to one second in Command options, as the operator can, the shortest it takes, and
     * {@link SleepingJar}'s child started as Run starts it: so that a {@code progress.json} the test writes with
     * {@link #progressWritten} is read within a second. The test is skipped where the child does not start.
     */
    static WindowForTests readingEverySecond(Path folder) throws Exception {
        ControlPanelState state = aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        WindowForTests window = shown(state);
        try {
            window.act(root -> intervalOf(root).getValueFactory().setValue(1));
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            Assumptions.assumeThat(refusal).as("the sleeping child is started").isEmpty();
            return window;
        } catch (Exception | Error failed) {
            window.close();
            throw failed;
        }
    }

    /** Writes {@code json} as the {@code progress.json} of the working directory {@link #readingEverySecond} reads under {@code folder}. */
    static void progressWritten(Path folder, String json) throws IOException {
        Files.writeString(folder.resolve("working").resolve(ProgressFile.FILE_NAME), json, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Integer> intervalOf(Parent root) {
        return node(root, "vespera.progress-interval", Spinner.class);
    }

    /** What {@code reading} reads of the window's root, laid out, on the toolkit's thread. */
    <T> T read(Function<Parent, T> reading) throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> {
            Parent root = stage.getScene().getRoot();
            root.applyCss();
            root.layout();
            return reading.apply(root);
        });
    }

    /** Does {@code acting} to the window's root on the toolkit's thread. */
    void act(Consumer<Parent> acting) throws Exception {
        read(root -> {
            acting.accept(root);
            return null;
        });
    }

    /**
     * What {@code reading} reads once {@code until} holds of it, read again every tenth of a second; what it read last
     * where {@link #BOUND} passes first, so that the claim on it fails with what the window showed.
     */
    <T> T awaiting(Function<Parent, T> reading, Predicate<T> until) throws Exception {
        Instant end = Instant.now().plus(BOUND);
        T seen = read(reading);
        while (!until.test(seen) && Instant.now().isBefore(end)) {
            Thread.sleep(LOOK.toMillis());
            seen = read(reading);
        }
        return seen;
    }

    /** Whether {@code holds} becomes true of the state within {@link #BOUND}, looked at every tenth of a second. */
    static boolean within(BooleanSupplier holds) throws InterruptedException {
        Instant end = Instant.now().plus(BOUND);
        while (!holds.getAsBoolean()) {
            if (Instant.now().isAfter(end)) {
                return false;
            }
            Thread.sleep(LOOK.toMillis());
        }
        return true;
    }

    /** The node of the window whose id is {@code id}, as {@code kind}; the test fails where there is none. */
    static <N extends Node> N node(Parent root, String id, Class<N> kind) {
        Node found = HeadlessToolkit.nodeWithId(root, id)
                .orElseThrow(() -> new AssertionError("the window holds no node whose id is " + id));
        return kind.cast(found);
    }

    /** The folder of the Inclusions tab's tree reached from the corpus root by {@code names}, each opened on the way. */
    @SuppressWarnings("unchecked")
    static CheckBoxTreeItem<String> folder(Parent root, String... names) {
        TreeView<String> tree = node(root, "inclusions-tree", TreeView.class);
        TreeItem<String> at = tree.getRoot();
        if (at == null) {
            throw new AssertionError("the Inclusions tab draws no tree");
        }
        for (String name : names) {
            at.setExpanded(true);
            TreeItem<String> from = at;
            at = from.getChildren().stream().filter(child -> name.equals(child.getValue())).findFirst()
                    .orElseThrow(() -> new AssertionError("the tree lists no folder " + name + " under "
                            + from.getValue() + ", only " + from.getChildren().stream().map(TreeItem::getValue).toList()));
        }
        return (CheckBoxTreeItem<String>) at;
    }

    /** Every node of {@code kind} under the node whose id is {@code id}, itself included, in order. */
    static <N extends Node> List<N> every(Parent root, String id, Class<N> kind) {
        return HeadlessToolkit.everyNodeUnder(node(root, id, Node.class)).stream()
                .filter(kind::isInstance).map(kind::cast).toList();
    }

    /**
     * What a report's tab of the window shows (ADR-344 §6): whether the tab is selected, the file its viewer is on, null
     * before it is on any, the title of the page it shows, and whether that page has loaded.
     */
    record ReportTab(boolean selected, Path shows, String title, boolean loaded) {
    }

    /** The tab of {@code report}, a file name of the working directory, and what its viewer shows; empty where none is open. */
    static Optional<ReportTab> reportTab(Parent root, String report) {
        Optional<Tab> tab = HeadlessToolkit.tabWithId(root, "report-" + report);
        if (tab.isEmpty()) {
            return Optional.empty();
        }
        Optional<WebView> viewer = HeadlessToolkit.nodeWithId(root, "viewer-" + report)
                .filter(WebView.class::isInstance).map(WebView.class::cast);
        if (viewer.isEmpty()) {
            return Optional.of(new ReportTab(tab.get().isSelected(), null, null, false));
        }
        WebEngine engine = viewer.get().getEngine();
        String location = engine.getLocation();
        Path shows = location == null || location.isEmpty() ? null : Path.of(URI.create(location.split("#", 2)[0]));
        boolean loaded = engine.getLoadWorker().getState() == Worker.State.SUCCEEDED;
        String title = loaded ? String.valueOf(engine.executeScript("document.title")) : null;
        return Optional.of(new ReportTab(tab.get().isSelected(), shows, title, loaded));
    }

    /** Whether {@code report}'s tab is open and its viewer has loaded the page it is on. */
    static boolean loaded(Optional<ReportTab> tab) {
        return tab.isPresent() && tab.get().loaded();
    }

    /** The ids of the tabs of the pane the Profile tab stands in, in their order. */
    static List<String> tabIds(Parent root) {
        Tab profile = HeadlessToolkit.tabWithId(root, "profile")
                .orElseThrow(() -> new AssertionError("the window holds no Profile tab"));
        return profile.getTabPane().getTabs().stream().map(Tab::getId).toList();
    }

    /** Presses the link to {@code report} under the node whose id is {@code id}; the test fails where there is none. */
    static void pressReportLink(Parent root, String id, String report) {
        every(root, id, Hyperlink.class).stream().filter(link -> report.equals(link.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("the window holds no link to " + report + " under " + id))
                .fire();
    }

    /** Clicks, by the page's own script, the element whose id is {@code element} in the page {@code report}'s viewer shows. */
    static void clickInsideTheReport(Parent root, String report, String element) {
        node(root, "viewer-" + report, WebView.class).getEngine()
                .executeScript("document.getElementById('" + element + "').click()");
    }

    @Override
    public void close() throws Exception {
        try {
            state.stop();
        } finally {
            HeadlessToolkit.onTheToolkit(() -> {
                stage.close();
                return null;
            });
        }
    }
}
