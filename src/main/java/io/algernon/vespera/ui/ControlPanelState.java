package io.algernon.vespera.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * What the control panel holds while its window is open (ADR-302 §2, ADR-342): the launch values, the sources the
 * window's fields start from (ADR-344 §3), the one child it started and how that child ended. The window reads and
 * changes it only through this class, and the checks that stand before an invocation starts (ADR-302 §2's lines,
 * ADR-331 §6's two among them) are made here, so that Run, Record my answers, Let the local model answer and Publish
 * start a child by the same checks. Every write of a working directory's file goes through {@link #write}, one at a
 * time with every start (ADR-295 §1). As the control panel's process ends, {@link #endWithTheControlPanel} ends the
 * child with it (ADR-307 §1). It also holds the answers the sidecars last gave ({@link #lastChecks}).
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

    private static final Duration LOOK = Duration.ofMillis(50);

    private static final Duration ENDING_BOUND = Duration.ofSeconds(10);

    /** How long the control panel's end waits to see the child it killed, and the child's descendants, end (ADR-307 §1). */
    static final Duration END_BOUND = Duration.ofSeconds(2);

    private final ChildRuntime runtime;
    private final Path launchValuesFile;
    private final Path corpusRootLockFolder;
    private final String userName;
    private final Set<String> reportsOpeningNatively;
    private final WorkingDirectoryWrite writer;
    private final List<String> notes = new ArrayList<>();

    private volatile StartingValues.Sources sources = StartingValues.Sources.none();
    private volatile WorkingDirectoryDefault workingDirectoryDefault = WorkingDirectoryDefault.of(Path.of(""), Map.of(),
            Map.of());
    private volatile LaunchValues values;
    private volatile SidecarChecks.Checks lastChecks = new SidecarChecks.Checks(
            new SidecarChecks.Check(SidecarChecks.Result.NOT_CHECKED),
            new SidecarChecks.Check(SidecarChecks.Result.NOT_CHECKED));
    private Child child;
    private String childCommand = "";
    private Instant childStartInstant = Instant.EPOCH;
    private Ending ending;
    private boolean controlPanelEnds;

    ControlPanelState(ChildRuntime runtime, Path launchValuesFile, Path corpusRootLockFolder, String userName) {
        this(runtime, launchValuesFile, corpusRootLockFolder, userName, Set.of());
    }

    /**
     * As above, with {@code reportsOpeningNatively}, the names of the analysis reports the window draws from their JSON
     * (ADR-362 §10); every other opens in its viewer. The window as shipped passes {@link Reports#OPENING_NATIVELY}; the constructor above passes none.
     */
    ControlPanelState(ChildRuntime runtime, Path launchValuesFile, Path corpusRootLockFolder, String userName,
            Set<String> reportsOpeningNatively) {
        this.reportsOpeningNatively = Set.copyOf(reportsOpeningNatively);
        this.runtime = runtime;
        this.launchValuesFile = launchValuesFile;
        this.corpusRootLockFolder = corpusRootLockFolder;
        this.userName = userName;
        this.writer = new WorkingDirectoryWrite(this::childRuns, userName);
        this.values = LaunchValues.load(launchValuesFile, userName, notes::add);
    }

    /** The analysis reports, by name, that the window draws natively from their JSON (ADR-362 §10). */
    Set<String> reportsOpeningNatively() {
        return reportsOpeningNatively;
    }

    /** Whether the operator chose the dark theme, kept beside the launch values; light where none was kept or it cannot be read. */
    boolean darkChosen() {
        try {
            Path file = launchValuesFile.resolveSibling("theme");
            return Files.exists(file) && "dark".equals(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8).strip());
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    /** Keeps the theme chosen, only in a folder of the user's own that already exists; the window keeps it for the session where it cannot. */
    void keepDark(boolean dark) {
        try {
            Path folder = launchValuesFile.toAbsolutePath().getParent();
            if (Files.isDirectory(folder) && LaunchValues.refusalOfFolder(folder, userName).isEmpty()) {
                Files.writeString(folder.resolve("theme"), dark ? "dark" : "light", java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException unwritten) {
            // the theme is a convenience
        }
    }

    /** The launch values as held: what the launch values file said as the control panel started, then what each start kept. */
    LaunchValues values() {
        return values;
    }

    /** What the window's fields start from: the launch values file, the environment, {@code .env} and {@code application.yaml} (ADR-344 §3). */
    StartingValues.Sources sources() {
        return new StartingValues.Sources(launchValuesByProperty(values), sources.environment(), sources.dotEnv(),
                sources.applicationYaml());
    }

    /** Hands the state what the environment, {@code .env} and {@code application.yaml} say; the launch values are its own. */
    void useSources(StartingValues.Sources read) {
        this.sources = read;
    }

    /**
     * Hands the state the folder the jar would use on this machine where no source names the working directory
     * (ADR-340 §6, kept by ADR-345 §1), which {@code ControlPanel.open()} resolves once from its JVM. A state given none
     * holds the one of the JVM's current directory, with no property or variable.
     */
    void useWorkingDirectoryDefault(WorkingDirectoryDefault resolved) {
        this.workingDirectoryDefault = resolved;
    }

    /** The folder an empty working directory field stands for (ADR-345 §1). */
    WorkingDirectoryDefault workingDirectoryDefault() {
        return workingDirectoryDefault;
    }

    private static Map<String, String> launchValuesByProperty(LaunchValues held) {
        Map<String, String> byProperty = new HashMap<>();
        byProperty.put("vespera.corpus-root", held.corpusRoot());
        byProperty.put("vespera.working-dir", held.workingDirectory());
        byProperty.put("vespera.deliverable-root", held.deliverableRoot());
        byProperty.put("vespera.docling.image", held.doclingImage());
        byProperty.put("vespera.docling.base-url", held.doclingBaseUrl());
        byProperty.put("spring.ai.ollama.base-url", held.ollamaBaseUrl());
        byProperty.put("vespera.label.model", held.labelModel());
        return byProperty;
    }

    /**
     * Whether any destination's rendering is drawn where the fields say the tree lies (ADR-308 §6, ADR-333 §6,
     * ADR-345 §4).
     */
    boolean aRenderingIsDrawn(Optional<Path> workingDirectory, String deliverableRoot) {
        return !destinationsDrawn(workingDirectory, deliverableRoot).isEmpty();
    }

    /**
     * The destinations, of {@link ChildCommand#DESTINATIONS} and in its order, whose rendering's list is found at the
     * top of the tree the output folder names, or of a generation run's folder under the working directory's
     * {@code deliverable/} where it names none (ADR-334 §1). Read from the files alone, as the control panel links no
     * class of the jar (ADR-285).
     */
    List<String> destinationsDrawn(Optional<Path> workingDirectory, String deliverableRoot) {
        return ChildCommand.DESTINATIONS.stream().filter(each -> isDrawn(each, workingDirectory, deliverableRoot))
                .toList();
    }

    private static boolean isDrawn(String destination, Optional<Path> workingDirectory, String deliverableRoot) {
        String list = ChildCommand.DESTINATION_LISTS.get(destination);
        try {
            if (deliverableRoot != null && !deliverableRoot.isBlank()
                    && !namesTheDefaultFolder(workingDirectory, deliverableRoot)) {
                return Files.isRegularFile(Path.of(deliverableRoot.stripTrailing()).resolve(destination).resolve(list));
            }
            if (workingDirectory.isEmpty()) {
                return false;
            }
            Path trees = workingDirectory.get().resolve("deliverable");
            if (!Files.isDirectory(trees)) {
                return false;
            }
            try (Stream<Path> runs = Files.list(trees)) {
                return runs.anyMatch(tree -> Files.isRegularFile(tree.resolve(destination).resolve(list)));
            }
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * Whether the output folder named is the working directory's {@code deliverable} folder, which is read as no folder
     * named (ADR-334 §1): the trees lie in a folder of their own for each run there.
     */
    private static boolean namesTheDefaultFolder(Optional<Path> workingDirectory, String deliverableRoot) {
        if (workingDirectory.isEmpty()) {
            return false;
        }
        try {
            Path theDefault = workingDirectory.get().toAbsolutePath().normalize().resolve("deliverable");
            return namesTheSameFolder(deliverableRoot, theDefault.toString());
        } catch (RuntimeException cannotBeHeld) {
            return false;
        }
    }

    /** Whether the two name the same folder, compared by their real paths where they exist, as ADR-276 §2 compares. */
    static boolean namesTheSameFolder(String typed, String theDefault) {
        try {
            Path one = Path.of(typed.stripTrailing()).toAbsolutePath().normalize();
            Path other = Path.of(theDefault).toAbsolutePath().normalize();
            if (Files.exists(one) && Files.exists(other)) {
                return Files.isSameFile(one, other);
            }
            return one.toString().equalsIgnoreCase(other.toString())
                    && (File.separatorChar == '\\' || one.toString().equals(other.toString()));
        } catch (IOException | RuntimeException cannotBeHeld) {
            return false;
        }
    }

    void hold(LaunchValues replacement) {
        this.values = replacement;
    }

    /** What the sidecars last said, {@code not-checked} for each until the window has asked (ADR-302 §5). */
    SidecarChecks.Checks lastChecks() {
        return lastChecks;
    }

    void keepChecks(SidecarChecks.Checks asked) {
        this.lastChecks = asked;
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

    /** As {@link #write(Path, String, Function)}, for one action writing several files under one hold of the lock. */
    synchronized Optional<String> write(Path workingDirectory, List<WorkingDirectoryWrite.Change> changes) {
        return writer.write(workingDirectory, changes);
    }

    /**
     * What the jar last said it would use where the profile names nothing, read at each request that shows it
     * (ADR-330 §1); empty where no working directory is held or the file is absent or cannot be read.
     */
    Optional<EffectiveValues> effectiveValues(Optional<Path> workingDirectory) {
        return EffectiveValues.read(workingDirectory, runtime.jar());
    }

    /** {@code text} as a path, empty where the system refuses it: a value it refuses names no folder (ADR-331 §6). */
    static Optional<Path> pathOf(String text) {
        try {
            return Optional.of(Path.of(text));
        } catch (InvalidPathException refused) {
            return Optional.empty();
        }
    }

    /** The line a lock gives for the working directory held and for the corpus root held, each empty where it is held by nobody. */
    record Holders(Optional<String> workingDirectory, Optional<String> corpusRoot) {
        boolean any() {
            return workingDirectory.isPresent() || corpusRoot.isPresent();
        }
    }

    /** Who holds the working directory and the corpus root named here, which the caller reads from what it is about to use. */
    Holders holders(Optional<Path> workingDirectory, Optional<Path> corpusRoot) {
        Optional<String> working = workingDirectory
                .flatMap(folder -> heldByAnother(folder.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME)));
        Optional<String> root = corpusRoot
                .flatMap(corpus -> held(CorpusRootLockFile.in(corpusRootLockFolder, corpus)));
        return new Holders(working, root);
    }

    private static Optional<Path> folderOf(String named) {
        return named == null || named.isBlank() ? Optional.empty() : pathOf(named.stripTrailing());
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

    /**
     * The invocation the window watches (ADR-362 §8): its pid is the child's and its report is the child's only where it
     * says it started no earlier than {@code notBefore}, the instant taken before the process was started.
     */
    record Watched(long pid, Instant notBefore) {
    }

    /** The child this object started, empty where none was. */
    synchronized Optional<Watched> watchedChild() {
        return child == null ? Optional.empty() : Optional.of(new Watched(child.pid(), childStartInstant));
    }

    /**
     * The invocation another process runs in {@code workingDirectory}, from its holder line (ADR-278 §4), watched where
     * the line is held and is not the control panel's own; its report is its own where it started no earlier than the
     * line's {@code started} less a second, the line giving it to the second only.
     */
    Optional<Watched> watchedHolder(Optional<Path> workingDirectory) {
        try {
            if (workingDirectory.isEmpty()) {
                return Optional.empty();
            }
            HolderReading reading = HolderReading.read(workingDirectory.get().resolve(WorkingDirectoryWrite.LOCK_FILE_NAME));
            return reading.line().filter(line -> reading.held() && line.pid() != ProcessHandle.current().pid())
                    .map(line -> new Watched(line.pid(), line.started().minusSeconds(1)));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
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
     * Starts the invocation as ADR-302 §2 does, or gives the line that refuses it: the launch values' own refusal, a
     * folder the system cannot hold or a working directory that is no folder (ADR-331 §6), then the control panel's
     * own child, {@code vespera.lock}, the corpus-root lock. The file of launch values is
     * stored as it starts (ADR-292 §2); a line it gives is returned in {@code stored}.
     *
     * @return the refusal, empty where the child was started
     */
    synchronized Optional<String> start(Start kind, List<String> stored) {
        LaunchValues held = values;
        return start(kind, new Plan(held, held, Map.of(), null), stored);
    }

    /**
     * What the window starts an invocation with (ADR-344 §3, §4, ADR-345 §4): the values passed to the child, the values
     * kept in the launch values file as it starts, the presets passed as options of the command line, the label file,
     * null where none is named, and the destinations ticked, which only a publish passes.
     */
    record Plan(LaunchValues toPass, LaunchValues toKeep, Map<String, String> presets, Path labelFile,
            List<String> destinations) {

        Plan {
            destinations = List.copyOf(destinations);
        }

        /** A plan ticking no destination, as every start but a publish has. */
        Plan(LaunchValues toPass, LaunchValues toKeep, Map<String, String> presets, Path labelFile) {
            this(toPass, toKeep, presets, labelFile, List.of());
        }
    }

    /** The command {@code kind} starts with {@code plan}, or the refusal of its values (ADR-292 §7); the window shows its line. */
    static ChildCommand commandOf(ChildRuntime runtime, Start kind, Plan plan) {
        LaunchValues now = plan.toPass();
        ChildCommand command = switch (kind) {
            case RUN -> ChildCommand.run(runtime.java(), runtime.jar(), now);
            case LABEL -> ChildCommand.label(runtime.java(), runtime.jar(), false, now);
            case LABEL_AUTO -> ChildCommand.label(runtime.java(), runtime.jar(), true, now);
            case PUBLISH -> ChildCommand.publish(runtime.java(), runtime.jar(), now, plan.destinations());
        };
        command = command.withOptions(plan.presets());
        return kind == Start.LABEL && plan.labelFile() != null ? command.withLabelFile(plan.labelFile()) : command;
    }

    ChildRuntime runtime() {
        return runtime;
    }

    /** As {@link #start(Start, List)}, with the values, presets and label file of {@code plan}. */
    synchronized Optional<String> start(Start kind, Plan plan, List<String> stored) {
        return start(kind, plan, Optional.empty(), stored);
    }

    /**
     * As {@link #start(Start, Plan, List)}, giving the child {@code confluenceToken} where one is typed, in its
     * environment alone (ADR-359 §3). The token is held on this call's stack and nowhere else: not in {@code plan},
     * not in the launch values, not in {@link #childCommand()}.
     */
    synchronized Optional<String> start(Start kind, Plan plan, Optional<String> confluenceToken, List<String> stored) {
        if (controlPanelEnds) {
            return Optional.of(kind.name + " is not started: the control panel is ending");
        }
        LaunchValues held = plan.toKeep();
        LaunchValues now = plan.toPass();
        Optional<String> refusal = kind == Start.RUN ? now.refusalToRun() : now.refusalToLabel();
        if (refusal.isPresent()) {
            return refusal;
        }
        for (String[] field : new String[][] {{now.corpusRoot(), "the corpus root"},
                {now.workingDirectory(), "the working directory"}, {now.deliverableRoot(), "the output folder"}}) {
            if (field[0] != null && !field[0].isBlank() && pathOf(field[0].stripTrailing()).isEmpty()) {
                return Optional.of(kind.name + " is not started: " + field[1] + " is no path this system can hold");
            }
        }
        if (now.workingDirectory() != null && !now.workingDirectory().isBlank()
                && !Files.isDirectory(Path.of(now.workingDirectory().stripTrailing()))) {
            return Optional.of(kind.name + " is not started: the working directory " + now.workingDirectory()
                    + " is no folder that is there");
        }
        if (childRuns()) {
            return Optional.of(kind.name + " is not started while the invocation the control panel started runs");
        }
        Holders holders = holders(folderOf(now.workingDirectory()), folderOf(now.corpusRoot()));
        if (holders.workingDirectory().isPresent()) {
            return Optional.of(kind.name + " is not started: the working directory " + now.workingDirectory()
                    + " is held by another Vespera invocation (" + holders.workingDirectory().get() + ")");
        }
        if (holders.corpusRoot().isPresent()) {
            return Optional.of(kind.name + " is not started: the corpus root " + now.corpusRoot()
                    + " is held by another Vespera invocation (" + holders.corpusRoot().get() + ")");
        }
        ChildCommand command;
        try {
            command = commandOf(runtime, kind, plan);
            if (confluenceToken.isPresent()) {
                command = command.withConfluenceToken(confluenceToken.get());
            }
        } catch (RuntimeException notBuilt) {
            return Optional.of(kind.name + " is not started: " + (notBuilt.getMessage() == null
                    ? notBuilt.getClass().getSimpleName() : notBuilt.getMessage()));
        }
        // the values kept, so that a value left as a source started it is passed and never stored (ADR-344 §3)
        this.values = held;
        held.store(launchValuesFile, userName, stored::add);
        try {
            Instant before = Instant.now();
            child = Child.start(command.processBuilder());
            childStartInstant = before;
        } catch (IOException | RuntimeException notStarted) {
            return Optional.of(kind.name + " is not started: " + (notStarted.getMessage() == null
                    ? notStarted.getClass().getSimpleName() : notStarted.getMessage()));
        }
        childCommand = kind.command;
        ending = null;
        return Optional.empty();
    }
}
