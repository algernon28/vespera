package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
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
 * The subtopic floor decides how the clustering step divides each topic, so a machine's value other than the shipped
 * 300 is another scoring run (ADR-406 §5, ADR-409 §8): this machine names a floor of {@value #A_FLOOR}, and the scoring
 * run's recorded configuration ends with it, as the last member appended.
 *
 * <p>{@code RunIdentityGoldenTest} holds the other side: at the shipped value no member is written.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Clustering")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class TheSubtopicFloorNamesTheScoringRunInvocationTest {

    /** A floor other than the shipped one, and the smallest the preset accepts. */
    private static final int A_FLOOR = 1;

    /** The tail of the scoring run's recorded configuration where only the subtopic floor differs from what ships. */
    private static final String THE_MEMBER = ",\"subtopicFloor\":" + A_FLOOR + "}";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void thisMachinesValue(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.clustering.subtopic-floor", () -> String.valueOf(A_FLOOR));
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
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A machine naming its own subtopic floor gets a scoring run that names it, last")
    void aFloorOtherThanTheShippedOneEntersTheScoringRun() {
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        claim("the invocation ended well, a floor of " + A_FLOOR + " being one the preset accepts",
                () -> assertThat(cli.getExitCode()).isZero());
        List<String> configurations = jdbcTemplate.queryForList(
                "SELECT config_consumed FROM run WHERE stage = 'embedding-scoring' AND config_consumed LIKE ?",
                String.class,
                "%" + Walk.canonicalRoot(root).toString().replace("\\", "\\\\") + "%");
        claim("its one scoring run's recorded configuration ends with the subtopic floor of " + A_FLOOR + ", the one"
                        + " member this machine's values add to it, because the clustering step divides each topic by it",
                () -> assertThat(configurations).singleElement().asString().endsWith(THE_MEMBER));
    }
}
