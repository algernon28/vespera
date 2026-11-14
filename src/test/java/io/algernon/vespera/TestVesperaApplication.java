package io.algernon.vespera;

import org.springframework.boot.SpringApplication;

/**
 * Development entry point: the real application, with the Testcontainers containers of
 * {@link TestcontainersConfiguration} added to it.
 * <p>
 * Those containers are throwaway Ollama and docling-serve containers, created on start and
 * destroyed on exit, and they are the only containers this entry point starts. No Docker Compose
 * support is on any classpath (ADR-179 §1), so the containers {@code compose.yaml} declares are never
 * started or stopped from here.
 * <p>
 * Run {@link VesperaApplication} directly when you want the {@code compose.yaml} containers instead: it
 * starts them where they are not answering (ADR-323 §1).
 *
 * <p>
 * It names both sidecars on a host under {@code .invalid}, which never resolves (RFC 6761), as JVM system
 * properties, so the starter, which reads them before Testcontainers has published its own, takes them for
 * another machine's and starts nothing beside the containers this entry point brings; the converter's address
 * Testcontainers registers, and Ollama's connection detail, come before them once published (ADR-389 §7).
 *
 * @see TestcontainersConfiguration for the containers this adds
 */
public class TestVesperaApplication {

    static void main(String[] args) {
        System.setProperty("vespera.docling.base-url", "http://sidecars.invalid:5001");
        System.setProperty("spring.ai.ollama.base-url", "http://sidecars.invalid:11434");
        SpringApplication.from(VesperaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
