package io.algernon.vespera;

import io.algernon.vespera.engine.pipeline.BesideTheJar;
import io.algernon.vespera.engine.pipeline.BesideTheJarUnwritableException;
import io.algernon.vespera.engine.pipeline.CorpusRootLock;
import io.algernon.vespera.engine.pipeline.MisshapenProfileRefusal;
import io.algernon.vespera.engine.pipeline.ProfileShapeCheck;
import io.algernon.vespera.engine.pipeline.SidecarStarter;
import io.algernon.vespera.engine.pipeline.TemporaryFilesInTheWorkingDirectory;
import io.algernon.vespera.engine.pipeline.WorkingDirectoryInUseRefusal;
import io.algernon.vespera.engine.pipeline.WorkingDirectoryLock;
import io.algernon.vespera.engine.pipeline.WorkingDirectoryPreparer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class VesperaApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(VesperaApplication.class);
        // Registered here rather than as beans: each has to run while the environment is being
        // prepared, which is before any bean exists — the preparer before the datasource opens a file
        // inside the directory it creates (ADR-054), the lock after it, because the directory must
        // exist, and before the datasource opens vespera.db, so a second invocation is refused before it
        // touches the file (ADR-177), the check before a bean reads a profile.yaml that does not load
        // and fails the context under its own name (#321). The fourth sets SQLite's directory for temporary
        // files to the working directory, once, before any connection pool exists and after the others, so
        // that a start they refuse opens no connection to SQLite and changes nothing of the process's
        // (ADR-211 section 12). None declares an order: each hears the event in the order it is added here.
        // The folder beside the jar comes first (ADR-397 section 3): it hears that a start has begun, before Spring
        // prepares its environment, and writes what is missing there before any configuration is read. Spring is
        // pointed at the two files there among its default properties, the lowest of its sources (section 4).
        BesideTheJar besideTheJar = BesideTheJar.ofThisApplication();
        besideTheJar.pointSpringAt(application);
        application.addListeners(
                besideTheJar,
                new WorkingDirectoryPreparer(),
                new WorkingDirectoryLock(),
                new ProfileShapeCheck(),
                new TemporaryFilesInTheWorkingDirectory());
        // The corpus-root lock is a bean only here, so contexts built by Spring's test support take none
        // (ADR-279 section 7); `run` takes it once it knows its root.
        application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                "corpusRootLock",
                new CorpusRootLock(CorpusRootLock.folderNamedBy(
                        context.getEnvironment().getProperty(CorpusRootLock.FOLDER_PROPERTY)))));
        // So is the starter of the sidecars (ADR-323 section 1): `run` and `label --auto` ask it, a test context has
        // none, and so no test context starts the Docker engine or a container.
        application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                "sidecarStarter", SidecarStarter.forTheApplication(context.getEnvironment())));
        ConfigurableApplicationContext context;
        try {
            context = application.run(args);
        } catch (RuntimeException failure) {
            // A folder beside the jar that cannot be written is thrown before logging is configured, so a log line
            // would be dropped: it goes on, and the entry point says its line on standard error and exits 1 (ADR-397 §3).
            if (failure instanceof BesideTheJarUnwritableException) {
                throw failure;
            }
            if (MisshapenProfileRefusal.refuses(failure)) {
                System.exit(MisshapenProfileRefusal.EXIT_CODE);
                return;
            }
            if (WorkingDirectoryInUseRefusal.refuses(failure)) {
                System.exit(WorkingDirectoryInUseRefusal.EXIT_CODE);
                return;
            }
            throw failure;
        }
        // The command's exit code is the process's (ADR-141): SpringApplication.exit closes the
        // context and reads every ExitCodeGenerator, and System.exit ends the JVM whatever
        // non-daemon thread a dependency has started and left parked.
        System.exit(SpringApplication.exit(context));
    }
}
