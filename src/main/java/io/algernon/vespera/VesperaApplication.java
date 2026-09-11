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
        // Closed and exited explicitly, because an invocation is one process that ends (ADR-047) and
        // returning from main does not guarantee that. The JVM exits on its own only once no
        // non-daemon thread is left, and this application's own libraries keep one: the HTTP client
        // stage 2 converts through holds a ScheduledThreadPoolExecutor for its timeouts, whose idle
        // worker is non-daemon and outlives the job by design. A run therefore finished its work,
        // reported it, and sat there -- observed for twelve minutes before being killed by hand.
        //
        // SpringApplication.exit runs the context's shutdown, collects the exit code every
        // ExitCodeGenerator bean contributes (VesperaCli is one), and hands it to System.exit, which
        // is also what makes `vespera run` usable from a script.
        System.exit(SpringApplication.exit(application.run(args)));
    }
}
