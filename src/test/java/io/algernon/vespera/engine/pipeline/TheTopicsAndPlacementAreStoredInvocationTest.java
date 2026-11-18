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
 * The relevance-scoring step keeps what it finds and places (ADR-409 §2, §5): the topics that stand at the breadth
 * floor, each keyed by its first passage and holding its passages, the seeds left feeding no topic, and every scored
 * document's winning topic and the topics it fits, all under the embedding-scoring run.
 *
 * <p>On {@link PlacementIsReadInTheScoringPassInvocationTest}'s fixture: this machine's breadth floor is {@value
 * #THE_FLOOR} and its neighbours for the topics {@value #TWO_NEIGHBOURS}; a seed of {@link
 * SeedScriptedExtractionBeans#ON_SLUICES} converts to a text of its own that embeds along one axis, one of {@link
 * SeedScriptedExtractionBeans#ON_PUMPS} along another, and every other file to one text that embeds to the vector of
 * ones. Every row is read under the scoring run of the test's own corpus root, the database being the class's.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class TheTopicsAndPlacementAreStoredInvocationTest {

    /** This machine's breadth floor: a topic of two thirds of the passages stands, one of a third does not. */
    private static final double THE_FLOOR = 0.6;

    /** Two neighbours, so that each passage's neighbours are passages of its own word, and two words two topics. */
    private static final int TWO_NEIGHBOURS = 2;

    /** A word whose text embeds along the first axis. */
    private static final String SLUICES = "sluices";

    /** A word whose text embeds along the second axis, a tenth of the way towards the first. */
    private static final String PUMPS = "pumps";

    /** The one seed of the first test, whose one passage is its one topic's first. */
    private static final String THE_SEED = "seed.txt";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.topics.breadth-floor", () -> String.valueOf(THE_FLOOR));
        registry.add("vespera.topics.neighbours", () -> String.valueOf(TWO_NEIGHBOURS));
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void theTwoWordsEmbedApart() {
        EmbeddingScriptedBeans.servesEveryModel();
        EmbeddingScriptedBeans.embedsTextHolding(SLUICES, 1, 0, 0, 0, 0, 0, 0, 0);
        EmbeddingScriptedBeans.embedsTextHolding(PUMPS, 0.1f, 1, 0, 0, 0, 0, 0, 0);
    }

    @AfterEach
    void everyTextEmbedsAsBeforeAgain() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("The topics and each document's place are kept")
    @DisplayName("The one topic, its one passage and the one document's topic are kept under the scoring run, with no link and no seed left out")
    void theTopicItsPassageAndTheSurvivorsWinningTopicAreStoredUnderTheScoringRun(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document of " + root.getFileName());
        Files.writeString(seeds.resolve(THE_SEED), "a seed document of " + seeds.getFileName());
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        String scoring = theScoringRunOf(root);

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("one topic is kept under the scoring run, keyed by the seed's path and its first chunk, 0, and holding"
                        + " its one passage",
                () -> assertThat(rows("SELECT seed_path || ' ' || chunk_index || ' ' || passage_count FROM topic"
                                + " WHERE run_id = ? ORDER BY seed_path, chunk_index", scoring))
                        .containsExactly(THE_SEED + " 0 1"));
        claim("its one passage is kept, by the seed's path and the chunk's index, as lying in that topic",
                () -> assertThat(rows("SELECT seed_path || ' ' || chunk_index || ' in ' || topic_seed_path || ' '"
                                + " || topic_chunk_index FROM topic_passage WHERE run_id = ?", scoring))
                        .containsExactly(THE_SEED + " 0 in " + THE_SEED + " 0"));
        claim("the one corpus document is placed under that topic, its winning topic kept beside its score",
                () -> assertThat(rows("SELECT f.path || ' in ' || t.topic_seed_path || ' ' || t.topic_chunk_index"
                                + " FROM winning_topic t JOIN file_occurrence f ON f.id = t.occurrence_id"
                                + " WHERE t.run_id = ?", scoring))
                        .containsExactly("corpus.txt in " + THE_SEED + " 0"));
        claim("no other topic is linked to it, there being none, and no seed is left feeding no topic",
                () -> {
                    assertThat(rows("SELECT topic_seed_path FROM see_also WHERE run_id = ?", scoring)).isEmpty();
                    assertThat(rows("SELECT seed_path FROM seed_feeding_no_topic WHERE run_id = ?", scoring)).isEmpty();
                });
    }

    @Test
    @Story("The topics and each document's place are kept")
    @DisplayName("A topic the breadth floor drops is not kept, its seeds are listed, and the document goes under the one that stands")
    void aTopicTheFloorDropsIsNotStoredAndItsSeedsAreListed(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(
                root.resolve(SeedScriptedExtractionBeans.A_DOCUMENT_ON_PUMPS), "a corpus document of " + root.getFileName());
        List<String> onSluices = SeedScriptedExtractionBeans.ON_SLUICES.subList(0, 4);
        List<String> onPumps = SeedScriptedExtractionBeans.ON_PUMPS.subList(0, 2);
        for (String name : onSluices) {
            Files.writeString(seeds.resolve(name), "a seed document " + name + " of " + seeds.getFileName());
        }
        for (String name : onPumps) {
            Files.writeString(seeds.resolve(name), "a seed document " + name + " of " + seeds.getFileName());
        }
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        String scoring = theScoringRunOf(root);
        String theKey = onSluices.getFirst() + " 0";

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("one topic is kept, the four seeds on sluices, four of the six passages at a floor of " + THE_FLOOR
                        + ", keyed by the first of them in path order; the two on pumps, two of six, are no topic",
                () -> assertThat(rows("SELECT seed_path || ' ' || chunk_index || ' ' || passage_count FROM topic"
                                + " WHERE run_id = ? ORDER BY seed_path, chunk_index", scoring))
                        .containsExactly(theKey + " 4"));
        claim("its four passages are kept, in path order, each as lying in it, and no passage of the dropped topic is",
                () -> assertThat(rows("SELECT seed_path || ' ' || chunk_index || ' in ' || topic_seed_path || ' '"
                                + " || topic_chunk_index FROM topic_passage WHERE run_id = ?"
                                + " ORDER BY seed_path, chunk_index", scoring))
                        .containsExactly(onSluices.stream().map(name -> name + " 0 in " + theKey).toArray(String[]::new)));
        claim("the two seeds on pumps are listed as feeding no topic, in path order",
                () -> assertThat(rows("SELECT seed_path FROM seed_feeding_no_topic WHERE run_id = ? ORDER BY seed_path",
                                scoring))
                        .containsExactlyElementsOf(onPumps));
        claim("and the one document, whose text is on pumps, is placed under the one topic that stands",
                () -> assertThat(rows("SELECT topic_seed_path || ' ' || topic_chunk_index FROM winning_topic"
                                + " WHERE run_id = ?", scoring))
                        .containsExactly(theKey));
    }

    private List<String> rows(String query, String scoring) {
        return jdbcTemplate.queryForList(query, String.class, scoring);
    }

    private String theScoringRunOf(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'embedding-scoring' AND w.root = ?",
                String.class,
                Walk.canonicalRoot(root).toString());
    }
}
