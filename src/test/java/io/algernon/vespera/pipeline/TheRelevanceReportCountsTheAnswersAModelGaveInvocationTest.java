package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * The relevance report's counter over the answers a local model gave (ADR-205, #444), beside the one it
 * already has over every recorded answer (ADR-192 section 4).
 *
 * <p>The report looks each answer a model gave up in the ledger, one statement an answer, to learn which
 * occurrence of this walk it is about. ADR-192 section 1 counts a loop whose body reads the database, and
 * this one arrived with ADR-197, after ADR-192's survey, so it had no counter. ADR-205 gives it one: {@value
 * #MODEL_ANSWERS_MATCHED}, over the answers a model gave for the seed set, an item being one answer looked
 * up, matched or not, opened once the timed read of those answers (ADR-204 section 2) has ended.
 *
 * <p>The fixtures are {@code StageFiveReportsItsProgressInvocationTest}'s: a corpus of {@value
 * #CORPUS_DOCUMENTS} documents and one seed, every answer written through {@code RelevanceLabels} after a
 * first invocation, and the lines read through a list appender on the application's own logger. Both totals
 * are under 40, so every item is a line. The two counters are told apart by their totals: a model's answer
 * is a recorded answer too, so the counter over every answer goes through the person's as well.
 *
 * <p><b>Not reached here:</b> a scoring run with no walk, where neither counter is opened and neither read
 * is made; past the report's gates a scoring run always has one.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("444")
