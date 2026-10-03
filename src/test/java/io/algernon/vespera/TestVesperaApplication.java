package io.algernon.vespera;

import org.springframework.boot.SpringApplication;

/**
 * Development entry point: the real application, with the Testcontainers containers of
 * {@link TestcontainersConfiguration} added to it.
 * <p>
 * Those containers are throwaway Chroma, Ollama and docling-serve containers, created on start and
 * destroyed on exit, and they are the only containers this entry point starts. No Docker Compose
 * support is on any classpath (ADR-179 §1), so the containers {@code compose.yaml} declares are never
 * started or stopped from here.
 * <p>
 * Run {@link VesperaApplication} directly when you want the {@code compose.yaml} containers instead.
 * Start them first, with the {@code up} line the README gives, exactly as for the packaged jar: no entry
 * point starts or stops them (ADR-158, ADR-179).
 *
 * @see TestcontainersConfiguration for the containers this adds
 */
public class TestVesperaApplication {

    static void main(String[] args) {
        SpringApplication.from(VesperaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
