package io.algernon.vespera;

import org.springframework.boot.SpringApplication;

/**
 * Development entry point: the real application, with the Testcontainers containers of
 * {@link TestcontainersConfiguration} added to it.
 * <p>
 * Those containers are throwaway Chroma, Ollama and docling-serve containers, created on start and
 * destroyed on exit. This class does not turn off {@code spring-boot-docker-compose}, which is on
 * this classpath, and nothing in the tree sets {@code spring.docker.compose.enabled} for it. Spring
 * Boot skips compose only when a JUnit or {@code org.springframework.boot.test} frame is on the
 * stack, and a {@code main} launch has none. So, by the code, the containers declared in
 * {@code compose.yaml} come up as well. That is read from the code and not measured, and neither is
 * which of the two sets the application's connection details then point at. Run
 * {@link VesperaApplication} directly when you want only the {@code compose.yaml} containers,
 * which {@code spring-boot-docker-compose} starts and stops for you.
 * <p>
 * Both paths honour the same decision: the tool owns its own sidecars, so neither asks the operator
 * to start a service by hand (ADR-011). The packaged jar is the one path that does ask: it carries
 * no compose support, and the operator starts {@code compose.yaml} before running it (ADR-158).
 *
 * @see TestcontainersConfiguration for the containers this adds
 */
public class TestVesperaApplication {

    static void main(String[] args) {
        SpringApplication.from(VesperaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
