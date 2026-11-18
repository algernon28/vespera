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
 * A topic other than its winning one that a document fits is kept as a {@code see_also} row under the embedding-scoring
 * run (ADR-400 §2, ADR-409 §2): the row {@link TheTopicsAndPlacementAreStoredInvocationTest} finds none of, its
 * fixtures fitting no second topic.
 *
 * <p>Three seeds on sluices and three on pumps, at this machine's {@value #TWO_NEIGHBOURS} neighbours for the topics
 * and the shipped breadth floor, give two topics, each keyed by its first seed in path order. The one corpus document,
 * {@link SeedScriptedExtractionBeans#A_DOCUMENT_IN_THREE_PARTS}, is cut into three chunks, two holding the word
 * {@value #SLUICES} and one the word {@value #PUMPS}: a text holding the first embeds along one axis and one holding
 * the second along another, a tenth of the way towards the first. So its chunks are nearest the topic on sluices on
 * average, and one of its three, more than the 15 per cent ADR-400 §2 asks, is nearest the topic on pumps.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-400", url = Adr.THE_WINNING_TOPIC_IS_THE_CENTROID_ITS_CHUNKS_ARE_NEAREST_ON_AVERAGE, type = "adr")
class ADocumentIsLinkedFromATopicItFitsInvocationTest {

    /** Two neighbours, so that each passage's neighbours are passages of its own word, and two words two topics. */
    private static final int TWO_NEIGHBOURS = 2;

    /** How many seeds of each word: three passages, two of them each passage's neighbours. */
    private static final int SEEDS_OF_EACH_WORD = 3;

    /** A word whose text embeds along the first axis. */
    private static final String SLUICES = "sluices";

    /** A word whose text embeds along the second axis, a tenth of the way towards the first. */
    private static final String PUMPS = "pumps";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
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
    @DisplayName("A document with a part on another topic is kept under its own topic, and linked from the other")
    void aDocumentWithAPartOnAnotherTopicIsLinkedFromIt(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.A_DOCUMENT_IN_THREE_PARTS),
                "a corpus document of " + root.getFileName());
        List<String> onSluices = SeedScriptedExtractionBeans.ON_SLUICES.subList(0, SEEDS_OF_EACH_WORD);
        List<String> onPumps = SeedScriptedExtractionBeans.ON_PUMPS.subList(0, SEEDS_OF_EACH_WORD);
        for (String name : List.of(onSluices, onPumps).stream().flatMap(List::stream).toList()) {
            Files.writeString(seeds.resolve(name), "a seed document " + name + " of " + seeds.getFileName());
        }
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        String scoring = theScoringRunOf(root);
        String theDocument = SeedScriptedExtractionBeans.A_DOCUMENT_IN_THREE_PARTS;

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("two topics stand, the one on pumps and the one on sluices, each keyed by its first seed",
                () -> assertThat(rows("SELECT seed_path || ' ' || chunk_index FROM topic WHERE run_id = ?"
                                + " ORDER BY seed_path, chunk_index", scoring))
                        .containsExactly(onPumps.getFirst() + " 0", onSluices.getFirst() + " 0"));
        claim("the document is placed under the topic on sluices, which two of its three parts are nearest",
                () -> assertThat(rows("SELECT f.path || ' in ' || t.topic_seed_path || ' ' || t.topic_chunk_index"
                                + " FROM winning_topic t JOIN file_occurrence f ON f.id = t.occurrence_id"
                                + " WHERE t.run_id = ?", scoring))
                        .containsExactly(theDocument + " in " + onSluices.getFirst() + " 0"));
        claim("and it is linked from the topic on pumps, which its third part is nearest, by one row under the scoring"
                        + " run, and from no other",
                () -> assertThat(rows("SELECT f.path || ' also in ' || s.topic_seed_path || ' ' || s.topic_chunk_index"
                                + " FROM see_also s JOIN file_occurrence f ON f.id = s.occurrence_id"
                                + " WHERE s.run_id = ?", scoring))
                        .containsExactly(theDocument + " also in " + onPumps.getFirst() + " 0"));
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
