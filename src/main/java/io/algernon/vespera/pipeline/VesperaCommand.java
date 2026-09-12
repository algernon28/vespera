package io.algernon.vespera.pipeline;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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
 * <p>{@code run} takes the corpus root and nothing else. The root is the argument, and
 * {@code vespera.corpus-root} in {@code application.yaml} answers only an invocation that names none
 * (ADR-066). Where the database and the profile live is operator configuration rather than something
 * derived from the root (ADR-054), so it is {@code vespera.working-dir} in the same file, overridden
 * per invocation with {@code --db-dir=<path>}.
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

    /** Bare {@code vespera} names no act, so it prints what the acts are. */
    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
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

    /** Walks a corpus and records what it holds — everything this slice does. */
    @Component
    @Command(name = "run", description = "Walks a corpus and records what it holds.")
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
                        + " in application.yaml when omitted.")
        private Path root;

        @Option(
                names = "--db-dir",
                paramLabel = "<path>",
                description = "Where the database and profile.yaml live. Must be given as --db-dir=<path>,"
                        + " because it is read before this command is parsed.")
        private Path databaseDirectory;

        public Run(
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
         * not change between invocations of the same process.
         */
        void forgetPreviousInvocation() {
            root = null;
            databaseDirectory = null;
        }

        @Override
        public Integer call() throws Exception {
            String misnamedDatabaseDirectory = misnamedDatabaseDirectory();
            if (misnamedDatabaseDirectory != null) {
                System.err.println(misnamedDatabaseDirectory);
                return CommandLine.ExitCode.SOFTWARE;
            }
            Path corpusRoot = rootToWalk();
            if (corpusRoot == null) {
                System.err.println(("vespera run named no root and %s is not set: give the root as the argument"
                                + " -- vespera run <root> -- or configure it in application.yaml. A root is never"
                                + " guessed, because a census of the wrong tree reports success.")
                        .formatted(ROOT_PROPERTY));
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
            log.info("{}", nextAction.line());
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

        /**
         * What to tell the operator when the option they typed is not the directory the application
         * actually opened, or {@code null} when the two agree.
         *
         * <p>{@code --db-dir} is read twice by two different mechanisms: as a property, before the
         * datasource exists, and as an option here. That is not redundancy — without the option
         * picocli would reject the argument outright — but it does mean the two could drift, and the
         * way they drift is silent: rename the property placeholder and the flag keeps parsing while
         * the database quietly opens somewhere else. Comparing them is what makes that loud.
         *
         * <p>Returned as a message rather than thrown, for the same reason the missing-root case is:
         * this is an operator typing a flag the wrong way round, and the useful answer is the sentence
         * that says so plus a non-zero exit code. Thrown, it leaves {@code call} through picocli's
         * default handler, which prints a Java stack trace at somebody who mistyped an argument.
         */
        private String misnamedDatabaseDirectory() {
            if (databaseDirectory == null) {
                return null;
            }
            Path named = databaseDirectory.toAbsolutePath().normalize();
            Path opened = workingDirectoryInUse.toAbsolutePath().normalize();
            if (named.equals(opened)) {
                return null;
            }
            return ("vespera run was given --db-dir %s but the database and profile were opened in %s;"
                            + " --db-dir has to be given as --db-dir=<path>, because it is read as the %s"
                            + " property before this command is parsed")
                    .formatted(named, opened, WorkingDirectoryPreparer.PROPERTY);
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

        @Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "<file>",
                description = "The completed label file. Defaults to the one the last run wrote in the"
                        + " working directory.")
        private Path file;

        public Label(LabelIngestion labelIngestion, NextAction nextAction) {
            this.labelIngestion = labelIngestion;
            this.nextAction = nextAction;
        }

        /** Drops what a previous invocation parsed, for the reason {@link Run} does the same. */
        void forgetPreviousInvocation() {
            file = null;
        }

        @Override
        public Integer call() {
            LabelIngestion.Outcome outcome = labelIngestion.ingest(java.util.Optional.ofNullable(file));
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
    }
}
