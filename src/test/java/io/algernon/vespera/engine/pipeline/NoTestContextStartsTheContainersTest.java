package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A context Spring's test support builds holds no container starter, because only {@code VesperaApplication.main}
 * registers one (ADR-323 §1): so no test that runs a command starts the Docker engine or a container. The context
 * names a working directory of its own, as every invocation test's does, since the jar names none (ADR-436 §1).
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The containers")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class NoTestContextStartsTheContainersTest {

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void theWorkingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @Story("A command that uses no container starts none")
    @DisplayName("A context built for a test holds no container starter")
    void aTestContextHoldsNoStarter() {
        claim("there is no container starter in the context, so a command run in it starts nothing",
                () -> assertThat(context.getBeanProvider(ContainerStarter.class).getIfAvailable()).isNull());
    }
}
