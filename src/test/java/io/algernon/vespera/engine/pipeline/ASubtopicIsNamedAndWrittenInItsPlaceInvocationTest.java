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
 * A subtopic's place reaches what is asked of it: its name is asked from its survivors' leading chunks strongest first
 * (ADR-409 §6), and at 6b its clusters are walked, and numbered for the topic's overview, subtopic by subtopic in the
 * subtopics' order (ADR-409 §9, ADR-406 §4).
 *
 * <p>One seed of one passage gives one topic, and every corpus document is placed under it: the four files of {@link
 * SeedScriptedExtractionBeans#ON_SLUICES} and the three of {@link SeedScriptedExtractionBeans#ON_PUMPS}, each converting
 * to a text of its own holding its word and its file's name. A text holding {@value #SLUICES} embeds along the first
 * axis and one holding {@value #PUMPS} along the third, so the two are unlike; the first file on sluices, {@value
 * #SET_APART}, embeds half a step towards the second axis, so it is less like the rest of its subtopic than the other
 * three are, though its path comes first. At this machine's {@value #TWO_NEIGHBOURS} neighbours for clustering, each
 * document's neighbours are of its own word, and at its subtopic floor of {@value #THE_FLOOR} the topic is divided into
 * the four on sluices and the three on pumps, each one cluster. The four go first, being more; their subtopic's number
 * is whatever the communities give it, and no claim here reads it.
 *
 * <p><b>The scenario is built once for the class</b> (ADR-337 §4), since both tests read it: an invocation to the
 * approval, the approval, and an invocation that writes, each read as soon as it ended.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Arrangement")
@Feature("Topic names")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class ASubtopicIsNamedAndWrittenInItsPlaceInvocationTest {

    /** This machine's subtopic floor, under the seven documents, so that the one topic is divided. */
    private static final int THE_FLOOR = 3;

    /** This machine's neighbours for clustering: each document's two nearest are of its own word. */
    private static final int TWO_NEIGHBOURS = 2;

    /** A word whose text embeds along the first axis. */
    private static final String SLUICES = "sluices";

    /** A word whose text embeds along the third axis, unlike the first. */
    private static final String PUMPS = "pumps";

    /** The file on sluices whose text embeds half a step towards the second axis: the first in path order. */
    private static final String SET_APART = "on-sluices-1.txt";

    /** The writing model the approval names. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    @TempDir
    static Path workingDirectory;

    /** The corpus root of the scenario the class builds once. */
    @TempDir
    static Path theRoot;

    /** The seed folder of that scenario. */
    @TempDir
    static Path theSeeds;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.clustering.subtopic-floor", () -> String.valueOf(THE_FLOOR));
        registry.add("vespera.clustering.neighbours", () -> String.valueOf(TWO_NEIGHBOURS));
    }

    /**
     * What the scenario left.
     *
     * @param arrangedExit how the invocation to the approval ended
     * @param namingQuestions the naming questions it sent, in the order asked
     * @param writtenExit how the invocation that writes ended
     * @param clusterQuestions the questions that invocation sent for the clusters' writing, in the order asked, each
     *     as the word its documents are on
     * @param overviewSources each number the topic's overview was sent, with a path of the cluster at that number, in
     *     the numbers' order and each number's paths in path order
     */
    private record Seen(
            int arrangedExit,
            List<String> namingQuestions,
            int writtenExit,
            List<String> clusterQuestions,
            List<String> overviewSources) {}

    private static final Once<Seen> seen = new Once<>();

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void nothingIsScriptedAndTheWordsEmbedApart() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
        EmbeddingScriptedBeans.embedsTextHolding(SET_APART, 1, 0.5f, 0, 0, 0, 0, 0, 0);
        EmbeddingScriptedBeans.embedsTextHolding(SLUICES, 1, 0, 0, 0, 0, 0, 0, 0);
        EmbeddingScriptedBeans.embedsTextHolding(PUMPS, 0, 0, 1, 0, 0, 0, 0, 0);
    }

    @AfterEach
    void nothingIsScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("A name is asked from the passages most like the rest of the topic")
    @DisplayName("A subtopic's name is asked from its documents most like the rest of it first, those equally alike in path order")
    void aSubtopicsDocumentsAreSentStrongestFirst() {
        Seen seen = seen();
        List<String> onSluices = seen.namingQuestions().stream().filter(question -> question.contains(SLUICES)).toList();

        claim("the invocation reports success, stopping at the arrangement's approval",
                () -> assertThat(seen.arrangedExit()).isZero());
        claim("one name was asked from the documents on sluices, and it sent the three alike first, in their paths'"
                        + " order, and then the one set apart, though its path is the first of the four",
                () -> assertThat(onSluices).singleElement().satisfies(question -> assertThat(List.of(
                                question.indexOf("on-sluices-2.txt"),
                                question.indexOf("on-sluices-3.txt"),
                                question.indexOf("on-sluices-4.txt"),
                                question.indexOf(SET_APART)))
                        .doesNotContain(-1)
                        .isSorted()));
    }

    @Test
    @Story("Each topic gets a short overview of its groups")
    @DisplayName("The groups of a divided topic are written, and numbered for its overview, in the order of its subtopics")
    void theWalkAndTheOverviewsNumbersGoInTheSubtopicsOrder() {
        Seen seen = seen();

        claim("the invocation that writes reports success", () -> assertThat(seen.writtenExit()).isZero());
        claim("it asked for the group of the four on sluices first, and then for the group of the three on pumps, the"
                        + " subtopic holding more going first",
                () -> assertThat(seen.clusterQuestions()).containsExactly(SLUICES, PUMPS));
        claim("and the topic's overview was sent the group of the four as number 1 and the group of the three as number"
                        + " 2",
                () -> assertThat(seen.overviewSources()).containsExactly(
                        "1 on-sluices-1.txt",
                        "1 on-sluices-2.txt",
                        "1 on-sluices-3.txt",
                        "1 on-sluices-4.txt",
                        "2 on-pumps-1.txt",
                        "2 on-pumps-2.txt",
                        "2 on-pumps-3.txt"));
    }

    /** The scenario, built the first time a test asks for it. */
    private Seen seen() {
        return seen.get(() -> {
            for (String document : List.of(SeedScriptedExtractionBeans.ON_SLUICES, SeedScriptedExtractionBeans.ON_PUMPS)
                    .stream().flatMap(List::stream).toList()) {
                Files.writeString(theRoot.resolve(document), "a corpus document " + document + " of " + theRoot.getFileName());
            }
            Files.writeString(theSeeds.resolve("seed.txt"), "a seed document of " + theSeeds.getFileName());
            EveryGateOpen.throughStageFive(profileStore, theSeeds);

            cli.run("run", theRoot.toString());
            int arrangedExit = cli.getExitCode();
            List<String> namingQuestions = GenerationScriptedBeans.namingQuestionsSent();
            EveryGateOpen.approve(
                    profileStore, ArrangementGate.shortNameOf(new RunId(runOf("arrangement"))), GENERATION_MODEL);

            cli.run("run", theRoot.toString());
            List<String> clusterQuestions = GenerationScriptedBeans.promptsSent().stream()
                    .map(question -> question.contains(PUMPS) ? PUMPS : SLUICES)
                    .toList();
            List<String> overviewSources = jdbcTemplate.queryForList(
                    "SELECT s.number || ' ' || f.path FROM topic_overview_source s"
                            + " JOIN cluster_member m ON m.topic_seed_path = s.topic_seed_path"
                            + " AND m.topic_chunk_index = s.topic_chunk_index AND m.subtopic_ordinal = s.subtopic_ordinal"
                            + " AND m.cluster_ordinal = s.cluster_ordinal AND m.run_id = ?"
                            + " JOIN file_occurrence f ON f.id = m.occurrence_id"
                            + " WHERE s.run_id = ? ORDER BY s.number, f.path",
                    String.class,
                    runOf("embedding-scoring"),
                    runOf("generation"));
            return new Seen(arrangedExit, namingQuestions, cli.getExitCode(), clusterQuestions, overviewSources);
        });
    }

    /** The one run of {@code stage} over the scenario's root. */
    private String runOf(String stage) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(theRoot).toString());
    }
}
