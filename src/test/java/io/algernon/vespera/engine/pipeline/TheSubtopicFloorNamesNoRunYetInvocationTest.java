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
 * The subtopic floor is shipped and refused when invalid, but read by no step until #832 wires the division into the
 * clustering step, and so names no run (ADR-406 §5): this machine names a floor of {@value #A_FLOOR}, other than the
 * shipped 300, and the scoring run's recorded configuration still carries no {@code subtopicFloor}.
 *
 * <p>#832 replaces this test with its opposite, beside {@code TheClusteringPresetsInvocationTest}: once the step reads
 * the floor, a value other than 300 is written into the scoring run (ADR-406 §7).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Clustering")
@Issue("831")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class TheSubtopicFloorNamesNoRunYetInvocationTest {

    /** A floor other than the shipped one, and the smallest the preset accepts. */
    private static final int A_FLOOR = 1;

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
    @DisplayName("A machine naming its own subtopic floor gets a scoring run naming none, until the clustering step reads it")
    void noMemberIsWrittenBeforeTheStepReadsTheFloor() {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(EveryGateOpen.BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EveryGateOpen.EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());

        cli.run("run", root.toString());

        claim("the invocation ended well, a floor of 1 being one the preset accepts",
                () -> assertThat(cli.getExitCode()).isZero());
        List<String> configurations = jdbcTemplate.queryForList(
                "SELECT config_consumed FROM run WHERE stage = 'embedding-scoring' AND config_consumed LIKE ?",
                String.class,
                "%" + Walk.canonicalRoot(root).toString().replace("\\", "\\\\") + "%");
        claim("its one scoring run's recorded configuration names no subtopic floor, the floor deciding nothing it did",
                () -> assertThat(configurations).singleElement().asString().doesNotContain("subtopicFloor"));
    }
}