@Link(name = "ADR-205", url = Adr.THE_REPORT_COUNTS_THE_ANSWERS_A_MODEL_GAVE, type = "adr")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class TheRelevanceReportCountsTheAnswersAModelGaveInvocationTest {

    /** How many corpus documents each test writes. */
    static final int CORPUS_DOCUMENTS = 3;

    /** The answers the local model gave about documents the collection holds: the first two of the three. */
    private static final int MODEL_ANSWERS_ABOUT_DOCUMENTS_HELD = 2;

    /** One more answer of the model's, about a document the collection no longer holds under that path. */
    private static final int MODEL_ANSWERS_ABOUT_A_DOCUMENT_GONE = 1;

    /** Every answer the model gave for the seed set: what its counter is over. */
    private static final int MODEL_ANSWERS = MODEL_ANSWERS_ABOUT_DOCUMENTS_HELD + MODEL_ANSWERS_ABOUT_A_DOCUMENT_GONE;

    /** The one answer a person gave, about the third document. */
    private static final int ONE_ANSWER_OF_A_PERSON = 1;

    /** Every answer recorded for the seed set, whoever gave it: what the other counter is over. */
    private static final int RECORDED_ANSWERS = MODEL_ANSWERS + ONE_ANSWER_OF_A_PERSON;

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** A labeller's identity, as ADR-197 section 3 shapes one. No such model exists. */
    private static final String A_LABELLER = "ollama:synthetic-labeller;digest=synthetic";

    /** A path no file of the corpus has. */
    private static final String A_DOCUMENT_GONE = "moved-away-since-it-was-answered.txt";

    private static final String SAMPLED_SURVIVORS = "Stage 5 (relevance report, sampled survivors)";
    private static final String ANSWERS_MATCHED = "Stage 5 (relevance report, answers matched)";
    private static final String MODEL_ANSWERS_MATCHED = "Stage 5 (relevance report, model answers matched)";

    /** The report's own name, which its statement lines open with. */
    private static final String THE_REPORT = "Stage 5 (relevance report)";

    /** What every line the report writes about itself opens with: its statements, its counters, its finish. */
    private static final String ANY_LINE_OF_THE_REPORT = "Stage 5 (relevance report";

    private static final String THE_SCORES = "the scores";
    private static final String THE_RECORDED_ANSWERS = "the recorded answers";
    private static final String THE_SCORES_AGAINST_THE_ANSWERS = "the scores against the answers";
    private static final String THE_EMBEDDER_IDENTITIES = "the embedder identities";
    private static final String THE_ANSWERS_A_MODEL_GAVE = "the answers a model gave";

    private static final String RELEVANCE_REPORT_FINISHED = "Stage 5 (relevance report) finished under scoring run ";

    private static final Pattern SECONDS_TO_ONE_DECIMAL = Pattern.compile(" in \\d+\\.\\d s$");

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

    @Autowired
    private RelevanceDistribution relevanceDistribution;

    @Autowired
    private RelevanceLabels relevanceLabels;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /** Disarms the seed fixture's move before as well as after each method: the hook is static. */
    @BeforeEach
    void captureOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("The relevance report says how many of a local model's answers it has looked up")
    @DisplayName("The relevance report counts each answer a local model gave as it looks it up, under a name of its own")
    void theReportCountsEachAnswerAModelGave(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        List<String> documents = theDocumentsOf(root);
        for (String document : documents.subList(0, MODEL_ANSWERS_ABOUT_DOCUMENTS_HELD)) {
            aModelAnswersAbout(document, root, seeds);
        }
        aModelAnswersAbout(A_DOCUMENT_GONE, root, seeds);
        aPersonAnswersAbout(documents.getLast(), root, seeds);
        logged.list.clear();

        cli.run("run", root.toString());

        claim("the second invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        theReportRan();
        claim(
                "the counter over every recorded answer is as it was: " + RECORDED_ANSWERS + " answers are"
                        + " recorded, " + MODEL_ANSWERS + " a local model's and " + ONE_ANSWER_OF_A_PERSON
                        + " a person's, and it has one line for each",
                () -> assertThat(progressOf(ANSWERS_MATCHED))
                        .containsExactlyElementsOf(ProgressLines.expected(ANSWERS_MATCHED, RECORDED_ANSWERS)));
        claim(
                "the answers a local model gave have a counter of their own, with one line for each of the "
                        + MODEL_ANSWERS + " it gave: " + MODEL_ANSWERS_ABOUT_DOCUMENTS_HELD + " about documents"
                        + " the collection holds and " + MODEL_ANSWERS_ABOUT_A_DOCUMENT_GONE + " about a document"
                        + " it no longer holds, which is looked up and counted all the same. The person's answer"
                        + " is not among them",
                () -> assertThat(progressOf(MODEL_ANSWERS_MATCHED))
                        .containsExactlyElementsOf(ProgressLines.expected(MODEL_ANSWERS_MATCHED, MODEL_ANSWERS)));
        claim(
                "every line of that counter was written by the progress counter itself",
                () -> assertThat(ProgressLines.loggersOf(logged.list, MODEL_ANSWERS_MATCHED))
                        .isNotEmpty()
                        .containsOnly(ProgressLines.theCountersLogger()));
        int sampled = putToAPerson();
        claim(
                "everything the report says about itself, in the order it says it, with nothing left out: it"
                        + " reads the scores, counts the " + sampled + " documents it samples, reads the recorded"
                        + " answers and counts all " + RECORDED_ANSWERS + ", reads the scores against the"
                        + " answers and the embedder identities twice, reads the answers a model gave, and only"
                        + " once that read has said how long it took counts those " + MODEL_ANSWERS
                        + " answers, before the line that the report finished",
                () -> assertThat(everyLineOfTheReport())
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(THE_REPORT, THE_SCORES),
                                ProgressLines.expected(SAMPLED_SURVIVORS, sampled),
                                StatementLines.timedRead(THE_REPORT, THE_RECORDED_ANSWERS),
                                ProgressLines.expected(ANSWERS_MATCHED, RECORDED_ANSWERS),
                                StatementLines.timedRead(THE_REPORT, THE_SCORES_AGAINST_THE_ANSWERS),
                                StatementLines.timedRead(THE_REPORT, THE_EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(THE_REPORT, THE_EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(THE_REPORT, THE_ANSWERS_A_MODEL_GAVE),
                                ProgressLines.expected(MODEL_ANSWERS_MATCHED, MODEL_ANSWERS),
                                List.of(RELEVANCE_REPORT_FINISHED))));
    }

    @Test
    @Story("The relevance report says nothing about a local model's answers where there are none")
    @DisplayName("Where no local model has answered, the report still says it read that model's answers, and counts none")
    void theReportCountsNothingWhereNoModelAnswered(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        aPersonAnswersAbout(theDocumentsOf(root).getLast(), root, seeds);
        logged.list.clear();

        cli.run("run", root.toString());

        claim("the second invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        theReportRan();
        claim(
                "the " + ONE_ANSWER_OF_A_PERSON + " answer a person gave is looked up and counted, one of one",
                () -> assertThat(progressOf(ANSWERS_MATCHED))
                        .containsExactlyElementsOf(ProgressLines.expected(ANSWERS_MATCHED, ONE_ANSWER_OF_A_PERSON)));
        claim(
                "no local model has answered for this seed set, so there is no answer of one to look up and"
                        + " that counter writes no line",
                () -> assertThat(progressOf(MODEL_ANSWERS_MATCHED)).isEmpty());
        int sampled = putToAPerson();
        claim(
                "everything the report says about itself, in the order it says it, with nothing left out: the"
                        + " read of the answers a model gave is still made and still says how long it took, and"
                        + " the next thing the report says is that it finished",
                () -> assertThat(everyLineOfTheReport())
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(THE_REPORT, THE_SCORES),
                                ProgressLines.expected(SAMPLED_SURVIVORS, sampled),
                                StatementLines.timedRead(THE_REPORT, THE_RECORDED_ANSWERS),
                                ProgressLines.expected(ANSWERS_MATCHED, ONE_ANSWER_OF_A_PERSON),
                                StatementLines.timedRead(THE_REPORT, THE_SCORES_AGAINST_THE_ANSWERS),
                                StatementLines.timedRead(THE_REPORT, THE_EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(THE_REPORT, THE_EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(THE_REPORT, THE_ANSWERS_A_MODEL_GAVE),
                                List.of(RELEVANCE_REPORT_FINISHED))));
    }

    /** That the report did its work in this invocation, so a claim about its counters fails for the counter alone. */
    private void theReportRan() {
        claim(
                "the relevance report ran, which its own finishing line says",
                () -> assertThat(operatorLines()).anyMatch(written -> written.startsWith(RELEVANCE_REPORT_FINISHED)));
    }

    /**
     * Every line the report wrote about itself, in order: its statement lines with {@value
     * StatementLines#SECONDS} for their seconds, its counters' lines as written, and its finishing line cut
     * to the words before the run it names.
     */
    private List<String> everyLineOfTheReport() {
        return operatorLines().stream()
                .filter(line -> line.startsWith(ANY_LINE_OF_THE_REPORT))
                .map(line -> line.startsWith(RELEVANCE_REPORT_FINISHED)
                        ? RELEVANCE_REPORT_FINISHED
                        : SECONDS_TO_ONE_DECIMAL.matcher(line).replaceFirst(" in " + StatementLines.SECONDS + " s"))
                .toList();
    }

    /** What the relevance report's own finishing line says it put to a person. */
    private int putToAPerson() {
        String finishing = operatorLines().stream()
                .filter(line -> line.startsWith(RELEVANCE_REPORT_FINISHED))
                .findFirst()
                .orElseThrow();
        String before = finishing.substring(0, finishing.indexOf(" put to a person"));
        return Integer.parseInt(before.substring(before.lastIndexOf(' ') + 1));
    }

    private List<String> progressOf(String label) {
        return ProgressLines.of(logged.list, label);
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and no threshold. */
    private void profile(Path seeds) {
        String noThreshold = null;
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(noThreshold, noThreshold)
                .build());
    }

    /** The paths of the corpus's documents as the walk of {@code root} recorded them, in path order. */
    private List<String> theDocumentsOf(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT f.path FROM file_occurrence f JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY f.path",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    /** An answer about {@code document}, recorded as a local model's, under the first invocation's scoring run. */
    private void aModelAnswersAbout(String document, Path root, Path seeds) {
        relevanceLabels.recordByModel(
                new OccurrencePath(document),
                Walk.canonicalRoot(seeds).toString(),
                true,
                theFirstScoringRunOver(root),
                1.0,
                jdbcTemplate.queryForObject("SELECT DISTINCT embedder_identity FROM vector", String.class),
                A_LABELLER);
    }

    /** An answer about {@code document}, recorded as a person's, under the first invocation's scoring run. */
    private void aPersonAnswersAbout(String document, Path root, Path seeds) {
        relevanceLabels.record(
                new OccurrencePath(document),
                Walk.canonicalRoot(seeds).toString(),
                true,
                theFirstScoringRunOver(root),
                1.0,
                jdbcTemplate.queryForObject("SELECT DISTINCT embedder_identity FROM vector", String.class));
    }

    private RunId theFirstScoringRunOver(Path root) {
        return new RunId(jdbcTemplate
                .queryForList(
                        "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                                + " WHERE run.stage = 'embedding-scoring' AND w.root = ? ORDER BY run.id",
                        String.class,
                        Walk.canonicalRoot(root).toString())
                .getFirst());
    }
}
