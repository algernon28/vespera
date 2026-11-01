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
 * scoring run's identity, in the place the record gives it, and the profile's resolution overrides the configured one,
 * as ADR-298 has it of the embedding model. A context of its own, because the configuration is a property of the
 * context.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Clustering")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("ADR-330: awaits the two presets in the scoring run's identity")
class TheClusteringPresetsFromConfigurationInvocationTest {

    private static final int DOCUMENTS = 3;

    /** A resolution finer than the shipped 1.0, named by configuration. */
    private static final String CONFIGURED_RESOLUTION = "2.5";

    /** Fewer neighbours than the shipped 15, named by configuration. */
    private static final String CONFIGURED_NEIGHBOURS = "1";

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

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
    static void aCorpusOfThreeDocumentsAndOneSeed() throws IOException {
        for (int document = 0; document < DOCUMENTS; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "a corpus document, the " + document + "th");
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("Both numbers named by configuration enter the scoring run's identity, the neighbours after the resolution, and the profile's resolution overrides the configured one")
    void configurationNamesBothAndTheProfileOverridesIt() throws IOException {
        everyGateOpen();
        cli.run("run", root.toString());
        claim("the scoring run's recorded configuration ends with the resolution and the neighbours, in that order",
                () -> assertThat(theScoringRunsConfigurations()).singleElement().asString()
                        .endsWith(",\"clusterResolution\":" + CONFIGURED_RESOLUTION + ",\"clusterNeighbours\":"
                                + CONFIGURED_NEIGHBOURS + "}"));

        everyGateOpen();
        Path profile = profileStore.file();
        Files.writeString(profile, Files.readString(profile, StandardCharsets.UTF_8)
                .replaceAll("(?m)^clusterResolution:\\R(  .*\\R)*", "")
                + "clusterResolution:\n  value: \"1.0\"\n  provenance: \"set by this test\"\n", StandardCharsets.UTF_8);
        cli.run("run", root.toString());

        claim("with the profile naming 1.0, that is what is used, so it is left out and the configured neighbours stay",
                () -> assertThat(theScoringRunsConfigurations()).hasSize(2).last().asString()
                        .doesNotContain("clusterResolution"));
    }

    private void everyGateOpen() {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so the embedding-model gate is open")
                .build());
    }

    private List<String> theScoringRunsConfigurations() {
        return jdbcTemplate.queryForList(
                "SELECT config_consumed FROM run WHERE stage = 'embedding-scoring' ORDER BY rowid", String.class);
    }
}
