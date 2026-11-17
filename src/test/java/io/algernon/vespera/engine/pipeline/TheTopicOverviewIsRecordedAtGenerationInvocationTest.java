package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.ledger.RunId;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The generation step asks each topic's overview after its walk over the clusters, and records it under the
 * generation run keyed by the topic: the writing, the asking it was believed at, and each number the call sent with
 * the cluster at that number (ADR-402 §6, §7; ADR-409 §4, §9).
 *
 * <p>One seed of one passage gives one topic, keyed by {@code seed.txt} and its first chunk, 0; its one corpus document
 * is one cluster, numbered 0, in a topic with no subtopics, -1. This fixture's model writes the cluster as it writes
 * every one, and the overview as {@link GenerationScriptedBeans#OVERVIEW_WRITING} followed by the number of the first
 * cluster its question sends, a writing the overview's check believes.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Generation")
@Feature("Topic overview")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class TheTopicOverviewIsRecordedAtGenerationInvocationTest {

    /** The writing model the approval names. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    /** The one topic's key, its first passage's seed path and chunk index. */
    private static final String THE_TOPIC = "seed.txt 0";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void nothingIsScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("Each topic gets a short overview of its groups")
    @DisplayName("A topic's overview is kept under the writing run, believed at the first asking, with the one group it was written over")
    void eachTopicsOverviewIsRecordedWithTheClustersItWasWrittenOver(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document of " + root.getFileName());
        Files.writeString(seeds.resolve("seed.txt"), "a seed document of " + seeds.getFileName());
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        EveryGateOpen.approve(profileStore, ArrangementGate.shortNameOf(new RunId(runOf(root, "arrangement"))), GENERATION_MODEL);

        cli.run("run", root.toString());
        String generation = runOf(root, "generation");

        claim("the invocation that writes reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the one topic's overview was asked once", () -> assertThat(GenerationScriptedBeans.overviewQuestionsSent())
                .hasSize(1));
        claim("its writing is kept under the generation run by the topic's key, as believed at the first asking",
                () -> assertThat(rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' asked ' || asked || ': '"
                                + " || writing FROM topic_overview WHERE run_id = ?", generation))
                        .containsExactly(THE_TOPIC + " asked 1: " + GenerationScriptedBeans.OVERVIEW_WRITING + " [1]"));
        claim("the one number its call sent, 1, is kept with the cluster at that number, the topic's one cluster, of no"
                        + " subtopic",
                () -> assertThat(rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' number ' || number"
                                + " || ' is subtopic ' || subtopic_ordinal || ' cluster ' || cluster_ordinal"
                                + " FROM topic_overview_source WHERE run_id = ? ORDER BY number", generation))
                        .containsExactly(THE_TOPIC + " number 1 is subtopic -1 cluster 0"));
        claim("and no fault is kept for the topic",
                () -> assertThat(rows("SELECT kind FROM topic_overview_fault WHERE run_id = ?", generation)).isEmpty());
    }

    private List<String> rows(String query, String run) {
        return jdbcTemplate.queryForList(query, String.class, run);
    }

    private String runOf(Path root, String stage) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }
}
