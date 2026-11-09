package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The invocation report (ADR-362 section 3): {@code report.json} at the top of the working directory and its copy
 * {@code logs/report-<invocation id>.json}, the single source from which a window, a script or an agent reads how the
 * invocation in hand, or the last to end, stands. {@code vespera run} and {@code vespera label} write it; the job's
 * progress page hands it the reading it draws from at each of its writes.
 *
 * <p><b>It is built from typed values</b> as {@link InvocationAccount} is (ADR-198 section 2): an enumeration, a step
 * name, a count, an instant, a {@link Path} and a line of the jar's own printed whole. No method takes a
 * {@link Throwable}'s message or a document's field, so none can be written. It holds no rule: it decides no verdict,
 * cache key, cluster or text of the deliverable.
 *
 * <p>A write that fails fails nothing: it is warned of once, until a write succeeds again (ADR-265 section 4).
 * Every write is made under one lock, and none after the invocation has ended is a running one.
 */
final class InvocationReport {

    private static final Logger log = LoggerFactory.getLogger(InvocationReport.class);

    /** The format of {@code report.json}, raised by a change that removes, renames or retypes a field (ADR-362 section 3). */
    static final int FORMAT = 1;

    static final String FILE_NAME = "report.json";

    private static final String LOGS = "logs";

    private static final String COPY_PREFIX = "report-";

    /** An instant is written in UTC with {@code Z}, to the millisecond (ADR-362 section 3). */
    private static final DateTimeFormatter INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter ID = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    /** The job's steps in the order it runs them. */
    static final List<String> STEPS_IN_JOB_ORDER = List.of(
            StepNames.CENSUS,
            StepNames.BYTE_LEVEL_REDUCTION,
            StepNames.EXTRACTION,
            StepNames.CONTENT_CENSUS,
            StepNames.REDUNDANCY_SIGNATURE,
            StepNames.CONTENT_REDUNDANCY,
            StepNames.SEED_EXTRACTION,
            StepNames.SEED_CORPUS_COMPARISON,
            StepNames.EMBEDDING_SCORING,
            StepNames.TRANSLATION,
            StepNames.RELEVANCE_SCORING,
            StepNames.RELEVANCE_FLOOR,
            StepNames.CLUSTERING,
            StepNames.RELEVANCE_REPORT,
            StepNames.ARRANGEMENT,
            StepNames.GENERATION);

    /** The steps that record their completion under a run of each stage, in job order (ADR-269 section 5). */
    static final Map<String, List<String>> STEPS_OF_A_STAGE = Map.of(
            StageModules.BYTE_LEVEL_REDUCTION.stage(), List.of(StepNames.BYTE_LEVEL_REDUCTION),
            StageModules.EXTRACTION.stage(), List.of(StepNames.EXTRACTION),
            StageModules.CONTENT_CENSUS.stage(), List.of(StepNames.CONTENT_CENSUS),
            StageModules.CONTENT_REDUNDANCY.stage(), List.of(StepNames.REDUNDANCY_SIGNATURE, StepNames.CONTENT_REDUNDANCY),
            StageModules.SEED_MEASUREMENT.stage(), List.of(StepNames.SEED_EXTRACTION, StepNames.SEED_CORPUS_COMPARISON),
            StageModules.EMBEDDING_SCORING.stage(),
            List.of(StepNames.EMBEDDING_SCORING, StepNames.TRANSLATION, StepNames.RELEVANCE_SCORING, StepNames.CLUSTERING),
            StageModules.ARRANGEMENT.stage(), List.of(StepNames.ARRANGEMENT),
            StageModules.GENERATION.stage(), List.of(StepNames.GENERATION));

    /** The four legs of the timeline, each with its steps in job order, every step in exactly one (ADR-324 section 1). */
    static final Map<String, List<String>> LEGS = legs();

