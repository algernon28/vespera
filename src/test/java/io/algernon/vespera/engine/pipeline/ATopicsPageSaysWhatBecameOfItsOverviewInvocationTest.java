package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.deliverable.Html;
import io.algernon.vespera.engine.deliverable.TopicOverviewWording;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.OverviewUnwritten;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
 * Through stage 6b, the tree draws a topic's page from what the ledger recorded (ADR-415 §2, §3, §5, §11): what became
 * of its overview, its writing with the line saying how many groups it read where they were fewer than the topic's, the
 * sentence of its fault's kind, that nothing was written over any of its groups only where every group of the topic is
 * settled with none written (ADR-402 §6, §7), and otherwise that writing has not reached it; a crowded topic's
 * subtopics under the names recorded for them; and the survivors of other topics that fit a topic, by their paths, in
 * the order of the topics they are placed under.
 *
 * <p><b>The clusters are written straight into the arrangement</b>, as {@link GenerationBreakerInvocationTest} writes
 * them and for its reason: every document this fixture converts comes back alike, so no corpus clusters into groups of
 * its own. Each document, in occurrence order, is one cluster of its own, labelled {@code Group one} on, placed as the
 * test states: in the topic the seed gave, or in a topic keyed by a seed path no seed has, whose name is recorded under
 * the arrangement run as the naming step records one, and in a subtopic where the test says so, its name recorded the
 * same way. A cluster nothing can be sent for is made so by taking its document's conversion and chunks off the record
 * ({@link ConversionOffTheRecordFixture}); a document fitting another topic is given that topic's {@code see_also} row
 * under the scoring run.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Generation")
