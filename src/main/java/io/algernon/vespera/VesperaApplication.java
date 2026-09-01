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
        application.run(args);
    }
}