    private static Map<String, List<String>> legs() {
        Map<String, List<String>> legs = new LinkedHashMap<>();
        legs.put("deduplication", List.of(
                StepNames.CENSUS, StepNames.BYTE_LEVEL_REDUCTION, StepNames.EXTRACTION, StepNames.CONTENT_CENSUS,
                StepNames.REDUNDANCY_SIGNATURE, StepNames.CONTENT_REDUNDANCY));
        legs.put(StepNames.TRANSLATION, List.of(
                StepNames.SEED_EXTRACTION, StepNames.SEED_CORPUS_COMPARISON, StepNames.EMBEDDING_SCORING,
                StepNames.TRANSLATION));
        legs.put(StepNames.CLUSTERING, List.of(
                StepNames.RELEVANCE_SCORING, StepNames.RELEVANCE_FLOOR, StepNames.CLUSTERING, StepNames.RELEVANCE_REPORT));
        legs.put(StepNames.GENERATION, List.of(StepNames.ARRANGEMENT, StepNames.GENERATION));
        return Collections.unmodifiableMap(legs);
    }

    /** The stages the job numbers where it records no completion under a run of them (ADR-345 section 3). */
    private static final Map<String, String> NUMBERED = Map.of(
            StepNames.CENSUS, "0", StepNames.RELEVANCE_FLOOR, "5e", StepNames.RELEVANCE_REPORT, "5");

    /**
     * The twelve analysis reports, by the name of the page each writes (ADR-362 section 9); a report is named by its
     * page's name without the ending, which {@link #ANALYSIS_REPORTS} holds.
     */
    private static final List<String> ANALYSIS_PAGES = List.of(
            "format-mix.html", "extraction-failures.html", "extraction-survivors.html", "confidence-distribution.html",
            "word-count-distribution.html", "document-frequency.html", "seed-corpus-comparison.html",
            "translations.html", "relevance-labelling.html", "cluster-sizes.html", "arrangement.html",
            "cluster-page-sizes.html");

    /** The twelve analysis reports, by name. */
    static final List<String> ANALYSIS_REPORTS =
            ANALYSIS_PAGES.stream().map(page -> page.substring(0, page.length() - ".html".length())).toList();

    /** The analysis reports whose JSON is written, each with the format its writer writes (ADR-362 section 9); a ticket of step 5 adds its own. */
    static final Map<String, Integer> FORMAT_OF_A_REPORT = Map.ofEntries(
            Map.entry(FormatMixReport.NAME, FormatMixReport.FORMAT),
            Map.entry(ReviewListReport.NAME, 1),
            Map.entry(SurvivorListReport.NAME, 1),
            Map.entry(SeedCorpusComparisonReport.NAME, 1));

    /** The command {@code vespera run} begins as: written from a capital, as no literal of this class names a table (ADR-362 section 3). */
    static final String RUN = "Run".toLowerCase(Locale.ROOT);

    /** The key of the arrangement to approve in {@code next}, which is also a stage's name: written so that no literal names a stage. */
    private static final String ARRANGEMENT = "Arrangement".toLowerCase(Locale.ROOT);

    /** A JSON key that is also the name of a table, written so that this class names no table: the key is no statement. */
    private static final String WALK = "Walk".toLowerCase(Locale.ROOT);

    /** The analysis reports each profile value is read off, as the README's table of values names them. */
    private static final Map<String, List<String>> REPORTS_OF_A_KEY = Map.ofEntries(
            Map.entry("boilerplateDocumentFrequencyFloor", List.of("document-frequency.html")),
            Map.entry("relevanceScoreFloor",
                    List.of("relevance-labelling.html", "cluster-sizes.html", "seed-corpus-comparison.html")),
            Map.entry("degenerateOutputConfidenceFloor", List.of("confidence-distribution.html")),
            Map.entry("degenerateOutputWordFloor", List.of("word-count-distribution.html")),
            Map.entry("extractionAttempt", List.of("extraction-failures.html")),
            Map.entry("arrangementApproved", List.of("arrangement.html")),
            Map.entry("logTimestampShareFloor", List.of("format-mix.html")),
            Map.entry("codeNameEndings", List.of("format-mix.html")),
            Map.entry("outOfScopeNameEndings", List.of("format-mix.html")),
            Map.entry("configurationSettingShareFloor", List.of("format-mix.html")),
            Map.entry("preferredLanguages", List.of("translations.html")),
            Map.entry("translationSimilarityFloor", List.of("translations.html")),
            Map.entry("clusterPageByteCeiling", List.of("cluster-page-sizes.html")),
            Map.entry("clusterResolution", List.of("cluster-sizes.html")));

    /** Whether a display is present, asked of the system: a seam, so that a test answers it on any system. */
    @FunctionalInterface
    interface Display {
        DisplayPresence.Answer ask();
    }

