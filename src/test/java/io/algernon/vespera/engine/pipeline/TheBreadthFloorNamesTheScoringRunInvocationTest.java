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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The breadth floor is a preset, and enters the identity of the embedding-scoring run, under which the topics are found
 * and the floor applied, where it is not the value that ships (ADR-401, under ADR-330 §9's rule): this machine names
 * {@value #A_NUMBER_OF_NEIGHBOURS} neighbours and a floor of {@value #A_FLOOR}, and the scoring run's recorded
 * configuration carries both, the floor after the neighbours.
 *
 * <p>{@code RunIdentityGoldenTest} holds the text of every run's identity with the presets at their shipped values, and
 * passes unchanged once the floor is a preset: at the shipped value it is not written.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("827")
@Link(name = "ADR-401", url = Adr.A_TOPIC_UNDER_THE_BREADTH_FLOOR_IS_DROPPED, type = "adr")
class TheBreadthFloorNamesTheScoringRunInvocationTest {

    /** A floor other than the shipped one, and written by Java as it is here. */
    private static final double A_FLOOR = 0.02;

    /** A number of neighbours other than the shipped one, so that the member the floor follows is written. */
    private static final int A_NUMBER_OF_NEIGHBOURS = 7;

    /** The two members, as the scoring run's recorded configuration writes them, side by side and in this order. */
    private static final String THE_MEMBERS =
            "\"topicNeighbours\":" + A_NUMBER_OF_NEIGHBOURS + ",\"topicBreadthFloor\":" + A_FLOOR;

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.topics.neighbours", () -> String.valueOf(A_NUMBER_OF_NEIGHBOURS));
        registry.add("vespera.topics.breadth-floor", () -> String.valueOf(A_FLOOR));
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
    @Story("A topic too narrow for the top level is not a topic")
    @DisplayName("A machine naming its own breadth floor for the topics gets a scoring run naming it")
    void theFloorEntersTheScoringRun() {
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
        claim("its one scoring run's recorded configuration names the number of neighbours and then the breadth floor"
                        + " this machine gave",
                () -> assertThat(configurations).singleElement().asString().contains(THE_MEMBERS));
    }
}
