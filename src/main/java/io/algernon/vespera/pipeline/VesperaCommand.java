package io.algernon.vespera.pipeline;

import io.algernon.vespera.confluence.Publisher;
import io.algernon.vespera.deliverable.DeliverableRoot;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.Runs;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.TextValue;
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
        subcommands = {VesperaCommand.Run.class, VesperaCommand.Label.class, VesperaCommand.Publish.class},
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
        spec.commandLine().usage(System.out);
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
        return new CommandLine(this, new CommandLine.IFactory() {
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

        @Override
        public Integer call() throws Exception {
            String misnamedDatabaseDirectory = databaseDirectory.disagreement(workingDirectoryInUse);
            if (misnamedDatabaseDirectory != null) {
                System.err.println(misnamedDatabaseDirectory);
                return CommandLine.ExitCode.SOFTWARE;
            }
            Path corpusRoot = rootToWalk();
            if (corpusRoot == null) {
                System.err.println("vespera run named no root and " + ROOT_PROPERTY + " is not set: give the root"
                        + " as the argument -- vespera run <root> -- or set " + ROOT_PROPERTY + " in"
                        + " application-local.yaml, as VESPERA_CORPUS_ROOT, or with --" + ROOT_PROPERTY
                        + "=<path>. A root is never guessed, because a census of the wrong tree reports"
                        + " success.");
                return CommandLine.ExitCode.USAGE;
            }
            Optional<String> misplacedDeliverableRoot = DeliverableRoot.of(deliverableRoot)
                    .refusalAtTheStart(workingDirectoryInUse, corpusRoot, seedFolder());
            if (misplacedDeliverableRoot.isPresent()) {
                System.err.println(misplacedDeliverableRoot.get());
                return CommandLine.ExitCode.SOFTWARE;
            }
            if (corpusRootLock != null) {
                try {
                    corpusRootLock.take(corpusRoot, workingDirectoryInUse, arguments);
                } catch (CorpusRootInUseException | CorpusRootLockUnavailableException refused) {
                    System.err.println(refused.getMessage());
                    return CommandLine.ExitCode.SOFTWARE;
                }
            }
            log.info("Progress page: {}", ProgressPage.pathIn(workingDirectoryInUse));
            log.info(
                    "Walking {}, named {}",
                    corpusRoot,
                    root != null ? "on the command line" : "by " + ROOT_PROPERTY + " in configuration");
            JobExecution execution = jobOperator.start(
                    vesperaJob,
                    new JobParametersBuilder()
                            .addString("root", corpusRoot.toString())
                            .addLocalDateTime("startedAt", java.time.LocalDateTime.now())
                            .toJobParameters());
            if (execution.getStatus().isUnsuccessful()) {
                return CommandLine.ExitCode.SOFTWARE;
            }
            log.info("{}", nextAction.line(corpusRoot, new InvocationRuns(execution.getExecutionContext())));
            return CommandLine.ExitCode.OK;
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
     * resumable across days, and a terminal walking a person through sixty documents is a session
     * that has to be finished or lost.
     */
    @Component
    @Command(name = "label", description = "Records the answers written into the relevance label file.")
    public static class Label implements Callable<Integer> {

        private static final Logger log = LoggerFactory.getLogger(Label.class);

        private final ProgressPage progressPage;
        private final LabelIngestion labelIngestion;
        private final NextAction nextAction;
        private final Path workingDirectoryInUse;
        private final ObjectProvider<AutoLabelling> autoLabelling;

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
                ObjectProvider<AutoLabelling> autoLabelling,
                ProgressPage progressPage) {
            this.progressPage = progressPage;
            this.labelIngestion = labelIngestion;
            this.nextAction = nextAction;
            this.workingDirectoryInUse = workingDirectoryInUse;
            this.autoLabelling = autoLabelling;
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
        @Override
        public Integer call() {
            String misnamedDatabaseDirectory = databaseDirectory.disagreement(workingDirectoryInUse);
            if (misnamedDatabaseDirectory != null) {
                System.err.println(misnamedDatabaseDirectory);
                return CommandLine.ExitCode.SOFTWARE;
            }
            if (auto && file != null) {
                System.err.println("vespera label --auto takes no file: it works on the label file the last run"
                        + " wrote in the working directory. Give either --auto or a file, not both.");
                return CommandLine.ExitCode.USAGE;
            }
            log.info("Progress page: {}", ProgressPage.pathIn(workingDirectoryInUse));
            progressPage.commandStarts(auto ? "vespera label --auto" : "vespera label");
            String ended = "failed";
            Map<String, Integer> counts = Map.of();
            try {
                if (auto) {
                    AutoLabelling labelling = autoLabelling.getIfAvailable();
                    if (labelling == null) {
                        ended = "refused";
                        return callAuto(null);
                    }
                    AutoLabelling.Outcome outcome = labelling.run();
                    ended = outcome.refused() ? "refused" : "recorded";
                    counts = outcome.counts();
                    return callAuto(outcome);
                }
                LabelIngestion.Outcome outcome = labelIngestion.ingest(file);
                ended = outcome.refused() ? "refused" : "recorded";
                counts = outcome.counts();
                if (outcome.refused()) {
                    System.err.println("vespera label recorded nothing: " + outcome.message());
                    return outcome.message().startsWith(LabelIngestion.NO_FILE)
                            ? CommandLine.ExitCode.USAGE
                            : CommandLine.ExitCode.SOFTWARE;
                }
                System.out.println(outcome.message());
                System.out.println(nextAction.line());
                return CommandLine.ExitCode.OK;
            } finally {
                progressPage.commandEnds(ended, counts);
            }
        }

        /**
         * {@code --auto} (ADR-197 §6): it needs no corpus root and takes none (ADR-208). It finds each
         * opening under the key stage 2 recorded and opens no archive file (ADR-206 §4).
         */
        private Integer callAuto(AutoLabelling.Outcome outcome) {
            if (outcome == null) {
                System.err.println("vespera label --auto recorded nothing: no local labeller is wired into"
                        + " this build.");
                return CommandLine.ExitCode.SOFTWARE;
            }
            if (outcome.refused()) {
                System.err.println("vespera label --auto recorded nothing: " + outcome.message());
                return CommandLine.ExitCode.SOFTWARE;
            }
            System.out.println(outcome.message());
            System.out.println(nextAction.line());
            return CommandLine.ExitCode.OK;
        }
    }

    /**
     * Sends the Confluence rendering of the generation run this working directory last drew to the operator's
     * Confluence Cloud site (ADR-308 §5): a separate command because it is a separate act, the one that sends anything
     * off the machine, and the operator's alone to ask for.
     *
     * <p>The generation run is found through the ledger: the last one minted whose generation step is recorded as
     * finished and whose tree holds a page list. Where the tree lies is the deliverable root {@code run} draws it at
     * (ADR-276). It mints no run and opens no archive file; what it sends and how is {@link Publisher}'s.
     */
    @Component
    @Command(name = "publish", description = "Sends the Confluence rendering of the last generation run to the"
            + " operator's Confluence Cloud site, as the VESPERA_CONFLUENCE_* variables name it.")
    public static class Publish implements Callable<Integer> {

        private final Ledger ledger;
        private final Publisher publisher;
        private final Path workingDirectoryInUse;
        private final DeliverableRoot deliverableRoot;

        /** The same option {@code run} and {@code label} take (ADR-054, #310). */
        @Mixin
        private WorkingDirectoryOption databaseDirectory = new WorkingDirectoryOption();

        Publish(
                Ledger ledger,
                Publisher publisher,
                @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectoryInUse,
                @Value("${" + Run.DELIVERABLE_ROOT_PROPERTY + ":}") String deliverableRoot) {
            this.ledger = ledger;
            this.publisher = publisher;
            this.workingDirectoryInUse = workingDirectoryInUse;
            this.deliverableRoot = DeliverableRoot.of(deliverableRoot);
        }

        /** Drops what a previous invocation parsed, for the reason {@link Run} does the same. */
        void forgetPreviousInvocation() {
            databaseDirectory = new WorkingDirectoryOption();
        }

        @Override
        public Integer call() {
            String misnamedDatabaseDirectory = databaseDirectory.disagreement(workingDirectoryInUse);
            if (misnamedDatabaseDirectory != null) {
                System.err.println(misnamedDatabaseDirectory);
                return CommandLine.ExitCode.SOFTWARE;
            }
            Optional<String> generation = lastDrawn();
            if (generation.isEmpty()) {
                System.err.println("vespera publish sent nothing: no Confluence rendering is drawn in this working"
                        + " directory. Run vespera run to the deliverable, and publish again.");
                return CommandLine.ExitCode.SOFTWARE;
            }
            Path confluence = renderingOf(generation.get());
            Publisher.Outcome outcome =
                    publisher.publish(confluence, generation.get(), workingDirectoryInUse, System.out::println);
            if (!outcome.published()) {
                System.err.println(outcome.closingLine());
                return CommandLine.ExitCode.SOFTWARE;
            }
            System.out.println(outcome.closingLine());
            return CommandLine.ExitCode.OK;
        }

        /** The last generation run minted whose generation step finished and whose tree holds a page list. */
        private Optional<String> lastDrawn() {
            Map<String, Set<String>> finished = ledger.runs().finishedSteps();
            List<Runs.Listed> runs = ledger.runs().listed();
            for (Runs.Listed listed : runs.reversed()) {
                if (StageModules.GENERATION.stage().equals(listed.stage())
                        && finished.getOrDefault(listed.id(), Set.of()).contains(StepNames.GENERATION)
                        && Files.isRegularFile(renderingOf(listed.id()).resolve(Publisher.PAGE_LIST_FILE_NAME))) {
                    return Optional.of(listed.id());
                }
            }
            return Optional.empty();
        }

        private Path renderingOf(String generationRun) {
            return deliverableRoot.treeOf(workingDirectoryInUse, generationRun).resolve(Publisher.RENDERING_FOLDER);
        }
    }
}
