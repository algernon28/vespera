package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.RecordedClusterFault;
import io.algernon.vespera.synthesis.RecordedSynthesisDoc;
import io.algernon.vespera.synthesis.SynthesisDocs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 6b when the serving engine refuses a cluster's prompt as longer than the window (#332):
 * the refusal costs that cluster and nothing else, where it used to fail the step, roll back every
 * reason recorded before it, and end the invocation.
 *
 * <p><b>What was observed.</b> On Ollama 0.33.2's llama-server path,
 * the first call — the largest cluster — came back HTTP 400, {@code exceed_context_size_error}, and
 * Spring AI raised it as {@link NonTransientAiException} out of {@code OllamaChatModel.call}. Nothing
 * caught it, so no cluster was written and no {@code cluster_fault} row was left, and the invocation
 * exited 1 with a stack trace.
 *
 * <p><b>How the refusal is scripted.</b> {@link GenerationScriptedBeans#refuseEvery} refuses every
 * call whose question the predicate picks out, the counting call before an answer as well as the
 * answer (ADR-166). Which question is refused is keyed on the cluster's name as the fixture's own
 * scripts are, so no call order is pinned. Refused whatever it carries, a cluster has no shorter
 * question the engine will read, and so is the one case in which a refusal on length still costs it.
 *
 * <p><b>Scripted fixture state is dropped before each test as well as after</b>, because the fixture's
 * counters are static and shared with every other class in this package, and the order classes run in
 * differs by machine.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("332")
@Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
class GenerationPromptRefusedInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /**
     * How much every call in this class may read, in tokens, stated rather than left to whatever an
     * earlier class wrote into the profile this working directory shares.
     */
    private static final String THE_READING_WINDOW = "8192";

    /** The same window as a number, which is what the refusal names. */
    private static final int THE_WINDOW = 8192;

    /** How long the engine counted the refused question, in tokens: the count the defect was found at. */
    private static final int THE_PROMPT_THE_ENGINE_COUNTED = 8280;

    /**
     * The refusal as it reached the caller in the run that found the defect: Spring AI's {@code "HTTP
     * <status> - <body>"}, the body being the runner's own JSON quoted under Ollama's {@code error} key.
     */
    private static final String THE_REFUSAL_ON_LENGTH = "HTTP 400 - {\"error\":\"{\\\"error\\\":{\\\"code\\\":400,"
            + "\\\"message\\\":\\\"request (" + THE_PROMPT_THE_ENGINE_COUNTED + " tokens) exceeds the available"
            + " context size (" + THE_WINDOW + " tokens), try increasing it\\\",\\\"type\\\":"
            + "\\\"exceed_context_size_error\\\",\\\"n_prompt_tokens\\\":" + THE_PROMPT_THE_ENGINE_COUNTED
            + ",\\\"n_ctx\\\":" + THE_WINDOW + "}}\"}";

    /** Ollama's refusal of a model it has never pulled: not about any one cluster. */
    private static final String THE_REFUSAL_OF_A_MODEL_NEVER_PULLED =
            "HTTP 404 - {\"error\":\"model 'qwen3:8b' not found\"}";

    /** The name given to the cluster put ahead of the one the corpus produces. */
    private static final String THE_CLUSTER_AHEAD = "A group asked about first";

    /** An answer nothing can read back into a heading and its writing: it stops partway through. */
    private static final String AN_ANSWER_NOTHING_CAN_READ = "{\"title\":\"Site Safety Audits\",\"prose\":";

    /** What one cluster left unwritten leaves behind: one reason, kept against it. */
    private static final int ONE_REASON_KEPT = 1;

    /** Two clusters turned down in one piece of work, which the second one must not undo. */
    private static final int TWO_REASONS_KEPT = 2;

    /** What the other cluster of a two-cluster run leaves behind when its own answer was believed. */
    private static final int ONE_PIECE_OF_WRITING = 1;

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
    private SynthesisDocs synthesisDocs;

    @Autowired
    private ClusterFaults clusterFaults;

    @Test
    @Story("A question too long for the window is turned down rather than stopping the whole run")
    @DisplayName("A group whose question the serving engine refuses as too long leaves the groups after it written")
    void carriesOnPastAClusterWhosePromptWasRefusedAsPastTheWindow(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpusOfTwoClusters(root, seeds);
        refuseEveryQuestion(asked -> asked.contains(THE_CLUSTER_AHEAD), THE_REFUSAL_ON_LENGTH);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: one group's question being too long for the window is a"
                        + " fact about that group's documents, and a run that fell over on it would cost"
                        + " every group after it",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the group after the refused one carries its writing, " + ONE_PIECE_OF_WRITING
                        + " piece of it, so the refusal cost its own group and nothing further along",
                () -> assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and exactly " + ONE_REASON_KEPT + " reason is kept, for the refused group, saying its"
                        + " question did not fit the window",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
        claim(
                "and the reason carries the count the engine refused, " + THE_PROMPT_THE_ENGINE_COUNTED
                        + " tokens against a window of " + THE_WINDOW,
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().detail())
                        .contains(String.valueOf(THE_PROMPT_THE_ENGINE_COUNTED))));
    }

    @Test
    @Story("One answer nobody believes costs one group and no more")
    @DisplayName("A question refused as too long leaves the reasons kept before it standing")
    void keepsTheReasonsFromEarlierInTheStepPastAPromptRefusedAsPastTheWindow(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpusOfTwoClusters(root, seeds);
        GenerationScriptedBeans.answerFor(THE_CLUSTER_AHEAD, ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));
        refuseEveryQuestion(asked -> !asked.contains(THE_CLUSTER_AHEAD), THE_REFUSAL_ON_LENGTH);

        cli.run("run", root.toString());

        claim(
                "both reasons are kept, all " + TWO_REASONS_KEPT + " of them: the refusal is turned down"
                        + " like any other unusable answer, where a failure of another kind would have"
                        + " rolled the piece of work back and taken the reason recorded before it with it"
                        + " -- which is what the run that found this lost",
                () -> assertThat(reasonsKept(root)).hasSize(TWO_REASONS_KEPT));
    }

    @Test
    @Story("A refusal that would come back for every group still stops the run")
    @DisplayName("A refusal because the model was never fetched still ends the run unsuccessfully")
    void stillFailsTheStepOnARefusalThatIsNotAboutLength(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpusOfTwoClusters(root, seeds);
        refuseEveryQuestion(asked -> true, THE_REFUSAL_OF_A_MODEL_NEVER_PULLED);

        cli.run("run", root.toString());

        claim(
                "the invocation does not report success: a model the engine does not have refuses every"
                        + " group alike, so it is the run that is wrong rather than one group, and turning"
                        + " it into a group's reason would spend five calls to say so",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and no reason is kept against any group, since nothing is wrong with any of them",
                () -> assertThat(reasonsKept(root)).isEmpty());
    }

    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /**
     * Makes the model refuse, with {@code refusal} as Spring AI raises it, every question {@code
     * refused} picks out, whether it is being counted or answered (ADR-166): here the engine refuses
     * the question whatever documents it carries, so no shorter question is left for it to read.
     */
    private static void refuseEveryQuestion(Predicate<String> refused, String refusal) {
        GenerationScriptedBeans.refuseEvery(refused, refusal);
    }

    /**
     * A corpus of two documents, walked once, its arrangement approved, with a second cluster put ahead
     * of the one it produced and one of the documents moved into it, so both are asked about.
     */
    private void anApprovedCorpusOfTwoClusters(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads in the same window")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .build());
        RunId arrangement = theApprovedArrangement(root);
        jdbcTemplate.update(
                "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                        + " document_count, partition_order, cluster_order)"
                        + " SELECT run_id, winning_seed_occurrence_id, cluster_ordinal + 1, ?,"
                        + " document_count, partition_order, cluster_order - 1"
                        + " FROM cluster WHERE run_id = ?",
                THE_CLUSTER_AHEAD,
                arrangement.value());
        jdbcTemplate.update(
                "UPDATE document_cluster SET cluster_ordinal = cluster_ordinal + 1 WHERE rowid ="
                        + " (SELECT rowid FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)"
                        + " ORDER BY occurrence_id DESC LIMIT 1)",
                arrangement.value());
    }

    /** Everything stage 6b wrote over the clusters of {@code root}, under whichever run it wrote them. */
    private List<RecordedSynthesisDoc> writingKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> WholeRun.synthesisDocs(jdbcTemplate,run).stream())
                .toList();
    }

    /** Every reason stage 6b kept for a cluster of {@code root} it left unwritten. */
    private List<RecordedClusterFault> reasonsKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> WholeRun.clusterFaults(jdbcTemplate,run).stream())
                .toList();
    }

    /** The arrangement the most recent invocation over {@code root} recorded — the one its page names. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** The arrangement of {@code root} the profile's approval names. */
    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }

    /**
     * Every record of this step having run over {@code root}, oldest first — scoped to this corpus's
     * walk, because one working directory serves the whole class and the database outlives each method.
     */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