    /** One step of the job at a write. */
    record Step(
            String name, String state, long read, long written, long skipped, Instant started, Instant ended,
            Long seconds, String progress, Long done, Long total) {}

    /** The step removing occurrences now or last (ADR-268). */
    record Removing(
            String step, long entered, long survivors, Map<String, Long> verdicts, Map<String, Double> perMinute) {}

    /** One chart, as the progress page's rules pick its series. */
    record Chart(String id, String title, String axis, List<Long> seconds, List<Line> lines) {}

    /** A line of a chart. */
    record Line(String name, double[] values) {}

    /** One run of the working directory's table of runs (ADR-269 section 5). */
    record RunRow(
            long walk, String stage, int ordinal, Integer stepsFinished, Integer stepsOf, String state,
            boolean thisInvocation, Map<String, Long> verdicts) {}

    /** The runs, read at the invocation's start or end: {@code read} is none, start, end or failed. */
    record Runs(String read, List<RunRow> rows) {}

    /** A failed step: its type and at most three causes, class names alone. */
    record Fault(String step, String type, List<String> causes) {}

    /** What the job holds at one write, which its progress page hands over. */
    record Reading(
            boolean last, String outcome, List<Step> steps, Map<String, Optional<String>> gateKeys, long databaseBytes,
            Removing removing, List<Chart> charts, Runs runs, Map<String, String> arrivedAt, List<Fault> faults) {}

    private final Path workingDirectory;
    private final Duration interval;
    private final Clock clock;
    private final Display display;
    private final String deliverableRoot;
    private final Object writing = new Object();
    private final String reportTemplate;

    // Guarded by writing: what one invocation holds, forgotten at the next start.
    private boolean begun;
    private String id;
    private Path copy;
    private String command;
    private long pid;
    private Instant started;
    private Instant ended;
    private String state;
    private String outcome;
    private Integer exitCode;
    private DisplayPresence.Answer displayAnswer;
    private Path corpusRoot;
    private final List<String> refusals = new ArrayList<>();
    private String closing;
    private Map<String, Object> next;
    private Map<String, Object> counts;
    private List<Map<String, Object>> answers;
    private Reading reading;
    private boolean warned;
    private boolean htmlWarned;
    private ReportHtml html;

    InvocationReport(Path workingDirectory, Duration interval, Clock clock, Display display, String deliverableRoot) {
        this(workingDirectory, interval, clock, display, deliverableRoot, "");
    }

    /** {@code reportTemplate} is {@code vespera.report.template}: the absolute path of the operator's template, blank where unset. */
    InvocationReport(
            Path workingDirectory, Duration interval, Clock clock, Display display, String deliverableRoot,
            String reportTemplate) {
        this.reportTemplate = reportTemplate;
        this.html = new ReportHtml(reportTemplate);
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.interval = interval;
        this.clock = clock;
        this.display = display;
        this.deliverableRoot = deliverableRoot == null ? "" : deliverableRoot;
    }

