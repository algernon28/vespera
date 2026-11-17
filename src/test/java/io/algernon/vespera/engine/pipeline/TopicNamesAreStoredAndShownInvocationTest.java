package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
 *
 * <p>What the page shows, its JSON shows (ADR-409 §7): one section a topic, whose {@code id} is {@code topic-} and its
 * place and whose {@code title} is its recorded name. The closing line a stopped step records is read off the operator
 * logger, where the step's listener logs it (ADR-364 §4); the line of each name given, off the application's.
 *
 * <p><b>The one-topic arrangement is built once for the class</b> (ADR-337 §4), since four tests read it: the first of
 * them to run makes the invocation over {@link #theRoot}, and what it left, the names, the page and its JSON, is kept as
 * it was read, the working directory's page being written again by every later invocation of the class. The fallback
 * and the stopped step each make invocations of their own, over folders of their own.
 *
 * <p>Until #833 decides how a topic is drawn, the tree draws it where a seed partition stood, named from its recorded
 * name (ADR-409 §10): the topic named {@code Scripted Topic 1} over {@code seed.txt} is drawn in the folder {@value
 * #THE_TOPICS_FOLDER}, its order 1 then its name as a slug, where a seed partition's folder took the seed's stem.
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

    /** The words of {@link #A_FALLBACK_FOR_AN_EMPTY_ANSWER} without its markup, as the JSON's text holds them. */
    private static final String THE_FALLBACKS_WORDS = "The naming model's name for this topic was not used: the model's"
            + " answer was empty. The name above stands in for it, taken from the seed feeding the topic the most"
            + " passages, or from the topic's place where that seed gives none.";

    /** The line of counts of a topic holding the one corpus document in one group. */
    private static final String ONE_DOCUMENT_IN_ONE_GROUP = "1 document(s) in 1 group(s).";

    /** What the serving engine answers a question it refuses with, which says nothing of the question's length. */
    private static final String A_REFUSAL = "the serving engine is busy";

    /** The words a question asking for a name again puts before its last instruction, the name taken quoted (ADR-407 §5). */
    private static final String THE_FIRST_NAME_TAKEN =
            "Other sections are already named \"" + THE_FIRST_NAME + "\"; name this one differently.";

    /** What every line logged for a topic's name opens with (ADR-407 §9). */
    private static final String A_TOPICS_NAME_LINE = "Stage 6a (arrangement) topic ";

    /** The logger every operator-facing line of the application is written through, the names' among them. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** The writing model the approval names, so that the tree is drawn. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    /** The folder the one topic named {@link #THE_FIRST_NAME} is drawn in: its order, 1, then its name as a slug. */
    private static final String THE_TOPICS_FOLDER = "1-scripted-topic-1";

    /** The one corpus document of the one-topic arrangement, by its path beneath the root. */
    private static final String THE_CORPUS_DOCUMENT = "corpus.txt";

    /** The schema {@code arrangement.json} is published under (ADR-362 §9, ADR-409 §7). */
    private static final Path ARRANGEMENT_SCHEMA = Path.of("docs", "report", "arrangement.schema.json");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    static Path workingDirectory;

    /** The corpus root of the one-topic arrangement the class builds once. */
    @TempDir
    static Path theRoot;

    /** The seed folder of that arrangement. */
    @TempDir
    static Path theSeeds;

    /**
     * What the one-topic arrangement left, read as soon as its invocation ended.
     *
     * @param exitCode how the invocation ended
     * @param arrangement the arrangement run it minted
     * @param names the names kept under that run, as {@link #namesUnder} gives them
     * @param page {@code arrangement.html} as it was written
     * @param json {@code arrangement.json} as it was written
     */
    private record Arranged(int exitCode, String arrangement, List<String> names, String page, JsonNode json) {}

    private static final Once<Arranged> arranged = new Once<>();

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

    private ListAppender<ILoggingEvent> operatorLines;

    private ListAppender<ILoggingEvent> applicationLines;

    @BeforeEach
    void hearTheOperatorLines() {
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
    void eachTopicsNameIsStoredUnderTheArrangementRunWithItsOrigin() {
        Arranged arranged = arranged();

        claim("the invocation reports success, stopping at the arrangement's approval",
                () -> assertThat(arranged.exitCode()).isZero());
        claim("the one topic's name is kept under the one arrangement, by the topic's key, as the model wrote it at"
                        + " the first asking, with no reason for a fallback",
                () -> assertThat(arranged.names()).containsExactly(THE_SEED + " 0 " + THE_FIRST_NAME + " WRITTEN none"));
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("The approval page heads each topic with the name kept for it")
    void theArrangementPageHeadsEachTopicWithItsStoredName() {
        Arranged arranged = arranged();

        claim("the invocation reports success", () -> assertThat(arranged.exitCode()).isZero());
        claim("the page heads the one topic with the name the model gave it, and its line of counts follows the heading"
                        + " directly, no paragraph saying the name stands in for another",
                () -> assertThat(arranged.page())
                        .contains("<h2>" + THE_FIRST_NAME + "</h2>\n<p>" + ONE_DOCUMENT_IN_ONE_GROUP + "</p>\n"));
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("The approval page's JSON has one section for the topic, titled by the name kept for it")
    void theArrangementsJsonHasOneSectionATopicTitledByItsName() {
        Arranged arranged = arranged();
        JsonNode json = arranged.json();

        claim("the invocation reports success", () -> assertThat(arranged.exitCode()).isZero());
        claim("after what the page says and what it asks, the JSON has one section, topic-1, for the one topic, and no"
                        + " section named for a seed",
                () -> assertThat(sectionsOf(json))
                        .extracting(section -> section.substring(0, section.indexOf(':')))
                        .containsExactly("overview", "approval", "topic-1"));
        claim("the topic's section is titled by the name the model gave it, and holds its line of counts and its one"
                        + " group, as the page does",
                () -> assertThat(sectionsOf(json)).contains("topic-1: " + THE_FIRST_NAME + " "
                        + List.of(ONE_DOCUMENT_IN_ONE_GROUP) + " table of 1 row(s)"));
        claim("its headline counts the topics, one, before the groups and the documents",
                () -> assertThat(headlineOf(json)).containsExactly(
                        "topics Topics 1", "clusters Groups 1", "documents Documents 1"));
        JsonNode schema = theSchema();
        claim("the JSON is written in the format its published schema is of, the one sections named for topics began",
                () -> assertThat(json.get("format").asLong())
                        .as("arrangement.json's format against arrangement.schema.json's const")
                        .isEqualTo(schema.get("properties").get("format").get("const").asLong()));
        claim("and the JSON follows that schema: every field it requires, none it does not name, each of its type and"
                        + " among the values it allows",
                () -> assertThat(SchemaWalker.problems(json, schema)).isEmpty());
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
        claim("and the JSON's section for the topic is titled by that name, its text the paragraph's words and then its"
                        + " line of counts, as the page has them",
                () -> assertThat(sectionsOf(theJson())).contains("topic-1: " + SeedScriptedExtractionBeans.STUBBED_TITLE
                        + " " + List.of(THE_FALLBACKS_WORDS, ONE_DOCUMENT_IN_ONE_GROUP) + " table of 1 row(s)"));
    }

    /**
     * The serving engine refuses every question about sluices, so the step stops once the topic on pumps, whose key
     * comes first, is named. Before the next invocation the engine answers again, and this fixture's model numbers its
     * names from 1 again: so its first answer to that invocation is the name kept for the topic on pumps, which counts
     * as taken (ADR-407 §8), and the topic on sluices is asked once more, the question naming it.
     */
    @Test
    @Story("Each topic is named before the arrangement is shown for approval")
    @DisplayName("A name given before the step was stopped is kept, and the next invocation asks only for the names it lacks, the kept name counting as taken")
    void aNameRecordedBeforeTheStepStoppedIsNotAskedForAgain(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(THE_CORPUS_DOCUMENT), "a corpus document of " + root.getFileName());
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
        List<ILoggingEvent> saidAtTheStop = List.copyOf(operatorLines.list);
        String arrangement = theArrangementOf(root);
        List<String> namedBeforeTheStop = namesUnder(arrangement);
        GenerationScriptedBeans.forgetScriptedAnswers();
        int loggedBeforeTheNext = applicationLines.list.size();

        cli.run("run", root.toString());
        List<String> askedSince = GenerationScriptedBeans.namingQuestionsSent();
        List<String> namesLoggedSince = applicationLines.list.subList(loggedBeforeTheNext, applicationLines.list.size())
                .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.startsWith(A_TOPICS_NAME_LINE))
                .toList();

        claim("the first invocation failed, the engine refusing the question about the topic on sluices",
                () -> assertThat(stopped).isNotZero());
        claim("its closing line, at ERROR, names the topic on sluices by its key as the line of a name given writes it,"
                        + " gives the engine's refusal word for word, and says the names given are kept and the next"
                        + " invocation asks only for the rest",
                () -> assertThat(saidAtTheStop)
                        .filteredOn(event -> event.getLevel() == Level.ERROR)
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .contains("the arrangement step stopped: the naming model gave no answer for topic "
                                + onSluices.getFirst() + " chunk 0 -- " + A_REFUSAL + "; the names given before it are"
                                + " kept, and the next invocation asks only for the rest"));
        claim("by then the topic on pumps, whose key comes first, had been named and its name kept, and the one on"
                        + " sluices had not",
                () -> assertThat(namedBeforeTheStop).containsExactly(onPumpsKey + " " + THE_FIRST_NAME + " WRITTEN none"));
        claim("the next invocation reports success, arriving at the same arrangement",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(theArrangementOf(root)).isEqualTo(arrangement);
                });
        claim("it asked nothing about the topic on pumps, and asked about the topic on sluices twice: its first answer"
                        + " was the name kept for the topic on pumps, so the second question, and only the second, named"
                        + " that name as taken",
                () -> {
                    assertThat(askedSince).hasSize(2).allSatisfy(question -> assertThat(question)
                            .contains(SLUICES)
                            .doesNotContain(PUMPS));
                    assertThat(askedSince.getFirst()).doesNotContain(THE_FIRST_NAME_TAKEN);
                    assertThat(askedSince.getLast()).contains(THE_FIRST_NAME_TAKEN);
                });
        claim("both names stand under the arrangement: the one kept before the stop unchanged, and the topic on sluices'"
                        + " the model's second answer, written when asked again",
                () -> assertThat(namesUnder(arrangement)).containsExactly(
                        onPumpsKey + " " + THE_FIRST_NAME + " WRITTEN none",
                        onSluicesKey + " " + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2 WRITTEN_WHEN_ASKED_AGAIN none"));
        claim("and the next invocation logged the name it was given and not the one it read back",
                () -> assertThat(namesLoggedSince).containsExactly(A_TOPICS_NAME_LINE + onSluices.getFirst()
                        + " chunk 0 named \"" + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2\" (asked again)"));
    }

    @Test
    @Story("A topic is drawn under its name until its rendering is decided")
    @DisplayName("The tree draws the topic in a folder named from the name kept for it, headed by that name, and the listing gives its name and place")
    void theTreeNamesTheTopicsFolderAndHeadingByItsName() throws IOException {
        Arranged arranged = arranged();
        EveryGateOpen.throughStageFive(profileStore, theSeeds);
        EveryGateOpen.approve(
                profileStore, ArrangementGate.shortNameOf(new RunId(arranged.arrangement())), GENERATION_MODEL);

        cli.run("run", theRoot.toString());
        Path tree = workingDirectory.resolve("deliverable").resolve(theLatestGenerationOf(theRoot));
        List<String> numberedFolders;
        try (Stream<Path> entries = Files.list(tree)) {
            numberedFolders = entries.filter(Files::isDirectory)
                    .map(entry -> entry.getFileName().toString())
                    .filter(name -> Character.isDigit(name.charAt(0)))
                    .sorted()
                    .toList();
        }
        String index = Files.readString(tree.resolve("index.html"), StandardCharsets.UTF_8);
        Map<String, String> listed = theListingsRowOf(tree, THE_CORPUS_DOCUMENT);

        claim("the invocation that writes reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the one topic is drawn in one numbered folder, its place, 1, then the name kept for it, and none is named"
                        + " from the seed's file",
                () -> assertThat(numberedFolders).containsExactly(THE_TOPICS_FOLDER));
        claim("the index heads the topic with the name kept for it, and not with the seed's path",
                () -> assertThat(index)
                        .contains("<h2>" + THE_FIRST_NAME + "</h2>")
                        .doesNotContain("<h2>" + THE_SEED + "</h2>"));
        claim("and the listing's row for the corpus document gives, under its two columns of the old names, the"
                        + " topic's name and its place, 1",
                () -> assertThat(List.of(listed.get("seed_partition"), listed.get("partition_order")))
                        .containsExactly(THE_FIRST_NAME, "1"));
    }

    /**
     * The one-topic arrangement, made the first time a test asks for it: one corpus document and one seed of one
     * passage, run to the approval, and what that invocation left read at once.
     */
    private Arranged arranged() {
        return arranged.get(() -> {
            oneSeedOfOnePassage(theRoot, theSeeds);
            cli.run("run", theRoot.toString());
            String arrangement = theArrangementOf(theRoot);
            return new Arranged(cli.getExitCode(), arrangement, namesUnder(arrangement), thePage(), theJson());
        });
    }

    /** One corpus document and one seed of one passage, every gate up to the approval open. */
    private void oneSeedOfOnePassage(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(THE_CORPUS_DOCUMENT), "a corpus document of " + root.getFileName());
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

    /** The latest generation run over {@code root}, the database serving the class. */
    private String theLatestGenerationOf(Path root) {
        return jdbcTemplate.queryForList(
                        "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = 'generation' AND w.root = ?"
                                + " ORDER BY r.rowid",
                        String.class,
                        Walk.canonicalRoot(root).toString())
                .getLast();
    }

    /** The cells of {@code documents.csv}'s row for {@code path}, by the header's names. */
    private static Map<String, String> theListingsRowOf(Path tree, String path) throws IOException {
        List<String> lines = Files.readAllLines(tree.resolve("documents.csv"), StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        List<String> header = cellsOf(lines.getFirst());
        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = cellsOf(line);
            if (cells.get(header.indexOf("path")).equals(path)) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int at = 0; at < header.size(); at++) {
                    row.put(header.get(at), cells.get(at));
                }
                return row;
            }
        }
        throw new IllegalStateException("documents.csv lists no row for " + path);
    }

    /** One CSV line's cells, the quotes around a cell taken off and a doubled quote read as one. */
    private static List<String> cellsOf(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int at = 0; at < line.length(); at++) {
            char c = line.charAt(at);
            if (c == '"' && quoted && at + 1 < line.length() && line.charAt(at + 1) == '"') {
                cell.append('"');
                at++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private static String thePage() throws IOException {
        return Files.readString(workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME), StandardCharsets.UTF_8);
    }

    private static JsonNode theJson() throws IOException {
        return JSON.readTree(Files.readString(
                workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_JSON_FILE_NAME), StandardCharsets.UTF_8));
    }

    /** {@code docs/report/arrangement.schema.json}, the schema {@code arrangement.json} is published under (ADR-362 §9). */
    static JsonNode theSchema() throws IOException {
        return JSON.readTree(Files.readString(ARRANGEMENT_SCHEMA, StandardCharsets.UTF_8));
    }

    /**
     * Each section of an analysis report's JSON, in order, as {@code id: title [text, ...]} and then {@code table of n
     * row(s)}, or {@code no table} where the section has none.
     */
    static List<String> sectionsOf(JsonNode json) {
        List<String> sections = new ArrayList<>();
        JsonNode all = json.get("sections");
        for (int at = 0; at < all.size(); at++) {
            JsonNode section = all.get(at);
            List<String> text = new ArrayList<>();
            JsonNode lines = section.get("text");
            for (int line = 0; lines != null && line < lines.size(); line++) {
                text.add(lines.get(line).asString());
            }
            JsonNode table = section.get("table");
            sections.add(section.get("id").asString() + ": " + section.get("title").asString() + " " + text
                    + (table == null || table.isNull() ? " no table" : " table of " + table.get("rows").size() + " row(s)"));
        }
        return sections;
    }

    /** The figures of an analysis report's JSON headline, in order, as {@code key label value}. */
    static List<String> headlineOf(JsonNode json) {
        List<String> figures = new ArrayList<>();
        JsonNode headline = json.get("headline");
        for (int at = 0; at < headline.size(); at++) {
            JsonNode figure = headline.get(at);
            figures.add(figure.get("key").asString() + " " + figure.get("label").asString() + " "
                    + figure.get("value").toString());
        }
        return figures;
    }
}
