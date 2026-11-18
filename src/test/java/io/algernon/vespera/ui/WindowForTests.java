package io.algernon.vespera.ui;

import java.io.IOException;
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
import javafx.scene.Node;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.assertj.core.api.Assumptions;

/**
 * The control panel's window shown on a stage of the Headless platform (ADR-342 §6), as {@code ControlPanelWindow}
 * shows it when it starts, with dialogs a test answers: what the window tests that act on it, wait for its clock and read
 * it again share, and the Profile window (ADR-389) and the Reports window (ADR-414) it opens. Every read and every act
 * runs on the toolkit's thread. Closing it stops the child its state started and closes the stage, and every Profile
 * window and Reports window showing.
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
     * {@link SleepingJar}'s child started as Run starts it: so that a {@code report.json} the test writes with
     * {@link #reportWritten} is read within a second. The test is skipped where the child does not start.
     */
    static WindowForTests readingEverySecond(Path folder) throws Exception {
        ControlPanelState state = aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        WindowForTests window = shown(state);
        try {
            window.readEverySecond();
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            Assumptions.assumeThat(refusal).as("the sleeping child is started").isEmpty();
            return window;
        } catch (Exception | Error failed) {
            window.close();
            throw failed;
        }
    }

    /**
     * Writes {@code job}, the steps and charts a test describes (see {@link ReportFixtures}), as the {@code report.json}
     * the child this window started has just written, in the working directory {@link #readingEverySecond} reads under
     * {@code folder}.
     */
    void reportWritten(Path folder, String job) throws IOException {
        ReportFixtures.write(folder.resolve("working"), state.watchedChild().orElseThrow().pid(), job);
    }

    /** Sets the progress interval to one second in Command options, as the operator can, the shortest it takes. */
    void readEverySecond() throws Exception {
        act(root -> intervalOf(root).getValueFactory().setValue(1));
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

    /** What a report's tab of the window shows (ADR-362 §10): whether the tab is selected. */
    record ReportTab(boolean selected) {
    }

    /** The tab of {@code report}, an analysis report's name, and whether it is selected; empty where none is open. */
    static Optional<ReportTab> reportTab(Parent root, String report) {
        Optional<Tab> tab = HeadlessToolkit.tabWithId(root, "analysis-report-" + report);
        if (tab.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReportTab(tab.get().isSelected()));
    }

    /** The ids of the tabs of the pane the Inclusions tab stands in, in their order. */
    static List<String> tabIds(Parent root) {
        Tab inclusions = HeadlessToolkit.tabWithId(root, "inclusions")
                .orElseThrow(() -> new AssertionError("the window holds no Inclusions tab"));
        return inclusions.getTabPane().getTabs().stream().map(Tab::getId).toList();
    }

    // ---- the Reports window (ADR-414)

    /** The id of the Reports window's pane of tabs, by which a test finds that window among the toolkit's (ADR-414 §2). */
    static final String ANALYSIS_REPORT_TABS = "analysis-report-tabs";

    /** Every Reports window showing, in the toolkit's order: each stage but this window's own whose root holds the pane. */
    List<Stage> analysisReportsWindows() throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> windowsBeside(stage, ANALYSIS_REPORT_TABS));
    }

    /** The one Reports window showing; the test fails where there is none, or more than one. */
    Stage analysisReportsWindow() throws Exception {
        List<Stage> showing = analysisReportsWindows();
        if (showing.size() != 1) {
            throw new AssertionError("the control panel shows " + showing.size() + " Reports windows, not one");
        }
        return showing.getFirst();
    }

    /** What {@code reading} reads of the Reports window's root, laid out, on the toolkit's thread. */
    <T> T readAnalysisReports(Function<Parent, T> reading) throws Exception {
        return readOn(analysisReportsWindow(), reading);
    }

    /** Does {@code acting} to the Reports window's root on the toolkit's thread. */
    void actOnAnalysisReports(Consumer<Parent> acting) throws Exception {
        readAnalysisReports(root -> {
            acting.accept(root);
            return null;
        });
    }

    /** The ids of the Reports window's tabs, in their order; none where no Reports window shows. */
    List<String> analysisReportTabIds() throws Exception {
        if (analysisReportsWindows().isEmpty()) {
            return List.of();
        }
        return readAnalysisReports(root -> node(root, ANALYSIS_REPORT_TABS, TabPane.class).getTabs().stream()
                .map(Tab::getId).toList());
    }

    /** Closes {@code tab} as its close button does: asked to close, taken out of its pane, then told it is closed. */
    static void closeTabAsTheOperatorDoes(Tab tab) {
        Event.fireEvent(tab, new Event(tab, tab, Tab.TAB_CLOSE_REQUEST_EVENT));
        tab.getTabPane().getTabs().remove(tab);
        Event.fireEvent(tab, new Event(tab, tab, Tab.CLOSED_EVENT));
    }

    /** Presses the link a cell of the table whose id is {@code table} draws with {@code text}; fails where it draws none. */
    static void pressCellLink(Parent root, String table, String text) {
        every(root, table, Hyperlink.class).stream().filter(link -> text.equals(link.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("the table " + table + " draws no link to " + text)).fire();
    }

    // ---- the Profile window (ADR-389)

    /** Opens the Profile window as the operator does: the sidebar's Profile section opened and Open profile pressed. */
    void openProfile() throws Exception {
        act(root -> {
            section(root, "profile").setExpanded(true);
            node(root, "open-profile", Button.class).fire();
        });
    }

    /** The sidebar's section whose id is {@code id}; the test fails where the sidebar holds none. */
    static TitledPane section(Parent root, String id) {
        return node(root, "sidebar", Accordion.class).getPanes().stream().filter(pane -> id.equals(pane.getId()))
                .findFirst().orElseThrow(() -> new AssertionError("the sidebar holds no section whose id is " + id));
    }

    /** Every Profile window showing, in the toolkit's order: each stage but this window's own whose root holds the form. */
    List<Stage> profileWindows() throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> windowsBeside(stage, "profile-form"));
    }

    /** Every stage showing but {@code cockpit} whose root holds the node whose id is {@code id}, in the toolkit's order. */
    private static List<Stage> windowsBeside(Stage cockpit, String id) {
        return Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                .filter(each -> each != cockpit && each.isShowing() && each.getScene() != null
                        && each.getScene().getRoot() != null
                        && HeadlessToolkit.nodeWithId(each.getScene().getRoot(), id).isPresent())
                .toList();
    }

    /** The one Profile window showing; the test fails where there is none, or more than one. */
    Stage profileWindow() throws Exception {
        List<Stage> showing = profileWindows();
        if (showing.size() != 1) {
            throw new AssertionError("the control panel shows " + showing.size() + " Profile windows, not one");
        }
        return showing.getFirst();
    }

    /** What {@code reading} reads of the Profile window's root, laid out, on the toolkit's thread. */
    <T> T readProfile(Function<Parent, T> reading) throws Exception {
        return readOn(profileWindow(), reading);
    }

    /** What {@code reading} reads of {@code window}'s root, laid out, on the toolkit's thread. */
    private static <T> T readOn(Stage window, Function<Parent, T> reading) throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> {
            Parent root = window.getScene().getRoot();
            root.applyCss();
            root.layout();
            return reading.apply(root);
        });
    }

    /** Does {@code acting} to the Profile window's root on the toolkit's thread. */
    void actOnProfile(Consumer<Parent> acting) throws Exception {
        readProfile(root -> {
            acting.accept(root);
            return null;
        });
    }

    /**
     * Asks {@code window} to close as the system's close button does: a close request, which a handler may consume to
     * keep it open, and which closes it where none does. Answers whether it is still showing.
     */
    static boolean closeAsTheOperatorDoes(Stage window) throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> {
            Event.fireEvent(window, new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST));
            return window.isShowing();
        });
    }

    /** Presses the link to {@code report} under the node whose id is {@code id}; the test fails where there is none. */
    static void pressReportLink(Parent root, String id, String report) {
        every(root, id, Hyperlink.class).stream().filter(link -> report.equals(link.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("the window holds no link to " + report + " under " + id))
                .fire();
    }

    /**
     * Presses the link of {@code key}'s row, in the list whose ids start {@code prefix}, to the report {@code name}, by
     * its id {@code <prefix><key>.report-<name>} and not by its text (ADR-377 §5); the test fails where it is no link.
     */
    static void pressRowAnalysisReportLink(Parent root, String prefix, ProfileKey key, String name) {
        node(root, prefix + key.key() + ".report-" + name, Hyperlink.class).fire();
    }

    /**
     * Closes the cockpit as the operator does and waits, within the tests' bound, for it to close: since ADR-390 §5 it
     * stays shown while its child and then the sidecars are stopped, and closes after. Gives whether it is still shown.
     */
    boolean closedAsTheOperatorDoesAndAwaited() throws Exception {
        closeAsTheOperatorDoes(stage);
        within(() -> {
            try {
                return !HeadlessToolkit.onTheToolkit(stage::isShowing);
            } catch (Exception unanswered) {
                throw new IllegalStateException(unanswered);
            }
        });
        return HeadlessToolkit.onTheToolkit(stage::isShowing);
    }

    @Override
    public void close() throws Exception {
        try {
            state.stop();
        } finally {
            HeadlessToolkit.onTheToolkit(() -> {
                windowsBeside(stage, "profile-form").forEach(Stage::close);
                windowsBeside(stage, ANALYSIS_REPORT_TABS).forEach(Stage::close);
                stage.close();
                return null;
            });
        }
    }
}
