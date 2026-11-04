package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

/**
 * The control panel's window shown on a stage of the Headless platform (ADR-339 §6), as {@code ControlPanelWindow}
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
