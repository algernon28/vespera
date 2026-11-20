package io.algernon.vespera.publish.retrieval;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.deliverable.RetrievalRecord;
import io.algernon.vespera.engine.deliverable.RetrievalRecordId;
import io.algernon.vespera.engine.deliverable.RetrievalRecords;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.TopicKey;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The retrieval plugin draws the retrieval records it is handed into {@code retrieval/records.jsonl} (ADR-434 §8): one
 * JSON object a line, in the order taken, each line ended by a line feed, its fields in the order the record fixes,
 * an empty value {@code null}, a vector's components as {@code Float.toString} writes them, and every character but a
 * quote, a backslash and a control character as its UTF-8 bytes. The file is written through its {@code .part} and
 * moved into place when the reading finishes. The plugin draws from no tree and takes member passages.
 *
 * <p>The folder is made first, as {@code Deliverable} makes each plugin's folder before opening it (ADR-333 §11).
 */
@Epic("The deliverable")
@Feature("Retrieval records")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
class TheRecordsFileIsOneJsonLineARecordTest {

    private static final String GENERATION_RUN = "generation-run-of-the-fixture";

    /** The embedder identity every vector of the fixture is under, as {@code EmbedderIdentity.value()} composes one. */
    private static final String EMBEDDER =
            "model=embedder:test;digest=sha256-of-the-test-model;dtype=F16;dimension=3;instruction=none";

    private static final TopicKey SLUICES = new TopicKey("sluices/handbook.pdf", 0);

    /** The cluster numbered 2 within the topic's subtopic numbered 0, third in the topic's one sequence. */
    private static final ClusterSlot BY_HAND = new ClusterSlot(SLUICES, OptionalInt.of(0), 2);

    private static final String MEMBER = "member-content-hash-of-the-fixture";

    private static final RetrievalRecord OVERVIEW = new RetrievalRecord(
            RetrievalRecordId.ofTopicOverview(SLUICES),
            RetrievalRecord.Kind.TOPIC_OVERVIEW,
            "Sluices are worked by hand [1] and by a float [2].",
            new float[] {0.25f, -0.5f, 1.0f},
            EMBEDDER,
            new RetrievalRecord.Placement(
                    GENERATION_RUN, DeliverableLanguage.ENGLISH, "Sluices", 1,
                    Optional.empty(), OptionalInt.empty(), Optional.empty(), OptionalInt.empty(),
                    List.of(1, 2), Optional.empty(), OptionalInt.empty()));

    private static final RetrievalRecord SYNTHESIS_DOC = new RetrievalRecord(
            RetrievalRecordId.ofSynthesisDoc(BY_HAND),
            RetrievalRecord.Kind.SYNTHESIS_DOC,
            "A sluice is opened by turning its wheel [1].",
            new float[] {1.0E-5f, 0.0f, -1.0f},
            EMBEDDER,
            new RetrievalRecord.Placement(
                    GENERATION_RUN, DeliverableLanguage.ENGLISH, "Sluices", 1,
                    Optional.of("Hand-worked sluices"), OptionalInt.of(1),
                    Optional.of("Opening a sluice by hand"), OptionalInt.of(3),
                    List.of(1), Optional.empty(), OptionalInt.empty()));

    /** A passage of a member the cluster's call did not send, so citing nothing. */
    private static final RetrievalRecord PASSAGE = new RetrievalRecord(
            RetrievalRecordId.ofMemberPassage(BY_HAND, MEMBER, 4),
            RetrievalRecord.Kind.MEMBER_PASSAGE,
            "Turn the wheel twice.",
            new float[] {0.125f, 0.5f, -0.75f},
            EMBEDDER,
            new RetrievalRecord.Placement(
                    GENERATION_RUN, DeliverableLanguage.ENGLISH, "Sluices", 1,
                    Optional.of("Hand-worked sluices"), OptionalInt.of(1),
                    Optional.of("Opening a sluice by hand"), OptionalInt.of(3),
                    List.of(), Optional.of("plans/sluice-7.pdf"), OptionalInt.of(4)));

