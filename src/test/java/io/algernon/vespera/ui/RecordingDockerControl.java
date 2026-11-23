package io.algernon.vespera.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The window's {@link DockerControl} as a test answers it (ADR-390 §4, §5; ADR-430 §12): no Docker command is run and
 * nothing is asked over HTTP. Each start, shut-down and stop of the containers is kept in order, with the launch values
 * it was given, where it takes them, and whether the window's own child ran as it was made, and is answered with what
 * the test set for its kind. Each check is kept apart, with the models it was asked about and when it was asked, and is
 * answered with the check the test set last, every row OK at this machine's addresses until it sets one. Each download
 * is kept as a {@link ScriptedDownload}, with the stalled-at-most bound the window gave it, which the test drives: it
 * says how far it has got, that it is done, or that its connection was lost, and it records whether the window
 * cancelled it. A start, a shut-down and a check can each be held until the test releases it; a start and a shut-down
 * each tell the lines the test set for them before they are held, as a start and a shut-down tell each line they log
 * (ADR-437 §3). A refusal is read for the graphics card, an install answered and the card checked as the test scripts
 * them, each install kept with the password it was given and telling the lines the test set for it, and each check of
 * the card kept with its launch values (ADR-444 §5, §8, §10); the starts may answer in turn.
 */
final class RecordingDockerControl implements DockerControl {

    /** One call: {@code start}, {@code shut-down}, {@code stop-containers} or {@code install}, its values, and whether a child ran then. */
    record Call(String kind, Optional<LaunchValues> values, boolean childRan) {
    }

    /** One check: the launch values and the models it was asked about, and when it was asked. */
    record Checked(LaunchValues values, Models models, Instant at) {
    }

    /** Docling's and Ollama's addresses where the launch values name none, as the jar ships them. */
    static final String DOCLING_HERE = "http://localhost:5001";

    static final String OLLAMA_HERE = "http://localhost:11434";

    /** Every start, shut-down and stop of the containers, in order. */
    final List<Call> calls = new CopyOnWriteArrayList<>();

    /** Every check, in order. */
    final List<Checked> checks = new CopyOnWriteArrayList<>();

    /** Every download the window asked for, in order. */
    final List<ScriptedDownload> downloads = new CopyOnWriteArrayList<>();

    /** When each start and each shut-down returned, in order. */
    final List<Instant> startsReturned = new CopyOnWriteArrayList<>();

    final List<Instant> shutDownsReturned = new CopyOnWriteArrayList<>();

    private final Said startSays;
    private final Said shutDownSays;
    /** Whether the window was shown at each stop of the containers, in order; empty where it could not be asked. */
    final List<Optional<Boolean>> shownAtEachStop = new CopyOnWriteArrayList<>();

    private volatile BooleanSupplier childRuns = () -> false;
    private volatile Supplier<Optional<Boolean>> windowShown = Optional::empty;
    private volatile DockerCheck checkSays = everyRow(DockerCheck.Mark.OK, DockerCheck.Mark.OK, DockerCheck.Mark.OK,
            DockerCheck.Mark.OK, DockerCheck.Mark.OK);
    private volatile DockerCheck checkAfterAStartSays;
    private volatile CountDownLatch startHeldUntil = new CountDownLatch(0);
    private volatile CountDownLatch shutDownHeldUntil = new CountDownLatch(0);
    private volatile CountDownLatch checkHeldUntil = new CountDownLatch(0);
    private volatile List<String> startTells = List.of();
    private volatile List<String> shutDownTells = List.of();

    /** What the starts answer in turn, the last for every start after; empty where {@link #startSays} answers each. */
    private final List<Said> startsInTurn = new CopyOnWriteArrayList<>();

    private volatile java.util.function.Function<String, Optional<CardRefused>> cardRefusedSays = refusal -> Optional.empty();

    private volatile Said installSays = new Said(true, "");

    private volatile List<String> installTells = List.of();

    private volatile CardCheck graphicsCardSays = new CardCheck(Optional.empty(), List.of());

    /**
     * What each install was given (ADR-444 §8): the password's characters as they were when it was called, copied, or
     * null where it was given none.
     */
    final List<String> installsGiven = new CopyOnWriteArrayList<>();

    /** Every check of the graphics card, with the launch values it was given, in order (ADR-444 §5). */
    final List<LaunchValues> cardChecks = new CopyOnWriteArrayList<>();

