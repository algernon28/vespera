package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * One bound of the table of refusals in ADR-330 §9, held of the whole job: a value named by configuration that no run
 * can be made under stops {@code vespera run} at its start, in the record's one line and with no stack trace, no run
 * made; and the same value stands aside when the profile names another. Each class nested here is a context of its own,
 * because the configuration is a property of the context.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class APresetNoRunCanBeMadeUnderIsRefusedInvocationTest {

    /** What a stack frame looks like in the output, which a refusal in one line does not print. */
    private static final String A_STACK_FRAME = "\tat ";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @BeforeAll
    static void aCorpusAndASeed() throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** What each value's own class adds to: one invocation, and the three claims of a refusal. */
    @Epic("Pipeline")
    @Feature("What a piece of work is identified by")
    abstract static class Refused {

        @Autowired
        private VesperaCli cli;

        @Autowired
        private ProfileStore profileStore;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        /** The record's line for this value, word for word. */
        abstract String theLine();

        @Test
        @Story("A value no run can be made under stops the invocation")
        @DisplayName("It stops the invocation in one line, at its start, before anything is walked or recorded")
        void stopsInOneLineAtTheStart(CapturedOutput output) {
            everyGateOpen(profileStore);
            int before = output.getAll().length();

            cli.run("run", root.toString());
            String said = output.getAll().substring(before);

            claim("the invocation ends non-zero, because it was stopped",
                    () -> assertThat(cli.getExitCode()).isNotZero());
            claim("exactly one line says the record's words for the value, and no stack trace stands in its place",
                    () -> {
                        assertThat(said.lines().filter(line -> line.contains(theLine())).toList()).hasSize(1);
                        assertThat(said).doesNotContain(A_STACK_FRAME);
                    });
            claim("no run was made and no walk begun, so the value stopped the invocation at its start and nothing was"
                            + " recorded",
                    () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run", Integer.class)).isZero());
        }
    }

    private static void everyGateOpen(ProfileStore profileStore) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    // ---- the scoring run

    @Nested
    @TestPropertySource(properties = "vespera.clustering.resolution=0")
    @DisplayName("A resolution of 0")
    class AResolutionOfZero extends Refused {
        @Override
        String theLine() {
            return "vespera.clustering.resolution is 0: a number above 0";
        }

    }

    // ---- where nothing is refused

    /** A resolution the profile stands in place of is used by no run, so nothing is refused of it. */
    @Nested
    @TestPropertySource(properties = "vespera.clustering.resolution=0.0") // 0.0, so as not to share AResolutionOfZero's cached context and its runs
    @DisplayName("A resolution of 0 under a profile that names another")
    @Epic("Pipeline")
    @Feature("What a piece of work is identified by")
    class AResolutionOfZeroThatTheProfileStandsInPlaceOf {

        @Autowired
        private VesperaCli cli;

        @Autowired
        private ProfileStore profileStore;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        @Story("A value no run can be made under stops the invocation")
        @DisplayName("The invocation is not stopped: the profile's value names the scoring run")
        void stopsNothing() throws IOException {
            everyGateOpen(profileStore);
            Path profile = profileStore.file();
            Files.writeString(profile, Files.readString(profile, StandardCharsets.UTF_8)
                    .replaceAll("(?m)^clusterResolution:\\R(  .*\\R)*", "")
                    + "clusterResolution:\n  value: \"2.5\"\n  provenance: \"set by this test\"\n", StandardCharsets.UTF_8);

            cli.run("run", root.toString());

            claim("the invocation ends well, and the one scoring run says the profile's resolution",
                    () -> {
                        assertThat(cli.getExitCode()).isZero();
                        assertThat(jdbcTemplate.queryForList(
                                        "SELECT config_consumed FROM run WHERE stage = 'embedding-scoring'", String.class))
                                .singleElement().asString().endsWith(",\"clusterResolution\":2.5}");
                    });
        }

        /** The working directory is the outer class's, and the next class's context reads the file as it is built. */
        @AfterEach
        void takeTheKeyOutOfTheProfile() throws IOException {
            Files.deleteIfExists(profileStore.file());
        }
    }
}

