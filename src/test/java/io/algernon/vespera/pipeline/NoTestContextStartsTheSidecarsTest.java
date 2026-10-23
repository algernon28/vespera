package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * A context Spring's test support builds holds no sidecar starter, because only {@code VesperaApplication.main}
 * registers one (ADR-323 §1): so no test that runs a command starts the Docker engine or a container.
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class NoTestContextStartsTheSidecarsTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @Story("A command that uses no sidecar starts none")
    @DisplayName("A context built for a test holds no sidecar starter")
    void aTestContextHoldsNoStarter() {
        claim("there is no sidecar starter in the context, so a command run in it starts nothing",
                () -> assertThat(context.getBeanProvider(SidecarStarter.class).getIfAvailable()).isNull());
    }
}
