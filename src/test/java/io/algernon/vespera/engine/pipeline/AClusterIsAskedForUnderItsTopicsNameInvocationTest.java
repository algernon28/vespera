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
 * The question a cluster's writing is asked by names the topic the cluster lies in by its recorded name, and no seed
 * (ADR-409 §9): a topic's key seed is only the seed whose passage comes first in visiting order (ADR-399 §5), not the
 * seed its documents scored highest against, so a question naming it would say something false of the documents.
 *
 * <p>Two seeds, {@value #KEY_SEED} and {@value #WINNING_SEED}, each converting to a text of its own on sluices, feed
 * one topic, keyed by the first in path order. A text holding the key seed's name embeds a little towards the second
 * axis and every other text on sluices along the first, so the two corpus documents, {@value #FIRST_DOCUMENT} and
 * {@value #SECOND_DOCUMENT}, score highest against the other seed, and the key seed is no document's winning seed. The
 * two form one cluster, and its question is the one this class reads.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Generation")
@Feature("Topics")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class AClusterIsAskedForUnderItsTopicsNameInvocationTest {

    /** The seed whose passage comes first in path order, which keys the topic. */
    private static final String KEY_SEED = "on-sluices-1.txt";

    /** The seed the corpus documents score highest against. */
    private static final String WINNING_SEED = "on-sluices-2.txt";

    /** The first corpus document. */
    private static final String FIRST_DOCUMENT = "on-sluices-3.txt";

    /** The second. */
    private static final String SECOND_DOCUMENT = "on-sluices-4.txt";

    /** A word every text of the four holds. */
    private static final String SLUICES = "sluices";

    /** The writing model the approval names. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void nothingIsScriptedAndTheKeySeedEmbedsApart() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
        EmbeddingScriptedBeans.embedsTextHolding(KEY_SEED, 1, 0.3f, 0, 0, 0, 0, 0, 0);
        EmbeddingScriptedBeans.embedsTextHolding(SLUICES, 1, 0, 0, 0, 0, 0, 0, 0);
    }

    @AfterEach
    void nothingIsScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("Each group is written under the topic it lies in")
    @DisplayName("A group's question names its topic by the topic's name, and not the seed the topic's key names")
    void theQuestionNamesTheTopicByItsRecordedNameAndNoSeed(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        for (String document : List.of(FIRST_DOCUMENT, SECOND_DOCUMENT)) {
            Files.writeString(root.resolve(document), "a corpus document " + document + " of " + root.getFileName());
        }
        for (String seed : List.of(KEY_SEED, WINNING_SEED)) {
            Files.writeString(seeds.resolve(seed), "a seed document " + seed + " of " + seeds.getFileName());
        }
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        int arrangedExit = cli.getExitCode();
        EveryGateOpen.approve(
                profileStore, ArrangementGate.shortNameOf(new RunId(runOf(root, "arrangement"))), GENERATION_MODEL);
        cli.run("run", root.toString());
        int writtenExit = cli.getExitCode();
        List<String> questions = GenerationScriptedBeans.promptsSent();
        String scoring = runOf(root, "embedding-scoring");
        String arrangement = runOf(root, "arrangement");
        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM topic_name WHERE run_id = ?", String.class, arrangement);
        String label = jdbcTemplate.queryForObject(
                "SELECT label FROM arranged_cluster WHERE run_id = ?", String.class, arrangement);

        claim("both invocations report success, the first stopping at the arrangement's approval",
                () -> assertThat(List.of(arrangedExit, writtenExit)).containsOnly(0));
        claim("the scenario: one topic stands, keyed by the first seed in path order",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT seed_path || ' chunk ' || chunk_index FROM topic WHERE run_id = ?",
                                String.class,
                                scoring))
                        .containsExactly(KEY_SEED + " chunk 0"));
        claim("and both documents scored highest against the other seed, so the key seed is no document's winning seed",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT DISTINCT s.path FROM relevance_score r"
                                        + " JOIN file_occurrence s ON s.id = r.winning_seed_occurrence_id"
                                        + " WHERE r.run_id = ?",
                                String.class,
                                scoring))
                        .containsExactly(WINNING_SEED));
        claim("one group was asked for, and its question says the group sits under the topic, by the name recorded for"
                        + " the topic under the arrangement run",
                () -> assertThat(questions).singleElement().satisfies(question -> assertThat(question)
                        .contains("The group is called \"" + label + "\", and it sits under the topic \"" + name
                                + "\".")));
        claim("and names neither seed, nor any seed document",
                () -> assertThat(questions).singleElement().satisfies(question -> assertThat(question)
                        .doesNotContain(KEY_SEED, WINNING_SEED)
                        .doesNotContainIgnoringCase("seed document")));
    }

    /** The one run of {@code stage} over {@code root}. */
    private String runOf(Path root, String stage) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }
}
