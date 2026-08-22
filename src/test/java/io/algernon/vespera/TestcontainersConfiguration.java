package io.algernon.vespera;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Declares Vespera's external sidecars as test-scoped containers.
 * <p>
 * Each {@code @ServiceConnection} bean makes Spring Boot derive the connection properties — host and
 * mapped port — from the running container, so no URL is configured anywhere for tests. The set here
 * mirrors {@code compose.yaml}; keep the two in step, because a service present in one and absent
 * from the other means tests and runtime disagree about what exists.
 * <p>
 * Two roles, both optional at runtime, neither authoritative for data:
 * <ul>
 *   <li><b>Chroma</b> — a derived, disposable projection of vectors that SQLite holds
 *       authoritatively (ADR-039). It may be dropped and rebuilt at any time, which is exactly why
 *       a fresh empty container per test run is harmless.</li>
 *   <li><b>Ollama</b> — the default serving engine for extraction (ADR-013), and the serving runtime
 *       is configuration rather than code (ADR-012).</li>
 * </ul>
 * <b>On the {@code latest} tags:</b> they float, and this design keys durable caches on engine and
 * model identity — extraction output on the full extractor identity (ADR-012), vectors on chunk
 * hash plus model identity (ADR-032). A tag that silently moves breaks the assumption those keys
 * rest on, so pin these images before any cache or calibrated threshold is worth keeping.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    ChromaDBContainer chromaContainer() {
        return new ChromaDBContainer(DockerImageName.parse("chromadb/chroma:latest"));
    }

    @Bean
    @ServiceConnection
    OllamaContainer ollamaContainer() {
        return new OllamaContainer(DockerImageName.parse("ollama/ollama:latest"));
    }

}
