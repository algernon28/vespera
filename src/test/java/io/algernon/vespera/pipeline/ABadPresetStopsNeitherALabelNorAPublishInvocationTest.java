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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * A preset no run can be made under stops {@code vespera run} alone (ADR-330 §9): {@code vespera label} and {@code
 * vespera publish} mint no run and read no preset. Each here is refused for a reason of its own, nothing being there to
 * label or send, and says no line of the preset, which a check made as the application starts would. That a label with
 * answers to record goes on past the value is not shown: no run here can score under it to write a label file. It passes
 * today and carries no {@code @Disabled}.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "vespera.relevance.top-similarities=0")
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class ABadPresetStopsNeitherALabelNorAPublishInvocationTest {

    /** Each word of the refusal's opening, which a refusal of this value writes and nothing else does. */
    private static final String THE_REFUSAL = "vespera.relevance.top-similarities is 0";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Labelling is not stopped by a value only a run reads")
    void aLabelIsNotStoppedByIt(CapturedOutput output) {
        int before = output.getAll().length();

        cli.run("label");

        claim("no line of it is said",
                () -> assertThat(output.getAll().substring(before)).doesNotContain(THE_REFUSAL));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Publishing is not stopped by a value only a run reads")
    void aPublishIsNotStoppedByIt(CapturedOutput output) {
        int before = output.getAll().length();

        cli.run("publish");

        claim("no line of it is said",
                () -> assertThat(output.getAll().substring(before)).doesNotContain(THE_REFUSAL));
    }
}
