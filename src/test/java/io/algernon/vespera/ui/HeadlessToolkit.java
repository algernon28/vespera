package io.algernon.vespera.ui;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
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
import java.util.concurrent.TimeoutException;
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
 * what the work threw, bounded on the toolkit thread's processor time and not on the wall (ADR-385). A control is found by its id, never by its position, its style class or its words.
 */
final class HeadlessToolkit {

    /** How long a test waits for the toolkit to start before it fails. */
    private static final long BOUND_SECONDS = 10;

    /**
     * The most processor time the toolkit's thread spends, from when work is posted to it, before the work has answered
     * (ADR-385 §1): the toolkit's thread is the thread that pays the work's cost, so a busy machine that keeps it waiting
     * for a processor does not move the bound, and an earlier test's work still running on it does.
     */
    static final Duration WORK_PROCESSOR_TIME = Duration.ofSeconds(10);

    /**
     * How long the toolkit's thread stands waiting, blocked or asleep, gaining no processor time, while posted work has
     * not answered, before the test fails (ADR-385 §2): work that waits for something that does not come.
     */
    static final Duration STANDING_STILL = Duration.ofSeconds(10);

    /**
     * The longest a test waits on the clock on the wall for posted work to answer (ADR-385 §3): a ceiling for work that
     * neither spends its processor time nor stands still, as a nested event loop turning for a dialog no one answers.
     */
    static final Duration CEILING = Duration.ofSeconds(180);

    private static final long LOOK_MILLIS = 50;

    private static boolean started;

    private static volatile Thread toolkit;

    private HeadlessToolkit() {
    }

    /** Starts the toolkit on the Headless platform, once; a later call finds it started. */
    static synchronized void start() throws Exception {
        if (started) {
            return;
        }
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (!threads.isThreadCpuTimeSupported()) {
            throw new IllegalStateException("this Java measures no thread's processor time, which the toolkit's bound is on");
        }
        if (!threads.isThreadCpuTimeEnabled()) {
            threads.setThreadCpuTimeEnabled(true);
        }
        System.setProperty("glass.platform", "Headless");
        System.setProperty("prism.order", "sw");
        CompletableFuture<Void> up = new CompletableFuture<>();
        Platform.startup(() -> {
            toolkit = Thread.currentThread();
            up.complete(null);
        });
        up.get(BOUND_SECONDS, TimeUnit.SECONDS);
        // A stage closed by one test must not end the toolkit every later test needs.
        Platform.setImplicitExit(false);
        started = true;
    }

    /**
     * Runs {@code work} on the toolkit's thread and answers what it answered, or throws what it threw. It throws a
     * {@link TimeoutException} naming what the toolkit's thread was doing where the work has not answered within one of
     * the bounds of ADR-385, and refuses to be called on the toolkit's thread, where it would wait for itself.
     */
    static <T> T onTheToolkit(Callable<T> work) throws Exception {
        start();
        if (Platform.isFxApplicationThread()) {
            throw new IllegalStateException("work posted from the toolkit's own thread would wait for itself");
        }
        CompletableFuture<T> answer = new CompletableFuture<>();
        Thread thread = toolkit;
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long posted = System.nanoTime();
        long processorAtPost = threads.getThreadCpuTime(thread.threadId());
        Platform.runLater(() -> {
            try {
                answer.complete(work.call());
            } catch (Throwable failure) {
                answer.completeExceptionally(failure);
            }
        });
        long stillSince = posted;
        long processorWhenStill = processorAtPost;
        while (true) {
            try {
                return answer.get(LOOK_MILLIS, TimeUnit.MILLISECONDS);
            } catch (ExecutionException failed) {
                if (failed.getCause() instanceof Exception exception) {
                    throw exception;
                }
                throw failed;
            } catch (TimeoutException notYet) {
                long now = System.nanoTime();
                long processor = threads.getThreadCpuTime(thread.threadId());
                Thread.State state = thread.getState();
                if (processor - processorAtPost > WORK_PROCESSOR_TIME.toNanos()) {
                    throw notAnswered("spent " + millis(processor - processorAtPost) + " ms of its processor time", thread,
                            posted, now);
                }
                if (state == Thread.State.RUNNABLE || processor != processorWhenStill) {
                    stillSince = now;
                    processorWhenStill = processor;
                } else if (now - stillSince > STANDING_STILL.toNanos()) {
                    throw notAnswered("stood " + state + " for " + millis(now - stillSince) + " ms, gaining no processor"
                            + " time", thread, posted, now);
                }
                if (now - posted > CEILING.toNanos()) {
                    throw notAnswered("reached the ceiling on the wall", thread, posted, now);
                }
            }
        }
    }

    private static TimeoutException notAnswered(String how, Thread thread, long posted, long now) {
        StringBuilder words = new StringBuilder("work posted to the toolkit's thread has not answered after "
                + millis(now - posted) + " ms on the wall: the thread " + how + "; it stands " + thread.getState()
                + " at");
        for (StackTraceElement frame : thread.getStackTrace()) {
            words.append("\n\tat ").append(frame);
        }
        return new TimeoutException(words.toString());
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000;
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
