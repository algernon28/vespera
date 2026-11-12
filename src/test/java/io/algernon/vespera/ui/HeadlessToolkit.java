package io.algernon.vespera.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;

/**
 * JavaFX's toolkit for the tests, on its Headless platform (ADR-342 §6): started once per JVM, since JavaFX starts
 * once, with {@code glass.platform} set to {@code Headless} and {@code prism.order} to {@code sw} before it starts, so a
 * window is drawn with no display, on Linux and on Windows alike, and with no dependency beyond JavaFX's own.
 *
 * <p>Work on a scene runs on the toolkit's thread through {@link #onTheToolkit}, which waits for its answer and throws
 * what the work threw. A control is found by its id, never by its position, its style class or its words.
 */
final class HeadlessToolkit {

    /** How long a test waits for the toolkit to start, or for work on its thread to end, before it fails. */
    private static final long BOUND_SECONDS = 10;

    private static boolean started;

    private HeadlessToolkit() {
    }

    /** Starts the toolkit on the Headless platform, once; a later call finds it started. */
    static synchronized void start() throws Exception {
        if (started) {
            return;
        }
        System.setProperty("glass.platform", "Headless");
        System.setProperty("prism.order", "sw");
        CompletableFuture<Void> up = new CompletableFuture<>();
        Platform.startup(() -> up.complete(null));
        up.get(BOUND_SECONDS, TimeUnit.SECONDS);
        // A stage closed by one test must not end the toolkit every later test needs.
        Platform.setImplicitExit(false);
        started = true;
    }

    /** Runs {@code work} on the toolkit's thread and answers what it answered, or throws what it threw. */
    static <T> T onTheToolkit(Callable<T> work) throws Exception {
        start();
        CompletableFuture<T> answer = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                answer.complete(work.call());
            } catch (Throwable failure) {
                answer.completeExceptionally(failure);
            }
        });
        try {
            return answer.get(BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw failed;
        }
    }

    /** The node under {@code root}, itself included, whose id is {@code id}; the content of every tab and pane counted. */
    static Optional<Node> nodeWithId(Parent root, String id) {
        return everyNodeUnder(root).stream().filter(node -> id.equals(node.getId())).findFirst();
    }

    /** The tab of any tab pane under {@code root} whose id is {@code id}. */
    static Optional<Tab> tabWithId(Parent root, String id) {
        return everyNodeUnder(root).stream()
                .filter(TabPane.class::isInstance)
                .flatMap(pane -> ((TabPane) pane).getTabs().stream())
                .filter(tab -> id.equals(tab.getId()))
                .findFirst();
    }

    /** Every node under {@code root}, itself first, reached through children, split items, tabs and panes' content. */
    static List<Node> everyNodeUnder(Node root) {
        List<Node> found = new ArrayList<>();
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Node> toVisit = new ArrayDeque<>(List.of(root));
        while (!toVisit.isEmpty()) {
            Node node = toVisit.removeFirst();
            if (!seen.add(node)) {
                continue;
            }
            found.add(node);
            if (node instanceof Parent parent) {
                toVisit.addAll(parent.getChildrenUnmodifiable());
            }
            if (node instanceof SplitPane split) {
                toVisit.addAll(split.getItems());
            }
            if (node instanceof TabPane tabs) {
                tabs.getTabs().stream().map(Tab::getContent).filter(content -> content != null).forEach(toVisit::add);
            }
            if (node instanceof TitledPane titled && titled.getContent() != null) {
                toVisit.add(titled.getContent());
            }
            if (node instanceof ScrollPane scroll && scroll.getContent() != null) {
                toVisit.add(scroll.getContent());
            }
        }
        return found;
    }
}
