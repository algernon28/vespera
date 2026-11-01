package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code vespera publish} writes no {@code effective-values.properties} (ADR-330 §1): it reads no profile value, and
 * its environment holds the one credential a process of the jar holds. A guard: it passes before the writer exists, and
 * a writer put where every command starts turns it red.
 *
 * <p>The publish here is refused, having nothing drawn to send, since the slice holds no Confluence to send to. So it
 * sees a writer ahead of the command's first refusal, and does not see one after it, which only a publish that sends
 * could show.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class VesperaPublishWritesNoEffectiveValuesInvocationTest {

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A publish writes no file of what the jar would use")
    void aPublishWritesNoFile() {
        cli.run("publish");

        claim("the publish was refused, nothing being drawn to send, as this test's slice means it to be",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and no file of what the jar would use is in the working folder",
                () -> assertThat(workingDirectory.resolve("effective-values.properties")).doesNotExist());
    }
}
