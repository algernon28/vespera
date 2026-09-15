package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.ChunkEmbedderBeans;
import io.algernon.vespera.embedding.ClusteringBeans;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.embedding.RelevanceScoringBeans;
import io.algernon.vespera.embedding.SeedCorpusComparison;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.similarity.BoilerplateShingles;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.algernon.vespera.similarity.RedundancyResolution;
import io.algernon.vespera.similarity.RedundancySignatures;
import io.algernon.vespera.similarity.Shingler;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import io.algernon.vespera.synthesis.Clusters;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 6b end to end (ADR-107, ADR-108, ADR-110, ADR-114, #179, #180): the decision the step makes
 * before anything could be written, and then the writing itself.
 *
 * <p>Most of what is under test here is the gate: whether the operator has approved the arrangement
 * that would be written over, and what is recorded when they have. The last two tests are what
 * happens past it — one piece of writing per group, kept against the group it was about.
 *
 * <p>An assumption would have been the house idiom and is wrong here: it would abort on the very
 * condition under test, so a gate that opened and did the wrong thing would look exactly like a gate
 * that could not open.
 *
 * <p><b>The two shut states are one outcome and the ambiguous one is not.</b> Nothing approved, and
 * an approval naming no arrangement of this corpus, both end the invocation successfully having
 * written nothing — in each case nobody has approved anything, and a typo must not be able to behave
 * like an approval. An approval naming two arrangements stops instead, which is what this system
 * already does anywhere it would otherwise have to guess which of two runs was meant (ADR-099).
 *
 * <p><b>The tests that invoke more than once rest on ADR-115.</b> An approval names a 6a run id, a
 * run id hashes the walk it read; before that record a finished walk was never reused, so the
 * approval named an arrangement of a walk the next invocation was not looking at, and the gate could
 * not be opened by any value an operator could type. With walk churn stopped, the approval names the
 * very walk the next invocation reads, and the gate opens on it. The third-invocation test is the
 * other half of the same record: a re-derived generation run id meets the row it already wrote, and
 * {@code Ledger.startRun} continues under it rather than inserting a second time.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
    GenerationJobConfiguration.class,
    GenerationTasklet.class,
    GenerationRun.class,
    GenerationModel.class,
    CensusTasklet.class,
    ByteLevelReductionJobConfiguration.class,
    ByteLevelReductionTasklet.class,
    ExtractionJobConfiguration.class,
    ExtractionItemProcessor.class,
    ExtractionItemWriter.class,
    ExtractionRun.class,
    ExtractionTimeoutStreak.class,
    ExtractionCircuitBreaker.class,
    ExtractionHealthCheckListener.class,
    ContentCensusJobConfiguration.class,
    ContentCensusTasklet.class,
    ContentCensusRun.class,
    RedundancyJobConfiguration.class,
    RedundancyRun.class,
    RedundancyGate.class,
    RedundancyBoilerplate.class,
    RedundancySignatureItemWriter.class,
    RedundancyResolutionTasklet.class,
    SeedExtractionJobConfiguration.class,
    SeedExtractionItemProcessor.class,
    SeedExtractionItemWriter.class,
    SeedCorpusComparisonJobConfiguration.class,
    SeedCorpusComparisonTasklet.class,
    EmbeddingModelJobConfiguration.class,
    EmbeddingScoringTasklet.class,
    RelevanceScoringJobConfiguration.class,
    RelevanceScoringTasklet.class,
    RelevanceFloorJobConfiguration.class,
    RelevanceFloorTasklet.class,
    RelevanceFloor.class,
    ClusteringJobConfiguration.class,
    ClusteringTasklet.class,
    RelevanceReportJobConfiguration.class,
    RelevanceReportTasklet.class,
    ArrangementJobConfiguration.class,
    ArrangementTasklet.class,
    io.algernon.vespera.extraction.DocumentTitles.class,
    ArrangementRun.class,
    ArrangementGate.class,
    Clusters.class,
    SynthesisDocs.class,
    ClusterSynthesis.class,
    LeadingChunks.class,
    GenerationScriptedBeans.class,
    RelevanceDistribution.class,
    EmbeddingModelGate.class,
    SeedMeasurementRun.class,
    ScoringRun.class,
    SeedGate.class,
    UsableSeedGate.class,
    ChunkEmbedderBeans.class,
    RelevanceScoringBeans.class,
    ClusteringBeans.class,
    DocumentClusters.class,
    EmbeddingScriptedBeans.class,
    RedundancySignatures.class,
    RedundancyResolution.class,
    BoilerplateShingles.class,
    DocumentFrequency.class,
    ConfidenceDistribution.class,
    SeedCorpusComparison.class,
    UnusableSeeds.class,
    Shingler.class,
    HybridChunkerBeans.class,
    SeedScriptedExtractionBeans.class,
    ExtractionMetrics.class,
    LanguageDetection.class,
    ContentIdentity.class,
    DetectedFormats.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
    ImplementationVersions.class,
    ProfileStore.class,
    NextAction.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Label.class,
    LabelIngestion.class,
    RelevanceLabels.class,
    VesperaCli.class
})
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("179")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
@Link(name = "ADR-099", url = Adr.AN_UPSTREAM_IS_LOOKED_UP_AND_TWO_CANDIDATES_STOP_THE_RUN, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
class GenerationInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. Named for
     * which model it is because two different ones matter in this class, and the one under test is
     * the other. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** An approval of the right shape that is nobody's arrangement: twelve hexadecimal characters. */
    private static final String NAMES_NOTHING = "0123456789ab";

    /** What one approval is worth: one record of the work, and not a second over the same approval. */
    private static final int ONE_RECORD = 1;

    /** What one group of documents is worth: one piece of writing, however many documents are in it. */
    private static final int ONE_PIECE_OF_WRITING = 1;

    /** How many documents the two-document corpus puts in that one group. */
    private static final int TWO_DOCUMENTS = 2;

    /**
     * What stage 6a calls that one group: the title every document this fixture converts carries, which
     * is what the naming rule derives a group's name from.
     */
    private static final String THE_GROUPS_NAME = SeedScriptedExtractionBeans.STUBBED_TITLE;

    /** A heading scripted for that group alone, so a record holding any other answer is visible. */
    private static final String ITS_OWN_TITLE = "What The Two Stubbed Documents Have In Common";

    /** And the writing scripted with it. */
    private static final String ITS_OWN_PROSE = "Both of them [1] say the same thing twice.";

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

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("With the groups approved, the next invocation opens on exactly the groups that were read")
    void opensOnTheArrangementThatWasApproved(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));

        cli.run("run", root.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the step recorded its work once and no more: an approval is spent on the groups it"
                        + " named, and a second record against the same approval would be a second claim"
                        + " on work already accounted for",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RECORD));
        claim(
                "and what it reads is the very arrangement the approval named, rather than whichever was"
                        + " arranged most recently -- an approval is of a particular shape of the archive,"
                        + " and writing over a later one would spend an approval nobody gave",
                () -> assertThat(upstreamOf(generationRuns(root).getFirst()))
                        .containsExactly(theApprovedArrangement(root).value()));
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("With nothing approved, the invocation succeeds and nothing is opened")
    void opensNothingWhenNothingIsApproved(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        approve(null);

        cli.run("run", root.toString());

        claim(
                "the invocation succeeded rather than failing: a person who has not looked at the"
                        + " arrangement yet has done nothing wrong, and an invocation ending in red would"
                        + " be telling them they had",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and nothing at all was recorded -- not an empty record, none: what is written down is"
                        + " what was actually done, and nothing was",
                () -> assertThat(generationRuns(root)).isEmpty());
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("An approval matching nothing is treated exactly as no approval at all")
    void opensNothingWhenTheApprovalMatchesNothing(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        approve(NAMES_NOTHING);

        cli.run("run", root.toString());

        claim(
                "a mistyped name leaves the archive exactly where a blank one does: the one outcome this"
                        + " must never have is quietly behaving like an approval, because a person who"
                        + " mistyped believes they approved something",
                () -> assertThat(generationRuns(root)).isEmpty());
        claim(
                "and the invocation still succeeded, because a name matching nothing is something to"
                        + " correct and run again, not a broken tool",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Story("An approval that names two things stops rather than guessing")
    @DisplayName("An approval matching two sets of groups stops the invocation instead of choosing one")
    void stopsWhenTheApprovalMatchesTwo(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        RunId arranged = theLatestArrangement(root);
        anotherArrangementSharingThePrefixOf(arranged);
        approve(ArrangementGate.shortNameOf(arranged));

        cli.run("run", root.toString());

        claim(
                "the invocation stopped rather than picking one of them: writing over the wrong one would"
                        + " be writing over something nobody read, and doing it without saying so",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and it stopped before recording anything, so there is no half-finished record of work"
                        + " nobody authorised",
                () -> assertThat(generationRuns(root)).isEmpty());
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("Opening on the approved groups records no judgement against any document")
    void recordsNoJudgementAgainstAnyDocument(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));

        cli.run("run", root.toString());

        claim(
                "the step ran at all over the approved groups, which is what the claim below is about --"
                        + " asserted first and separately, so that a step which never ran fails saying so"
                        + " rather than failing while reaching for something that is not there",
                () -> assertThat(generationRuns(root)).isNotEmpty());
        claim(
                "no judgement was recorded: every judgement this system records exists to take a document"
                        + " out of what gets published, and writing connecting text over what survived"
                        + " takes nothing out of anything",
                () -> assertThat(verdictsAgainstTheGeneratedWork(root)).isZero());
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("Invoking a third time with the approval still standing adds nothing and breaks nothing")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void aThirdInvocationUnderAStandingApprovalAddsNothing(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        cli.run("run", root.toString());

        cli.run("run", root.toString());

        claim(
                "the third invocation reports success: nothing about the archive, the values a person set"
                        + " or the tool had changed since the second, so there was nothing for it to do --"
                        + " and an invocation that ends in red for having nothing to do is telling a person"
                        + " something is wrong when nothing is",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the work is still recorded exactly " + ONE_RECORD + " time: the third invocation"
                        + " works out the same name for this work as the second did, finds that name already"
                        + " written down, and carries on under it -- writing it a second time is the one"
                        + " thing the record of work will not accept",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RECORD));
    }

    @Test
    @Issue("180")
    @Story("Every approved group of documents is written over, once")
    @DisplayName("Each approved group comes out with one piece of writing over the whole group")
    void writesOnePieceOverEachApprovedGroup(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));

        cli.run("run", root.toString());

        claim(
                "the invocation reports success, which the claims below are about",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "every group the person approved has writing over it, and one piece of writing for the"
                        + " whole group rather than one per document: the two documents here are one group,"
                        + " and a piece of writing about each of them separately would be the pile this"
                        + " system exists to replace",
                () -> assertThat(generatedDocs(root)).hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and what is kept is what came back -- the heading, the text with its markers untouched,"
                        + " and the number of documents it was written from, both " + TWO_DOCUMENTS + " of"
                        + " them, which is what lets the finished page say what it was written over",
                () -> assertThat(generatedDocs(root)).singleElement().satisfies(doc -> {
                    assertThat(doc.doc().title()).isEqualTo(GenerationScriptedBeans.GENERATED_TITLE);
                    assertThat(doc.doc().prose()).isEqualTo(GenerationScriptedBeans.GENERATED_PROSE);
                    assertThat(doc.doc().documentsSent()).isEqualTo(TWO_DOCUMENTS);
                }));
    }

    @Test
    @Issue("180")
    @Story("Every approved group of documents is written over, once")
    @DisplayName("What was written over a particular group is what is kept against that group")
    void keepsWhatWasWrittenAgainstTheGroupItWasAbout(@TempDir Path root, @TempDir Path seeds) throws IOException {
        GenerationScriptedBeans.answerFor(THE_GROUPS_NAME, ITS_OWN_TITLE, ITS_OWN_PROSE);
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));

        cli.run("run", root.toString());

        claim(
                "the writing kept against this group is the writing that was produced for this group, not"
                        + " whatever was produced last: every group gets its own call, and a record that"
                        + " could hold another group's answer would be a page about the wrong documents",
                () -> assertThat(generatedDocs(root)).singleElement().satisfies(doc -> {
                    assertThat(doc.doc().title()).isEqualTo(ITS_OWN_TITLE);
                    assertThat(doc.doc().prose()).isEqualTo(ITS_OWN_PROSE);
                }));
    }

    @Test
    @Issue("180")
    @Story("Work that was not finished is not recorded as finished")
    @DisplayName("A group nothing could be sent for is not recorded as done")
    void leavesTheStepOpenWhenAGroupCouldNotBeWrittenOver(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        theArchiveNoLongerHandsOverItsDocuments(root);

        cli.run("run", root.toString());

        claim(
                "nothing was written over the group, because there was nothing to write from",
                () -> assertThat(generatedDocs(root)).isEmpty());
        claim(
                "and the work is not recorded as done: a group that was never written over is not a"
                        + " finished job, and it is exactly that record which every later invocation reads"
                        + " to decide whether to walk past this step -- so marking it done here would leave"
                        + " a hole in the finished work that nothing anywhere reports and nothing retries",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
        claim(
                "the invocation still reports success: an archive that moved underneath a run is something"
                        + " to look at and run again, not a broken tool",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    /**
     * Takes both corpus documents away, so nothing in the group can be opened when the call is built.
     *
     * <p>An archive is a live filesystem and this is the ordinary version of that: a document moved,
     * renamed or locked between being walked and being written about. Every earlier step has already
     * recorded its work, so this reaches the run at exactly the point the group is gathered.
     */
    private void theArchiveNoLongerHandsOverItsDocuments(Path root) throws IOException {
        Files.delete(root.resolve("corpus.txt"));
        Files.delete(root.resolve("another-corpus-document.txt"));
    }

    /** Whether this step's own work is recorded as complete under the run it wrote. */
    private boolean theWorkIsRecordedAsFinished(Path root) {
        return generationRuns(root).stream()
                .anyMatch(run -> jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                                Integer.class,
                                run,
                                GenerationRun.STAGE)
                        > 0);
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /** The same corpus with a second document in it, so a group holds more than one. */
    private void aCorpusOfTwoDocuments(Path root, Path seeds) throws IOException {
        aCorpus(root, seeds);
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
    }

    /** Everything stage 6b wrote over the groups of {@code root}, under whichever run it wrote them. */
    private List<RecordedSynthesisDoc> generatedDocs(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> synthesisDocs.forRun(run).stream())
                .toList();
    }

    /** One corpus document and one exemplar, with every gate before this one open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        Profile loaded = profileStore.load();
        profileStore.save(ProfileFixture.profileFrom(loaded)
                .arrangementApproved(approval, approval == null ? null : "read by this test")
                .build());
    }

    /** The arrangement the most recent invocation over {@code root} recorded — the one its page names. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                Walk.canonicalRoot(root).toString()));
    }

    /** The arrangement of {@code root} the profile's approval names, whichever invocation recorded it. */
    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }

    /**
     * A second arrangement under the same walk whose id opens with the first's twelve characters,
     * written straight into the table because a content-derived id cannot be steered into a collision.
     * What is under test is what the invocation does when it meets one, not how likely that is.
     */
    private void anotherArrangementSharingThePrefixOf(RunId first) {
        String colliding = ArrangementGate.shortNameOf(first)
                + "f".repeat(first.value().length() - ArrangementGate.APPROVAL_LENGTH);
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " SELECT ?, stage, 'v-collision', '{}', walk_id FROM run WHERE id = ?",
                colliding,
                first.value());
    }

    /**
     * Every record of this step having run over {@code root}, oldest first.
     *
     * <p>Scoped to the walk of this corpus rather than counted across the table. One working directory
     * serves the whole class and the database outlives each method, so an unscoped count would be a
     * claim about every corpus any method in this class ever walked — and "the step recorded its work
     * once" would quietly become a statement about test execution order.
     */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                GenerationRun.STAGE,
                Walk.canonicalRoot(root).toString());
    }

    private List<String> upstreamOf(String runId) {
        return jdbcTemplate.queryForList(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, runId);
    }

    /**
     * Judgements recorded against anything this step wrote, counted without reaching for a particular
     * record — so a step that never ran answers zero rather than raising.
     */
    private int verdictsAgainstTheGeneratedWork(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE run_id IN"
                        + " (SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ?)",
                Integer.class,
                GenerationRun.STAGE,
                Walk.canonicalRoot(root).toString());
    }
}
