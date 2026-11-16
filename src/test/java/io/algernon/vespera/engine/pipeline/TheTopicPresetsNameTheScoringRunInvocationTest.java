package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * The two numbers the topics are found at are presets, and each enters the identity of the embedding-scoring run, under
 * which the topics are written, where it is not the value that ships (ADR-399, under ADR-330 §9's rule): this machine
 * names a resolution of {@value #A_RESOLUTION} and {@value #A_NUMBER_OF_NEIGHBOURS} neighbours, and the scoring run's
 * recorded configuration carries both, the resolution first.
 *
 * <p>{@code RunIdentityGoldenTest} holds the text of every run's identity with the presets at their shipped values,
 * and passes unchanged once these two are presets: at the shipped values neither is written.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("826")
@Link(name = "ADR-399", url = Adr.TOPICS_ARE_COMMUNITIES_OF_THE_SEED_PASSAGES, type = "adr")
class TheTopicPresetsNameTheScoringRunInvocationTest {

    /** A resolution other than the shipped one, and written by Java as it is here. */
    private static final double A_RESOLUTION = 2.5;

    /** A number of neighbours other than the shipped one. */
    private static final int A_NUMBER_OF_NEIGHBOURS = 7;

    /** The two members, as the scoring run's recorded configuration writes them, side by side and in this order. */
    private static final String THE_MEMBERS =
            "\"topicResolution\":" + A_RESOLUTION + ",\"topicNeighbours\":" + A_NUMBER_OF_NEIGHBOURS;

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.topics.resolution", () -> String.valueOf(A_RESOLUTION));
        registry.add("vespera.topics.neighbours", () -> String.valueOf(A_NUMBER_OF_NEIGHBOURS));
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void aCorpusOfOneDocumentAndOneSeed() throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    @Test
    @Story("The topics are found at two preset numbers")
    @DisplayName("A machine naming its own resolution and number of neighbours for the topics gets a scoring run naming both")
    void bothValuesEnterTheScoringRun() {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(EveryGateOpen.BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EveryGateOpen.EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());

        cli.run("run", root.toString());

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        List<String> configurations = jdbcTemplate.queryForList(
                "SELECT config_consumed FROM run WHERE stage = 'embedding-scoring' AND config_consumed LIKE ?",
                String.class,
                "%" + Walk.canonicalRoot(root).toString().replace("\\", "\\\\") + "%");
        claim("its one scoring run's recorded configuration names the resolution and then the number of neighbours"
                        + " this machine gave",
                () -> assertThat(configurations).singleElement().asString().contains(THE_MEMBERS));
    }
}