    /** Answers a start with {@code startSays}, a shut-down with {@code shutDownSays}, and a stop of the containers as done. */
    RecordingDockerControl(Said startSays, Said shutDownSays) {
        this.startSays = startSays;
        this.shutDownSays = shutDownSays;
    }

    /** Answers a start and a shut-down as done, saying nothing. */
    RecordingDockerControl() {
        this(new Said(true, ""), new Said(true, ""));
    }

    /** Reads whether a child of the window runs from {@code state}, at each call. */
    RecordingDockerControl watching(ControlPanelState state) {
        childRuns = state::childRuns;
        return this;
    }

    /** Asks {@code shown} whether the window is shown, at each stop of the containers, as the stop is called. */
    RecordingDockerControl askingWhetherShown(Supplier<Optional<Boolean>> shown) {
        windowShown = shown;
        return this;
    }

    /** From now on, every check answers {@code check}. */
    RecordingDockerControl checkAnswering(DockerCheck check) {
        checkSays = check;
        return this;
    }

    /** Once a start has returned, every check answers {@code check}, as containers a start brought up do. */
    RecordingDockerControl afterAStartCheckAnswering(DockerCheck check) {
        checkAfterAStartSays = check;
        return this;
    }

    /** Holds every start until {@code released} counts down, as a start that waits for the containers does. */
    RecordingDockerControl holdingStartsUntil(CountDownLatch released) {
        startHeldUntil = released;
        return this;
    }

    /** Holds every shut-down until {@code released} counts down, as a shut-down that waits for its commands does. */
    RecordingDockerControl holdingShutDownsUntil(CountDownLatch released) {
        shutDownHeldUntil = released;
        return this;
    }

    /**
     * Holds every check, once it is kept in {@link #checks}, until {@code released} counts down, as a check that waits on
     * a command or a resolver does: so that a test reads the window before its first check has come back.
     */
    RecordingDockerControl holdingChecksUntil(CountDownLatch released) {
        checkHeldUntil = released;
        return this;
    }

    /** The starts answer {@code saids} in turn, the last answering every start after it. */
    RecordingDockerControl startsAnswering(Said... saids) {
        startsInTurn.clear();
        startsInTurn.addAll(List.of(saids));
        return this;
    }

    /** A refusal is read for the graphics card by {@code reading} (ADR-444 §5). */
    RecordingDockerControl cardRefusedReading(java.util.function.Function<String, Optional<CardRefused>> reading) {
        cardRefusedSays = reading;
        return this;
    }

    /** Every install answers {@code said}. */
    RecordingDockerControl installAnswering(Said said) {
        installSays = said;
        return this;
    }

    /** Every install tells {@code lines}, in order, as an install tells each line it logs (ADR-444 §6, ADR-437 §3). */
    RecordingDockerControl tellingOnEachInstall(List<String> lines) {
        installTells = List.copyOf(lines);
        return this;
    }

    /** Every check of the graphics card answers {@code check}. */
    RecordingDockerControl graphicsCardAnswering(CardCheck check) {
        graphicsCardSays = check;
        return this;
    }

    @Override
    public Optional<CardRefused> cardRefused(String refusal) {
        return cardRefusedSays.apply(refusal);
    }

    @Override
    public Said installTheNvidiaToolkit(char[] sudoPassword, Consumer<String> told) {
        installsGiven.add(sudoPassword == null ? null : new String(sudoPassword));
        calls.add(new Call("install", Optional.empty(), childRuns.getAsBoolean()));
        installTells.forEach(told);
        return installSays;
    }

    @Override
    public CardCheck graphicsCard(LaunchValues values) {
        cardChecks.add(values);
        return graphicsCardSays;
    }

    /** The calls of {@code kind}, in order. */
    List<Call> of(String kind) {
        return calls.stream().filter(call -> call.kind().equals(kind)).toList();
    }

    /** The checks asked after {@code instant}. */
    List<Checked> checksAfter(Instant instant) {
        return checks.stream().filter(check -> check.at().isAfter(instant)).toList();
    }

    /** Every start tells {@code lines}, in order, before it is held, as a start tells each line it logs (ADR-437 §3). */
    RecordingDockerControl tellingOnEachStart(List<String> lines) {
        startTells = List.copyOf(lines);
        return this;
    }

    /** Every shut-down tells {@code lines}, in order, before it is held, as a shut-down tells each line it logs (ADR-437 §3). */
    RecordingDockerControl tellingOnEachShutDown(List<String> lines) {
        shutDownTells = List.copyOf(lines);
        return this;
    }

