package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.publish.confluence.ConfluencePlugin;
import io.algernon.vespera.publish.confluence.Publisher;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.Runs;
import io.algernon.vespera.engine.corpus.CodeSetFolders;
import io.algernon.vespera.engine.profile.FoldersValue;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.TextValue;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The command surface (ADR-047, ADR-054, narrowed by ADR-101, widened by ADR-308 §5): {@code vespera run <root>},
 * {@code vespera label} and {@code vespera publish}.
 *
 * <p>One command per act, because they are not the same act. A run is unattended and never blocks
 * (ADR-047), and it now runs the cascade to its end (ADR-101). Label ingestion is the deliberate
 * act, and joined the surface for that reason (#105): a person invokes it having just finished
 * labelling, it reads a file they authored, and it mints no run, so folding it into the run would
 * weld a deliberate act onto an unattended pass.
 *
 * <p>{@code run} takes one argument, the corpus root. The root is the argument, and
 * {@code vespera.corpus-root} answers only an invocation that names none (ADR-066). It ships empty in
 * {@code application.yaml}, which counts as unset; a machine sets it in {@code application-local.yaml},
 * the environment or on the command line, never in the committed file. Where the database and the profile live is operator configuration rather than something
 * derived from the root (ADR-054), so it is {@code vespera.working-dir} in the same file, overridden
 * per invocation with {@code --db-dir=<path>} — on either command, since each opens that directory
 * ({@link WorkingDirectoryOption}, #310).
 */
@Component
@Command(
        name = "vespera",
        mixinStandardHelpOptions = true,
        subcommands = {
            VesperaCommand.Run.class,
            VesperaCommand.Label.class,
            VesperaCommand.Publish.class,
            VesperaCommand.Serve.class
        },
        description = "Curates a local archive into a publication-ready knowledge base.")
public class VesperaCommand implements Callable<Integer> {

    private final Run run;
    private final Label label;
    private final Publish publish;

    public VesperaCommand(Run run, Label label, Publish publish) {
        this.run = run;
        this.label = label;
        this.publish = publish;
    }

    /** The model {@link #commandLine()} built, subcommands and all, injected by picocli on parsing. */
    @Spec
    private CommandSpec spec;

    /**
     * Bare {@code vespera} names no act, so it prints what the acts are.
     *
     * <p>It prints from the model already built rather than building another from this object. A
     * second model would be built with picocli's default factory, which cannot construct the
     * subcommand beans, and reading {@code --db-dir} out of {@link WorkingDirectoryOption} needs the
     * subcommand instance to exist.
     */
    @Override
    public Integer call() {
        PrintWriter usage = OperatorLines.out();
        spec.commandLine().usage(usage);
        usage.flush();
        return CommandLine.ExitCode.USAGE;
    }

    /**
     * The command tree, built out of the beans rather than out of new objects.
     *
     * <p>picocli would otherwise instantiate each subcommand itself, and {@code run} needs the job
     * and the job operator injected into it. The factory is what hands it the beans instead.
     *
     * <p>The subcommands are singletons and this builds a fresh {@code CommandLine} over them every
     * invocation, so each one starts by forgetting what the last one parsed. picocli reads a field's
     * current value as that argument's <em>initial</em> value when the model is built, and restores
     * it for an argument the operator did not give — so without the reset, a second {@code run}
     * naming no root would silently walk the first one's root instead of falling back to
     * configuration (ADR-066), and report it as named on the command line. One process per
     * invocation is the normal case and never reaches this; the tests and any embedding of
     * {@link VesperaCli} do.
     */
    CommandLine commandLine() {
        run.forgetPreviousInvocation();
        label.forgetPreviousInvocation();
        publish.forgetPreviousInvocation();
        CommandLine commandLine = new CommandLine(this, new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> type) throws Exception {
                if (type.isInstance(run)) {
                    return type.cast(run);
                }
                if (type.isInstance(label)) {
                    return type.cast(label);
                }
                if (type.isInstance(publish)) {
                    return type.cast(publish);
                }
                return CommandLine.defaultFactory().create(type);
            }
        });
        // Picocli's own refusals and usage are logged through the operator logger (ADR-364 section 4), a line at a time.
        commandLine.setOut(OperatorLines.out());
        commandLine.setErr(OperatorLines.err());
        return commandLine;
    }

    /**
     * Serves the website folder of the working directory on loopback (ADR-333 §8). An entry of the tree so that help
     * lists it and so that {@link ServeCommand} parses the same {@code --db-dir} mixin; it is never executed through
     * Spring, since {@code VesperaApplication.main} hands {@code serve} to {@link ServeCommand} before any context
     * exists.
     */
    @Command(name = ServeCommand.NAME, description = "Serves the website folder of the working directory at"
            + " 127.0.0.1, until this process ends. Takes no lock and opens no database.")
    public static class Serve implements Callable<Integer> {

        @Mixin
        private WorkingDirectoryOption databaseDirectory = new WorkingDirectoryOption();

        /** The option picocli parsed into this command, which {@link ServeCommand} asks for its refusal. */
        WorkingDirectoryOption databaseDirectory() {
            return databaseDirectory;
        }

        @Override
        public Integer call() {
            OperatorLines.warn("vespera serve is started by the jar's main before Spring, and is not run through it.");
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    /** Walks a corpus and takes it as far as the next missing value: the whole job, each step behind its own gate (ADR-101). */
    @Component
    @Command(name = "run", description = "Walks a corpus and takes it as far as the next missing value.")
    public static class Run implements Callable<Integer> {

        /** The root an invocation that names none falls back to (ADR-066). Ships unset. */
        public static final String ROOT_PROPERTY = "vespera.corpus-root";

        /** The folder the deliverable is drawn at when the operator names one (ADR-276 §1). Ships unset. */
        public static final String DELIVERABLE_ROOT_PROPERTY = "vespera.deliverable-root";

        private static final Logger log = LoggerFactory.getLogger(Run.class);

        private final JobOperator jobOperator;
        private final Job vesperaJob;
        private final NextAction nextAction;
        private final Path workingDirectoryInUse;
        private final String configuredRoot;
        private final String deliverableRoot;
        private final ProfileStore profileStore;
        private CorpusRootLock corpusRootLock;
        private SidecarStarter sidecarStarter;
        private EffectiveValuesFile effectiveValues;
        private Presets presets;
        private ModelsAtTheStart modelsAtTheStart;
        private InvocationReport report;
        private String[] arguments = new String[0];

        @Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "<root>",
                description = "The corpus root to walk. Falls back to " + ROOT_PROPERTY
                        + " when omitted: set in application-local.yaml, as VESPERA_CORPUS_ROOT, or with"
                        + " --" + ROOT_PROPERTY + "=<path>.")
        private Path root;

        @Mixin
        private WorkingDirectoryOption databaseDirectory = new WorkingDirectoryOption();

        Run(
                JobOperator jobOperator,
                Job vesperaJob,
                NextAction nextAction,
                @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectoryInUse,
                @Value("${" + ROOT_PROPERTY + ":}") String configuredRoot) {
            this(jobOperator, vesperaJob, nextAction, workingDirectoryInUse, configuredRoot, "", null);
        }

        /** As above, naming the deliverable root and the profile whose seed folder it may not lie over (ADR-276 §2). */
        @Autowired
        Run(
                JobOperator jobOperator,
                Job vesperaJob,
                NextAction nextAction,
                @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectoryInUse,
                @Value("${" + ROOT_PROPERTY + ":}") String configuredRoot,
                @Value("${" + DELIVERABLE_ROOT_PROPERTY + ":}") String deliverableRoot,
                ProfileStore profileStore) {
            this.deliverableRoot = deliverableRoot;
            this.profileStore = profileStore;
            this.jobOperator = jobOperator;
            this.vesperaJob = vesperaJob;
            this.nextAction = nextAction;
            this.workingDirectoryInUse = workingDirectoryInUse;
            this.configuredRoot = configuredRoot;
        }

        /**
         * Drops what a previous invocation parsed, before the model that would inherit it is built.
         *
         * <p>Every parsed argument belongs to one invocation, and this bean outlives them all. Only
         * the two fields picocli writes are cleared; the injected ones are configuration, which does
         * not change between invocations of the same process. The option is cleared by swapping the
         * whole mixin for a fresh one, which is the instance picocli then parses into.
         */
        void forgetPreviousInvocation() {
            root = null;
            databaseDirectory = new WorkingDirectoryOption();
        }

        /**
         * The corpus-root lock and the arguments it records, provided only by {@code VesperaApplication.main}
         * (ADR-279 §7): a context built by Spring's test support has no lock, and this is then not called.
         */
        @Autowired(required = false)
        void holdCorpusRootWith(CorpusRootLock corpusRootLock, ApplicationArguments arguments) {
            this.corpusRootLock = corpusRootLock;
            this.arguments = arguments.getSourceArgs();
        }

        /**
         * The starter of the sidecars, provided only by {@code VesperaApplication.main} (ADR-323 §1): a context built
         * by Spring's test support has none, and this is then not called.
         */
        @Autowired(required = false)
        void startSidecarsWith(SidecarStarter sidecarStarter) {
            this.sidecarStarter = sidecarStarter;
        }

        /**
         * What the jar would use where the profile names nothing is written for the control panel (ADR-330 §1); a
         * command built with no environment, as some tests build it, writes none.
         */
        @Autowired(required = false)
        void sayWhatLiesBeneathTheProfileWith(Environment environment) {
            this.effectiveValues = new EffectiveValuesFile(environment, workingDirectoryInUse);
        }

        /**
         * The presets a run is minted under, checked at the start (ADR-330 §9); a command built without them, as some
         * tests build it, checks none.
         */
        @Autowired(required = false)
        void refusePresetsNoRunCanBeMadeUnderWith(Presets presets) {
            this.presets = presets;
        }

        /**
         * The reading of {@code modelsRunOn} made at the start (ADR-384 §2); a command built without it, as some tests
         * build it, reads none.
         */
        @Autowired(required = false)
        void refuseModelsNotLocalWith(ModelsAtTheStart modelsAtTheStart) {
            this.modelsAtTheStart = modelsAtTheStart;
        }

        /** The report this invocation writes, provided by the application; a command built without one writes none (ADR-362 section 3). */
        @Autowired(required = false)
        void writeTheReportWith(InvocationReport report) {
            this.report = report;
        }

        /** A refusal after the working-directory lock: its line is printed whole, and the report says it, once. */
        private Integer refuse(String line, int exitCode) {
            OperatorLines.warn(line);
            if (report != null) {
                report.refused(line, exitCode);
            }
            logTheReportLine();
            return exitCode;
        }

        @Override
        public Integer call() throws Exception {
            if (report != null) {
                report.begins(InvocationReport.RUN);
            }
            String misnamedDatabaseDirectory = databaseDirectory.disagreement(workingDirectoryInUse);
            if (misnamedDatabaseDirectory != null) {
                return refuse(misnamedDatabaseDirectory, CommandLine.ExitCode.SOFTWARE);
            }
            Path corpusRoot = rootToWalk();
            if (corpusRoot == null) {
                return refuse("vespera run named no root and " + ROOT_PROPERTY + " is not set: give the root"
                        + " as the argument -- vespera run <root> -- or set " + ROOT_PROPERTY + " in"
                        + " application-local.yaml, as VESPERA_CORPUS_ROOT, or with --" + ROOT_PROPERTY
                        + "=<path>. A root is never guessed, because a census of the wrong tree reports"
                        + " success.", CommandLine.ExitCode.USAGE);
            }
            if (report != null) {
                report.walks(corpusRoot);
            }
            // A code set's folder outside the corpus root, after the corpus root is known and before the deliverable
            // root's own checks (ADR-334 §5.1): one line, exit 1, nothing walked.
            Optional<String> misplacedCodeSetFolder = CodeSetFolders.refusal(
                    codeSetFolders(),
                    corpusRoot,
                    workingDirectoryInUse,
                    seedFolder(),
                    DeliverableRoot.of(deliverableRoot).folderOf(workingDirectoryInUse));
            if (misplacedCodeSetFolder.isPresent()) {
                return refuse(misplacedCodeSetFolder.get(), CommandLine.ExitCode.SOFTWARE);
            }
            Optional<String> misplacedDeliverableRoot = DeliverableRoot.of(deliverableRoot)
                    .refusalAtTheStart(workingDirectoryInUse, corpusRoot, seedFolder());
            if (misplacedDeliverableRoot.isPresent()) {
                return refuse(misplacedDeliverableRoot.get(), CommandLine.ExitCode.SOFTWARE);
            }
            // A preset no run can be made under, named by configuration, in one line and no stack trace: before the
            // corpus-root lock is taken, the sidecars started and the job started, so that nothing is walked, no run is
            // minted and the file of ADR-330 §1 is not written (ADR-330 §9). vespera label and vespera publish, which
            // read no preset, are not stopped by one.
            if (presets != null) {
                Optional<String> unmakeableRun = presets.refusal(profileToReadThePresetsOver());
                if (unmakeableRun.isPresent()) {
                    return refuse(unmakeableRun.get(), CommandLine.ExitCode.SOFTWARE);
                }
            }
            // A profile whose modelsRunOn is not local stops the invocation here, right after the presets' refusal and
            // before the corpus-root lock, the sidecars and the job: nothing is asked of Ollama (ADR-384 §2).
            if (modelsAtTheStart != null) {
                Optional<String> notLocal = modelsAtTheStart.refusal();
                if (notLocal.isPresent()) {
                    return refuse(notLocal.get(), CommandLine.ExitCode.SOFTWARE);
                }
            }
            if (corpusRootLock != null) {
                try {
                    corpusRootLock.take(corpusRoot, workingDirectoryInUse, arguments);
                } catch (CorpusRootInUseException | CorpusRootLockUnavailableException refused) {
                    return refuse(refused.getMessage(), CommandLine.ExitCode.SOFTWARE);
                }
            }
            // After the root is taken and before the job starts: a run refused for its root starts nothing (ADR-323 §1).
            if (sidecarStarter != null) {
                try {
                    sidecarStarter.ensureRunning();
                } catch (SidecarStartRefusedException refused) {
                    return refuse(refused.getMessage(), CommandLine.ExitCode.SOFTWARE);
                }
            }
            // Past the last refusal of the start and before the first step: an invocation refused above writes none (ADR-330 §1).
            if (effectiveValues != null) {
                effectiveValues.write();
            }
            OperatorLines.info("Report: " + ReportHtml.pathIn(workingDirectoryInUse));
            log.info(
                    "Walking {}, named {}",
                    corpusRoot,
                    root != null ? "on the command line" : "by " + ROOT_PROPERTY + " in configuration");
            JobExecution execution;
            try {
                execution = jobOperator.start(
                        vesperaJob,
                        new JobParametersBuilder()
                                .addString("root", corpusRoot.toString())
                                .addLocalDateTime("startedAt", java.time.LocalDateTime.now())
                                .toJobParameters());
            } catch (RuntimeException | Error thrown) {
                if (report != null) {
                    report.runEnds(CommandLine.ExitCode.SOFTWARE, null, null);
                }
                logTheReportLine();
                throw thrown;
            }
            if (execution.getStatus().isUnsuccessful()) {
                if (report != null) {
                    report.runEnds(CommandLine.ExitCode.SOFTWARE, null, null);
                }
                logTheReportLine();
                return CommandLine.ExitCode.SOFTWARE;
            }
            String closingLine = nextAction.line(corpusRoot, new InvocationRuns(execution.getExecutionContext()));
            OperatorLines.info(closingLine);
            if (report != null) {
                report.runEnds(CommandLine.ExitCode.OK, closingLine, nextAction.lastWritten());
            }
            logTheReportLine();
            return CommandLine.ExitCode.OK;
        }

        /** The report line, last, once the last write of the report is made and after the closing line (ADR-362 section 7). */
        private void logTheReportLine() {
            String line = report == null ? null : report.reportLine();
            if (line != null) {
                OperatorLines.info(line);
            }
        }

        /**
         * The profile the presets are checked over: whether its {@code clusterResolution} stands in place of the
         * configured one. A profile that cannot be read, or none to read, names nothing, and is left to the job's own
         * refusal of it.
         */
        private Profile profileToReadThePresetsOver() {
            if (profileStore != null) {
                try {
                    return profileStore.load();
                } catch (RuntimeException unreadable) {
                    // names nothing here
                }
            }
            return new Profile(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);
        }

        /**
         * The folders the profile's {@code codeSetFolders} names, in its order, where it names readable ones and the
         * profile can be read. A profile that cannot be read is left to the job's own refusal of it, and a value that
         * is unreadable reads none (ADR-120).
         */
        private List<Path> codeSetFolders() {
            if (profileStore == null) {
                return List.of();
            }
            try {
                return profileStore.load().codeSetFolders().reading() instanceof FoldersValue.Answered answered
                        ? answered.folders()
                        : List.of();
            } catch (RuntimeException unreadable) {
                return List.of();
            }
        }

        /**
         * The profile's seed folder where one is set and the profile can be read. A profile that cannot be read is
         * left to the job's own refusal of it, so it names no seed folder here.
         */
        private Optional<Path> seedFolder() {
            if (profileStore == null) {
                return Optional.empty();
            }
            try {
                TextValue seedFolder = profileStore.load().seedFolder();
                return seedFolder.isSet() ? Optional.of(Path.of(seedFolder.value())) : Optional.empty();
            } catch (RuntimeException unreadable) {
                return Optional.empty();
            }
        }

        /**
         * The root this invocation is against, or {@code null} if nobody has named one (ADR-066).
         *
         * <p>The argument wins whenever it says anything; the property answers only its silence. The
         * asymmetry with {@code --db-dir}, which is read as a property first and checked against the
         * option afterwards, is not an inconsistency: the working directory has to be known before the
         * datasource exists, and the root does not.
         */
        private Path rootToWalk() {
            if (root != null) {
                return root;
            }
            return configuredRoot == null || configuredRoot.isBlank() ? null : Path.of(configuredRoot.strip());
        }
    }


    /**
     * Records the answers a person wrote into the label file (ADR-088, #105) — a separate command
     * because it is a separate act: invoked deliberately, reading a file the operator authored, and
     * minting no run.
     *
     * <p>The file argument is optional. Omitted, it reads the label file the scoring run wrote in the
     * working directory, which is not a guess in ADR-066's sense: it is the file this application
     * itself wrote, under a name it chose, in a directory the operator configured.
     *
     * <p>Nothing here prompts. ADR-047's "the pipeline never blocks" is what makes the labelling loop
     * resumable across days, and a terminal walking a person through the shipped sixty documents is a session
     * that has to be finished or lost.
     */
    @Component
    @Command(name = "label", description = "Records the answers written into the relevance label file.")
    public static class Label implements Callable<Integer> {

        private static final Logger log = LoggerFactory.getLogger(Label.class);

        private final LabelIngestion labelIngestion;
        private final NextAction nextAction;
        private final Path workingDirectoryInUse;
        private final ObjectProvider<AutoLabelling> autoLabelling;
        private SidecarStarter sidecarStarter;
        private EffectiveValuesFile effectiveValues;
        private ModelsAtTheStart modelsAtTheStart;
        private InvocationReport report;

        @Option(
                names = "--auto",
                description = "Have the local model answer the questions in the label file, then set"
                        + " relevanceScoreFloor by the rule that loses no documentation (ADR-197). An answer a"
                        + " person gave is never replaced.")
        private boolean auto;

        @Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "<file>",
                description = "The completed label file. Defaults to the one the last run wrote in the"
                        + " working directory.")
        private Path file;

        /**
         * The same option {@code run} takes (ADR-054, #310): an operator who moved the working
         * directory names it on every invocation, and this is invocation 3.
         */
        @Mixin
        private WorkingDirectoryOption databaseDirectory = new WorkingDirectoryOption();

        /**
         * {@code --auto} is reached through an {@code ObjectProvider} and not injected, so that this
         * command is built in a context that does not provide it. The application provides it; the
         * whole-job slice does not, and a test that wants it imports {@link AutoLabelling} and a labeller
         * of its own. A build without it says so when {@code --auto} is typed. Nothing about {@code label}
         * without {@code --auto} reaches the labeller or the chat model behind it.
         */
        Label(
                LabelIngestion labelIngestion,
                NextAction nextAction,
                @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectoryInUse,
                ObjectProvider<AutoLabelling> autoLabelling) {
            this.labelIngestion = labelIngestion;
            this.nextAction = nextAction;
            this.workingDirectoryInUse = workingDirectoryInUse;
            this.autoLabelling = autoLabelling;
        }

        /** As {@link Run#startSidecarsWith}: {@code --auto} asks Ollama's model, so it asks the starter first (ADR-323 §1). */
        @Autowired(required = false)
        void startSidecarsWith(SidecarStarter sidecarStarter) {
            this.sidecarStarter = sidecarStarter;
        }

        /** As {@link Run#refuseModelsNotLocalWith}: {@code --auto} reads {@code modelsRunOn} first (ADR-384 §3). */
        @Autowired(required = false)
        void refuseModelsNotLocalWith(ModelsAtTheStart modelsAtTheStart) {
            this.modelsAtTheStart = modelsAtTheStart;
        }

        /** As {@link Run#sayWhatLiesBeneathTheProfileWith}: a label that has started has written the file (ADR-330 §1). */
        @Autowired(required = false)
        void sayWhatLiesBeneathTheProfileWith(Environment environment) {
            this.effectiveValues = new EffectiveValuesFile(environment, workingDirectoryInUse);
        }

        /** As {@link Run#writeTheReportWith}: {@code label} writes the report too (ADR-362 section 3). */
        @Autowired(required = false)
        void writeTheReportWith(InvocationReport report) {
            this.report = report;
        }

        /** Drops what a previous invocation parsed, for the reason {@link Run} does the same. */
        void forgetPreviousInvocation() {
            file = null;
            auto = false;
            databaseDirectory = new WorkingDirectoryOption();
        }

        /**
         * Records the answers, after refusing a {@code --db-dir} that is not the directory opened.
         *
         * <p>A named file is not checked against the directory it sits in. Where a file is kept says
         * nothing about which database it belongs to; what does is the run it was generated under,
         * which {@link LabelIngestion} compares with the sample the opened working directory holds.
         */
        /** How the invocation in hand ended, what it counted and the closing line it printed, for the report. */
        private String endedAs;
        private Map<String, Integer> answered;
        private String closingLine;

        /** Prints a line of the jar's own on standard error, whole, and keeps it for the report as the refusal it is. */
        private Integer refuse(String line, int exitCode) {
            OperatorLines.warn(line);
            if (report != null) {
                report.said(line);
            }
            closingLine = line;
            return exitCode;
        }

        @Override
        public Integer call() {
            endedAs = "failed";
            answered = Map.of();
            closingLine = null;
            if (report != null) {
                report.begins(auto ? "label --auto" : "label");
            }
            String misnamedDatabaseDirectory = databaseDirectory.disagreement(workingDirectoryInUse);
            if (misnamedDatabaseDirectory != null) {
                return refusedBeforeTheCommandRuns(misnamedDatabaseDirectory, CommandLine.ExitCode.SOFTWARE);
            }
            if (auto && file != null) {
                return refusedBeforeTheCommandRuns("vespera label --auto takes no file: it works on the label file the last run"
                        + " wrote in the working directory. Give either --auto or a file, not both.",
                        CommandLine.ExitCode.USAGE);
            }
            // Past a misnamed --db-dir and --auto given a file, as the report is written (ADR-330 §1).
            if (effectiveValues != null) {
                effectiveValues.write();
            }
            OperatorLines.info("Report: " + ReportHtml.pathIn(workingDirectoryInUse));
            if (report != null) {
                report.labelStarts();
            }
            int exit = CommandLine.ExitCode.SOFTWARE;
            try {
                exit = recordTheAnswers();
                return exit;
            } finally {
                if (report != null) {
                    report.labelEnds(
                            endedAs, answered, exit, closingLine,
                            exit == CommandLine.ExitCode.OK ? nextAction.lastWritten() : null);
                }
                logTheReportLine();
            }
        }

        /** A refusal that comes before the command starts: the report says it refused. */
        private Integer refusedBeforeTheCommandRuns(String line, int exitCode) {
            refuse(line, exitCode);
            if (report != null) {
                report.labelEnds("refused", Map.of(), exitCode, line, null);
            }
            logTheReportLine();
            return exitCode;
        }

        /** The report line, last, once the last write of the report is made and after the closing line (ADR-362 section 7). */
        private void logTheReportLine() {
            String line = report == null ? null : report.reportLine();
            if (line != null) {
                OperatorLines.info(line);
            }
        }

        private int recordTheAnswers() {
            if (auto) {
                // Before the sidecars and before the labeller is asked anything: a profile saying cloud ends it here
                // (ADR-384 §3).
                if (modelsAtTheStart != null) {
                    Optional<String> notLocal = modelsAtTheStart.refusalOfLabelAuto();
                    if (notLocal.isPresent()) {
                        endedAs = "refused";
                        return refuse(notLocal.get(), CommandLine.ExitCode.SOFTWARE);
                    }
                }
                if (sidecarStarter != null) {
                    try {
                        sidecarStarter.ensureRunning();
                    } catch (SidecarStartRefusedException refused) {
                        endedAs = "refused";
                        return refuse(refused.getMessage(), CommandLine.ExitCode.SOFTWARE);
                    }
                }
                AutoLabelling labelling = autoLabelling.getIfAvailable();
                if (labelling == null) {
                    endedAs = "refused";
                    return callAuto(null);
                }
                AutoLabelling.Outcome outcome = labelling.run();
                endedAs = outcome.refused() ? "refused" : "recorded";
                answered = outcome.counts();
                return callAuto(outcome);
            }
            LabelIngestion.Outcome outcome = labelIngestion.ingest(file);
            endedAs = outcome.refused() ? "refused" : "recorded";
            answered = outcome.counts();
            if (outcome.refused()) {
                return refuse(
                        "vespera label recorded nothing: " + outcome.message(),
                        outcome.message().startsWith(LabelIngestion.NO_FILE)
                                ? CommandLine.ExitCode.USAGE
                                : CommandLine.ExitCode.SOFTWARE);
            }
            OperatorLines.info(outcome.message());
            closingLine = nextAction.line();
            OperatorLines.info(closingLine);
            return CommandLine.ExitCode.OK;
        }

        /**
         * {@code --auto} (ADR-197 §6): it needs no corpus root and takes none (ADR-208). It finds each
         * opening under the key stage 2 recorded and opens no archive file (ADR-206 §4).
         */
        private int callAuto(AutoLabelling.Outcome outcome) {
            if (outcome == null) {
                return refuse("vespera label --auto recorded nothing: no local labeller is wired into"
                        + " this build.", CommandLine.ExitCode.SOFTWARE);
            }
            if (outcome.refused()) {
                return refuse("vespera label --auto recorded nothing: " + outcome.message(),
                        CommandLine.ExitCode.SOFTWARE);
            }
            OperatorLines.info(outcome.message());
            closingLine = nextAction.line();
            OperatorLines.info(closingLine);
            return CommandLine.ExitCode.OK;
        }
    }

    /**
     * Delivers the rendering of the generation run this working directory last drew to each destination {@code --to}
     * names (ADR-333 §6): a separate command because it is a separate act, the one that sends anything off the machine,
     * and the operator's alone to ask for. A bare publish is Confluence (ADR-308 §5).
     *
     * <p>Each destination runs in turn, in the order written, a name written twice delivered once, and one stopping
     * does not stop the next. Each finds its own rendering: the last generation run minted whose generation step is
     * recorded as finished and whose tree holds that plugin's folder with its list or a {@code drawing.txt}. Where the
     * tree lies is the deliverable root {@code run} draws it at (ADR-276). It mints no run and opens no archive file;
     * what each sends and how is its plugin's.
     */
    @Component
    @Command(name = "publish", description = "Delivers the rendering of the last generation run to each destination"
            + " named on --to: the operator's Confluence Cloud site, as the VESPERA_CONFLUENCE_* variables name it,"
            + " or the website folder of the working directory. Bare, it delivers to Confluence.")
    public static class Publish implements Callable<Integer> {

        private static final Logger log = LoggerFactory.getLogger(Publish.class);

        /** The destination a bare publish takes, the only one before {@code --to} (ADR-333 §6). */
        private static final String DESTINATION = Publisher.RENDERING_FOLDER;

        private final Ledger ledger;
        private final List<Plugin> plugins;
        private final Path workingDirectoryInUse;
        private final DeliverableRoot deliverableRoot;
        private InvocationReport report;

        /** The same option {@code run} and {@code label} take (ADR-054, #310). */
        @Mixin
        private WorkingDirectoryOption databaseDirectory = new WorkingDirectoryOption();

        /** The destinations named, by the plugins' names, in the order written (ADR-333 §6). */
        @Option(
                names = "--to",
                split = ",",
                paramLabel = "<destination>",
                description = "The destinations to deliver to, by their names, separated by commas.")
        private List<String> destinations = new java.util.ArrayList<>();

        /**
         * The plugins in the jar, sorted by name as a drawing orders them, of which a bare publish takes the one named
         * {@value #DESTINATION} (ADR-333 §6).
         */
        @Autowired
        Publish(
                Ledger ledger,
                List<Plugin> plugins,
                @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectoryInUse,
                @Value("${" + Run.DELIVERABLE_ROOT_PROPERTY + ":}") String deliverableRoot) {
            this.ledger = ledger;
            this.plugins = plugins.stream().sorted(java.util.Comparator.comparing(Plugin::name)).toList();
            if (this.plugins.stream().noneMatch(candidate -> DESTINATION.equals(candidate.name()))) {
                throw new IllegalStateException("no plugin named " + DESTINATION);
            }
            this.workingDirectoryInUse = workingDirectoryInUse;
            this.deliverableRoot = DeliverableRoot.of(deliverableRoot);
        }

        /** Publishes to Confluence through the given publisher, as a bare publish does (ADR-333 §13 step 1). */
        Publish(Ledger ledger, Publisher publisher, Path workingDirectoryInUse, String deliverableRoot) {
            this(ledger, List.<Plugin>of(new ConfluencePlugin(publisher)), workingDirectoryInUse, deliverableRoot);
        }

        /** As {@link Run#writeTheReportWith}: {@code publish} writes the report too (ADR-362 section 3). */
        @Autowired(required = false)
        void writeTheReportWith(InvocationReport report) {
            this.report = report;
        }

        /** Drops what a previous invocation parsed, for the reason {@link Run} does the same. */
        void forgetPreviousInvocation() {
            databaseDirectory = new WorkingDirectoryOption();
            destinations = new java.util.ArrayList<>();
        }

        /** A refusal after the working-directory lock: its line is printed whole, and the report says it, once. */
        private Integer refuse(String line, int exitCode) {
            OperatorLines.warn(line);
            if (report != null) {
                report.refused(line, exitCode);
            }
            logTheReportLine();
            return exitCode;
        }

        @Override
        public Integer call() {
            if (report != null) {
                report.begins(InvocationReport.PUBLISH);
            }
            String misnamedDatabaseDirectory = databaseDirectory.disagreement(workingDirectoryInUse);
            if (misnamedDatabaseDirectory != null) {
                return refuse(misnamedDatabaseDirectory, CommandLine.ExitCode.SOFTWARE);
            }
            boolean bare = destinations == null || destinations.isEmpty();
            List<Plugin> chosen = new java.util.ArrayList<>();
            for (String name : bare ? List.of(DESTINATION) : destinations) {
                Optional<Plugin> named = plugins.stream().filter(candidate -> candidate.name().equals(name.strip())).findFirst();
                if (named.isEmpty()) {
                    return refuse("vespera publish has no destination " + name.strip() + ": it knows "
                            + plugins.stream().map(Plugin::name).collect(java.util.stream.Collectors.joining(", "))
                            + ".", CommandLine.ExitCode.USAGE);
                }
                if (!chosen.contains(named.get())) {
                    chosen.add(named.get());
                }
            }
            OperatorLines.info("Report: " + ReportHtml.pathIn(workingDirectoryInUse));
            if (report != null) {
                report.publishStarts();
            }
            int exit = CommandLine.ExitCode.SOFTWARE;
            try {
                Map<String, Set<String>> finished = ledger.runs().finishedSteps();
                List<Runs.Listed> runs = ledger.runs().listed();
                boolean anyStopped = false;
                int ownCode = CommandLine.ExitCode.OK;
                for (Plugin plugin : chosen) {
                    int code = deliverTo(plugin, bare, finished, runs);
                    anyStopped |= code != CommandLine.ExitCode.OK;
                    if (ownCode == CommandLine.ExitCode.OK && code != CommandLine.ExitCode.OK
                            && code != CommandLine.ExitCode.SOFTWARE) {
                        ownCode = code;
                    }
                }
                exit = ownCode != CommandLine.ExitCode.OK ? ownCode
                        : anyStopped ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
                return exit;
            } finally {
                if (report != null) {
                    report.publishEnds(exit == CommandLine.ExitCode.OK ? "delivered" : "stopped", exit);
                }
                logTheReportLine();
            }
        }

        /** The report line, last, once the last write of the report is made and after the closing line (ADR-362 section 7). */
        private void logTheReportLine() {
            String line = report == null ? null : report.reportLine();
            if (line != null) {
                OperatorLines.info(line);
            }
        }

        /** A destination's stop: its line on standard error, whole, kept for the report as the refusal it is. */
        private int stopped(Plugin plugin, String line, int exitCode) {
            OperatorLines.warn(line);
            if (report != null) {
                report.said(line);
                report.destinationEnds(plugin.name(), "stopped", line);
            }
            return exitCode;
        }

        private int stopped(Plugin plugin, String line) {
            return stopped(plugin, line, CommandLine.ExitCode.SOFTWARE);
        }

        /**
         * Delivers the last rendering {@code plugin} drew, in one closing line: on standard output where it was
         * delivered and on standard error where it stopped or was refused (ADR-333 §6). A plugin that throws is a stop,
         * its line naming it and the exception's type and never its message, which could name a document (ADR-274 §8).
         *
         * @param bare whether this is a bare publish, which says what ADR-308 §5 said before {@code --to}
         * @return 0 where it was delivered, else the code its stop carries (1 where it carries none of its own)
         */
        private int deliverTo(Plugin plugin, boolean bare, Map<String, Set<String>> finished, List<Runs.Listed> runs) {
            Optional<String> generation = lastDrawn(plugin, finished, runs);
            if (generation.isEmpty()) {
                return stopped(plugin, bare
                        ? "vespera publish sent nothing: no Confluence rendering is drawn in this working directory."
                                + " Run vespera run to the deliverable, and publish again."
                        : "vespera publish sent nothing to " + plugin.name() + ": no " + plugin.name()
                                + " rendering is drawn in this working directory. Run vespera run to the deliverable,"
                                + " and publish again.");
            }
            Path rendering = renderingOf(plugin, generation.get());
            if (!drawingFinished(rendering)) {
                return stopped(plugin, "vespera publish sent nothing to " + plugin.name() + ": the last drawing"
                        + " of its rendering did not finish. Run vespera run to draw it again, and publish again.");
            }
            Plugin.Delivery outcome;
            try {
                outcome = plugin.deliver(rendering, generation.get(), workingDirectoryInUse, OperatorLines::info);
            } catch (RuntimeException thrown) {
                return stopped(plugin, "vespera publish stopped " + plugin.name() + ": it threw "
                        + thrown.getClass().getSimpleName() + ", whose message is not written. Publish again once"
                        + " that is dealt with.");
            }
            if (!outcome.delivered()) {
                return stopped(plugin, outcome.closingLine(), outcome.exitCode());
            }
            OperatorLines.info(outcome.closingLine());
            if (report != null) {
                report.destinationEnds(plugin.name(), "delivered", outcome.closingLine());
            }
            return CommandLine.ExitCode.OK;
        }

        /**
         * The last generation run minted whose generation step finished and whose tree holds {@code plugin}'s folder
         * with its list or a {@code drawing.txt}: the last run to have drawn it, or begun to, so that an older run's
         * rendering is never sent in place of one whose drawing did not finish (ADR-333 §6, §11).
         */
        private Optional<String> lastDrawn(Plugin plugin, Map<String, Set<String>> finished, List<Runs.Listed> runs) {
            for (Runs.Listed listed : runs.reversed()) {
                Path rendering = renderingOf(plugin, listed.id());
                if (StageModules.GENERATION.stage().equals(listed.stage())
                        && finished.getOrDefault(listed.id(), Set.of()).contains(StepNames.GENERATION)
                        && (Files.isRegularFile(rendering.resolve(plugin.listName()))
                                || Files.isRegularFile(rendering.resolve(Plugin.DRAWING_STATE_FILE_NAME)))) {
                    return Optional.of(listed.id());
                }
            }
            return Optional.empty();
        }

        /**
         * Whether the rendering's last drawing went to its end: its {@code drawing.txt} says {@code drawn}, or there is
         * none, as in a rendering drawn before ADR-333, and the page list alone decides (ADR-333 §11). Read before
         * anything else of the destination.
         */
        private static boolean drawingFinished(Path rendering) {
            Path state = rendering.resolve(Plugin.DRAWING_STATE_FILE_NAME);
            if (!Files.isRegularFile(state)) {
                return true;
            }
            try {
                return Plugin.DRAWN.equals(Files.readString(state, StandardCharsets.UTF_8).strip());
            } catch (IOException | RuntimeException unreadable) {
                return false;
            }
        }

        private Path renderingOf(Plugin plugin, String generationRun) {
            return deliverableRoot.treeOf(workingDirectoryInUse, generationRun).resolve(plugin.name());
        }
    }
}
