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
import org.springframework.util.StringUtils;

/**
 * A crowded topic's subtopics are named at the head of the arrangement step after the topics, each from its own
 * survivors, recorded under the arrangement run by its number within its topic, and shown at the 6a gate under its
 * topic, in their order, read-only; a subtopic's name that falls back is marked as a topic's is (ADR-406 §6, ADR-407
 * §4, §7, §8; ADR-409 §6, §7).
 *
 * <p>One seed of one passage gives one topic, and every one of the {@value #THE_FLOOR} documents is placed under it.
 * Every vector of the fixture is the same, and at this machine's resolution of {@value #A_FINER_RESOLUTION} such
 * documents form a community each; at its subtopic floor of {@value #THE_FLOOR} the topic is divided into three
 * subtopics of one document each, which go in their numbers' order, 0, 1, 2, being of as many documents. This
 * fixture's model names the n-th thing it is asked about {@code Scripted Topic n}, so the topic, named first, is 1.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Arrangement")
@Feature("Topic names")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class SubtopicsAreNamedAndShownInvocationTest {

    /** This machine's subtopic floor, and the documents of the corpus: the one topic is crowded. */
    private static final int THE_FLOOR = 3;

    /** A resolution at which documents of one vector form a community each. */
    private static final double A_FINER_RESOLUTION = 2.5;

    /** The one topic's key, its first passage's seed path and chunk index. */
    private static final String THE_TOPIC = "seed.txt 0";

    /** An answer in the shape asked whose name is empty, which ADR-407 §6 turns down as {@code EMPTY}. */
    private static final String AN_EMPTY_NAME = "{\"name\":\"\"}";

    /** The paragraph the page puts under a subtopic's name that fell back for an empty answer, whole. */
    private static final String A_FALLBACK_FOR_AN_EMPTY_ANSWER = "<p>The naming model's name for this subtopic was not"
            + " used: the model's answer was empty. The name above stands in for it, taken from the document most like"
            + " the rest of the subtopic, or from the subtopic's place where that document gives none.</p>\n";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.clustering.subtopic-floor", () -> String.valueOf(THE_FLOOR));
        registry.add("vespera.clustering.resolution", () -> String.valueOf(A_FINER_RESOLUTION));
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
    @Story("Each topic is named before the arrangement is shown for approval")
    @DisplayName("Each subtopic is named after its topic, and its name is kept under its number within the topic")
    void eachSubtopicIsNamedAfterTheTopicAndStoredInItsOrder(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCrowdedTopic(root, seeds);

        cli.run("run", root.toString());
        String arrangement = theArrangementOf(root);

        claim("the invocation reports success, stopping at the arrangement's approval",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("four names were asked, one for the topic and one for each of its three subtopics",
                () -> assertThat(GenerationScriptedBeans.namingCallsMade()).isEqualTo(1 + THE_FLOOR));
        claim("the topic's name, asked first, is kept as the model wrote it",
                () -> assertThat(rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' ' || name || ' '"
                                + " || origin FROM topic_name WHERE run_id = ?", arrangement))
                        .containsExactly(THE_TOPIC + " " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1 WRITTEN"));
        claim("and each subtopic's name is kept under its number within the topic, asked in the subtopics' order after"
                        + " the topic's, so subtopic 0 is the second name given, 1 the third and 2 the fourth",
                () -> assertThat(rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' subtopic '"
                                + " || subtopic_ordinal || ' ' || name || ' ' || origin || ' ' || IFNULL(turn_down, 'none')"
                                + " FROM subtopic_name WHERE run_id = ? ORDER BY subtopic_ordinal", arrangement))
                        .containsExactly(
                                THE_TOPIC + " subtopic 0 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2 WRITTEN none",
                                THE_TOPIC + " subtopic 1 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 3 WRITTEN none",
                                THE_TOPIC + " subtopic 2 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 4 WRITTEN none"));
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("The approval page shows each subtopic under its topic, headed by its name, in order")
    void thePageShowsEachSubtopicUnderItsTopicInOrder(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCrowdedTopic(root, seeds);

        cli.run("run", root.toString());
        String page = thePage();
        List<Integer> places = List.of(
                page.indexOf("<h2>" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1</h2>\n"),
                page.indexOf("<h3>" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2</h3>\n"),
                page.indexOf("<h3>" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 3</h3>\n"),
                page.indexOf("<h3>" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 4</h3>\n"));

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the page heads the topic with its name, and beneath it each of the three subtopics with its own, a"
                        + " level down, in the subtopics' order",
                () -> assertThat(places).doesNotContain(-1).isSorted());
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("A subtopic's name that fell back is kept with the reason, and the page says under it why the model's was not used")
    void aSubtopicsFallbackIsMarkedOnThePage(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCrowdedTopic(root, seeds);
        GenerationScriptedBeans.namesEveryTopicWith(AN_EMPTY_NAME);

        cli.run("run", root.toString());
        String page = thePage();

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("each subtopic's name is the title of the document it holds, kept as a fallback turned down as empty",
                () -> assertThat(rows("SELECT subtopic_ordinal || ' ' || name || ' ' || origin || ' '"
                                + " || IFNULL(turn_down, 'none') FROM subtopic_name WHERE run_id = ?"
                                + " ORDER BY subtopic_ordinal", theArrangementOf(root)))
                        .containsExactly(
                                "0 " + SeedScriptedExtractionBeans.STUBBED_TITLE + " FALLBACK EMPTY",
                                "1 " + SeedScriptedExtractionBeans.STUBBED_TITLE + " FALLBACK EMPTY",
                                "2 " + SeedScriptedExtractionBeans.STUBBED_TITLE + " FALLBACK EMPTY"));
        claim("the page heads each subtopic with that name, and the paragraph directly under it says the model's name"
                        + " was not used because its answer was empty, and where the name shown comes from",
                () -> assertThat(StringUtils.countOccurrencesOf(page,
                                "<h3>" + SeedScriptedExtractionBeans.STUBBED_TITLE + "</h3>\n" + A_FALLBACK_FOR_AN_EMPTY_ANSWER))
                        .as("how many times a subtopic's heading and its paragraph stand together on the page")
                        .isEqualTo(THE_FLOOR));
    }

    /** {@value #THE_FLOOR} corpus documents and one seed of one passage, every gate up to the approval open. */
    private void aCrowdedTopic(Path root, Path seeds) throws IOException {
        for (int document = 0; document < THE_FLOOR; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "a corpus document, the " + document + "th");
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    private List<String> rows(String query, String arrangement) {
        return jdbcTemplate.queryForList(query, String.class, arrangement);
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