    @Override
    public Said start(LaunchValues values, Consumer<String> told) {
        calls.add(new Call("start", Optional.of(values), childRuns.getAsBoolean()));
        startTells.forEach(told);
        try {
            startHeldUntil.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (checkAfterAStartSays != null) {
            checkSays = checkAfterAStartSays;
        }
        startsReturned.add(Instant.now());
        if (!startsInTurn.isEmpty()) {
            return startsInTurn.size() == 1 ? startsInTurn.getFirst() : startsInTurn.removeFirst();
        }
        return startSays;
    }

    @Override
    public Said shutDown(Consumer<String> told) {
        calls.add(new Call("shut-down", Optional.empty(), childRuns.getAsBoolean()));
        shutDownTells.forEach(told);
        awaitQuietly(shutDownHeldUntil);
        shutDownsReturned.add(Instant.now());
        return shutDownSays;
    }

    @Override
    public Said stopTheContainers(LaunchValues values) {
        shownAtEachStop.add(windowShown.get());
        calls.add(new Call("stop-containers", Optional.of(values), childRuns.getAsBoolean()));
        return new Said(true, "");
    }

    @Override
    public DockerCheck check(LaunchValues values, Models models) {
        checks.add(new Checked(values, models, Instant.now()));
        awaitQuietly(checkHeldUntil);
        return checkSays;
    }

    /** Waits for {@code released}, keeping the thread's interrupt where it is interrupted. */
    private static void awaitQuietly(CountDownLatch released) {
        try {
            released.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Download download(LaunchValues values, String model, Duration stalledAtMost, Consumer<Downloaded> told) {
        ScriptedDownload download = new ScriptedDownload(values, model, stalledAtMost, told);
        downloads.add(download);
        return download;
    }

    /** A check of the five rows, both containers on this machine at the jar's own addresses. */
    static DockerCheck everyRow(DockerCheck.Mark daemon, DockerCheck.Mark docling, DockerCheck.Mark ollama,
            DockerCheck.Mark embeddingModel, DockerCheck.Mark generativeModel) {
        return new DockerCheck(daemon,
                new DockerCheck.Service(docling, DockerCheck.Where.THIS_MACHINE, DOCLING_HERE),
                new DockerCheck.Service(ollama, DockerCheck.Where.THIS_MACHINE, OLLAMA_HERE),
                embeddingModel, generativeModel);
    }

    /** A check of both containers on another machine, {@code docling} and {@code ollama} their addresses there. */
    static DockerCheck bothElsewhere(String docling, DockerCheck.Mark doclingMark, String ollama,
            DockerCheck.Mark ollamaMark, DockerCheck.Mark embeddingModel, DockerCheck.Mark generativeModel) {
        return new DockerCheck(DockerCheck.Mark.NOT_ASKED,
                new DockerCheck.Service(doclingMark, DockerCheck.Where.ANOTHER_MACHINE, docling),
                new DockerCheck.Service(ollamaMark, DockerCheck.Where.ANOTHER_MACHINE, ollama),
                embeddingModel, generativeModel);
    }

    /**
     * A download the window asked for, which the test drives: each call tells the window, on the test's thread, as the
     * window's own download tells it on the thread it reads Ollama's answer on.
     */
    static final class ScriptedDownload implements Download {

        final LaunchValues values;
        final String model;
        /** How long the window said the download may stand still before it is lost (ADR-430 §7). */
        final Duration stalledAtMost;
        private final Consumer<Downloaded> told;
        private volatile boolean cancelled;
        private volatile long completed;
        private volatile long total;

        ScriptedDownload(LaunchValues values, String model, Duration stalledAtMost, Consumer<Downloaded> told) {
            this.values = values;
            this.model = model;
            this.stalledAtMost = stalledAtMost;
            this.told = told;
        }

        /** Ollama has reported {@code completedBytes} of {@code totalBytes}. */
        void progress(long completedBytes, long totalBytes) {
            completed = completedBytes;
            total = totalBytes;
            told.accept(new Downloaded(Downloaded.State.DOWNLOADING, completedBytes, totalBytes, ""));
        }

        /** Ollama has the whole of it. */
        void done() {
            completed = total;
            told.accept(new Downloaded(Downloaded.State.DONE, total, total, ""));
        }

        /** The connection to Ollama was lost, for {@code reason}. */
        void lost(String reason) {
            told.accept(new Downloaded(Downloaded.State.LOST, completed, total, reason));
        }

        boolean cancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
