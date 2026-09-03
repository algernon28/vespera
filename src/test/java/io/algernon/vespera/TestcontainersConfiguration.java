package io.algernon.vespera;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.springframework.test.context.DynamicPropertyRegistrar;
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
 *   <li><b>docling-serve</b> — the Docling sidecar (ADR-010, ADR-071). Unlike the two above it has no
 *       Spring Boot {@code ServiceConnection} support to derive a URL from, so it is a plain
 *       {@link GenericContainer} with a health-endpoint wait strategy, and its base URL is pushed into
 *       {@code vespera.docling.base-url} by {@link #doclingServeProperties} instead of an
 *       {@code @ServiceConnection} bean.</li>
 * </ul>
 * <b>On the tags:</b> both are pinned, and the reason is identity rather than reproducibility for
 * its own sake. This design keys durable caches on engine and model identity — extraction output on
 * the full extractor identity (ADR-012), vectors on chunk hash plus model identity (ADR-032) — and a
 * tag that silently moves breaks the assumption those keys rest on. {@code latest} moved for whoever
 * pulled next; a pinned tag moves in a commit.
 * <p>
 * A local image cache made this worse rather than better: Testcontainers does not re-pull a tag it
 * already holds, so a developer could sit on a months-old {@code latest} while CI pulled that day's,
 * with no commit between the two. The versions below are what {@code latest} resolved to when they
 * were pinned, so nothing changed the day this landed.
 * <p>
 * Raising them is a deliberate act: bump here and in {@code compose.yaml} together, in one commit,
 * and expect any calibrated threshold or durable cache keyed on engine identity to need revisiting.
 * <p>
 * Public only so an integration test living in its own module's package can {@code @Import} it —
 * {@code extraction}'s {@code DoclingClientIT} is the first. The {@code @Bean} methods stay
 * package-private, since Spring invokes them reflectively rather than through a compiled call.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Chroma 1.5.9, which is {@code sha256:1e0b73a1}. Keep in step with {@code compose.yaml}. */
    private static final String CHROMA_IMAGE = "chromadb/chroma:1.5.9";

    /** Ollama 0.33.2, which is {@code sha256:020e4134}. Keep in step with {@code compose.yaml}. */
    private static final String OLLAMA_IMAGE = "ollama/ollama:0.33.2";

    /** docling-serve v1.32.0 (CPU image). Keep in step with {@code compose.yaml}. */
    private static final String DOCLING_SERVE_IMAGE = "quay.io/docling-project/docling-serve-cpu:v1.32.0";

    /** The port docling-serve listens on inside its container (confirmed against the image's own metadata). */
    private static final int DOCLING_SERVE_PORT = 5001;

    @Bean
    @ServiceConnection
    ChromaDBContainer chromaContainer() {
        return new ChromaDBContainer(DockerImageName.parse(CHROMA_IMAGE));
    }

    @Bean
    @ServiceConnection
    OllamaContainer ollamaContainer() {
        return new OllamaContainer(DockerImageName.parse(OLLAMA_IMAGE));
    }

    /**
     * A plain container, not a dedicated Testcontainers module — none exists for docling-serve. The
     * health-endpoint wait strategy is ADR-071's own "readiness is checked once, lazily": here that
     * once is container start-up, standing in for the per-invocation check a later pipeline ticket
     * still owes stage 2's step.
     */
    @Bean
    GenericContainer<?> doclingServeContainer() {
        return new GenericContainer<>(DockerImageName.parse(DOCLING_SERVE_IMAGE))
                .withExposedPorts(DOCLING_SERVE_PORT)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200));
    }

    /**
     * Pushes the running container's mapped port into {@code vespera.docling.base-url}, since no
     * {@code @ServiceConnection} factory exists to do it declaratively (see the class javadoc). A
     * {@link DynamicPropertyRegistrar} bean is the {@code @TestConfiguration}-friendly equivalent of a
     * static {@code @DynamicPropertySource} method.
     */
    @Bean
    DynamicPropertyRegistrar doclingServeProperties(GenericContainer<?> doclingServeContainer) {
        return registry -> registry.add(
                "vespera.docling.base-url",
                () -> "http://%s:%d".formatted(doclingServeContainer.getHost(), doclingServeContainer.getMappedPort(DOCLING_SERVE_PORT)));
    }

}
