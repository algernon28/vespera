package io.algernon.vespera;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
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
 * Because it asserts nothing, it contributes no assertion steps to the report. Its display name is
 * therefore the whole of what the report can say about it, which is why the name states the claim.
 * <p>
 * As the capability modules land, this is where {@code ApplicationModules.verify()} belongs, so the
 * module boundaries (ADR-040) are checked by a test rather than by convention. It is not here yet
 * because there are no modules yet.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Epic("Architecture")
@Feature("Application startup")
@Link(name = "ADR-011", url = Adr.THE_TOOL_OWNS_ITS_SIDECARS, type = "adr")
@Link(name = "ADR-012", url = Adr.EXTRACTION_ENGINE_IS_CONFIGURABLE, type = "adr")
@Link(name = "ADR-013", url = Adr.OLLAMA_IS_THE_DEFAULT_ENGINE, type = "adr")
@Link(name = "ADR-039", url = Adr.CHROMA_IS_DERIVED, type = "adr")
class VesperaApplicationIT {

    @Test
    @Story("The application starts with the services it depends on")
    @DisplayName("The application starts against a running Chroma and Ollama")
    void contextLoads() {
    }

}
