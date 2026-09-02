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
 * The two-command surface (ADR-047, ADR-054): {@code vespera run <root>} and {@code vespera publish}.
 *
 * <p>Two commands and not one, because they are not the same act. A run is unattended and never
 * blocks (ADR-047); publication is terminal, one-shot and always invoked by a person (ADR-035,
 * ADR-024), which is exactly why it is not a stage of the run.
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
        subcommands = {VesperaCommand.Run.class, VesperaCommand.Publish.class},
        description = "Curates a local archive into a publication-ready knowledge base.")
public class VesperaCommand implements Callable<Integer> {

    private final Run run;
    private final Publish publish;

    public VesperaCommand(Run run, Publish publish) {
        this.run = run;
        this.publish = publish;
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
        return new CommandLine(this, new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> type) throws Exception {
                if (type.isInstance(run)) {
                    return type.cast(run);
                }
                if (type.isInstance(publish)) {
                    return type.cast(publish);
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
                @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectoryInUse,
                @Value("${" + ROOT_PROPERTY + ":}") String configuredRoot) {
            this.jobOperator = jobOperator;
            this.vesperaJob = vesperaJob;
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
            refuseToRunAgainstADirectoryTheOperatorDidNotName();
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
            return execution.getStatus().isUnsuccessful() ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
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
         * Checks that the option the operator typed is the directory the application actually opened.
         *
         * <p>{@code --db-dir} is read twice by two different mechanisms: as a property, before the
         * datasource exists, and as an option here. That is not redundancy — without the option
         * picocli would reject the argument outright — but it does mean the two could drift, and the
         * way they drift is silent: rename the property placeholder and the flag keeps parsing while
         * the database quietly opens somewhere else. Comparing them is what makes that loud.
         */
        private void refuseToRunAgainstADirectoryTheOperatorDidNotName() {
            if (databaseDirectory == null) {
                return;
            }
            Path named = databaseDirectory.toAbsolutePath().normalize();
            Path opened = workingDirectoryInUse.toAbsolutePath().normalize();
            if (!named.equals(opened)) {
                throw new IllegalStateException(
                        ("--db-dir named %s but the database and profile were opened in %s; --db-dir has to be"
                                        + " given as --db-dir=<path>, because it is read as the %s property"
                                        + " before this command is parsed")
                                .formatted(named, opened, WorkingDirectoryPreparer.PROPERTY));
            }
        }
    }

    /**
     * Publishes the survivors to Confluence — the adapter, invoked separately and always by a person
     * (ADR-025, ADR-035).
     *
     * <p>A stub in this slice. It takes no root because it reads the ledger, which already knows
     * which corpus was censused (ADR-054).
     */
    @Component
    @Command(name = "publish", description = "Publishes what survived. Not implemented yet.")
    public static class Publish implements Callable<Integer> {

        @Override
        public Integer call() {
            System.err.println("vespera publish is not implemented: the publication adapter does not exist yet.");
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
