package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The application reaches Chroma when the vector store is first asked for, and not before
 * (ADR-142).
 *
 * <p>The whole application is started with Chroma's client pointed at a port nothing listens on, so
 * the test holds whether or not a Chroma sidecar is up on the machine running it. Two claims, because
 * the startup alone cannot tell the fix from its wrong twin: switching the vector store off also lets
 * the context start, and it would fail the second claim with no such bean rather than with a refused
 * connection. That refusal is the evidence the store is still configured and was only deferred.
 *
 * <p>A third claim rules out the other wrong twin, global lazy initialisation, which passes both of
 * the above: the datasource stands for every other bean, and it still has to be built at start-up.
 *
 * <p>It needs no Docker daemon: the compose lifecycle is off, and nothing else in the context
 * reaches a sidecar while it starts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Epic("Architecture")
@Feature("Application startup")
@Link(name = "ADR-142", url = Adr.THE_VECTOR_STORE_CONNECTS_WHEN_FIRST_USED, type = "adr")
class VectorStoreIsReachedOnFirstUseTest {

    @DynamicPropertySource
    static void nothingAnswersForChroma(DynamicPropertyRegistry registry) throws IOException {
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("spring.ai.vectorstore.chroma.client.port", closedPort()::toString);
    }

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    @Story("The vector store connects when it is first used")
    @DisplayName("The application starts with no vector store reachable")
    void theApplicationStartsWithNoVectorStoreReachable() {
        claim("the application is running although nothing answers where the vector store is configured",
                () -> assertThat(context.isActive()).isTrue());
    }

    @Test
    @Story("The vector store connects when it is first used")
    @DisplayName("Asking for the vector store is what first tries to reach it")
    void askingForTheVectorStoreIsWhatFirstTriesToReachIt() {
        claim("the vector store is still configured, so asking for it tries to connect and is refused",
                () -> assertThatThrownBy(() -> context.getBean(VectorStore.class))
                        .hasRootCauseInstanceOf(ConnectException.class));
    }

    @Test
    @Story("The vector store connects when it is first used")
    @DisplayName("Only the vector store waits for first use")
    void onlyTheVectorStoreWaitsForFirstUse() {
        claim("the database connection pool is still built while the application starts, so a fault in it"
                        + " still surfaces there rather than at first use",
                () -> assertThat(context.getBeanFactory().getBeanDefinition("dataSource").isLazyInit()).isFalse());
    }

    /** A port nothing listens on: bound once to learn a free number, then released. */
    private static Integer closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
