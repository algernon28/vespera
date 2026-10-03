package io.algernon.vespera;

import io.algernon.vespera.pipeline.MisshapenProfileRefusal;
import io.algernon.vespera.pipeline.ProfileShapeCheck;
import io.algernon.vespera.pipeline.WorkingDirectoryInUseRefusal;
import io.algernon.vespera.pipeline.WorkingDirectoryLock;
import io.algernon.vespera.pipeline.WorkingDirectoryPreparer;
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
        // and fails the context under its own name (#321).
        application.addListeners(new WorkingDirectoryPreparer(), new WorkingDirectoryLock(), new ProfileShapeCheck());
        ConfigurableApplicationContext context;
        try {
            context = application.run(args);
        } catch (RuntimeException failure) {
            // Already said in one line by the refusal; its exit code is all that is left to deliver.
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
