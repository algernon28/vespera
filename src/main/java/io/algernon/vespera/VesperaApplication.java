package io.algernon.vespera;

import io.algernon.vespera.pipeline.WorkingDirectoryPreparer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VesperaApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(VesperaApplication.class);
        // Registered here rather than as a bean: it has to run while the environment is being
        // prepared, which is before any bean exists and before the datasource opens a file inside
        // the directory it creates (ADR-054).
        application.addListeners(new WorkingDirectoryPreparer());
        // The command's exit code is the process's (ADR-141): SpringApplication.exit closes the
        // context and reads every ExitCodeGenerator, and System.exit ends the JVM whatever
        // non-daemon thread a dependency has started and left parked.
        System.exit(SpringApplication.exit(application.run(args)));
    }
}
