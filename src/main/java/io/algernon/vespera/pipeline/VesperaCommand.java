package io.algernon.vespera.pipeline;

import java.nio.file.Path;
import java.util.concurrent.Callable;
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
 * <p>{@code run} takes the corpus root and nothing else. Where the database and the profile live is
 * operator configuration rather than something derived from the root (ADR-054), so it is
 * {@code vespera.working-dir} in {@code application.yaml}, overridden per invocation with
 * {@code --db-dir=<path>}.
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
     */
    CommandLine commandLine() {
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

        private final JobOperator jobOperator;
        private final Job vesperaJob;
        private final Path workingDirectoryInUse;

        @Parameters(index = "0", paramLabel = "<root>", description = "The corpus root to walk.")
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
                @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectoryInUse) {
            this.jobOperator = jobOperator;
            this.vesperaJob = vesperaJob;
            this.workingDirectoryInUse = workingDirectoryInUse;
        }

        @Override
        public Integer call() throws Exception {
            refuseToRunAgainstADirectoryTheOperatorDidNotName();
            JobExecution execution = jobOperator.start(
                    vesperaJob,
                    new JobParametersBuilder()
                            .addString("root", root.toString())
                            .addLocalDateTime("startedAt", java.time.LocalDateTime.now())
                            .toJobParameters());
            return execution.getStatus().isUnsuccessful() ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
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