    @Test
    @Story("The records file")
    @DisplayName("An overview, a group's writing and a passage taken are three lines of JSON, whole, in that order")
    void eachRecordIsOneLineInTheOrderTaken(@TempDir Path tree) throws IOException {
        Path file = drawn(tree, List.of(OVERVIEW, SYNTHESIS_DOC, PASSAGE));

        claim("the file holds the three lines, each ended by a line feed, the last too, each field in the order the"
                        + " record fixes and an empty value written null",
                () -> assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(
                        "{\"format\":1,\"id\":\"" + OVERVIEW.id() + "\",\"kind\":\"topic-overview\","
                                + "\"text\":\"Sluices are worked by hand [1] and by a float [2].\","
                                + "\"vector\":[0.25,-0.5,1.0],\"embedder\":\"" + EMBEDDER + "\","
                                + "\"generationRun\":\"" + GENERATION_RUN + "\",\"language\":\"en\","
                                + "\"topic\":\"Sluices\",\"topicOrder\":1,\"subtopic\":null,\"subtopicOrder\":null,"
                                + "\"cluster\":null,\"clusterNumber\":null,\"citations\":[1,2],"
                                + "\"document\":null,\"passage\":null}\n"
                        + "{\"format\":1,\"id\":\"" + SYNTHESIS_DOC.id() + "\",\"kind\":\"synthesis-doc\","
                                + "\"text\":\"A sluice is opened by turning its wheel [1].\","
                                + "\"vector\":[1.0E-5,0.0,-1.0],\"embedder\":\"" + EMBEDDER + "\","
                                + "\"generationRun\":\"" + GENERATION_RUN + "\",\"language\":\"en\","
                                + "\"topic\":\"Sluices\",\"topicOrder\":1,\"subtopic\":\"Hand-worked sluices\","
                                + "\"subtopicOrder\":1,\"cluster\":\"Opening a sluice by hand\",\"clusterNumber\":3,"
                                + "\"citations\":[1],\"document\":null,\"passage\":null}\n"
                        + "{\"format\":1,\"id\":\"" + PASSAGE.id() + "\",\"kind\":\"member-passage\","
                                + "\"text\":\"Turn the wheel twice.\","
                                + "\"vector\":[0.125,0.5,-0.75],\"embedder\":\"" + EMBEDDER + "\","
                                + "\"generationRun\":\"" + GENERATION_RUN + "\",\"language\":\"en\","
                                + "\"topic\":\"Sluices\",\"topicOrder\":1,\"subtopic\":\"Hand-worked sluices\","
                                + "\"subtopicOrder\":1,\"cluster\":\"Opening a sluice by hand\",\"clusterNumber\":3,"
                                + "\"citations\":[],\"document\":\"plans/sluice-7.pdf\",\"passage\":4}\n"));
    }

    @Test
    @Story("The records file")
    @DisplayName("A text's quotes, backslashes and line breaks are escaped, and an accented letter is written as it is")
    void aTextIsEscapedAndWrittenAsUtf8(@TempDir Path tree) throws IOException {
        RetrievalRecord quoted = new RetrievalRecord(
                PASSAGE.id(), PASSAGE.kind(), "He said \"open\" \\ then\nshut — è", PASSAGE.vector(), EMBEDDER,
                PASSAGE.placement());

        Path file = drawn(tree, List.of(quoted));

        claim("the text's field reads the quotes and the backslash escaped, the line break as \\n, and the dash"
                        + " and the accented letter as their own UTF-8 bytes, unescaped",
                () -> assertThat(Files.readString(file, StandardCharsets.UTF_8))
                        .contains("\"text\":\"He said \\\"open\\\" \\\\ then\\nshut — è\",")
                        .doesNotContain("\\u00e8", "\\u2014"));
        claim("and the file is still one line, the line break inside the text not breaking it",
                () -> assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).hasSize(1));
    }

    @Test
    @Story("The records file")
    @DisplayName("Nothing stands at the file until the reading of the records finishes")
    void nothingStandsAtTheFileUntilTheReadingFinishes(@TempDir Path tree) throws IOException {
        RetrievalPlugin plugin = new RetrievalPlugin();
        Files.createDirectories(tree.resolve(plugin.name()));
        Path file = tree.resolve(plugin.name()).resolve(plugin.listName());

        try (RetrievalRecords reading = plugin.records(contextOf(tree)).orElseThrow()) {
            reading.take(OVERVIEW);
            reading.take(SYNTHESIS_DOC);

            claim("two records taken and the reading not finished: no records.jsonl stands",
                    () -> assertThat(file).doesNotExist());

            reading.finish();
        }

        claim("once it finishes, records.jsonl stands, holding a line for each of the two",
                () -> assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).hasSize(2));
    }

    @Test
    @Story("The records file")
    @DisplayName("The plugin is named retrieval, lists its records in records.jsonl, draws from no pages and takes every passage")
    void thePluginDrawsFromNoTreeAndTakesMemberPassages(@TempDir Path tree) throws IOException {
        RetrievalPlugin plugin = new RetrievalPlugin();
        Files.createDirectories(tree.resolve("retrieval"));

        claim("its name, which is its folder and its destination, is retrieval, and its list records.jsonl",
                () -> {
                    assertThat(plugin.name()).isEqualTo("retrieval");
                    assertThat(plugin.listName()).isEqualTo("records.jsonl");
                });
        claim("it draws from no tree, so the outputs may name it without the pages",
                () -> assertThat(plugin.drawsFromTheTree()).isFalse());
        claim("it reads retrieval records and takes the members' passages",
                () -> {
                    try (RetrievalRecords reading = plugin.records(contextOf(tree)).orElseThrow()) {
                        assertThat(reading.takesMemberPassages()).isTrue();
                    }
                });
    }

    /** The plugin's file once {@code records} are taken and the reading has finished and closed. */
    private static Path drawn(Path tree, List<RetrievalRecord> records) throws IOException {
        RetrievalPlugin plugin = new RetrievalPlugin();
        Files.createDirectories(tree.resolve(plugin.name()));
        try (RetrievalRecords reading = plugin.records(contextOf(tree)).orElseThrow()) {
            for (RetrievalRecord record : records) {
                reading.take(record);
            }
            reading.finish();
        }
        return tree.resolve(plugin.name()).resolve(plugin.listName());
    }

    private static Plugin.DrawingContext contextOf(Path tree) {
        return new Plugin.DrawingContext(
                tree, GENERATION_RUN, ReplacingMove.ATOMIC, new HashSet<>(), DeliverableLanguage.ENGLISH);
    }
}
