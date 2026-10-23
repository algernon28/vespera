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

/**
 * Which system's commands start the Docker engine, read from the name the JVM reports for the system (ADR-323 §5):
 * macOS is asked first, because {@code darwin} contains {@code win}.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class HostSystemTest {

    @Test
    @Story("Vespera starts the Docker engine when it is not running")
    @DisplayName("Darwin and Mac OS X are macOS, Windows 11 is Windows, and Linux is Linux")
    void eachSystemNameIsReadAsItsSystem() {
        claim("Darwin, whose name contains win, is macOS", () -> assertThat(HostSystem.named("Darwin"))
                .isEqualTo(HostSystem.MAC));
        claim("Mac OS X is macOS", () -> assertThat(HostSystem.named("Mac OS X")).isEqualTo(HostSystem.MAC));
        claim("Windows 11 is Windows", () -> assertThat(HostSystem.named("Windows 11")).isEqualTo(HostSystem.WINDOWS));
        claim("and Linux is Linux", () -> assertThat(HostSystem.named("Linux")).isEqualTo(HostSystem.LINUX));
    }
}
