package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole application starts in-process with no Docker daemon, and holds no vector store and nothing
 * built for Chroma (ADR-214, superseding ADR-142).
 *
 * <p>ADR-142 kept a Chroma store configured and only deferred its connection, and its test showed the
 * deferral by asking for the store and meeting a refused connection. ADR-214 removes the store, so there
 * is nothing to ask for: what is left to hold is that the application still starts, which that test was
 * the one class under {@code ./mvnw test} to show for the whole context, and that no bean of a vector store
 * type, or named for one or for Chroma, is declared in it.
 *
 * <p>The bean types are read from the definitions without building any bean, so a lazy store, which
 * would reach for Chroma once built, is found here without being built.
 *
 * <p>Written before the change it pins: against the tree it was written on, the first test passes and the
 * second fails, on the Chroma store, its client and their configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
@Epic("Architecture")
@Feature("Application startup")
@Issue("352")
@Link(name = "ADR-214", url = Adr.CHROMA_IS_REMOVED_AND_VECTORS_LIVE_IN_SQLITE_ALONE, type = "adr")
class TheApplicationStartsWithNoVectorStoreTest {

    /** Where Spring AI's vector store types live, the interface and every store's own package alike. */
    private static final Set<String> VECTOR_STORE_PACKAGES =
            Set.of("org.springframework.ai.vectorstore.", "org.springframework.ai.chroma.");

    /** Words a bean built for a vector store or for Chroma is named with, compared without regard to case. */
    private static final Set<String> VECTOR_STORE_NAMES = Set.of("vectorstore", "chroma");

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    @Story("The application starts with no vector database")
    @DisplayName("The application starts with no Docker daemon and no sidecar running")
    void theApplicationStarts() {
        claim(
                "the whole application context is built and running, with no container started for it, so"
                        + " nothing it builds while starting needs a sidecar",
                () -> assertThat(context.isActive()).isTrue());
    }

    @Test
    @Story("The application starts with no vector database")
    @DisplayName("The application declares no vector store and nothing built for Chroma")
    void theApplicationDeclaresNoVectorStore() {
        ConfigurableListableBeanFactory beans = context.getBeanFactory();
        Set<String> forAVectorStore = new TreeSet<>();
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name, false);
            String typeName = type == null ? "" : type.getName();
            if (VECTOR_STORE_PACKAGES.stream().anyMatch(typeName::startsWith)
                    || VECTOR_STORE_NAMES.stream().anyMatch(word -> name.toLowerCase(Locale.ROOT).contains(word))) {
                forAVectorStore.add(name + " (" + typeName + ")");
            }
        }

        claim(
                "the context declares beans, so an empty answer below is not an empty context",
                () -> assertThat(beans.getBeanDefinitionCount()).isPositive());
        claim(
                "no bean in it is of a vector store type or named for a vector store or for Chroma, whether built"
                        + " already or waiting to be built when first asked for",
                () -> assertThat(forAVectorStore).as("beans still declared for a vector store").isEmpty());
    }
}
