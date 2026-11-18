package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The arrangement step writes {@code topics.json} and {@code topics.html} once the topics are named, from what stage 5
 * kept and the names it recorded: one row of counts a topic, the topics no survivor is placed under with each seed
 * feeding them, and the seeds feeding no topic (ADR-416 §1, §2).
 *
 * <p>On {@link TheTopicsAndPlacementAreStoredInvocationTest}'s fixture: a seed of {@link
 * SeedScriptedExtractionBeans#ON_SLUICES} converts to a text that embeds along one axis, one of {@link
 * SeedScriptedExtractionBeans#ON_PUMPS} along another, a tenth of the way towards the first, each seed one passage; at
 * this machine's {@value #TWO_NEIGHBOURS} neighbours for the topics each word's passages form a topic, and at its
 * breadth floor of {@value #THE_FLOOR} a topic of half the passages stands and one of a third does not. This fixture's
 * naming model names the topics {@code Scripted Topic 1}, {@code 2}, … in key order, the topic on pumps' key coming
 * first. Each invocation stops at the arrangement's approval, but the one whose naming is made to stop, and the report
 * is read from the class's working directory as soon as it ends. The report is written on both of the step's branches,
 * where it arranges and where it finds itself already recorded, and not where the naming stops (ADR-416 §1).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("834")
@Link(name = "ADR-416", url = Adr.THE_TOPICS_ANALYSIS_REPORT_LISTS_WHAT_FEEDS_EACH_TOPIC, type = "adr")
class TheTopicsAnalysisReportIsWrittenAtTheArrangementInvocationTest {

    /** This machine's breadth floor: a topic of half the passages stands, one of a third does not. */
    private static final double THE_FLOOR = 0.4;

    /** Two neighbours, so that each passage's neighbours are passages of its own word, and two words two topics. */
    private static final int TWO_NEIGHBOURS = 2;

    /** A word whose text embeds along the first axis. */
    private static final String SLUICES = "sluices";

    /** A word whose text embeds along the second axis, a tenth of the way towards the first. */
    private static final String PUMPS = "pumps";

    /** The name this fixture's model gives the first topic it is asked about, the one whose key comes first. */
    private static final String THE_FIRST_NAME = GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1";

    /** The name it gives the second. */
    private static final String THE_SECOND_NAME = GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 2";

    /** Every seed passage of either fixture: six seeds of one passage each. */
    private static final double SIX_PASSAGES = 6;

    /** The schema {@code topics.json} is published under (ADR-362 §9). */
    private static final Path SCHEMA = Path.of("docs", "report", "topics.schema.json");

    /** What the serving engine answers a question it refuses with, which says nothing of the question's length. */
    private static final String A_REFUSAL = "the serving engine is busy";

    /** What the arrangement step logs where it finds itself recorded under the run it arrives at, before the run. */
    private static final String THE_STEP_FOUND_RECORDED = "the arrangement step was already recorded under run ";

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @BeforeEach
    void theTwoWordsEmbedApartAndTheNamesAreNumberedFromOne() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
        EmbeddingScriptedBeans.embedsTextHolding(SLUICES, 1, 0, 0, 0, 0, 0, 0, 0);
        EmbeddingScriptedBeans.embedsTextHolding(PUMPS, 0.1f, 1, 0, 0, 0, 0, 0, 0);
    }

    @AfterEach
    void everyTextEmbedsAsBeforeAgain() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
    }

    /**
     * Three seeds on sluices and three on pumps, and one corpus document, {@link
     * SeedScriptedExtractionBeans#A_DOCUMENT_IN_THREE_PARTS}, two of whose parts are on sluices and one on pumps: it is
     * placed under the topic on sluices and linked from the one on pumps, as {@code
     * ADocumentIsLinkedFromATopicItFitsInvocationTest} holds, so no document is placed under the topic on pumps.
     */
    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("A topic no document was placed under is listed with each seed document feeding it, and every topic's counts are given")
    void aTopicNoDocumentIsPlacedUnderIsListedWithItsSeeds(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.A_DOCUMENT_IN_THREE_PARTS),
                "a corpus document of " + root.getFileName());
        List<String> onSluices = SeedScriptedExtractionBeans.ON_SLUICES.subList(0, 3);
        List<String> onPumps = SeedScriptedExtractionBeans.ON_PUMPS.subList(0, 3);
        seedsNamed(seeds, onSluices, onPumps);
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        JsonNode json = theReport();
        JsonNode schema = JSON.readTree(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        String onSluicesKey = onSluices.getFirst() + " chunk 0";
        String onPumpsKey = onPumps.getFirst() + " chunk 0";

        claim("the invocation ended well, at the arrangement's approval", () -> assertThat(cli.getExitCode()).isZero());
        claim("and the page was written beside its JSON",
                () -> assertThat(workingDirectory.resolve("topics.html")).isRegularFile());
        claim("the topic on sluices comes first, holding the one document, though its key comes after the other's:"
                        + " named second, its three passages a half of the six, its three seeds, and no link; then the"
                        + " topic on pumps, named first, its three passages a half, its three seeds, no document and"
                        + " the one document linked from it",
                () -> assertThat(AnalysisReportRows.of(sectionOf(json, "topics"))).containsExactly(
                        Arrays.asList(THE_SECOND_NAME, onSluicesKey, 3.0, 0.5, 3.0, 1.0, 0.0),
                        Arrays.asList(THE_FIRST_NAME, onPumpsKey, 3.0, 0.5, 3.0, 0.0, 1.0)));
        claim("the topic on pumps is listed as no document's, once for each of its three seeds, in path order, each"
                        + " giving it one passage",
                () -> assertThat(AnalysisReportRows.of(sectionOf(json, "topics-with-no-document")))
                        .containsExactlyElementsOf(onPumps.stream()
                                .map(seed -> Arrays.<Object>asList(THE_FIRST_NAME, onPumpsKey, seed, 1.0))
                                .toList()));
        claim("no seed is listed as feeding no topic",
                () -> assertThat(sectionOf(json, "seeds-feeding-no-topic").get("table").isNull()).isTrue());
        claim("the headline counts 2 topics, " + (int) SIX_PASSAGES + " seed passages, 1 topic with no document and no"
                        + " seed feeding none",
                () -> assertThat(figuresOf(json)).containsExactly(
                        "topics 2", "seed-passages 6", "topics-with-no-document 1", "seeds-feeding-no-topic 0"));
        claim("and the JSON follows its published schema",
                () -> assertThat(SchemaWalker.problems(json, schema)).isEmpty());
    }

    /**
     * Four seeds on sluices and two on pumps: the topic on pumps, a third of the passages, is under the floor and
     * dropped, and its two seeds feed no topic, as {@code TheTopicsAndPlacementAreStoredInvocationTest} holds. The one
     * corpus document, {@link SeedScriptedExtractionBeans#A_DOCUMENT_ON_PUMPS}, is placed under the one topic that
     * stands.
     */
    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("A seed document feeding no topic is listed, and the share of the topic that stands is of every seed passage")
    void aSeedFeedingNoTopicIsListed(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.A_DOCUMENT_ON_PUMPS),
                "a corpus document of " + root.getFileName());
        List<String> onSluices = SeedScriptedExtractionBeans.ON_SLUICES.subList(0, 4);
        List<String> onPumps = SeedScriptedExtractionBeans.ON_PUMPS.subList(0, 2);
        seedsNamed(seeds, onSluices, onPumps);
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        JsonNode json = theReport();

        claim("the invocation ended well, at the arrangement's approval", () -> assertThat(cli.getExitCode()).isZero());
        claim("the one topic is given, named first, its four passages 0.667 of the six, the dropped topic's two counted"
                        + " among them, its four seeds, the one document and no link",
                () -> assertThat(AnalysisReportRows.of(sectionOf(json, "topics"))).containsExactly(
                        Arrays.asList(THE_FIRST_NAME, onSluices.getFirst() + " chunk 0", 4.0, 0.667, 4.0, 1.0, 0.0)));
        claim("the two seeds on pumps are listed as feeding no topic, by their paths, in path order",
                () -> assertThat(AnalysisReportRows.of(sectionOf(json, "seeds-feeding-no-topic")))
                        .containsExactlyElementsOf(onPumps.stream().map(seed -> Arrays.<Object>asList(seed)).toList()));
        claim("no topic is listed as no document's",
                () -> assertThat(sectionOf(json, "topics-with-no-document").get("table").isNull()).isTrue());
        claim("and the headline counts 1 topic, " + (int) SIX_PASSAGES + " seed passages, no topic with no document and"
                        + " 2 seeds feeding none",
                () -> assertThat(figuresOf(json)).containsExactly(
                        "topics 1", "seed-passages 6", "topics-with-no-document 0", "seeds-feeding-no-topic 2"));
    }

    /**
     * {@link #aTopicNoDocumentIsPlacedUnderIsListedWithItsSeeds}'s fixture, run to the approval twice on one profile, the
     * report's two files deleted in between: the second invocation finds the arrangement step recorded under the run the
     * first minted, and writes the report again from the rows recorded, as it writes {@code arrangement.html} (ADR-154
     * §2). Which branch the step took is read off the line it logs on finding itself recorded.
     */
    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("An invocation finding the arrangement already made writes the topics page and its data again, as the first wrote them")
    void anInvocationFindingTheArrangementRecordedWritesTheTopicsAnalysisReportAgain(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.A_DOCUMENT_IN_THREE_PARTS),
                "a corpus document of " + root.getFileName());
        seedsNamed(seeds, SeedScriptedExtractionBeans.ON_SLUICES.subList(0, 3),
                SeedScriptedExtractionBeans.ON_PUMPS.subList(0, 3));
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        int first = cli.getExitCode();
        List<List<Object>> rowsTheFirstWrote = AnalysisReportRows.of(sectionOf(theReport(), "topics"));
        Files.delete(workingDirectory.resolve("topics.json"));
        Files.delete(workingDirectory.resolve("topics.html"));
        ListAppender<ILoggingEvent> lines = new ListAppender<>();
        lines.start();
        Logger arrangementLogger = (Logger) LoggerFactory.getLogger(ArrangementTasklet.class);
        arrangementLogger.addAppender(lines);
        try {
            cli.run("run", root.toString());
        } finally {
            arrangementLogger.detachAppender(lines);
            lines.stop();
        }

        claim("both invocations ended well, at the arrangement's approval",
                () -> assertThat(List.of(first, cli.getExitCode())).containsExactly(0, 0));
        claim("the second found the arrangement step already recorded, and so named and arranged nothing",
                () -> assertThat(lines.list)
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .anyMatch(line -> line.startsWith(THE_STEP_FOUND_RECORDED)));
        claim("the page and its JSON lie in the working directory again",
                () -> assertThat(List.of(workingDirectory.resolve("topics.html"), workingDirectory.resolve("topics.json")))
                        .allSatisfy(file -> assertThat(file).isRegularFile()));
        claim("and the JSON's row for each topic is the one the first invocation wrote",
                () -> assertThat(AnalysisReportRows.of(sectionOf(theReport(), "topics")))
                        .isEqualTo(rowsTheFirstWrote));
    }

    /**
     * {@link #aTopicNoDocumentIsPlacedUnderIsListedWithItsSeeds}'s fixture, the serving engine refusing every question
     * about sluices, so that the step stops once the topic on pumps, whose key comes first, is named (ADR-409 §6). The
     * report's files are deleted first, since the class's working directory keeps what its other tests wrote.
     */
    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("Where naming the topics stops, neither the topics page nor its data is written")
    void aStopWhileNamingWritesNoTopicsAnalysisReport(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.A_DOCUMENT_IN_THREE_PARTS),
                "a corpus document of " + root.getFileName());
        seedsNamed(seeds, SeedScriptedExtractionBeans.ON_SLUICES.subList(0, 3),
                SeedScriptedExtractionBeans.ON_PUMPS.subList(0, 3));
        EveryGateOpen.throughStageFive(profileStore, seeds);
        Files.deleteIfExists(workingDirectory.resolve("topics.json"));
        Files.deleteIfExists(workingDirectory.resolve("topics.html"));
        GenerationScriptedBeans.refuseEvery(question -> question.contains(SLUICES), A_REFUSAL);

        cli.run("run", root.toString());

        claim("the invocation failed, the naming model giving no answer for the topic on sluices",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and neither topics.json nor topics.html was written",
                () -> assertThat(List.of(workingDirectory.resolve("topics.json"), workingDirectory.resolve("topics.html")))
                        .allSatisfy(file -> assertThat(file).doesNotExist()));
    }

    private static void seedsNamed(Path seeds, List<String> onSluices, List<String> onPumps) throws IOException {
        for (String name : List.of(onSluices, onPumps).stream().flatMap(List::stream).toList()) {
            Files.writeString(seeds.resolve(name), "a seed document " + name + " of " + seeds.getFileName());
        }
    }

    /** {@code topics.json} as the last invocation of the class wrote it. */
    private static JsonNode theReport() throws IOException {
        return JSON.readTree(Files.readString(workingDirectory.resolve("topics.json"), StandardCharsets.UTF_8));
    }

    private static JsonNode sectionOf(JsonNode json, String id) {
        for (JsonNode section : json.get("sections")) {
            if (section.get("id").asString().equals(id)) {
                return section;
            }
        }
        throw new AssertionError("no section " + id + " in " + json);
    }

    private static List<String> figuresOf(JsonNode json) {
        List<String> figures = new ArrayList<>();
        json.get("headline").forEach(figure -> figures.add(
                figure.get("key").asString() + " " + figure.get("value").asLong()));
        return figures;
    }
}
