package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * What the control panel holds between requests (ADR-302 §2): the launch values, the one child it started and how that
 * child ended, and the line each page is to show once. The pages read and change it only through this class, and the
 * checks that stand before an invocation starts (ADR-302 §2's three lines) are made here, so that Run, Label,
 * {@code save-then-run}, {@code save-then-label} and {@code label-auto} start a child by the same checks. Every write
 * of a working directory's file goes through {@link #write}, one at a time with every start (ADR-295 §1). As the
 * control panel's process ends, {@link #endWithTheControlPanel} ends the child with it (ADR-307 §1).
 *
 * <p>The class holds no connection to {@code vespera.db} (ADR-278 §3) and writes no file but the launch values'
 * (ADR-292 §2), as an invocation starts.
 */
final class ControlPanelState {

    /** What an invocation starts: {@code run}, {@code label}, {@code label --auto}, or {@code publish} (ADR-308 §6). */
    enum Start {
        RUN("Run", "run"), LABEL("Label", "label"), LABEL_AUTO("Label", "label --auto"), PUBLISH("Publish", "publish");

        private final String name;
        private final String command;

        Start(String name, String command) {
            this.name = name;
            this.command = command;
        }
    }

    /** The rendering's folder at a tree's root, and its page list, as the jar writes them (ADR-312, ADR-316 §1). */
    private static final String RENDERING = "confluence";

    private static final String PAGE_LIST = "pages.json";

    private static final Duration LOOK = Duration.ofMillis(50);

    private static final Duration ENDING_BOUND = Duration.ofSeconds(10);

    /** How long the control panel's end waits to see the child it killed, and the child's descendants, end (ADR-307 §1). */
    static final Duration END_BOUND = Duration.ofSeconds(2);

    private final ChildRuntime runtime;
    private final Path launchValuesFile;
    private final Path corpusRootLockFolder;
    private final String userName;
    private final WorkingDirectoryWrite writer;
    private final List<String> notes = new ArrayList<>();
    private final Map<String, String> outcomes = new HashMap<>();

    private volatile LaunchValues values;
    private Child child;
    private String childCommand = "";
    private Ending ending;
    private boolean controlPanelEnds;

    ControlPanelState(ChildRuntime runtime, Path launchValuesFile, Path corpusRootLockFolder, String userName) {
        this.runtime = runtime;
        this.launchValuesFile = launchValuesFile;
        this.corpusRootLockFolder = corpusRootLockFolder;
        this.userName = userName;
        this.writer = new WorkingDirectoryWrite(this::childRuns, userName);
        this.values = LaunchValues.load(launchValuesFile, userName, notes::add);
    }

    LaunchValues values() {
        return values;
    }

    /**
     * Whether a Confluence rendering is drawn where the launch values say the tree lies (ADR-308 §6): a page list
     * under the deliverable root where one is named, else under a generation run's folder in the working directory's
     * {@code deliverable/}. Read from the files alone, as the control panel links no class of the jar (ADR-285).
     */
    boolean aRenderingIsDrawn() {
        LaunchValues now = values;
        try {
            if (now.deliverableRoot() != null && !now.deliverableRoot().isBlank()) {
                return Files.isRegularFile(
                        Path.of(now.deliverableRoot().strip()).resolve(RENDERING).resolve(PAGE_LIST));
            }
            if (now.workingDirectory() == null || now.workingDirectory().isBlank()) {
                return false;
            }
            Path trees = Path.of(now.workingDirectory().strip()).resolve("deliverable");
            if (!Files.isDirectory(trees)) {
                return false;
            }
            try (Stream<Path> runs = Files.list(trees)) {
                return runs.anyMatch(tree -> Files.isRegularFile(tree.resolve(RENDERING).resolve(PAGE_LIST)));
            }
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    void hold(LaunchValues replacement) {
        this.values = replacement;
    }

    /** The lines the launch values' file gave as it was loaded. */
    List<String> notes() {
        return List.copyOf(notes);
    }

    /**
     * Writes {@code fileName} as {@link WorkingDirectoryWrite#write} does, under this object's monitor, which
     * {@link #start} holds too: no child is started while a write of the control panel's holds the working-directory
     * lock, which its own holder line does not show as held (ADR-295 §1, ADR-306 §1).
     */
    synchronized Optional<String> write(Path workingDirectory, String fileName,
            Function<Optional<String>, String> change) {
        return writer.write(workingDirectory, fileName, change);
    }

    /** The working directory held, empty where none is named. */
    Optional<Path> workingDirectory() {
        String named = values.workingDirectory();
        return named.isEmpty() ? Optional.empty() : Optional.of(Path.of(named));
    }

    synchronized void say(String page, String line) {
        outcomes.put(page, line);
    }

    /** The line {@code page} is to show, once; {@code null} where it has none. */
    synchronized String takeOutcome(String page) {
        return outcomes.remove(page);
    }

    /** The line a lock gives for the working directory held and for the corpus root held, each empty where it is held by nobody. */
    record Holders(Optional<String> workingDirectory, Optional<String> corpusRoot) {
        boolean any() {
            return workingDirectory.isPresent() || corpusRoot.isPresent();
        }
    }

    Holders holders() {
        Optional<String> working = workingDirectory()
                .flatMap(folder -> heldByAnother(folder.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME)));
        Optional<String> root = values.corpusRoot().isEmpty() ? Optional.empty()
                : held(CorpusRootLockFile.in(corpusRootLockFolder, Path.of(values.corpusRoot())));
        return new Holders(working, root);
    }

    /**
     * The working directory's holder line as {@link #held} reads it, but for one naming the control panel's own
     * process, held by nobody: the control panel holds the lock only inside a write of its own (ADR-306 §1).
     */
    private static Optional<String> heldByAnother(Path lockFile) {
        try {
            HolderReading reading = HolderReading.read(lockFile);
            boolean own = reading.line().map(line -> line.pid() == ProcessHandle.current().pid()).orElse(false);
            return reading.held() && !own ? reading.text() : Optional.empty();
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<String> held(Path lockFile) {
        try {
            HolderReading reading = HolderReading.read(lockFile);
            return reading.held() ? reading.text() : Optional.empty();
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    synchronized boolean childRuns() {
        return child != null && ending().isEmpty();
    }

    /** How the child ended, once it has; empty while it runs and where none was started. */
    synchronized Optional<Ending> ending() {
        if (child != null && ending == null) {
            try {
                ending = child.waitForEnding(LOOK).orElse(null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return Optional.ofNullable(ending);
    }

    synchronized boolean childWasStarted() {
        return child != null;
    }

    synchronized String childCommand() {
        return childCommand;
    }

    synchronized List<String> tail() {
        return child == null ? List.of() : child.tail();
    }

    /** Stops the child it started, once it runs; returns when the child is gone and has been seen to end. */
    synchronized void stop() throws InterruptedException {
        if (child == null || ending().isPresent()) {
            return;
        }
        child.stop();
        ending = child.waitForEnding(ENDING_BOUND).orElse(null);
    }

    /**
     * Kills the child it started, with its descendants, as the control panel's process ends, and waits up to
     * {@link #END_BOUND} to see them end; no child is started after it (ADR-307 §1). A child that has ended already is
     * left as it is.
     */
    synchronized void endWithTheControlPanel() throws InterruptedException {
        controlPanelEnds = true;
        if (child != null && ending().isEmpty()) {
            child.endWithin(END_BOUND);
        }
    }

    /**
     * Starts the invocation as ADR-302 §2 does, or gives the line that refuses it: the launch values' own refusal,
     * then the control panel's own child, {@code vespera.lock}, the corpus-root lock. The file of launch values is
     * stored as it starts (ADR-292 §2); a line it gives is returned in {@code stored}.
     *
     * @return the refusal, empty where the child was started
     */
    synchronized Optional<String> start(Start kind, List<String> stored) {
        if (controlPanelEnds) {
            return Optional.of(kind.name + " is not started: the control panel is ending");
        }
        LaunchValues now = values;
        Optional<String> refusal = kind == Start.RUN ? now.refusalToRun() : now.refusalToLabel();
        if (refusal.isPresent()) {
            return refusal;
        }
        if (childRuns()) {
            return Optional.of(kind.name + " is not started while the invocation the control panel started runs");
        }
        Holders holders = holders();
        if (holders.workingDirectory().isPresent()) {
            return Optional.of(kind.name + " is not started: the working directory " + now.workingDirectory()
                    + " is held by another Vespera invocation (" + holders.workingDirectory().get() + ")");
        }
        if (holders.corpusRoot().isPresent()) {
            return Optional.of(kind.name + " is not started: the corpus root " + now.corpusRoot()
                    + " is held by another Vespera invocation (" + holders.corpusRoot().get() + ")");
        }
        ChildCommand command = switch (kind) {
            case RUN -> ChildCommand.run(runtime.java(), runtime.jar(), now);
            case LABEL -> ChildCommand.label(runtime.java(), runtime.jar(), false, now);
            case LABEL_AUTO -> ChildCommand.label(runtime.java(), runtime.jar(), true, now);
            case PUBLISH -> ChildCommand.publish(runtime.java(), runtime.jar(), now);
        };
        now.store(launchValuesFile, userName, stored::add);
        try {
            child = Child.start(command.processBuilder());
        } catch (IOException | RuntimeException notStarted) {
            return Optional.of(kind.name + " is not started: " + (notStarted.getMessage() == null
                    ? notStarted.getClass().getSimpleName() : notStarted.getMessage()));
        }
        childCommand = kind.command;
        ending = null;
        return Optional.empty();
    }
}
