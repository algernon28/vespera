package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * The generation step asks each topic's overview after its walk over the clusters, and records it under the
 * generation run keyed by the topic: the writing, the asking it was believed at, and each number the call sent with
 * the cluster at that number (ADR-402 §6, §7; ADR-409 §4, §9).
 *
 * <p>One seed of one passage gives one topic, keyed by {@code seed.txt} and its first chunk, 0; its one corpus document
 * is one cluster, numbered 0, in a topic with no subtopics, -1. This fixture's model writes the cluster as it writes
 * every one, and the overview as {@link GenerationScriptedBeans#OVERVIEW_WRITING} followed by the number of the first
 * cluster its question sends, a writing the overview's check believes.
 *
 * <p>An overview is asked once under its run, or twice, and never again: a topic with a row in {@code topic_overview}
 * or in {@code topic_overview_fault} is not asked again, and has a row in one, never both (ADR-409 §4, §5, §9). Where
 * the model is made to write {@link GenerationScriptedBeans#OVERVIEW_WRITING} alone, its one sentence cites no group,
 * which ADR-402 §7 turns down as {@code CITATION_NOT_IN_RANGE} and asks once more.
 *
 * <p><b>The one-topic arrangement is made, and approved, once for the class</b> (ADR-337 §4), since three tests read
 * it, over {@link #theRoot}. Two of them read one generation run of it, made once, under {@value #GENERATION_MODEL};
 * the turned-down overview is written under {@value #ANOTHER_GENERATION_MODEL}, the approval naming another writing
 * model and so another generation run, which has asked no overview yet. Each approval sets the profile again first,
 * since a test of the class may have left another seed folder in it.
 *
 * <p>Where a topic waits on a cluster turned down once, two topics are made as {@link
 * TopicNamesAreStoredAndShownInvocationTest} makes them: three seeds on sluices and three on pumps, each word's text
 * embedding along an axis of its own, at {@value #TWO_NEIGHBOURS} neighbours for the topics. The topic on sluices holds
 * two survivors, {@link SeedScriptedExtractionBeans#ON_SLUICES}' fourth and {@link
 * SeedScriptedExtractionBeans#A_DOCUMENT_IN_THREE_PARTS}, and the one on pumps one, {@link
 * SeedScriptedExtractionBeans#A_DOCUMENT_ON_PUMPS}: so the topic on sluices is first in the arrangement's order though
 * its key comes after the other's. The cluster of the topic on pumps is answered on the first invocation with writing
 * that ends with a stray closing brace, which ADR-162 turns down.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Generation")
@Feature("Topic overview")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class TheTopicOverviewIsRecordedAtGenerationInvocationTest {

    /** The writing model the approval of the two tests reading one generation run names. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    /** The writing model the approval of the turned-down overview names, so that its generation run is another. */
    private static final String ANOTHER_GENERATION_MODEL = "another-named-writing-model:8b";

    /** The one topic's key, its first passage's seed path and chunk index. */
    private static final String THE_TOPIC = "seed.txt 0";

    /** Two neighbours, so that each passage's neighbours are passages of its own word, and two words two topics. */
    private static final int TWO_NEIGHBOURS = 2;

    /** How many seeds of each word: three passages, two of them each passage's neighbours. */
    private static final int SEEDS_OF_EACH_WORD = 3;

    /** A word whose text embeds along the first axis. */
    private static final String SLUICES = "sluices";

    /** A word whose text embeds along the second axis, a tenth of the way towards the first. */
    private static final String PUMPS = "pumps";

    /** Every invocation over an unchanged archive and approval works under one generation run. */
    private static final int ONE_GENERATION_RUN = 1;

    @TempDir
    static Path workingDirectory;

    /** The corpus root of the one-topic arrangement the class makes once. */
    @TempDir
    static Path theRoot;

    /** The seed folder of that arrangement. */
    @TempDir
    static Path theSeeds;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.topics.neighbours", () -> String.valueOf(TWO_NEIGHBOURS));
    }

    /** The approval name of the one-topic arrangement, made the first time a test asks for it. */
    private static final Once<String> arrangement = new Once<>();

    /**
     * What the one generation run of the one-topic arrangement under {@value #GENERATION_MODEL} left: after its first
     * invocation, and after a later one over the same archive and approval.
     *
     * @param exitCode how the first invocation ended
     * @param generation the generation run it worked under
     * @param overviewQuestions how many overview questions it sent
     * @param overview its {@code topic_overview} rows, as text
     * @param sources its {@code topic_overview_source} rows, as text, by number
     * @param faults its {@code topic_overview_fault} rows' kinds
     * @param finished whether the generation step was recorded finished under the run
     * @param laterExitCode how the later invocation ended
     * @param laterGeneration the generation run the later invocation worked under
     * @param laterOverviewQuestions how many overview questions the later invocation sent
     * @param keptAfterTheLater the overview and its sources under the run after the later invocation
     */
    private record Written(
            int exitCode,
            String generation,
            int overviewQuestions,
            List<String> overview,
            List<String> sources,
            List<String> faults,
            boolean finished,
            int laterExitCode,
            String laterGeneration,
            int laterOverviewQuestions,
            List<String> keptAfterTheLater) {}

    private static final Once<Written> written = new Once<>();

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void nothingIsScriptedAndTheTwoWordsEmbedApart() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
        EmbeddingScriptedBeans.embedsTextHolding(SLUICES, 1, 0, 0, 0, 0, 0, 0, 0);
        EmbeddingScriptedBeans.embedsTextHolding(PUMPS, 0.1f, 1, 0, 0, 0, 0, 0, 0);
    }

    @AfterEach
    void nothingIsScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("Each topic gets a short overview of its groups")
    @DisplayName("A topic's overview is kept under the writing run, believed at the first asking, with the one group it was written over")
    void eachTopicsOverviewIsRecordedWithTheClustersItWasWrittenOver() {
        Written written = written();

        claim("the invocation that writes reports success", () -> assertThat(written.exitCode()).isZero());
        claim("the one topic's overview was asked once", () -> assertThat(written.overviewQuestions()).isEqualTo(1));
        claim("its writing is kept under the generation run by the topic's key, as believed at the first asking",
                () -> assertThat(written.overview())
                        .containsExactly(THE_TOPIC + " asked 1: " + GenerationScriptedBeans.OVERVIEW_WRITING + " [1]"));
        claim("the one number its call sent, 1, is kept with the cluster at that number, the topic's one cluster, of no"
                        + " subtopic",
                () -> assertThat(written.sources()).containsExactly(THE_TOPIC + " number 1 is subtopic -1 cluster 0"));
        claim("and no fault is kept for the topic", () -> assertThat(written.faults()).isEmpty());
    }

    /**
     * Holds that a generation step recorded finished asks no overview again under its run: the later invocation finds
     * the step finished and does not walk at all. That a topic with a row is not asked again by an invocation whose
     * step is still unfinished is {@link #aTopicWaitingOnATurnedDownClusterIsAskedByTheNextInvocationAlone}'s.
     */
    @Test
    @Story("Each topic gets a short overview of its groups")
    @DisplayName("Another invocation that arrives at a finished writing run asks for no overview again")
    void aLaterInvocationAtTheSameGenerationRunAsksNoOverviewAgain() {
        Written written = written();

        claim("the first invocation recorded the generation step finished, every topic having its overview",
                () -> assertThat(written.finished()).isTrue());
        claim("the later invocation reports success, arriving at the same generation run",
                () -> {
                    assertThat(written.laterExitCode()).isZero();
                    assertThat(written.laterGeneration()).isEqualTo(written.generation());
                });
        claim("it sent no overview question", () -> assertThat(written.laterOverviewQuestions()).isZero());
        claim("and the one overview and its one source stand as the first invocation kept them",
                () -> assertThat(written.keptAfterTheLater())
                        .containsExactlyElementsOf(theOverviewAndItsSourcesIn(written))
                        .hasSize(2));
    }

    @Test
    @Story("Each topic gets a short overview of its groups")
    @DisplayName("An overview turned down at both askings is kept as a fault, with nothing written for it")
    void aTurnedDownOverviewLeavesItsFaultAndNothingElse() {
        approvedUnder(ANOTHER_GENERATION_MODEL);
        GenerationScriptedBeans.writesEveryOverviewAs(GenerationScriptedBeans.OVERVIEW_WRITING);

        cli.run("run", theRoot.toString());
        String generation = generationRuns(theRoot).getLast();

        claim("the invocation reports success: a turned-down overview is no verdict, and stops nothing",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("the overview was asked twice, a writing citing no group being asked once more",
                () -> assertThat(GenerationScriptedBeans.overviewQuestionsSent()).hasSize(2));
        claim("one fault is kept for the topic under the generation run: a sentence citing nothing, the first such"
                        + " sentence named, after two answers asked for",
                () -> assertThat(rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' ' || kind || ' asked '"
                                + " || asked || ': ' || detail FROM topic_overview_fault WHERE run_id = ?", generation))
                        .containsExactly(THE_TOPIC + " CITATION_NOT_IN_RANGE asked 2: sentence 1 of 1 cites no group"));
        claim("and no overview, nor any number it was written over, is kept for the topic: a topic has an overview or"
                        + " a fault, never both",
                () -> assertThat(theOverviewAndItsSources(generation)).isEmpty());
    }

    @Test
    @Story("Each topic gets a short overview of its groups")
    @DisplayName("A topic waiting on a group turned down once is asked by the next invocation, which asks no other topic's overview again")
    void aTopicWaitingOnATurnedDownClusterIsAskedByTheNextInvocationAlone(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        String onSluicesAlone = SeedScriptedExtractionBeans.ON_SLUICES.getLast();
        for (String document : List.of(
                onSluicesAlone,
                SeedScriptedExtractionBeans.A_DOCUMENT_IN_THREE_PARTS,
                SeedScriptedExtractionBeans.A_DOCUMENT_ON_PUMPS)) {
            Files.writeString(root.resolve(document), "a corpus document " + document + " of " + root.getFileName());
        }
        List<String> onSluices = SeedScriptedExtractionBeans.ON_SLUICES.subList(0, SEEDS_OF_EACH_WORD);
        List<String> onPumps = SeedScriptedExtractionBeans.ON_PUMPS.subList(0, SEEDS_OF_EACH_WORD);
        for (String name : List.of(onSluices, onPumps).stream().flatMap(List::stream).toList()) {
            Files.writeString(seeds.resolve(name), "a seed document " + name + " of " + seeds.getFileName());
        }
        String onSluicesKey = onSluices.getFirst() + " 0";
        String onPumpsKey = onPumps.getFirst() + " 0";
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        EveryGateOpen.approve(profileStore, ArrangementGate.shortNameOf(new RunId(runOf(root, "arrangement"))), GENERATION_MODEL);
        GenerationScriptedBeans.answerFor(SeedScriptedExtractionBeans.A_DOCUMENT_ON_PUMPS, ScriptedAnswer.saying(
                GenerationScriptedBeans.GENERATED_TITLE, GenerationScriptedBeans.GENERATED_PROSE + " }"));

        cli.run("run", root.toString());
        int firstExit = cli.getExitCode();
        String generation = generationRuns(root).getLast();
        List<String> topicsAskedFirst = GenerationScriptedBeans.promptsSent().stream()
                .map(question -> question.contains(SeedScriptedExtractionBeans.A_DOCUMENT_ON_PUMPS) ? "on pumps" : "on sluices")
                .toList();
        int overviewsAskedFirst = GenerationScriptedBeans.overviewQuestionsSent().size();
        List<String> overviewedFirst = rows("SELECT topic_seed_path || ' ' || topic_chunk_index FROM topic_overview"
                + " WHERE run_id = ?", generation);
        List<String> faultedFirst = rows("SELECT topic_seed_path || ' ' || topic_chunk_index FROM topic_overview_fault"
                + " WHERE run_id = ?", generation);
        boolean finishedFirst = isFinished(generation);
        List<String> onSluicesKept = theOverviewAndItsSourcesOf(generation, onSluices.getFirst());
        GenerationScriptedBeans.forgetScriptedAnswers();

        cli.run("run", root.toString());

        claim("the first invocation that writes reports success, and leaves the generation step unfinished, a group"
                        + " turned down once being asked again",
                () -> {
                    assertThat(firstExit).isZero();
                    assertThat(finishedFirst).isFalse();
                });
        claim("it asked about the groups in the arrangement's order: the topic on sluices' first, though its key comes"
                        + " after the other's, and the one group of the topic on pumps last",
                () -> assertThat(topicsAskedFirst)
                        .startsWith("on sluices")
                        .endsWith("on pumps")
                        .containsOnlyOnce("on pumps"));
        claim("it asked one overview, and kept it for the topic on sluices alone, keeping nothing for the topic on"
                        + " pumps, which waits on its group",
                () -> {
                    assertThat(overviewsAskedFirst).isEqualTo(1);
                    assertThat(overviewedFirst).containsExactly(onSluicesKey);
                    assertThat(faultedFirst).isEmpty();
                });
        claim("the next invocation reports success, arriving at the same generation run",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(generationRuns(root)).hasSize(ONE_GENERATION_RUN).containsExactly(generation);
                });
        claim("it wrote the topic on pumps' group",
                () -> assertThat(rows("SELECT topic_seed_path || ' ' || topic_chunk_index FROM cluster_synthesis_doc"
                                + " WHERE run_id = ? AND topic_seed_path = ?", generation, onPumps.getFirst()))
                        .containsExactly(onPumpsKey));
        claim("then asked one overview, the topic on pumps', and kept it, so that both topics have one",
                () -> {
                    assertThat(GenerationScriptedBeans.overviewQuestionsSent()).hasSize(1);
                    assertThat(rows("SELECT topic_seed_path || ' ' || topic_chunk_index FROM topic_overview"
                                    + " WHERE run_id = ? ORDER BY topic_seed_path, topic_chunk_index", generation))
                            .containsExactly(onPumpsKey, onSluicesKey);
                });
        claim("the topic on sluices' overview and its numbers stand as the first invocation kept them",
                () -> assertThat(theOverviewAndItsSourcesOf(generation, onSluices.getFirst()))
                        .isNotEmpty()
                        .containsExactlyElementsOf(onSluicesKept));
        claim("and the generation step is recorded finished", () -> assertThat(isFinished(generation)).isTrue());
    }

    /** The one-topic arrangement's approval name, made the first time a test asks for it: run to the approval. */
    private String arrangement() {
        return arrangement.get(() -> {
            Files.writeString(theRoot.resolve("corpus.txt"), "a corpus document of " + theRoot.getFileName());
            Files.writeString(theSeeds.resolve("seed.txt"), "a seed document of " + theSeeds.getFileName());
            EveryGateOpen.throughStageFive(profileStore, theSeeds);
            cli.run("run", theRoot.toString());
            return ArrangementGate.shortNameOf(new RunId(runOf(theRoot, "arrangement")));
        });
    }

    /** The one-topic arrangement approved with {@code model} to write under, the profile set again before it. */
    private void approvedUnder(String model) {
        String approval = arrangement();
        EveryGateOpen.throughStageFive(profileStore, theSeeds);
        EveryGateOpen.approve(profileStore, approval, model);
    }

    /**
     * The one generation run under {@value #GENERATION_MODEL}, made the first time a test asks for it: an invocation
     * that writes, and a later one over the same archive and approval, each read as soon as it ended.
     */
    private Written written() {
        return written.get(() -> {
            approvedUnder(GENERATION_MODEL);
            cli.run("run", theRoot.toString());
            int exitCode = cli.getExitCode();
            String generation = generationRuns(theRoot).getLast();
            int overviewQuestions = GenerationScriptedBeans.overviewQuestionsSent().size();
            List<String> overview = rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' asked ' || asked || ': '"
                    + " || writing FROM topic_overview WHERE run_id = ?", generation);
            List<String> sources = rows("SELECT topic_seed_path || ' ' || topic_chunk_index || ' number ' || number"
                    + " || ' is subtopic ' || subtopic_ordinal || ' cluster ' || cluster_ordinal"
                    + " FROM topic_overview_source WHERE run_id = ? ORDER BY number", generation);
            List<String> faults = rows("SELECT kind FROM topic_overview_fault WHERE run_id = ?", generation);
            boolean finished = isFinished(generation);
            GenerationScriptedBeans.forgetScriptedAnswers();

            cli.run("run", theRoot.toString());
            return new Written(
                    exitCode,
                    generation,
                    overviewQuestions,
                    overview,
                    sources,
                    faults,
                    finished,
                    cli.getExitCode(),
                    generationRuns(theRoot).getLast(),
                    GenerationScriptedBeans.overviewQuestionsSent().size(),
                    theOverviewAndItsSources(generation));
        });
    }

    /** The overview and its sources the first invocation of {@link #written()} kept, as {@link #theOverviewAndItsSources} gives them. */
    private static List<String> theOverviewAndItsSourcesIn(Written written) {
        List<String> kept = new ArrayList<>();
        written.overview().forEach(row -> kept.add("overview " + row));
        written.sources().forEach(row -> kept.add("source " + row));
        return kept;
    }

    /** The rows of {@code topic_overview} and of {@code topic_overview_source} under {@code generation}, each as text. */
    private List<String> theOverviewAndItsSources(String generation) {
        List<String> kept = new ArrayList<>(rows("SELECT 'overview ' || topic_seed_path || ' '"
                + " || topic_chunk_index || ' asked ' || asked || ': ' || writing FROM topic_overview WHERE run_id = ?",
                generation));
        kept.addAll(rows("SELECT 'source ' || topic_seed_path || ' ' || topic_chunk_index || ' number ' || number"
                + " || ' is subtopic ' || subtopic_ordinal || ' cluster ' || cluster_ordinal"
                + " FROM topic_overview_source WHERE run_id = ? ORDER BY number", generation));
        return kept;
    }

    /** {@link #theOverviewAndItsSources}, of the one topic whose first passage is of {@code seedPath}. */
    private List<String> theOverviewAndItsSourcesOf(String generation, String seedPath) {
        return theOverviewAndItsSources(generation).stream()
                .filter(row -> row.startsWith("overview " + seedPath + " ") || row.startsWith("source " + seedPath + " "))
                .toList();
    }

    /** Whether the generation step is recorded finished under {@code generation}. */
    private boolean isFinished(String generation) {
        return jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                        Integer.class,
                        generation,
                        StepNames.GENERATION)
                > 0;
    }

    private List<String> rows(String query, Object... arguments) {
        return jdbcTemplate.queryForList(query, String.class, arguments);
    }

    /** The one run of {@code stage} over {@code root}. */
    private String runOf(Path root, String stage) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }

    /** Every generation run over {@code root}, oldest first, the database serving the class. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = 'generation' AND w.root = ?"
                        + " ORDER BY r.rowid",
                String.class,
                Walk.canonicalRoot(root).toString());
    }
}
