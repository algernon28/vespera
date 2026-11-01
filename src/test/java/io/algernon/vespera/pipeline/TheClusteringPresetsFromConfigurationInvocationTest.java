package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The two presets named by application configuration in place of the shipped file (ADR-330 §8, §9): each enters the
 * scoring run's identity, in the place the record gives it; the profile's resolution overrides the configured one, as
 * ADR-298 has it of the embedding model; and where the profile's holds nothing a run can use, the configured one
 * applies and not the shipped one. A context of its own, because the configuration is a property of the context.
 *
 * <p>The database is the class's, and each test walks a corpus root of its own, which is in the scoring run's
 * configuration: so each invocation mints a scoring run, and the last one minted is the one it arrived at.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Clustering")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the two clustering presets in the scoring run's identity")
class TheClusteringPresetsFromConfigurationInvocationTest {

    private static final int DOCUMENTS = 3;

    /** A resolution finer than the shipped 1.0, named by configuration. */
    private static final String CONFIGURED_RESOLUTION = "2.5";

    /** Fewer neighbours than the shipped 15, named by configuration. */
    private static final String CONFIGURED_NEIGHBOURS = "1";

    /** The two members ending a scoring run's configuration where the configuration here applies whole. */
    private static final String BOTH_MEMBERS = ",\"clusterResolution\":" + CONFIGURED_RESOLUTION
            + ",\"clusterNeighbours\":" + CONFIGURED_NEIGHBOURS + "}";

    /** The one member ending it where the profile's resolution is the shipped one, in place of the configured one. */
    private static final String THE_NEIGHBOURS_ALONE = ",\"clusterNeighbours\":" + CONFIGURED_NEIGHBOURS + "}";

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.clustering.resolution", () -> CONFIGURED_RESOLUTION);
        registry.add("vespera.clustering.neighbours", () -> CONFIGURED_NEIGHBOURS);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void oneSeed() throws IOException {
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("Both numbers named by configuration enter the scoring run's identity, the neighbours after the resolution")
    void configurationNamesBothInTheScoringRun(@TempDir Path root) throws IOException {
        aCorpusOfThreeDocumentsIn(root);
        everyGateOpen();
        cli.run("run", root.toString());

        claim("the scoring run's recorded configuration ends with the resolution and the neighbours, in that order",
                () -> assertThat(theLastScoringRunsConfiguration()).endsWith(BOTH_MEMBERS));
    }

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("The profile's resolution overrides the configured one: naming the shipped value leaves the resolution out, and the configured neighbours stay")
    void theProfilesResolutionOverridesTheConfiguredOneAndTheNeighboursStay(@TempDir Path root) throws IOException {
        aCorpusOfThreeDocumentsIn(root);
        everyGateOpen();
        theProfileAlsoNamesTheResolution("1.0");
        cli.run("run", root.toString());

        claim("with the profile naming 1.0 that is what is used, so its member is left out, and the neighbours"
                        + " configured still end the configuration",
                () -> assertThat(theLastScoringRunsConfiguration())
                        .doesNotContain("clusterResolution")
                        .endsWith(THE_NEIGHBOURS_ALONE));
    }

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("Where the profile's resolution cannot be read, the one configuration names applies, not the shipped one")
    void anUnreadableResolutionFallsToTheConfiguredOne(@TempDir Path root) throws IOException {
        aCorpusOfThreeDocumentsIn(root);
        everyGateOpen();
        theProfileAlsoNamesTheResolution("0,5");
        cli.run("run", root.toString());

        claim("the scoring run is the one the configuration alone arrives at, the configured resolution in its"
                        + " configuration",
                () -> assertThat(theLastScoringRunsConfiguration()).endsWith(BOTH_MEMBERS));
    }

    private static void aCorpusOfThreeDocumentsIn(Path root) throws IOException {
        for (int document = 0; document < DOCUMENTS; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "a corpus document, the " + document + "th");
        }
    }

    /** Appends the key as the operator writes it, since the fixture names only the keys the jar knows. */
    private void theProfileAlsoNamesTheResolution(String value) throws IOException {
        Path profile = profileStore.file();
        Files.writeString(profile, Files.readString(profile, StandardCharsets.UTF_8)
                .replaceAll("(?m)^clusterResolution:\\R(  .*\\R)*", "")
                + "clusterResolution:\n  value: \"" + value + "\"\n  provenance: \"set by this test\"\n",
                StandardCharsets.UTF_8);
    }

    private void everyGateOpen() {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
    }

    /** The configuration of the scoring run minted last, the one this test's own corpus root makes its invocation's. */
    private String theLastScoringRunsConfiguration() {
        List<String> configurations = jdbcTemplate.queryForList(
                "SELECT config_consumed FROM run WHERE stage = 'embedding-scoring' ORDER BY rowid", String.class);
        claim("the invocation reached the scoring run", () -> assertThat(configurations).isNotEmpty());
        return configurations.getLast();
    }
}
