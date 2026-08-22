package io.algernon.vespera;

import org.springframework.boot.SpringApplication;

/**
 * Development entry point: the real application, with its sidecars supplied by Testcontainers
 * instead of by {@code compose.yaml}.
 * <p>
 * Run this class rather than {@link VesperaApplication} when you want throwaway Chroma and Ollama
 * containers that are created on start and destroyed on exit. Run {@link VesperaApplication}
 * directly when you want the long-lived containers declared in {@code compose.yaml}, which
 * {@code spring-boot-docker-compose} starts and stops for you.
 * <p>
 * Both paths honour the same decision: the tool owns its own sidecars, so neither asks the operator
 * to start a service by hand (ADR-011).
 *
 * @see TestcontainersConfiguration for the containers this adds
 */
public class TestVesperaApplication {

    static void main(String[] args) {
        SpringApplication.from(VesperaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
