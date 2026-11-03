package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * An invocation refused at its start leaves {@code effective-values.properties} as the last one left it (ADR-330 §1). A
 * test leaves a marker in the file in place of a first invocation's text, since a writer ahead of the refusal could write
 * the same bytes back. This passes today and carries no {@code @Disabled}: a writer ahead of the refusal is what it sees.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class ARefusedInvocationWritesNoEffectiveValuesInvocationTest {

    /** What a test leaves in the file, which no writer of the jar would write. */
    private static final String A_MARKER = "a marker this test left\n";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    private Path file;

    @BeforeEach
    void aMarkerInTheFile() throws IOException {
        file = workingDirectory.resolve("effective-values.properties");
        Files.writeString(file, A_MARKER, StandardCharsets.UTF_8);
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A run refused for naming no root leaves the file as the last invocation wrote it")
    void aRefusedInvocationLeavesTheFileAsItWas() {
        cli.run("run");

        claim("the invocation was refused, having no root to walk",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and what this test left in the file stands, so nothing wrote the file, even the same bytes",
                () -> assertThat(file).hasContent(A_MARKER));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A label refused at its start leaves the file as the last invocation wrote it")
    void aLabelRefusedAtItsStartLeavesTheFileAsItWas() {
        cli.run("label", "--auto", "a-file-it-may-not-be-given.yaml");

        claim("the label was refused, being given a file with --auto",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and what this test left in the file stands, so nothing wrote the file, even the same bytes",
                () -> assertThat(file).hasContent(A_MARKER));
    }
}