@Feature("Topic overview")
@Issue("833")
@Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class ATopicsPageSaysWhatBecameOfItsOverviewInvocationTest {

    /** The writing model every approval here names. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    /** The window every call reads in, written into the profile so no test reads another's. */
    private static final String THE_READING_WINDOW = "4096";

    /** The labels the clusters are given, document by document; none reads as part of another. */
    private static final List<String> CLUSTER_NAMES =
            List.of("Group one", "Group two", "Group three", "Group four", "Group five", "Group six");

    /** How many answers turned down in a row stop the step (ADR-111). */
    private static final int THE_STREAK_THAT_STOPS_THE_STEP = 5;

    /** Given where no cluster is to be left with nothing to send. */
    private static final int EVERY_CLUSTER_SENDABLE = -1;

    /** Where a cluster's topic is the one the seed gave rather than one of the test's own. */
    private static final String THE_SEEDS_TOPIC = null;

    /** How a cluster in no subtopic is written in a row (ADR-409 §3). */
    private static final int NO_SUBTOPIC = -1;

    /** Topics keyed by seed paths no seed has, each with the name recorded for it. */
    private static final String A_SECOND_TOPIC = "a-second-topic.txt";

    private static final String A_THIRD_TOPIC = "a-third-topic.txt";

    private static final Map<String, String> TOPIC_NAMES =
            Map.of(A_SECOND_TOPIC, "The Second Topic", A_THIRD_TOPIC, "The Third Topic");

    /** The names recorded for the seed's topic's two subtopics, the first placed first though numbered 1. */
    private static final String THE_FIRST_SUBTOPICS_NAME = "Pump Seals";

    private static final String THE_SECOND_SUBTOPICS_NAME = "Pump Motors";

    /** An answer nothing can read back into a heading and its writing, which is turned down. */
    private static final String AN_ANSWER_NOTHING_CAN_READ = "{\"title\":\"Site Safety Audits\",\"prose\":";

    /**
     * Where one document's cluster is placed: its topic, {@link #THE_SEEDS_TOPIC} or a seed path of the test's own, the
     * topic's place, its subtopic and the subtopic's place, or {@link #NO_SUBTOPIC} and 0, its ordinal and its place.
     */
    private record Placed(String topic, int topicOrder, int subtopic, int subtopicOrder, int ordinal, int clusterOrder) {

        static Placed undivided(String topic, int topicOrder, int ordinal) {
            return new Placed(topic, topicOrder, NO_SUBTOPIC, 0, ordinal, ordinal + 1);
        }
    }

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
    void nothingIsScriptedBefore() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void nothingIsScriptedAfter() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("A topic's page says why it carries no overview")
    @DisplayName("Where writing stopped before a topic, its page says writing has not reached it, and so does the page of the topic it stopped in, not that nothing was written")
    void aTopicWritingStoppedBeforeSaysItWasNotReached(@TempDir Path root, @TempDir Path seeds) throws IOException {
        List<Placed> placed = new ArrayList<>();
        for (int cluster = 0; cluster < THE_STREAK_THAT_STOPS_THE_STEP; cluster++) {
            placed.add(Placed.undivided(THE_SEEDS_TOPIC, 1, cluster));
        }
        placed.add(Placed.undivided(A_SECOND_TOPIC, 2, 0));
        anApprovedArrangement(root, seeds, placed, EVERY_CLUSTER_SENDABLE);
        for (int cluster = 0; cluster < THE_STREAK_THAT_STOPS_THE_STEP; cluster++) {
            GenerationScriptedBeans.answerFor(CLUSTER_NAMES.get(cluster), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));
        }

        cli.run("run", root.toString());
        Path tree = theTreeOf(root);
        String notReached = inItalics(OverviewUnwritten.NOT_REACHED);
        String nothingWritten = inItalics(OverviewUnwritten.NOTHING_WRITTEN_OVER_ANY_CLUSTER);

        claim("the step stopped on five answers turned down in a row, the second topic's group never asked about",
                () -> {
                    assertThat(cli.getExitCode()).isNotZero();
                    assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(THE_STREAK_THAT_STOPS_THE_STEP);
                });
        claim("the second topic's page says writing has not reached it, and not that nothing was written over its"
                        + " group, which was never asked about",
                () -> assertThat(theTopicsPage(tree, 2)).contains(notReached).doesNotContain(nothingWritten));
        claim("and so does the first topic's: its groups' faults stand in a step that did not finish, so they are not"
                        + " settled, and the next invocation asks them again",
                () -> assertThat(theTopicsPage(tree, 1)).contains(notReached).doesNotContain(nothingWritten));
    }

    @Test
    @Story("A topic's page says why it carries no overview")
    @DisplayName("Where nothing could be sent for any group of a topic, its page says nothing was written over any of them")
    void aTopicNoneOfWhoseGroupsCouldBeSentSaysNothingWasWritten(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangement(root, seeds, List.of(Placed.undivided(THE_SEEDS_TOPIC, 1, 0)), 0);

        cli.run("run", root.toString());
        String page = theTopicsPage(theTreeOf(root), 1);

        claim("no group was asked about, nothing of its one group being sendable",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isZero());
        claim("the topic's page says nothing was written over any of its groups, every group being settled",
                () -> assertThat(page)
                        .contains(inItalics(OverviewUnwritten.NOTHING_WRITTEN_OVER_ANY_CLUSTER))
                        .doesNotContain(inItalics(OverviewUnwritten.NOT_REACHED)));
    }

    @Test
    @Story("A topic's page carries its overview, each citation leading to the group it cites")
    @DisplayName("A topic's overview written through stage 6b is on its page, citing its group, and the line beneath it says it read one of the topic's two groups")
    void aWrittenOverviewIsOnThePageWithHowManyGroupsItRead(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangement(root, seeds,
                List.of(Placed.undivided(THE_SEEDS_TOPIC, 1, 0), Placed.undivided(THE_SEEDS_TOPIC, 1, 1)), 1);

        cli.run("run", root.toString());
        String generation = generationRuns(root).getLast();
        String page = theTopicsPage(theTreeOf(root), 1);
        List<Integer> numbersSent = jdbcTemplate.queryForList(
                "SELECT number FROM topic_overview_source WHERE run_id = ? ORDER BY number", Integer.class, generation);

        claim("the overview was written over the one group that has writing, the first; the second could send nothing",
                () -> assertThat(numbersSent).containsExactly(1));
        claim("the topic's page carries the overview's words, its citation linking the first group's page",
                () -> assertThat(page).containsSubsequence(
                        GenerationScriptedBeans.OVERVIEW_WRITING + " ", "1-group-one/index.html\">[1]</a>"));
        claim("and beneath it the line saying it read one of the topic's two groups, the one number its call sent of"
                        + " the topic's two",
                () -> assertThat(page)
                        .contains("<p><em>" + Html.escape(TopicOverviewWording.disclosure(DeliverableLanguage.ENGLISH)
                                .formatted(numbersSent.size(), 2)) + "</em></p>"));
    }

    @Test
    @Story("A topic's page says why it carries no overview")
    @DisplayName("A topic whose overview was turned down at both askings carries the sentence of that fault's kind")
    void aTurnedDownOverviewsPageCarriesItsFaultsSentence(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedArrangement(root, seeds, List.of(Placed.undivided(THE_SEEDS_TOPIC, 1, 0)), EVERY_CLUSTER_SENDABLE);
        GenerationScriptedBeans.writesEveryOverviewAs(GenerationScriptedBeans.OVERVIEW_WRITING);

        cli.run("run", root.toString());
        String generation = generationRuns(root).getLast();
        String page = theTopicsPage(theTreeOf(root), 1);

        claim("one fault is kept for the topic, a sentence citing nothing",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT kind FROM topic_overview_fault WHERE run_id = ?", String.class, generation))
                        .containsExactly("CITATION_NOT_IN_RANGE"));
        claim("the topic's page carries that kind's sentence, and neither that nothing was written nor that writing has"
                        + " not reached it",
                () -> assertThat(page)
                        .contains(inItalics(OverviewUnwritten.FAULT_CITATION_NOT_IN_RANGE))
                        .doesNotContain(inItalics(OverviewUnwritten.NOTHING_WRITTEN_OVER_ANY_CLUSTER))
                        .doesNotContain(inItalics(OverviewUnwritten.NOT_REACHED)));
    }

    @Test
    @Story("A divided topic's subtopics are pages beneath it")
    @DisplayName("A divided topic's page lists its subtopics under the names recorded for them, in their order, each linking the page in its folder named after it")
    void eachSubtopicIsDrawnUnderTheNameRecordedForIt(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedArrangement(root, seeds, List.of(
                        new Placed(THE_SEEDS_TOPIC, 1, 0, 2, 0, 1),
                        new Placed(THE_SEEDS_TOPIC, 1, 1, 1, 0, 1)),
                EVERY_CLUSTER_SENDABLE);

        cli.run("run", root.toString());
        Path tree = theTreeOf(root);
        String page = theTopicsPage(tree, 1);
        Path topicFolder = theTopicsFolder(tree, 1);

        claim("the invocation that writes reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the topic's page lists the subtopic numbered 1 first, under the name recorded for it, then the one"
                        + " numbered 0, each linking the page in its folder, named from its order and its name",
                () -> assertThat(page).containsSubsequence(
                        "<a href=\"1-pump-seals/index.html\">" + THE_FIRST_SUBTOPICS_NAME + "</a>",
                        "<a href=\"2-pump-motors/index.html\">" + THE_SECOND_SUBTOPICS_NAME + "</a>"));
        claim("and each subtopic's page lies in that folder, headed by its recorded name",
                () -> {
                    assertThat(Files.readString(topicFolder.resolve("1-pump-seals").resolve("index.html")))
                            .contains("<h1>" + THE_FIRST_SUBTOPICS_NAME + "</h1>");
                    assertThat(Files.readString(topicFolder.resolve("2-pump-motors").resolve("index.html")))
                            .contains("<h1>" + THE_SECOND_SUBTOPICS_NAME + "</h1>");
                });
    }

    @Test
    @Story("A document that fits another topic is linked from that topic's page")
    @DisplayName("A topic's See also names each document fitting it by its path, in the order of the topics they are placed under, not in the order the walk recorded them")
    void theSeeAlsoLinesAreNamedByPathInTheOrderOfTheTopicsTheyArePlacedUnder(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        // In occurrence order: the third topic's document, the second topic's, then the seed's topic's.
        List<Placed> placed = List.of(
                Placed.undivided(A_THIRD_TOPIC, 3, 0),
                Placed.undivided(A_SECOND_TOPIC, 2, 0),
                Placed.undivided(THE_SEEDS_TOPIC, 1, 0));
        List<Long> documents = anApprovedArrangement(root, seeds, placed, EVERY_CLUSTER_SENDABLE);
        String scoring = theScoringRunOf(root);
        for (Long fitting : List.of(documents.get(1), documents.get(2))) {
            jdbcTemplate.update(
                    "INSERT INTO see_also (occurrence_id, run_id, topic_seed_path, topic_chunk_index) VALUES (?, ?, ?, 0)",
                    fitting, scoring, A_THIRD_TOPIC);
        }
        String placedFirst = pathOf(documents.get(2));
        String placedSecond = pathOf(documents.get(1));

        cli.run("run", root.toString());
        String page = theTopicsPage(theTreeOf(root), 3);

        claim("the invocation that writes reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the third topic's page lists, under See also, the document placed under the first topic, then the one"
                        + " placed under the second, though the walk recorded them the other way round, each named by"
                        + " its path and followed by the name of the topic it is placed under",
                () -> assertThat(page).containsSubsequence(
                        "<h2>See also</h2>",
                        ">" + Html.escape(placedFirst) + "</a> (",
                        ">" + Html.escape(placedSecond) + "</a> (" + TOPIC_NAMES.get(A_SECOND_TOPIC) + ")</li>"));
        claim("and lists no document of its own",
                () -> assertThat(page).doesNotContain(">" + Html.escape(pathOf(documents.get(0))) + "</a> ("));
    }

    /** ADR-402 §5's sentence for {@code why}, in italics, as a topic's page draws it. */
    private static String inItalics(OverviewUnwritten why) {
        return "<p><em>" + Html.escape(TopicOverviewWording.of(why, DeliverableLanguage.ENGLISH)) + "</em></p>";
    }

    /**
     * A corpus of one document for each of {@code placed}, walked once, its arrangement approved and then written over
     * as {@code placed} states, document by document in occurrence order, and the cluster of the document at {@code
     * unsendable}, counted from 0, left with nothing to send; answers the documents in that order.
     */
    private List<Long> anApprovedArrangement(Path root, Path seeds, List<Placed> placed, int unsendable)
            throws IOException {
        for (int document = 1; document <= placed.size(); document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"),
                    "corpus document " + document + " of " + root.getFileName());
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document of " + seeds.getFileName());
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every call here reads in the same window")
                .build());
        cli.run("run", root.toString());
        RunId arrangement = new RunsOver(jdbcTemplate, root).latest("arrangement");
        EveryGateOpen.approve(profileStore, ArrangementGate.shortNameOf(arrangement), GENERATION_MODEL);
        List<Long> documents = placedAsStated(arrangement, placed, root);
        if (unsendable != EVERY_CLUSTER_SENDABLE) {
            ConversionOffTheRecordFixture.takenOffTheRecord(jdbcTemplate, root.resolve(pathOf(documents.get(unsendable))));
        }
        return documents;
    }

    /**
     * Replaces the arranged clusters with one cluster for each document, placed as {@code placed} states, and records the
     * names of the test's own topics and of every subtopic under the arrangement run; answers this walk's documents in
     * occurrence order.
     */
    private List<Long> placedAsStated(RunId arrangement, List<Placed> placed, Path root) {
        String scoring = theScoringRunOf(root);
        List<Long> documents = jdbcTemplate.queryForList(
                "SELECT dc.occurrence_id FROM cluster_member dc"
                        + " JOIN file_occurrence fo ON fo.id = dc.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id"
                        + " WHERE dc.run_id = ? AND w.root = ? ORDER BY dc.occurrence_id",
                Long.class,
                scoring,
                Walk.canonicalRoot(root).toString());
        if (documents.size() < placed.size()) {
            throw new IllegalStateException("this fixture needs " + placed.size() + " documents in the arrangement,"
                    + " and this walk produced " + documents.size());
        }
        Map<String, Object> seedsTopic = jdbcTemplate.queryForMap(
                "SELECT topic_seed_path, topic_chunk_index FROM cluster_member WHERE run_id = ? AND occurrence_id = ?",
                scoring,
                documents.getFirst());
        jdbcTemplate.update("DELETE FROM arranged_cluster WHERE run_id = ?", arrangement.value());
        for (int document = 0; document < placed.size(); document++) {
            Placed at = placed.get(document);
            Object seedPath = at.topic() == null ? seedsTopic.get("topic_seed_path") : at.topic();
            Object chunkIndex = at.topic() == null ? seedsTopic.get("topic_chunk_index") : 0;
            jdbcTemplate.update(
                    "UPDATE cluster_member SET cluster_ordinal = ?, topic_seed_path = ?, topic_chunk_index = ?,"
                            + " subtopic_ordinal = ? WHERE run_id = ? AND occurrence_id = ?",
                    at.ordinal(), seedPath, chunkIndex, at.subtopic(), scoring, documents.get(document));
            // Placed where it is clustered, so the clustering step finds every placed survivor clustered under its
            // topic and walks past, rather than doing its work again over the placement the scoring step recorded.
            jdbcTemplate.update(
                    "UPDATE winning_topic SET topic_seed_path = ?, topic_chunk_index = ? WHERE run_id = ?"
                            + " AND occurrence_id = ?",
                    seedPath, chunkIndex, scoring, documents.get(document));
            jdbcTemplate.update(
                    "INSERT INTO arranged_cluster (run_id, topic_seed_path, topic_chunk_index, subtopic_ordinal,"
                            + " cluster_ordinal, label, document_count, topic_order, subtopic_order, cluster_order)"
                            + " VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?)",
                    arrangement.value(), seedPath, chunkIndex, at.subtopic(), at.ordinal(), CLUSTER_NAMES.get(document),
                    at.topicOrder(), at.subtopicOrder(), at.clusterOrder());
            if (at.topic() != null && jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM topic_name WHERE run_id = ? AND topic_seed_path = ?",
                            Integer.class, arrangement.value(), at.topic()) == 0) {
                jdbcTemplate.update(
                        "INSERT INTO topic_name (run_id, topic_seed_path, topic_chunk_index, name, origin, turn_down)"
                                + " VALUES (?, ?, 0, ?, 'WRITTEN', NULL)",
                        arrangement.value(), at.topic(), TOPIC_NAMES.get(at.topic()));
            }
            if (at.subtopic() != NO_SUBTOPIC) {
                jdbcTemplate.update(
                        "INSERT INTO subtopic_name (run_id, topic_seed_path, topic_chunk_index, subtopic_ordinal, name,"
                                + " origin, turn_down) VALUES (?, ?, ?, ?, ?, 'WRITTEN', NULL)",
                        arrangement.value(), seedPath, chunkIndex, at.subtopic(),
                        at.subtopicOrder() == 1 ? THE_FIRST_SUBTOPICS_NAME : THE_SECOND_SUBTOPICS_NAME);
            }
        }
        return documents.subList(0, placed.size());
    }

    /** The scoring run the arrangement over {@code root} reads. */
    private String theScoringRunOf(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?",
                String.class,
                new RunsOver(jdbcTemplate, root).latest("arrangement").value());
    }

    /** The path the walk recorded {@code occurrence} at, beneath the corpus root. */
    private String pathOf(long occurrence) {
        return jdbcTemplate.queryForObject("SELECT path FROM file_occurrence WHERE id = ?", String.class, occurrence);
    }

    /** The numbered folder of the topic placed at {@code order} in {@code tree}. */
    private static Path theTopicsFolder(Path tree, int order) throws IOException {
        try (Stream<Path> entries = Files.list(tree)) {
            return entries.filter(Files::isDirectory)
                    .filter(entry -> entry.getFileName().toString().startsWith(order + "-"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("the tree " + tree + " holds no topic placed " + order));
        }
    }

    /** The page of the topic placed at {@code order} in {@code tree}: {@code index.html} in its numbered folder. */
    private static String theTopicsPage(Path tree, int order) throws IOException {
        return Files.readString(theTopicsFolder(tree, order).resolve("index.html"), StandardCharsets.UTF_8);
    }

    /** The tree the last generation run over {@code root} drew. */
    private Path theTreeOf(Path root) {
        return workingDirectory.resolve("deliverable").resolve(generationRuns(root).getLast());
    }

    /** Every generation run over {@code root}, oldest first. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = 'generation' AND w.root = ?"
                        + " ORDER BY r.rowid",
                String.class,
                Walk.canonicalRoot(root).toString());
    }
}
