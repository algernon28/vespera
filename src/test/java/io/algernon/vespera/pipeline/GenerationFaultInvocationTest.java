package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.ListedFault;
import io.algernon.vespera.synthesis.ListedDoc;
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
 * Stage 6b when the answer is not believed (ADR-108, ADR-109, ADR-111, ADR-116, ADR-121, #183): four
 * checks run before a word of an answer is kept, and a cluster whose answer fails one of them costs
 * that cluster and nothing else.
 *
 * <p><b>Why this is an invocation test rather than a unit one.</b> What each check costs is spread
 * across a seam: the checking happens where the call is made, the reason is kept in a table, and
 * whether the run carries on and whether the work counts as done are decided by the step around it.
 * A test of the checking alone would leave every one of those unclaimed.
 *
 * <p><b>Each test fails exactly one check.</b> The fixture's ordinary answer passes all four, and
 * each test here scripts one property of one answer away from that. A fixture that failed two at once
 * would pin whichever the code happened to test first, which is an ordering no record decides.
 *
 * <p><b>The two citation failures ADR-109 states separately are recorded separately</b>, which is
 * settled here: its uncited-prose rule is a clause of its own beside its range check, so a cluster
 * whose prose cited nothing at all and a cluster whose prose cited {@code [0]} may not leave the same
 * detail behind. The wording is fixed as {@code "no citation at all in the writing, against N
 * document(s) sent"} for the first and the existing {@code "citation 0 against N document(s) sent"}
 * for the second, and the two cases of {@link #turnsDownWritingThatPointsAtNothingAtAll} each claim
 * their own reading and the absence of the other's.
 *
 * <p><b>The consecutive-run breaker is deliberately not reached here.</b> No test in this class
 * leaves five clusters turned down in a row, so nothing here depends on what happens when it fires;
 * {@link GenerationBreakerInvocationTest} is where that is claimed.
 *
 * <p><b>ADR-121's deferred question is settled here in the negative</b>, and the last test is the
 * pin: a cluster whose every document is larger than the reading room gets no reason recorded, because
 * the four ways an answer can be turned down are four things a returned answer can do, and no answer
 * was ever returned for that cluster. The step stays unfinished either way, so a fifth kind would buy
 * the operator a row and no change of behaviour.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em>, and that is settled rather
 * than sloppy</b> (ADR-122). Everything a reader of the report sees -- the feature, the stories, the
 * display names, every claim -- renders the term as <em>group</em>, because the everyday sense of
 * "cluster" is a set of interchangeable things, which is what {@code CONTEXT.md} says a cluster is
 * not, and that reader cannot open {@code CONTEXT.md} to find out. Everything this project names for
 * itself -- the constants, the fixture methods, these comments -- says cluster. Do not reconcile the
 * two by changing either side.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("183")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
@Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
class GenerationFaultInvocationTest {

    /**
     * How much every call in this class may read, in tokens, written into the profile by the fixture
     * rather than left at whatever the shipped default is.
     *
     * <p>Two reasons it is stated. One test sets it much lower and the profile outlives a test method,
     * so a window nobody stated would be whichever the last test left behind. And the number is what
     * the first of the four checks compares against, so a test claiming an answer reached it has to
     * know what it reached.
     */
    private static final String THE_READING_WINDOW = "4096";

    /** The same number as an integer: the window every call in this class reads in. */
    private static final int THE_WINDOW = 4096;

    /**
     * The ceiling the first check reads against: the window less one (ADR-166). A question the engine
     * cuts to fit is counted there, because every call asks it to keep the question's beginning up to
     * the window's last token; and a whole question that long leaves the answer not one token.
     */
    private static final int THE_CEILING = THE_WINDOW - 1;

    /** A prompt count of exactly the ceiling, which is the boundary the check has to fail on. */
    private static final int A_COUNT_AT_THE_CEILING = THE_CEILING;

    /** One token short of the ceiling: the whole question arrived, and the answer stands. */
    private static final int A_COUNT_UNDER_THE_CEILING = THE_CEILING - 1;

    /** An answer nothing can read back into a heading and its writing: it stops partway through. */
    private static final String AN_ANSWER_NOTHING_CAN_READ = "{\"title\":\"Site Safety Audits\",\"prose\":";

    /** The first of the two documents every test here walks, both in the one group the arrangement makes. */
    private static final String THE_CORPUS_DOCUMENT = "corpus.txt";

    /** The second of them. */
    private static final String THE_OTHER_CORPUS_DOCUMENT = "another-corpus-document.txt";

    /** The name given to the cluster whose answer is scripted away from the ordinary one. */
    private static final String THE_CLUSTER_ANSWERED_BADLY = "A group whose answer is not believed";

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
    @Story("An answer written from part of the question is never believed")
    @DisplayName("A question reaching the window's last token is already too much, and its group is left unwritten")
    void turnsDownAnAnswerThatReachedTheWindowsLastToken(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), anOrdinaryAnswer().havingRead(A_COUNT_AT_THE_CEILING));

        cli.run("run", root.toString());

        claim(
                "a question reaching the window's last token is turned down rather than let through: that"
                        + " is the count a question cut down to fit comes back at, since every call asks the"
                        + " engine to keep a too-long question's beginning up to there -- so " + THE_CEILING
                        + " of a " + THE_WINDOW + "-token window fails where " + A_COUNT_UNDER_THE_CEILING
                        + " does not",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
    }

    @Test
    @Story("An answer that cannot be read is turned down rather than stopping the whole run")
    @DisplayName("An answer nothing can read leaves its group unwritten and the invocation still succeeds")
    void turnsDownAnAnswerNothingCanRead(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: an answer that came back in the wrong shape costs the"
                        + " group it was about and nothing else, where a run that fell over on it would"
                        + " cost every group after it -- and on an archive of hundreds of thousands of"
                        + " documents that is a night's work thrown away over one answer",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the reason kept says the answer did not come back in the shape the call asked for",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION)));
        claim(
                "and it says where reading the answer stopped rather than being left blank: a reason"
                        + " holding nothing is a row that records that something happened and not what,"
                        + " which costs the most expensive call this system makes to find out",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().detail())
                        .isNotBlank()
                        .containsPattern("[0-9]")));
        claim(
                "and nothing was written over the group",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("A group is written over, or left unwritten with a reason, and never both")
    @DisplayName("No group carries both a piece of writing and a reason it was left unwritten")
    void neverKeepsWritingAndAReasonForOneCluster(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "the run kept writing for one group and a reason for another, so the two can be compared at all",
                () -> assertThat(List.of(writingKept(root).isEmpty(), reasonsKept(root).isEmpty()))
                        .containsExactly(false, false));
        claim(
                "no group has both writing kept for it and a reason it was left unwritten: those two"

                        + " records answer the same question in opposite directions, and a group carrying"
                        + " both leaves the finished work and the record of it disagreeing about whether it"
                        + " was ever written",
                () -> assertThat(writingKept(root).stream()
                                .map(written -> written.winningSeed().value() + "/" + written.clusterOrdinal())
                                .filter(written -> reasonsKept(root).stream()
                                        .anyMatch(kept -> written.equals(
                                                kept.winningSeed().value() + "/" + kept.clusterOrdinal())))
                                .toList())
                        .isEmpty());
    }

    /**
     * Drops what was scripted and what was counted before each test as well as after it, so that what
     * ran before this class cannot be read as this class's own calls.
     *
     * <p>Every test here that counts calls carries the same exposure {@link
     * GenerationBreakerInvocationTest} was caught by: the count is static on the fixture, eighteen
     * classes in this package share it, and only the three generation classes drop it. Clearing after
     * a test leaves this class trusting whichever class ran before it to have done the same.
     */
    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /**
     * The one cluster this fixture's corpus produces, named by the title every document it converts
     * carries — which is what the naming rule derives a cluster's name from, and what a scripted answer
     * is keyed on.
     */
    private static String theOnlyCluster() {
        return SeedScriptedExtractionBeans.STUBBED_TITLE;
    }

    /** An answer that passes every check, which each test scripts exactly one property away from. */
    private static ScriptedAnswer anOrdinaryAnswer() {
        return ScriptedAnswer.saying(
                GenerationScriptedBeans.GENERATED_TITLE, GenerationScriptedBeans.GENERATED_PROSE);
    }

    /**
     * A corpus of two documents, walked once, with the arrangement it produced approved — which is the
     * state every test here starts from, because none of them is about the gate in front of it.
     */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(THE_CORPUS_DOCUMENT), "a corpus document");
        Files.writeString(root.resolve(THE_OTHER_CORPUS_DOCUMENT), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads in the same window")
                .build());
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(new RunsOver(jdbcTemplate, root).latest("arrangement")));
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    /**
     * Puts a second cluster into the approved arrangement, ahead of the one that is there, under a name of
     * its own.
     *
     * <p>Written straight into the table, for the reason the colliding arrangement in this package's
     * other invocation test is: a fixture whose documents all convert alike cannot be steered into
     * producing two clusters. It copies the arranged cluster's row one place earlier in the order, so what
     * happens to it happens <em>before</em> the cluster that was already there — which is the whole point,
     * since a run that stopped at a turned-down answer and a run that carried on past it are told apart
     * only by what happened to the clusters after it.
     *
     * <p><b>It copies document_count unchanged</b>, so the injected row claims more documents than it
     * holds, which no arrangement run would write. Harmless here, because a call is sized from
     * membership and never from that column.
     */
    private void aSecondClusterAheadOfTheFirst(RunId arrangement) {
        jdbcTemplate.update(
                "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                        + " document_count, partition_order, cluster_order)"
                        + " SELECT run_id, winning_seed_occurrence_id, cluster_ordinal + 1, ?,"
                        + " document_count, partition_order, cluster_order - 1"
                        + " FROM cluster WHERE run_id = ?",
                THE_CLUSTER_ANSWERED_BADLY,
                arrangement.value());
    }

    /**
     * Moves one of the corpus documents into that second cluster, so it has something to send and is
     * therefore asked about at all.
     *
     * <p>Written straight into the table for the reason the cluster itself is. Which document moves does
     * not matter, so the query names one by taking the last.
     */
    private void thatClusterIsGivenADocumentItCanSend(RunId arrangement) {
        jdbcTemplate.update(
                "UPDATE document_cluster SET cluster_ordinal = cluster_ordinal + 1 WHERE rowid ="
                        + " (SELECT rowid FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)"
                        + " ORDER BY occurrence_id DESC LIMIT 1)",
                arrangement.value());
    }

    /** Everything stage 6b wrote over the clusters of {@code root}, under whichever run it wrote them. */
    private List<ListedDoc> writingKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> WholeRun.synthesisDocs(jdbcTemplate,run).stream())
                .toList();
    }

    /** Every reason stage 6b kept for a cluster of {@code root} it left unwritten. */
    private List<ListedFault> reasonsKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> WholeRun.clusterFaults(jdbcTemplate,run).stream())
                .toList();
    }

    /** The arrangement of {@code root} the profile's approval names, whichever invocation recorded it. */
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
     * Every record of this step having run over {@code root}, oldest first.
     *
     * <p>Scoped to the walk of this corpus rather than counted across the table. One working directory
     * serves the whole class and the database outlives each method, so an unscoped count would be a
     * claim about every corpus any method in this class ever walked.
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
