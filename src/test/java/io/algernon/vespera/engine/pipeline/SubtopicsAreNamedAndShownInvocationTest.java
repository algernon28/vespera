package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A crowded topic's subtopics are named at the head of the arrangement step after the topics, each from its own
 * survivors, recorded under the arrangement run by its number within its topic, and shown at the 6a gate under its
 * topic, in their order, read-only; a subtopic's name that falls back is marked as a topic's is (ADR-406 §6, ADR-407
 * §4, §7, §8; ADR-409 §6, §7).
 *
 * <p>One seed of one passage gives one topic, and every one of the {@value #THREE_DOCUMENTS} documents is placed under
 * it. Every vector of the fixture is the same, and at this machine's resolution of {@value #A_FINER_RESOLUTION} such
 * documents form a community each; at its subtopic floor of {@value #THE_FLOOR} the topic is divided into three
 * subtopics of one document each, which go in their numbers' order, 0, 1, 2, being of as many documents. This
 * fixture's model names the n-th thing it is asked about {@code Scripted Topic n}, so the topic, named first, is 1.
 *
 * <p><b>The crowded topic is built once for the class</b> (ADR-337 §4), since four tests read it: the first of them to
 * run makes the invocation over {@link #theRoot}, and what it left is kept as it was read. The fallback and the stopped
 * step make invocations of their own, over folders of their own. The closing line a stopped step records is read off
 * the operator logger, and the line of each name given off the application's.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Arrangement")
@Feature("Topic names")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class SubtopicsAreNamedAndShownInvocationTest {

    /** This machine's subtopic floor. */
    private static final int THE_FLOOR = 3;

    /** The documents of the corpus, as many as the floor, so that the one topic is crowded. */
    private static final int THREE_DOCUMENTS = 3;

    /** The subtopics the crowded topic is divided into, one a document. */
    private static final int THREE_SUBTOPICS = 3;

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

    /** What the serving engine answers a question it refuses with, which says nothing of the question's length. */
    private static final String A_REFUSAL = "the serving engine is busy";

    /** The logger every operator-facing line of the application is written through, the names' among them. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** What every line logged for a subtopic's name opens with (ADR-409 §6). */
    private static final String A_SUBTOPICS_NAME_LINE = "Stage 6a (arrangement) subtopic ";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    static Path workingDirectory;

    /** The corpus root of the crowded topic the class builds once. */
    @TempDir
    static Path theRoot;

    /** The seed folder of that topic. */
    @TempDir
    static Path theSeeds;

    /**
     * What the crowded topic's invocation left, read as soon as it ended.
     *
     * @param exitCode how the invocation ended
     * @param namingCalls how many names it asked for
     * @param topicNames the topic's name kept, as its key, its name and its origin
     * @param subtopicNames each subtopic's name kept, in its numbers' order
     * @param namesLogged the lines logged for the subtopics' names, in the order logged
     * @param page {@code arrangement.html} as it was written
     * @param json {@code arrangement.json} as it was written
     */
    private record Crowded(
            int exitCode,
            int namingCalls,
            List<String> topicNames,
            List<String> subtopicNames,
            List<String> namesLogged,
            String page,
            JsonNode json) {}

    private static final Once<Crowded> crowded = new Once<>();

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

    private ListAppender<ILoggingEvent> operatorLines;

    private ListAppender<ILoggingEvent> applicationLines;

    @BeforeEach
    @AfterEach
    void nothingIsScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @BeforeEach
    void hearTheLines() {
        operatorLines = new ListAppender<>();
        operatorLines.start();
        ((Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).addAppender(operatorLines);
        applicationLines = new ListAppender<>();
        applicationLines.start();
        ((Logger) LoggerFactory.getLogger(APPLICATION_LOGGER)).addAppender(applicationLines);
    }

    @AfterEach
    void stopHearingThem() {
        ((Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).detachAppender(operatorLines);
        operatorLines.stop();
        ((Logger) LoggerFactory.getLogger(APPLICATION_LOGGER)).detachAppender(applicationLines);
        applicationLines.stop();
    }

    @Test
    @Story("Each topic is named before the arrangement is shown for approval")
    @DisplayName("Each subtopic is named after its topic, and its name is kept under its number within the topic")
    void eachSubtopicIsNamedAfterTheTopicAndStoredInItsOrder() {
        Crowded crowded = crowded();

        claim("the invocation reports success, stopping at the arrangement's approval",
                () -> assertThat(crowded.exitCode()).isZero());
        claim("four names were asked, one for the topic and one for each of its three subtopics",
                () -> assertThat(crowded.namingCalls()).isEqualTo(1 + THREE_SUBTOPICS));
        claim("the topic's name, asked first, is kept as the model wrote it",
                () -> assertThat(crowded.topicNames())
                        .containsExactly(THE_TOPIC + " " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1 WRITTEN"));
        claim("and each subtopic's name is kept under its number within the topic, asked in the subtopics' order after"
                        + " the topic's, so subtopic 0 is the second name given, 1 the third and 2 the fourth",
                () -> assertThat(crowded.subtopicNames())
                        .containsExactly(
                                THE_TOPIC + " subtopic 0 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2 WRITTEN none",
                                THE_TOPIC + " subtopic 1 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 3 WRITTEN none",
                                THE_TOPIC + " subtopic 2 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 4 WRITTEN none"));
    }

    @Test
    @Story("Each topic is named before the arrangement is shown for approval")
    @DisplayName("Each subtopic's name is logged as it is given, with the subtopic's number within its topic and the topic's key")
    void eachSubtopicsNameIsLoggedByItsNumberWithinItsTopic() {
        Crowded crowded = crowded();

        claim("one line is logged for each of the three subtopics, in their numbers' order, naming the subtopic by its"
                        + " number within the topic, 0 to 2, and the topic by its seed's file and first chunk, then the"
                        + " name the model wrote, between quotation marks, and that the model wrote it",
                () -> assertThat(crowded.namesLogged()).containsExactly(
                        A_SUBTOPICS_NAME_LINE + "0 of topic seed.txt chunk 0 named \""
                                + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2\" (written)",
                        A_SUBTOPICS_NAME_LINE + "1 of topic seed.txt chunk 0 named \""
                                + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 3\" (written)",
                        A_SUBTOPICS_NAME_LINE + "2 of topic seed.txt chunk 0 named \""
                                + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 4\" (written)"));
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("The approval page shows each subtopic under its topic, headed by its name, in order")
    void thePageShowsEachSubtopicUnderItsTopicInOrder() {
        Crowded crowded = crowded();
        String page = crowded.page();
        List<Integer> places = List.of(
                page.indexOf("<h2>" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1</h2>\n"),
                page.indexOf("<h3>" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2</h3>\n"),
                page.indexOf("<h3>" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 3</h3>\n"),
                page.indexOf("<h3>" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 4</h3>\n"));

        claim("the invocation reports success", () -> assertThat(crowded.exitCode()).isZero());
        claim("the page heads the topic with its name, and beneath it each of the three subtopics with its own, a"
                        + " level down, in the subtopics' order",
                () -> assertThat(places).doesNotContain(-1).isSorted());
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("The approval page's JSON has a section for the topic and one for each of its subtopics, each titled by its name")
    void theArrangementsJsonHasOneSectionForTheTopicAndOneForEachSubtopic() {
        Crowded crowded = crowded();
        JsonNode json = crowded.json();
        String oneDocumentInOneGroup = List.of("1 document(s) in 1 group(s).") + " table of 1 row(s)";

        claim("the invocation reports success", () -> assertThat(crowded.exitCode()).isZero());
        claim("after what the page says and what it asks, the JSON has a section for the topic, titled by its name and"
                        + " holding no line of counts and no table, these being its subtopics'; then one for each"
                        + " subtopic, in the subtopics' order, titled by its name and holding its line of counts and its"
                        + " one group",
                () -> assertThat(TopicNamesAreStoredAndShownInvocationTest.sectionsOf(json))
                        .filteredOn(section -> section.startsWith("topic-"))
                        .containsExactly(
                                "topic-1: " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1 [] no table",
                                "topic-1-subtopic-1: " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2 "
                                        + oneDocumentInOneGroup,
                                "topic-1-subtopic-2: " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 3 "
                                        + oneDocumentInOneGroup,
                                "topic-1-subtopic-3: " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 4 "
                                        + oneDocumentInOneGroup));
        claim("and no other section follows what the page says and what it asks",
                () -> assertThat(TopicNamesAreStoredAndShownInvocationTest.sectionsOf(json))
                        .extracting(section -> section.substring(0, section.indexOf(':')))
                        .containsExactly("overview", "approval", "topic-1", "topic-1-subtopic-1", "topic-1-subtopic-2",
                                "topic-1-subtopic-3"));
        claim("its headline counts one topic, three groups and three documents",
                () -> assertThat(TopicNamesAreStoredAndShownInvocationTest.headlineOf(json)).containsExactly(
                        "topics Topics 1", "clusters Groups " + THREE_SUBTOPICS, "documents Documents " + THREE_DOCUMENTS));
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
                        .isEqualTo(THREE_SUBTOPICS));
    }

    /**
     * The engine refuses every question after the first, the topic's, so the step stops at the first subtopic, whose
     * number is 0. Before the next invocation the engine answers again, and this fixture's model goes on numbering its
     * names from where it stopped.
     */
    @Test
    @Story("Each topic is named before the arrangement is shown for approval")
    @DisplayName("A stop at a subtopic keeps the topic's name, and the next invocation asks only for the subtopics'")
    void aStopAtASubtopicKeepsTheTopicsNameAndTheNextInvocationAsksTheSubtopicsAlone(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCrowdedTopic(root, seeds);
        GenerationScriptedBeans.refuseEvery(question -> GenerationScriptedBeans.namingCallsMade() >= 1, A_REFUSAL);

        cli.run("run", root.toString());
        int stopped = cli.getExitCode();
        List<ILoggingEvent> saidAtTheStop = List.copyOf(operatorLines.list);
        String arrangement = theArrangementOf(root);
        List<String> topicNamedBeforeTheStop = topicNamesUnder(arrangement);
        List<String> subtopicsNamedBeforeTheStop = subtopicNamesUnder(arrangement);
        int askedBeforeTheStop = GenerationScriptedBeans.namingCallsMade();
        GenerationScriptedBeans.refuseEvery(question -> false, "");

        cli.run("run", root.toString());
        int askedSince = GenerationScriptedBeans.namingCallsMade() - askedBeforeTheStop;

        claim("the first invocation failed, the engine refusing the first subtopic's question",
                () -> assertThat(stopped).isNotZero());
        claim("its closing line, at ERROR, names the subtopic by its number within the topic, 0, and the topic by its"
                        + " key, gives the engine's refusal word for word, and says the names given are kept and the next"
                        + " invocation asks only for the rest",
                () -> assertThat(saidAtTheStop)
                        .filteredOn(event -> event.getLevel() == Level.ERROR)
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .contains("the arrangement step stopped: the naming model gave no answer for subtopic 0 of topic"
                                + " seed.txt chunk 0 -- " + A_REFUSAL + "; the names given before it are kept, and the next"
                                + " invocation asks only for the rest"));
        claim("by then the topic had been named and its name kept, and no subtopic had",
                () -> {
                    assertThat(topicNamedBeforeTheStop)
                            .containsExactly(THE_TOPIC + " " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1 WRITTEN");
                    assertThat(subtopicsNamedBeforeTheStop).isEmpty();
                });
        claim("the next invocation reports success, arriving at the same arrangement",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(theArrangementOf(root)).isEqualTo(arrangement);
                });
        claim("it asked three names, the subtopics', and none for the topic",
                () -> assertThat(askedSince).isEqualTo(THREE_SUBTOPICS));
        claim("and the topic's name stands as it was kept, the subtopics' kept under their numbers in the order asked",
                () -> {
                    assertThat(topicNamesUnder(arrangement)).isEqualTo(topicNamedBeforeTheStop);
                    assertThat(subtopicNamesUnder(arrangement)).containsExactly(
                            THE_TOPIC + " subtopic 0 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2 WRITTEN none",
                            THE_TOPIC + " subtopic 1 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 3 WRITTEN none",
                            THE_TOPIC + " subtopic 2 " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 4 WRITTEN none");
                });
    }

    /**
     * The crowded topic, made the first time a test asks for it: its documents and one seed, run to the approval, and
     * what that invocation left read at once.
     */
    private Crowded crowded() {
        return crowded.get(() -> {
            aCrowdedTopic(theRoot, theSeeds);
            int loggedBefore = applicationLines.list.size();
            cli.run("run", theRoot.toString());
            String arrangement = theArrangementOf(theRoot);
            List<String> namesLogged = applicationLines.list.subList(loggedBefore, applicationLines.list.size()).stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(line -> line.startsWith(A_SUBTOPICS_NAME_LINE))
                    .toList();
            return new Crowded(
                    cli.getExitCode(),
                    GenerationScriptedBeans.namingCallsMade(),
                    topicNamesUnder(arrangement),
                    subtopicNamesUnder(arrangement),
                    namesLogged,
                    thePage(),
                    JSON.readTree(Files.readString(
                            workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_JSON_FILE_NAME), StandardCharsets.UTF_8)));
        });
    }

    /** The topic's name kept under {@code arrangement}: its key, its name and its origin. */
    private List<String> topicNamesUnder(String arrangement) {
        return rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' ' || name || ' ' || origin FROM topic_name"
                + " WHERE run_id = ?", arrangement);
    }

    /** Each subtopic's name kept under {@code arrangement}, in its numbers' order: the key, the number, the name, the origin and the turn-down. */
    private List<String> subtopicNamesUnder(String arrangement) {
        return rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' subtopic ' || subtopic_ordinal || ' ' || name"
                + " || ' ' || origin || ' ' || IFNULL(turn_down, 'none') FROM subtopic_name WHERE run_id = ?"
                + " ORDER BY subtopic_ordinal", arrangement);
    }

    /** {@value #THREE_DOCUMENTS} corpus documents and one seed of one passage, every gate up to the approval open. */
    private void aCrowdedTopic(Path root, Path seeds) throws IOException {
        for (int document = 0; document < THREE_DOCUMENTS; document++) {
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
