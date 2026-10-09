package io.algernon.vespera.pipeline;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The command surface (ADR-047, ADR-054, narrowed by ADR-101): {@code vespera run <root>} and
 * {@code vespera label}.
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
        subcommands = {VesperaCommand.Run.class, VesperaCommand.Label.class},
        description = "Curates a local archive into a publication-ready knowledge base.")
public class VesperaCommand implements Callable<Integer> {

    private final Run run;
    private final Label label;

    public VesperaCommand(Run run, Label label) {
        this.run = run;
        this.label = label;
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
        return new CommandLine(this, new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> type) throws Exception {
                if (type.isInstance(run)) {
                    return type.cast(run);
                }
                if (type.isInstance(label)) {
                    return type.cast(label);
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

        private static final Logger log = LoggerFactory.getLogger(Run.class);

        private final JobOperator jobOperator;
        private final Job vesperaJob;
        private final NextAction nextAction;
        private final Path workingDirectoryInUse;
        private final String configuredRoot;

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
                ObjectProvider<AutoLabelling> autoLabelling) {
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
            if (auto) {
                return callAuto();
            }
            LabelIngestion.Outcome outcome = labelIngestion.ingest(file);
            if (outcome.refused()) {
                System.err.println("vespera label recorded nothing: " + outcome.message());
                return outcome.message().startsWith(LabelIngestion.NO_FILE)
                        ? CommandLine.ExitCode.USAGE
                        : CommandLine.ExitCode.SOFTWARE;
            }
            System.out.println(outcome.message());
            System.out.println(nextAction.line());
            return CommandLine.ExitCode.OK;
        }

        /**
         * {@code --auto} (ADR-197 §6): it needs no corpus root and takes none (ADR-208). It finds each
         * opening under the key stage 2 recorded and opens no archive file (ADR-206 §4).
         */
        private Integer callAuto() {
            AutoLabelling labelling = autoLabelling.getIfAvailable();
            if (labelling == null) {
                System.err.println("vespera label --auto recorded nothing: no local labeller is wired into"
                        + " this build.");
                return CommandLine.ExitCode.SOFTWARE;
            }
            AutoLabelling.Outcome outcome = labelling.run();
            if (outcome.refused()) {
                System.err.println("vespera label --auto recorded nothing: " + outcome.message());
                return CommandLine.ExitCode.SOFTWARE;
            }
            System.out.println(outcome.message());
            System.out.println(nextAction.line());
            return CommandLine.ExitCode.OK;
        }
    }
}