    /** The report's absolute, normalised path in {@code workingDirectory}. */
    static Path pathIn(Path workingDirectory) {
        return workingDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    // ---- the invocation's life

    /**
     * An invocation begins: its id is minted, its start taken and the display asked, once, and everything the last
     * invocation held is forgotten. Nothing is written until the invocation has something to say.
     */
    void begins(String commandLine) {
        synchronized (writing) {
            begun = true;
            command = commandLine;
            pid = ProcessHandle.current().pid();
            started = truncated(clock.instant());
            ended = null;
            state = "running";
            outcome = null;
            exitCode = null;
            corpusRoot = null;
            refusals.clear();
            closing = null;
            next = null;
            counts = null;
            answers = null;
            reading = null;
            warned = false;
            htmlWarned = false;
            html = new ReportHtml(reportTemplate);
            displayAnswer = askTheDisplay();
            mintTheId();
        }
    }

    /** A job starts: an invocation the command has not begun is begun here, and one that has ended is a new one. */
    void jobStarts() {
        synchronized (writing) {
            if (!begun || "ended".equals(state)) {
                begins(RUN);
            }
        }
    }

    /** The root {@code run} walks. */
    void walks(Path root) {
        synchronized (writing) {
            corpusRoot = root.toAbsolutePath().normalize();
        }
    }

    /** The job has handed its reading; written whole, unless the invocation has ended already. */
    void job(Reading held) {
        synchronized (writing) {
            if (!begun) {
                begins(RUN);
            }
            if (!held.last() && "ended".equals(state)) {
                return;
            }
            reading = held;
            if (held.last()) {
                state = "ended";
                ended = ended == null ? truncated(clock.instant()) : ended;
                outcome = held.outcome();
                exitCode = exitCode == null ? ("completed".equals(outcome) ? 0 : 1) : exitCode;
            }
            write();
        }
    }

    /** The invocation was refused after the lock was taken: one write, with the line the jar printed. */
    void refused(String line, int exit) {
        synchronized (writing) {
            if (!begun) {
                return;
            }
            refusals.add(line);
            closing = line;
            outcome = "refused";
            state = "ended";
            ended = truncated(clock.instant());
            exitCode = exit;
            write();
        }
    }

    /** {@code vespera run} ends: its exit code, and the closing line and what the file beside it says, where printed. */
    void runEnds(int exit, String closingLine, NextInvocationFile.Contents nextContents) {
        synchronized (writing) {
            if (!begun) {
                return;
            }
            if (!"ended".equals(state)) {
                state = "ended";
                ended = truncated(clock.instant());
                outcome = exit == 0 ? "completed" : "failed";
            }
            exitCode = exit;
            closing = closingLine;
            next = nextContents == null ? null : nextOf(nextContents);
            write();
        }
    }

    /** {@code vespera label} starts: the first write. */
    void labelStarts() {
        synchronized (writing) {
            write();
        }
    }

    /** {@code vespera label} ends with {@code outcome} (recorded, refused or failed) and the answers it counted. */
    void labelEnds(
            String howItEnded, Map<String, Integer> answered, int exit, String closingLine,
            NextInvocationFile.Contents nextContents) {
        synchronized (writing) {
            if (!begun) {
                return;
            }
            state = "ended";
            ended = truncated(clock.instant());
            outcome = howItEnded;
            exitCode = exit;
            answers = new ArrayList<>();
            answered.forEach((words, value) -> answers.add(object("words", words, "count", (long) value)));
            if (closingLine != null) {
                closing = closingLine;
            }
            next = nextContents == null ? null : nextOf(nextContents);
            write();
        }
    }

    /** The jar printed a refusal line of its own; kept whole, in the order printed. */
    void said(String refusalLine) {
        synchronized (writing) {
            if (begun) {
                refusals.add(refusalLine);
            }
        }
    }

    /** The account's counts, scoped to the runs this invocation arrived at (ADR-198 section 3). */
    void counted(Map<String, Object> held) {
        synchronized (writing) {
            counts = held;
            if (begun && "ended".equals(state)) {
                write();
            }
        }
    }

    // ---- minting

    private static Instant truncated(Instant instant) {
        return instant.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    /** Takes the id the copy is named for: the start to the second, with {@code -2} and on for one in the same second. */
    private void mintTheId() {
        String stamp = ID.format(started);
        Path logs = workingDirectory.resolve(LOGS);
        copy = null;
        try {
            Files.createDirectories(logs);
        } catch (IOException | RuntimeException e) {
            id = stamp;
            return;
        }
        for (int attempt = 1; attempt < 10_000; attempt++) {
            String candidate = stamp + (attempt == 1 ? "" : "-" + attempt);
            Path taken = logs.resolve(COPY_PREFIX + candidate + ".json");
            try {
                Files.writeString(taken, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                id = candidate;
                copy = taken;
                return;
            } catch (java.nio.file.FileAlreadyExistsException inUse) {
                // the next suffix
            } catch (IOException | RuntimeException unwritable) {
                break;
            }
        }
        id = stamp;
    }

    private DisplayPresence.Answer askTheDisplay() {
        if (System.getProperty(DisplayPresence.HEADLESS_PROPERTY) != null) {
            return new DisplayPresence.Answer(false, "headless", null);
        }
        DisplayPresence.Answer answer;
        try {
            answer = display.ask();
        } catch (RuntimeException | Error thrown) {
            answer = new DisplayPresence.Answer(false, "not-answered", thrown.getClass().getName());
        }
        if ("not-answered".equals(answer.reason())) {
            LoggerFactory.getLogger(DisplayPresence.class)
                    .warn("whether a display is present could not be asked: {}", answer.thrown());
        }
        return answer;
    }

    // ---- writing

    /** One write, under {@link #writing}: whole, into {@code .part} and moved into place, the copy the same text. */
    private void write() {
        try {
            Instant writtenAt = truncated(clock.instant());
            String text = Json.render(tree(writtenAt)) + "\n";
            if (!html.tried()) {
                html.trial(text);
                text = Json.render(tree(writtenAt)) + "\n";
            }
            String page = null;
            try {
                try {
                    page = html.draw(text);
                } catch (RuntimeException thrown) {
                    if (!html.namedDraws()) {
                        throw thrown;
                    }
                    html.fallBack(thrown);
                    text = Json.render(tree(writtenAt)) + "\n";
                    page = html.draw(text);
                }
            } catch (RuntimeException | StackOverflowError unDrawn) {
                if (!htmlWarned) {
                    htmlWarned = true;
                    log.warn("the report could not be written: {}", unDrawn.getClass().getName());
                }
            }
            put(workingDirectory.resolve(FILE_NAME), text);
            if (copy != null) {
                put(copy, text);
            }
            warned = false;
            if (page != null) {
                try {
                    put(ReportHtml.pathIn(workingDirectory), page);
                    htmlWarned = false;
                } catch (IOException | RuntimeException unwritable) {
                    deleteTheParts(ReportHtml.pathIn(workingDirectory));
                    if (!htmlWarned) {
                        htmlWarned = true;
                        log.warn("the report could not be written: {}", unwritable.getClass().getName());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            for (Path part : List.of(
                    workingDirectory.resolve(FILE_NAME + ".part"),
                    copy == null ? workingDirectory.resolve(FILE_NAME + ".part") : copy.resolveSibling(copy.getFileName() + ".part"))) {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException | RuntimeException ignored) {
                    // the next write overwrites it
                }
            }
            if (!warned) {
                warned = true;
                log.warn("the report could not be written: {}", e.getClass().getName());
            }
        }
    }

    private static void deleteTheParts(Path place) {
        try {
            Files.deleteIfExists(place.resolveSibling(place.getFileName() + ".part"));
        } catch (IOException | RuntimeException ignored) {
            // the next write overwrites it
        }
    }

    private static void put(Path place, String text) throws IOException {
        Path part = place.resolveSibling(place.getFileName() + ".part");
        Files.writeString(part, text, StandardCharsets.UTF_8);
        ReplacingMove.ATOMIC.intoPlace(part, place);
    }

    // ---- the tree

    private Map<String, Object> tree(Instant writtenAt) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("format", (long) FORMAT);
        report.put("vespera", InvocationReport.class.getPackage().getImplementationVersion());
        Map<String, Object> invocation = new LinkedHashMap<>();
        invocation.put("id", id);
        invocation.put("pid", pid);
        invocation.put("command", command);
        invocation.put("started", instant(started));
        invocation.put("ended", instant(ended));
        invocation.put("state", state);
        invocation.put("outcome", outcome);
        invocation.put("exitCode", exitCode == null ? null : (long) exitCode);
        invocation.put("writtenAt", instant(writtenAt));
        invocation.put("interval", Math.max(1L, (interval.toMillis() + 999) / 1000));
        report.put("invocation", invocation);
        report.put("display", object("present", displayAnswer.present(), "reason", displayAnswer.reason()));
        report.put("opened", null);
        Map<String, Object> places = new LinkedHashMap<>();
        places.put("workingDirectory", workingDirectory.toString());
        places.put("corpusRoot", corpusRoot == null ? null : corpusRoot.toString());
        places.put("deliverableRoot", deliverableRootDrawn());
        report.put("places", places);
        List<Step> steps = reading == null ? List.of() : reading.steps();
        Map<String, String> states = new LinkedHashMap<>();
        STEPS_IN_JOB_ORDER.forEach(step -> states.put(step, "not started"));
        steps.forEach(step -> states.put(step.name(), step.state()));
        report.put("steps", stepsTree(steps));
        report.put("legs", legsTree(states, reading != null && reading.last() || "ended".equals(state)));
        report.put("removing", reading == null ? null : removingTree(reading.removing()));
        report.put("database", object("bytes", reading == null ? databaseBytes() : reading.databaseBytes()));
        report.put("charts", reading == null ? List.of() : chartsTree(reading.charts()));
        report.put("runs", runsTree());
        report.put("arrivedAt", arrivedAtTree());
        report.put("gates", gatesTree(steps));
        report.put("refusals", new ArrayList<Object>(refusals));
        report.put("faults", faultsTree());
        report.put("counts", counts);
        report.put("label", "label".equals(commandName()) ? labelTree() : null);
        report.put("publish", null);
        report.put("closing", closing);
        report.put("next", next);
        report.put("analysisReports", analysisReportsTree());
        report.put("files", filesTree());
        report.put("template", object("named", html.named(), "used", html.used()));
        return report;
    }

    private String commandName() {
        return command == null ? "" : command.startsWith("label") ? "label" : command;
    }

    private static String instant(Instant instant) {
        return instant == null ? null : INSTANT.format(instant);
    }

    private List<Object> stepsTree(List<Step> steps) {
        List<Object> tree = new ArrayList<>();
        for (Step step : steps) {
            Map<String, Object> each = new LinkedHashMap<>();
            each.put("name", step.name());
            each.put("stage", stageOf(step.name()));
            each.put("leg", legOf(step.name()));
            each.put("state", step.state());
            each.put("read", step.read());
            each.put("written", step.written());
            each.put("skipped", step.skipped());
            each.put("started", instant(step.started()));
            each.put("ended", instant(step.ended()));
            each.put("seconds", step.seconds());
            Map<String, Object> progress = null;
            if (step.progress() != null) {
                progress = object("line", step.progress(), "done", step.done(), "total", step.total());
            }
            each.put("progress", progress);
            tree.add(each);
        }
        return tree;
    }

    /** The stage under whose runs the job records the step's completion, else the number the record gives it. */
    static String stageOf(String step) {
        return STEPS_OF_A_STAGE.entrySet().stream()
                .filter(stage -> stage.getValue().contains(step))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseGet(() -> NUMBERED.get(step));
    }

    private static String legOf(String step) {
        return LEGS.entrySet().stream()
                .filter(leg -> leg.getValue().contains(step))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** A leg's state, read off its steps' states at the write by the first rule that holds (ADR-324 section 3). */
    static String legState(List<String> steps, Map<String, String> states, boolean last) {
        List<String> of = steps.stream().map(step -> states.getOrDefault(step, "not started")).toList();
        if (of.stream().allMatch("not started"::equals)) {
            return "not started";
        }
        if (of.stream().allMatch("finished"::equals)) {
            return "finished";
        }
        if (of.contains("failed")) {
            return "failed";
        }
        if (of.contains("stopped at a gate")) {
            return "stopped at a gate";
        }
        if (of.contains("running")) {
            return "running";
        }
        return last ? "failed" : "running";
    }

    /** Whether a leg is open: it is running or failed, or holds the first step stopped at a gate (ADR-324 section 4). */
    static boolean legOpen(String state, List<String> steps, Map<String, String> states) {
        String firstGated = STEPS_IN_JOB_ORDER.stream()
                .filter(step -> "stopped at a gate".equals(states.get(step)))
                .findFirst()
                .orElse(null);
        return "running".equals(state) || "failed".equals(state) || (firstGated != null && steps.contains(firstGated));
    }

    private List<Object> legsTree(Map<String, String> states, boolean last) {
        List<Object> tree = new ArrayList<>();
        for (Map.Entry<String, List<String>> leg : LEGS.entrySet()) {
            String legState = legState(leg.getValue(), states, last);
            tree.add(object(
                    "name", leg.getKey(),
                    "state", legState,
                    "open", legOpen(legState, leg.getValue(), states)));
        }
        return tree;
    }

    private static Map<String, Object> removingTree(Removing removing) {
        if (removing == null) {
            return null;
        }
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("step", removing.step());
        tree.put("entered", removing.entered());
        tree.put("survivors", removing.survivors());
        tree.put("verdicts", new LinkedHashMap<String, Object>(removing.verdicts()));
        tree.put("perMinute", new LinkedHashMap<String, Object>(removing.perMinute()));
        return tree;
    }

    private long databaseBytes() {
        long bytes = 0;
        for (String name : List.of("vespera.db", "vespera.db-wal")) {
            try {
                bytes += Files.size(workingDirectory.resolve(name));
            } catch (IOException | RuntimeException absent) {
                // the log is there only while the database is open
            }
        }
        return bytes;
    }

    private static List<Object> chartsTree(List<Chart> charts) {
        List<Object> tree = new ArrayList<>();
        for (Chart chart : charts) {
            List<Object> lines = new ArrayList<>();
            for (Line line : chart.lines()) {
                List<Object> values = new ArrayList<>();
                for (double value : line.values()) {
                    values.add(Double.isFinite(value) ? (Object) value : (Object) 0.0);
                }
                lines.add(object("name", line.name(), "values", values));
            }
            tree.add(object(
                    "id", chart.id(), "title", chart.title(), "axis", chart.axis(),
                    "seconds", new ArrayList<Object>(chart.seconds()), "lines", lines));
        }
        return tree;
    }

    private Map<String, Object> runsTree() {
        Runs runs = reading == null ? new Runs("none", List.of()) : reading.runs();
        List<Object> rows = new ArrayList<>();
        for (RunRow row : runs.rows()) {
            Map<String, Object> each = new LinkedHashMap<>();
            each.put(WALK, row.walk());
            each.put("stage", row.stage());
            each.put("ordinal", (long) row.ordinal());
            each.put("stepsFinished", row.stepsFinished() == null ? null : (long) row.stepsFinished());
            each.put("stepsOf", row.stepsOf() == null ? null : (long) row.stepsOf());
            each.put("state", row.state());
            each.put("thisInvocation", row.thisInvocation());
            each.put("verdicts", new LinkedHashMap<String, Object>(row.verdicts()));
            rows.add(each);
        }
        return object("read", runs.read(), "rows", rows);
    }

    private List<Object> arrivedAtTree() {
        List<Object> tree = new ArrayList<>();
        if (reading != null) {
            reading.arrivedAt().forEach((stage, run) -> tree.add(object("stage", stage, RUN, run)));
        }
        return tree;
    }

    private List<Object> gatesTree(List<Step> steps) {
        List<Object> tree = new ArrayList<>();
        for (Step step : steps) {
            if (!"stopped at a gate".equals(step.state())) {
                continue;
            }
            String key = reading.gateKeys().getOrDefault(step.name(), Optional.empty()).orElse(null);
            List<Object> reports = new ArrayList<>(key == null ? List.of() : REPORTS_OF_A_KEY.getOrDefault(key, List.of()));
            tree.add(object("step", step.name(), "key", key, "analysisReports", reports));
        }
        return tree;
    }

    private List<Object> faultsTree() {
        List<Object> tree = new ArrayList<>();
        if (reading != null) {
            for (Fault fault : reading.faults()) {
                tree.add(object("step", fault.step(), "type", fault.type(), "causes", new ArrayList<Object>(fault.causes())));
            }
        }
        return tree;
    }

    private Map<String, Object> labelTree() {
        return object("answers", answers == null ? new ArrayList<Object>() : new ArrayList<Object>(answers));
    }

    private static Map<String, Object> nextOf(NextInvocationFile.Contents contents) {
        return object(
                "invocation", contents.invocation().written(),
                "wants", new ArrayList<Object>(contents.wants()),
                "answer", contents.answer(),
                ARRANGEMENT, contents.arrangement().orElse(null));
    }

    private List<Object> analysisReportsTree() {
        List<Object> tree = new ArrayList<>();
        for (String name : ANALYSIS_REPORTS) {
            Path html = workingDirectory.resolve(name + ".html");
            Path json = workingDirectory.resolve(name + ".json");
            tree.add(object(
                    "name", name,
                    "html", html.toString(),
                    "json", json.toString(),
                    "written", Files.isRegularFile(html),
                    "headline", headlineOf(name, workingDirectory.resolve(name + ".json"))));
        }
        return tree;
    }

    /**
     * The headline the report's JSON holds, copied and no row of it (ADR-362 section 9), where the report's writer has
     * landed and the file is of the format it writes; {@code null} otherwise, as for a report none writes yet.
     */
    private static List<Object> headlineOf(String name, Path json) {
        Integer format = FORMAT_OF_A_REPORT.get(name);
        return format == null ? null : AnalysisReportJson.headlineOf(json, name, format);
    }

    /** The root the tree is drawn at, where this invocation drew one. */
    private String deliverableRootDrawn() {
        if (reading == null || !reading.steps().stream()
                .anyMatch(step -> StepNames.GENERATION.equals(step.name()) && "finished".equals(step.state()))) {
            return null;
        }
        return DeliverableRoot.of(deliverableRoot).folderOf(workingDirectory).toAbsolutePath().normalize().toString();
    }

    /** Each file of the working directory's top and its {@code logs} folder this invocation wrote or rewrote. */
    private List<Object> filesTree() {
        List<Object> tree = new ArrayList<>();
        for (Path folder : List.of(workingDirectory, workingDirectory.resolve(LOGS))) {
            try (Stream<Path> inside = Files.list(folder)) {
                for (Path file : inside.sorted().toList()) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".part") || !Files.isRegularFile(file)
                            || Files.getLastModifiedTime(file).toInstant().isBefore(started)) {
                        continue;
                    }
                    tree.add(object("role", roleOf(name, folder.equals(workingDirectory)), "path", file.toString()));
                }
            } catch (IOException | RuntimeException unreadable) {
                // a folder that cannot be listed lists nothing
            }
        }
        return tree;
    }

    private static String roleOf(String name, boolean top) {
        if (top && (name.equals(FILE_NAME) || name.equals(ReportHtml.FILE_NAME))) {
            return "report";
        }
        if (!top && name.startsWith(COPY_PREFIX)) {
            return "report-copy";
        }
        if (name.startsWith("progress") && name.endsWith(".html")) {
            return "progress-page";
        }
        if (name.startsWith(InvocationAccount.FILE_PREFIX)) {
            return "invocation-account";
        }
        if (name.startsWith("vespera.db")) {
            return "database";
        }
        if (name.endsWith(".log") || name.contains(".log.")) {
            return "log";
        }
        if (name.equals("profile.yaml")) {
            return "profile";
        }
        if (name.equals(NextInvocationFile.FILE_NAME)) {
            return "next-invocation";
        }
        if (name.equals("effective-values.properties")) {
            return "effective-values";
        }
        if (name.equals("relevance-labels.yaml")) {
            return "label-file";
        }
        for (String report : ANALYSIS_REPORTS) {
            if (name.equals(report + ".html") || name.equals(report + ".json")) {
                return "analysis-report";
            }
        }
        return "other";
    }

    /** The roles a file in {@code files} has, a closed list the schema holds. */
    static final List<String> FILE_ROLES = List.of(
            "report", "report-copy", "progress-page", "invocation-account", "database", "log", "profile",
            "next-invocation", "effective-values", "label-file", "analysis-report", "other");

    // ---- small helpers

    /** An object of the pairs given, in order. */
    static Map<String, Object> object(Object... pairs) {
        Map<String, Object> object = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            object.put((String) pairs[i], pairs[i + 1]);
        }
        return object;
    }

    /** JSON text of a tree of maps, lists, strings, numbers, booleans and nulls. */
    private static final class Json {

        private Json() {}

        static String render(Object value) {
            StringBuilder out = new StringBuilder();
            write(out, value);
            return out.toString();
        }

        private static void write(StringBuilder out, Object value) {
            switch (value) {
                case null -> out.append("null");
                case String text -> quoted(out, text);
                case Boolean flag -> out.append(flag);
                case Double number -> out.append(Double.isFinite(number) ? number.toString() : "0");
                case Float number -> out.append(Float.isFinite(number) ? number.toString() : "0");
                case Number number -> out.append(number.longValue());
                case Map<?, ?> map -> {
                    out.append('{');
                    boolean first = true;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        out.append(first ? "" : ",");
                        first = false;
                        quoted(out, String.valueOf(entry.getKey()));
                        out.append(':');
                        write(out, entry.getValue());
                    }
                    out.append('}');
                }
                case List<?> list -> {
                    out.append('[');
                    for (int i = 0; i < list.size(); i++) {
                        out.append(i == 0 ? "" : ",");
                        write(out, list.get(i));
                    }
                    out.append(']');
                }
                default -> throw new IllegalArgumentException("not a JSON value: " + value.getClass().getName());
            }
        }

        private static void quoted(StringBuilder out, String text) {
            out.append('"');
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }
    }
}
