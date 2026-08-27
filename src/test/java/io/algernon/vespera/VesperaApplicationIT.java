package io.algernon.vespera;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the application context starts, with the sidecars of
 * {@link TestcontainersConfiguration} present.
 * <p>
 * {@code contextLoads} asserts nothing itself — the test fails only if the context cannot be built.
 * That is a low bar, but it is the bar that catches the failures this project's dependency set is
 * prone to: a starter on the classpath whose auto-configuration demands a schema or a connection
 * that does not exist. Requires a running Docker daemon.
 * <p>
 * As the capability modules land, this is where {@code ApplicationModules.verify()} belongs, so the
 * module boundaries (ADR-040) are checked by a test rather than by convention. It is not here yet
 * because there are no modules yet.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class VesperaApplicationTests {

    @Test
    void contextLoads() {
    }

}
