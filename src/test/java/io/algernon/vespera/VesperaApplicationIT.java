package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the application context starts, with the sidecars of
 * {@link TestcontainersConfiguration} present, and the vector store it defers connects to the real
 * Chroma once asked for.
 * <p>
 * {@code contextLoads} asserts nothing itself — the test fails only if the context cannot be built.
 * That is a low bar, but it is the bar that catches the failures this project's dependency set is
 * prone to: a starter on the classpath whose auto-configuration demands a schema that does not
 * exist. Requires a running Docker daemon.
 * <p>
 * Starting no longer contacts any sidecar: the vector store is built on first use (ADR-142), and
 * neither Ollama's clients nor docling-serve's reach anything while the context starts. So the second test asks for the vector
 * store explicitly. It is the one place Spring AI's Chroma store is built against the pinned Chroma
 * image, collection initialisation included, and the success-side counterpart of
 * {@link VectorStoreIsReachedOnFirstUseTest}, which shows the same request refused when nothing
 * answers.
 * <p>
 * Module boundaries (ADR-040) are checked separately, by {@link ModuleBoundariesTest}: it runs
 * {@code ApplicationModules.verify()} and, unlike that call alone, also catches a module that ships
 * with no {@code allowedDependencies} declaration.
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
@Link(name = "ADR-142", url = Adr.THE_VECTOR_STORE_CONNECTS_WHEN_FIRST_USED, type = "adr")
class VesperaApplicationIT {

    @Autowired
    private ApplicationContext context;

    @Test
    @Story("The application starts with the services it depends on")
    @DisplayName("The application starts with Chroma, Ollama and the document converter running beside it")
    void contextLoads() {
    }

    @Test
    @Story("The vector store connects when it is first used")
    @DisplayName("Asked for, the vector store connects to the running Chroma")
    void askedForTheVectorStoreConnectsToTheRunningChroma() {
        claim("asking for the vector store builds it against the running Chroma without an error",
                () -> assertThat(context.getBean(VectorStore.class)).isNotNull());
    }

}
