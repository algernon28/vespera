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
import java.nio.charset.StandardCharsets;
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
 * A topic's name is recorded under the arrangement run as it is given, with where it came from and, where it fell
 * back, why; it is read back and never asked for again under that run; and the 6a gate's page heads each topic with
 * the name recorded, a fallback marked by the reason the model's name was not used (ADR-409 §4, §6, §7).
 *
 * <p>One seed of one passage gives one topic, keyed by {@code seed.txt} and its first chunk, 0, which this fixture's
 * model names {@code Scripted Topic 1}. Where the model is made to answer with an empty name, the topic falls back to
 * the title of the seed feeding it, which every conversion of this fixture carries. Two topics are made as {@link
 * PlacementIsReadInTheScoringPassInvocationTest} makes them: three seeds on pumps and three on sluices, each word's
 * text embedding along an axis of its own, at this machine's {@value #TWO_NEIGHBOURS} neighbours for the topics; the
 * topic on pumps has the key that comes first, and is named first.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Arrangement")
@Feature("Topic names")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class TopicNamesAreStoredAndShownInvocationTest {

    /** Two neighbours, so that each passage's neighbours are passages of its own word, and two words two topics. */
    private static final int TWO_NEIGHBOURS = 2;

    /** A word whose text embeds along the first axis. */
    private static final String SLUICES = "sluices";

    /** A word whose text embeds along the second axis, a tenth of the way towards the first. */
    private static final String PUMPS = "pumps";

    /** The seed of the one-topic tests, whose one passage is its topic's first, and so its key. */
    private static final String THE_SEED = "seed.txt";

    /** The name this fixture's model gives the first topic it is asked about. */
    private static final String THE_FIRST_NAME = GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1";

    /** An answer in the shape asked whose name is empty, which ADR-407 §6 turns down as {@code EMPTY}. */
    private static final String AN_EMPTY_NAME = "{\"name\":\"\"}";

    /** The paragraph the page puts under a topic's name that fell back for an empty answer, whole. */
    private static final String A_FALLBACK_FOR_AN_EMPTY_ANSWER = "<p>The naming model's name for this topic was not"
            + " used: the model's answer was empty. The name above stands in for it, taken from the seed feeding the"
            + " topic the most passages, or from the topic's place where that seed gives none.</p>\n";

    /** What the serving engine answers a question it refuses with, which says nothing of the question's length. */
    private static final String A_REFUSAL = "the serving engine is busy";

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
    @AfterEach
    void theTwoWordsEmbedApartAndNothingIsScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
        EmbeddingScriptedBeans.embedsTextHolding(SLUICES, 1, 0, 0, 0, 0, 0, 0, 0);
        EmbeddingScriptedBeans.embedsTextHolding(PUMPS, 0.1f, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @Story("Each topic is named before the arrangement is shown for approval")
    @DisplayName("A topic's name is kept under the arrangement, with the model having written it")
    void eachTopicsNameIsStoredUnderTheArrangementRunWithItsOrigin(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        oneSeedOfOnePassage(root, seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success, stopping at the arrangement's approval",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("the one topic's name is kept under the one arrangement, by the topic's key, as the model wrote it at"
                        + " the first asking, with no reason for a fallback",
                () -> assertThat(namesUnder(theArrangementOf(root)))
                        .containsExactly(THE_SEED + " 0 " + THE_FIRST_NAME + " WRITTEN none"));
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("The approval page heads each topic with the name kept for it")
    void theArrangementPageHeadsEachTopicWithItsStoredName(@TempDir Path root, @TempDir Path seeds) throws IOException {
        oneSeedOfOnePassage(root, seeds);

        cli.run("run", root.toString());
        String page = thePage();

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the page heads the one topic with the name the model gave it, and its line of counts follows the heading"
                        + " directly, no paragraph saying the name stands in for another",
                () -> assertThat(page).contains("<h2>" + THE_FIRST_NAME + "</h2>\n<p>1 document(s) in 1 group(s).</p>\n"));
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("A name that fell back is kept with the reason, and the page says under it why the model's was not used")
    void aFallbackNameIsStoredWithItsTurnDownAndMarkedOnThePage(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        oneSeedOfOnePassage(root, seeds);
        GenerationScriptedBeans.namesEveryTopicWith(AN_EMPTY_NAME);

        cli.run("run", root.toString());
        String page = thePage();

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the topic's name is the seed's own title, kept as a fallback turned down as empty",
                () -> assertThat(namesUnder(theArrangementOf(root))).containsExactly(
                        THE_SEED + " 0 " + SeedScriptedExtractionBeans.STUBBED_TITLE + " FALLBACK EMPTY"));
        claim("the page heads the topic with that name, and the paragraph directly under it says the model's name was"
                        + " not used because its answer was empty, and where the name shown comes from",
                () -> assertThat(page).contains(
                        "<h2>" + SeedScriptedExtractionBeans.STUBBED_TITLE + "</h2>\n" + A_FALLBACK_FOR_AN_EMPTY_ANSWER));
    }

    @Test
    @Story("Each topic is named before the arrangement is shown for approval")
    @DisplayName("A name given before the step was stopped is kept, and the next invocation asks only for the names it lacks")
    void aNameRecordedBeforeTheStepStoppedIsNotAskedForAgain(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document of " + root.getFileName());
        List<String> onPumps = SeedScriptedExtractionBeans.ON_PUMPS.subList(0, 3);
        List<String> onSluices = SeedScriptedExtractionBeans.ON_SLUICES.subList(0, 3);
        for (String name : List.of(onPumps, onSluices).stream().flatMap(List::stream).toList()) {
            Files.writeString(seeds.resolve(name), "a seed document " + name + " of " + seeds.getFileName());
        }
        EveryGateOpen.throughStageFive(profileStore, seeds);
        String onPumpsKey = onPumps.getFirst() + " 0";
        String onSluicesKey = onSluices.getFirst() + " 0";
        GenerationScriptedBeans.refuseEvery(question -> question.contains(SLUICES), A_REFUSAL);

        cli.run("run", root.toString());
        int stopped = cli.getExitCode();
        String arrangement = theArrangementOf(root);
        List<String> namedBeforeTheStop = namesUnder(arrangement);
        int askedBeforeTheStop = GenerationScriptedBeans.namingQuestionsSent().size();
        GenerationScriptedBeans.refuseEvery(question -> false, "");

        cli.run("run", root.toString());
        List<String> askedSince = GenerationScriptedBeans.namingQuestionsSent().subList(
                askedBeforeTheStop, GenerationScriptedBeans.namingQuestionsSent().size());

        claim("the first invocation failed, the engine refusing the question about the topic on sluices",
                () -> assertThat(stopped).isNotZero());
        claim("by then the topic on pumps, whose key comes first, had been named and its name kept, and the one on"
                        + " sluices had not",
                () -> assertThat(namedBeforeTheStop).containsExactly(onPumpsKey + " " + THE_FIRST_NAME + " WRITTEN none"));
        claim("the next invocation reports success, arriving at the same arrangement",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(theArrangementOf(root)).isEqualTo(arrangement);
                });
        claim("it asked one name, the topic on sluices', and no question of it was about pumps",
                () -> assertThat(askedSince).singleElement().satisfies(question -> assertThat(question)
                        .contains(SLUICES)
                        .doesNotContain(PUMPS)));
        claim("and both names stand under the arrangement, the one kept before the stop unchanged",
                () -> assertThat(namesUnder(arrangement)).containsExactly(
                        onPumpsKey + " " + THE_FIRST_NAME + " WRITTEN none",
                        onSluicesKey + " " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2 WRITTEN none"));
    }

    /** One corpus document and one seed of one passage, every gate up to the approval open. */
    private void oneSeedOfOnePassage(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document of " + root.getFileName());
        Files.writeString(seeds.resolve(THE_SEED), "a seed document of " + seeds.getFileName());
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /** The names kept under {@code arrangement}, in key order: the key, the name, the origin and the turn-down. */
    private List<String> namesUnder(String arrangement) {
        return jdbcTemplate.queryForList(
                "SELECT topic_seed_path || ' ' || topic_chunk_index || ' ' || name || ' ' || origin || ' '"
                        + " || IFNULL(turn_down, 'none') FROM topic_name WHERE run_id = ?"
                        + " ORDER BY topic_seed_path, topic_chunk_index",
                String.class,
                arrangement);
    }

    private String theArrangementOf(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = 'arrangement' AND w.root = ?",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    private static String thePage() throws IOException {
        return Files.readString(workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME), StandardCharsets.UTF_8);
    }
}
