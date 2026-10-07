package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.LabelQuestion;
import io.algernon.vespera.embedding.RelevanceLabeller;
import io.algernon.vespera.profile.Profile;
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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code vespera label --auto} through the command an operator types (ADR-197): a local labeller
 * answers the sample's questions, its answers say it set them, the floor is the rule over the labels,
 * and anything a person answered or wrote stays theirs.
 *
 * <p>The labeller is a scripted double that says relevant to everything and counts its questions. No
 * test here reaches a model.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AutoLabelling.class, LocalLabellingInvocationTest.Beans.class})
@Epic("Relevance")
@Feature("Local labelling")
@Issue("423")
@Link(name = "ADR-197", url = Adr.A_LOCAL_MODEL_LABELS_AND_THE_FLOOR_IS_A_RULE, type = "adr")
@ExtendWith(OutputCaptureExtension.class)
class LocalLabellingInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String LABEL_FILE = "relevance-labels.yaml";
    private static final String THE_LABELLER = "scripted-labeller";
    private static final String ONE_DOCUMENT_IN_THE_SAMPLE = "the fixture corpus holds one document";
    private static final Pattern SCORE_IN_FILE = Pattern.compile("score: ([0-9.eE-]+)");

    /** Questions the scripted labeller was asked since the test began. */
    static final AtomicInteger QUESTIONS = new AtomicInteger();

    /** The opening each of those questions put to the labeller, empty where none could be read. */
    static final List<Optional<String>> OPENINGS_PUT = new CopyOnWriteArrayList<>();

    /** A path written into the label file by hand, which no file of the fixture corpus has. */
    private static final String A_PATH_THE_RUN_NEVER_WALKED = "a-path-the-run-never-walked.txt";

    /** The words every scripted conversion carries, so finding them means a document's opening was put. */
    private static final String WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH = "stubbed but real content";

    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller scriptedLabeller() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return THE_LABELLER;
                }

                @Override
                public Optional<String> refusal() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    QUESTIONS.incrementAndGet();
                    OPENINGS_PUT.add(question.opening());
                    return Optional.of(true);
                }
            };
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
    void forgetQuestions() {
        QUESTIONS.set(0);
        OPENINGS_PUT.clear();
    }

    /**
     * The document is rewritten in place after the run that scored it, with nothing a directory listing
     * shows changed, and the model is still put its opening (ADR-206 sections 4 and 5).
     *
     * <p>{@code label --auto} runs no job, so it has no run of its own to read the key under: it follows
     * the recorded chain upstream from the scoring run its label file names to the run that converted the
     * document. Until ADR-206 it hashed the file as it is now, found no conversion under that, and asked
     * the model about a document with no opening.
     */
    @Test
    @Issue("349")
    @Story("The model is put the document as it was converted")
    @DisplayName("A document rewritten in place since it was scored is still put to the model with its opening")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    void putsTheOpeningOfADocumentRewrittenInPlace(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        UnseenEditFixture.editedWithoutTheWalkNoticing(root.resolve("corpus.txt"));

        cli.run("label", "--auto");

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the model was asked about the one document in the sample (" + ONE_DOCUMENT_IN_THE_SAMPLE
                        + "), and the question carried the opening of the text its score was computed"
                        + " from: the opening is found by the key recorded when the document was"
                        + " converted, not by what its file holds now. A question with no opening would"
                        + " have the model judge a document by its name alone",
                () -> assertThat(OPENINGS_PUT)
                        .singleElement()
                        .satisfies(opening -> assertThat(opening)
                                .hasValueSatisfying(text ->
                                        assertThat(text).contains(WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH))));
    }

    /**
     * A label file edited by hand to name a path the run never walked (ADR-206 section 5).
     *
     * <p>{@code label --auto} finds a document's opening through its occurrence in the walk of the run
     * the file names, and such a path has none. It arises only from a hand edit: every path a run writes
     * into the file is one of its own walk.
     */
    @Test
    @Issue("349")
    @Story("The model is put the document as it was converted")
    @DisplayName("A path in the label file that the run never walked is asked about with no opening, and a warning names it")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    void asksAboutAPathTheRunNeverWalkedWithNoOpening(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aScoredCorpus(root, seeds);
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("corpus.txt", A_PATH_THE_RUN_NEVER_WALKED));

        cli.run("label", "--auto");

        claim(
                "the invocation reports success: a file somebody edited is theirs to correct, and one"
                        + " path nothing is known about is no reason to stop",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the model was asked about the one entry in the file (" + ONE_DOCUMENT_IN_THE_SAMPLE
                        + ", renamed here), and the question carried no opening: nothing was converted"
                        + " under that path, so there is nothing to show",
                () -> assertThat(OPENINGS_PUT).containsExactly(Optional.empty()));
        claim(
                "and a warning names the path, so whoever edited the file can see which entry it was",
                () -> assertThat(output.getAll())
                        .contains(A_PATH_THE_RUN_NEVER_WALKED)
                        .contains("is in the label file but not in the walk"));
    }

    @Test
    @Story("A label a model set says so")
    @DisplayName("The answers are recorded as the model's, in the ledger and in the label file")
    void recordsTheAnswersWithTheModelsName(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);

        cli.run("label", "--auto");

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the model was asked about the one document in the sample (" + ONE_DOCUMENT_IN_THE_SAMPLE + ")",
                () -> assertThat(QUESTIONS.get()).isEqualTo(1));
        claim("its answer is recorded", () -> assertThat(relevantAnswers(seeds)).isEqualTo(1));
        claim(
                "and the ledger names the model that set it",
                () -> assertThat(labeller(seeds)).isEqualTo(THE_LABELLER));
        claim(
                "the label file shows the answer and who set it, so the operator can read and correct it",
                () -> assertThat(labelFile())
                        .contains("relevant: true")
                        .containsPattern("labelledBy: \"?" + THE_LABELLER + "\"?"));
    }

    @Test
    @Story("The floor is a rule over the labels")
    @DisplayName("The floor is set to the lowest relevant score, and its provenance names the rule and the model")
    void setsTheFloorByTheRule(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);

        cli.run("label", "--auto");

        Profile profile = profileStore.load();
        claim(
                "the floor is the score the one relevant document was shown with, because the lowest"
                        + " relevant score is the floor and there is one",
                () -> {
                    assertThat(profile.relevanceScoreFloor().value()).isNotNull();
                    assertThat(Double.parseDouble(profile.relevanceScoreFloor().value()))
                            .isEqualTo(scoreInFile());
                });
        claim(
                "its provenance says a rule set it and that a model set the label it rests on",
                () -> assertThat(profile.relevanceScoreFloor().provenance())
                        .contains("loses no documentation")
                        .contains(THE_LABELLER));
    }

    @Test
    @Story("An operator can overrule any answer")
    @DisplayName("A floor the operator wrote is never overwritten")
    void leavesAFloorAPersonWrote(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor("0.123", "set by the operator, by hand")
                .build());

        cli.run("label", "--auto");

        claim(
                "the value and the provenance are the operator's still",
                () -> {
                    assertThat(profileStore.load().relevanceScoreFloor().value()).isEqualTo("0.123");
                    assertThat(profileStore.load().relevanceScoreFloor().provenance())
                            .isEqualTo("set by the operator, by hand");
                });
    }

    @Test
    @Story("An operator can overrule any answer")
    @DisplayName("An answer a person gave is not asked again and not replaced")
    void aPersonsAnswerIsNotReplaced(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: false"));
        cli.run("label");

        cli.run("label", "--auto");

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the model was not asked about it", () -> assertThat(QUESTIONS.get()).isZero());
        claim(
                "the person's no stands, unmarked",
                () -> {
                    assertThat(relevantAnswers(seeds)).isZero();
                    assertThat(labeller(seeds)).isNull();
                });
    }

    @Test
    @Story("An operator can overrule any answer")
    @DisplayName("Editing the model's answer in the file and labelling makes it the person's")
    void aPersonCanOverruleTheModel(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        cli.run("label", "--auto");

        cli.run("label");
        claim(
                "ingesting the file as the model left it changes nothing about who set the answer",
                () -> assertThat(labeller(seeds)).isEqualTo(THE_LABELLER));

        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: true", "relevant: false"));
        cli.run("label");

        claim(
                "the person's answer replaced the model's",
                () -> assertThat(relevantAnswers(seeds)).isZero());
        claim(
                "and nothing says a model set it any more",
                () -> assertThat(labeller(seeds)).isNull());
    }

    @Test
    @Story("An operator can overrule any answer")
    @DisplayName("An answer typed into the file and not yet recorded stops --auto before any question")
    void refusesWhileAnAnswerIsNotYetRecorded(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aScoredCorpus(root, seeds);
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: false"));
        byte[] before = Files.readAllBytes(labels);

        cli.run("label", "--auto");

        claim("the invocation fails", () -> assertThat(cli.getExitCode()).isEqualTo(1));
        claim(
                "it says how many answers are waiting and what to do",
                () -> assertThat(output.getAll())
                        .contains("the label file holds 1 answer(s) not yet recorded; run vespera label first"));
        claim("the model was asked nothing", () -> assertThat(QUESTIONS.get()).isZero());
        claim(
                "and the file is as the person left it, so what they typed is not overwritten",
                () -> assertThat(Files.readAllBytes(labels)).isEqualTo(before));
    }

    @Test
    @Story("The floor is a rule over the labels")
    @DisplayName("A floor the rule wrote is written again by the rule")
    void rewritesAFloorTheRuleWrote(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor("0.123", "set by the rule that loses no documentation (an earlier run)")
                .build());

        cli.run("label", "--auto");

        claim(
                "the rule's own earlier value is replaced by what the labels say now",
                () -> assertThat(Double.parseDouble(profileStore.load().relevanceScoreFloor().value()))
                        .isEqualTo(scoreInFile()));
    }

    @Test
    @Story("A label a model set says so")
    @DisplayName("A later vespera run keeps the mark of who set an answer in the label file")
    void aLaterRunKeepsTheMark(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        cli.run("label", "--auto");

        cli.run("run", root.toString());

        claim(
                "the file the later run wrote still names the model beside the answer",
                () -> assertThat(labelFile())
                        .contains("relevant: true")
                        .containsPattern("labelledBy: \"?" + THE_LABELLER + "\"?"));
    }

    @Test
    @Story("A label a model set says so")
    @DisplayName("--auto with a file is refused, since it works on the file the last run wrote")
    void refusesAFileWithAuto(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);

        cli.run("label", "--auto", workingDirectory.resolve(LABEL_FILE).toString());

        claim("the invocation fails", () -> assertThat(cli.getExitCode()).isNotZero());
        claim("the model was asked nothing", () -> assertThat(QUESTIONS.get()).isZero());
    }

    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
        cli.run("run", root.toString());
    }

    private String labelFile() throws IOException {
        return Files.readString(workingDirectory.resolve(LABEL_FILE));
    }

    private double scoreInFile() throws IOException {
        Matcher matcher = SCORE_IN_FILE.matcher(labelFile());
        assertThat(matcher.find()).as("the label file carries a score").isTrue();
        return Double.parseDouble(matcher.group(1));
    }

    private String seedSetOf(Path seeds) {
        return io.algernon.vespera.corpus.Walk.canonicalRoot(seeds).toString();
    }

    private long relevantAnswers(Path seeds) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_label WHERE seed_set = ? AND relevant = 1",
                Long.class,
                seedSetOf(seeds));
    }

    private String labeller(Path seeds) {
        return jdbcTemplate
                .queryForList(
                        "SELECT labelled_by FROM relevance_label_provenance WHERE seed_set = ?",
                        String.class,
                        seedSetOf(seeds))
                .stream()
                .findFirst()
                .orElse(null);
    }
}
