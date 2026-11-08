package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 6b end to end (ADR-107, ADR-108, ADR-110, ADR-114, #179, #180): the decision the step makes
 * before anything could be written, and then the writing itself.
 *
 * <p>Most of what is under test here is the gate: whether the operator has approved the arrangement
 * that would be written over, and what is recorded when they have. The last two tests are what
 * happens past it — one piece of writing per cluster, kept against the cluster it was about.
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
@Issue("179")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
@Link(name = "ADR-099", url = Adr.AN_UPSTREAM_IS_LOOKED_UP_AND_TWO_CANDIDATES_STOP_THE_RUN, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
class GenerationInvocationTest {

    /** What one approval is worth: one record of the work, and not a second over the same approval. */
    private static final int ONE_RECORD = 1;

    /** What one cluster of documents is worth: one piece of writing, however many documents are in it. */
    private static final int ONE_PIECE_OF_WRITING = 1;

    /** A window wider than the one the code ships with, which is the change an operator makes here. */
    private static final String A_WIDER_WINDOW = "16384";

    /** What two different answers to how much may be read are worth: two records of the work. */
    private static final int TWO_RECORDS = 2;

    /** The name given to the cluster nothing in this run can be sent for, so a claim can name it plainly. */
    private static final String A_CLUSTER_WITH_NOTHING_TO_SEND = "A group whose documents cannot be opened";

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
        approve(ArrangementGate.shortNameOf(new RunsOver(jdbcTemplate, root).latest("arrangement")));

        cli.run("run", root.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the step recorded its work once and no more: an approval is spent on the groups it"
                        + " named, and a second record against the same approval would be a second claim"
                        + " on work already accounted for",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RECORD));
        claim(
                "and what it reads is the very arrangement the approval named, the one run upstream of it"
                        + " -- an approval is of a particular shape of the archive, and writing over another"
                        + " would spend an approval nobody gave",
                () -> assertThat(upstreamOf(generationRuns(root).getFirst()))
                        .containsExactly(theApprovedArrangement(root).value()));
    }

    @Test
    @Issue("208")
    @Story("Reading more of each group is a different piece of work, not the same one again")
    @DisplayName("With the reading window changed, the archive is written over again under a record of its own")
    void writesAgainUnderItsOwnRecordWhenTheReadingWindowChanges(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(new RunsOver(jdbcTemplate, root).latest("arrangement")));
        cli.run("run", root.toString());

        setTheReadingWindowTo(A_WIDER_WINDOW);
        cli.run("run", root.toString());

        claim(
                "the invocation after the change reports success, which the claims below are about",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the work is recorded " + TWO_RECORDS + " times rather than once, the second under a name of"
                        + " its own: how much of a group one call may read decides how much of it the"
                        + " writing rests on, so the same archive read two ways is two pieces of work -- and"
                        + " if the number the archive's owner changed did not reach the name this work is"
                        + " recorded under, the invocation would find the earlier record, decide the work"
                        + " was done and write nothing, leaving them with writing made under a window they"
                        + " had stopped using",
                () -> assertThat(generationRuns(root)).hasSize(TWO_RECORDS));

        claim(
                "and the second record has writing of its own beneath it, one piece over the group, rather"
                        + " than pointing back at what the first read: a wider window is a different reading"
                        + " of the same documents, and what a reader opens has to be the reading that was"
                        + " asked for",
                () -> assertThat(WholeRun.synthesisDocs(jdbcTemplate,new RunId(generationRuns(root).getLast())))
                        .hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and the first record keeps the writing it produced, so the earlier reading is still there"
                        + " to be compared against rather than having been written over",
                () -> assertThat(WholeRun.synthesisDocs(jdbcTemplate,new RunId(generationRuns(root).getFirst())))
                        .hasSize(ONE_PIECE_OF_WRITING));
    }

    /**
     * Splits the approved arrangement's one cluster into two of one document each, and leaves the
     * second holding a document nothing of can be sent, so the invocation writes over one cluster and
     * leaves the other with nothing (ADR-121).
     *
     * <p>The two clusters are written straight into the tables, because every document this fixture
     * converts carries the same text and embeds alike, so nothing put in the corpus arranges itself into
     * two clusters. <b>They are rows an arrangement could have recorded</b>: each cluster holds exactly
     * the one document its count says it does, in the membership the scoring run records, so the
     * arrangement stays total and its page can be drawn from it (ADR-112, ADR-154 §2).
     *
     * <p>What leaves the second cluster with nothing to send is that its one document's conversion and
     * chunks are taken out of the caches between the invocations ({@link ConversionOffTheRecordFixture}).
     * Its file is untouched, so the walk still sees the same archive and the approval still stands, and
     * stage 6b finds no chunk on record for it. Until ADR-206 the document was rewritten in place instead,
     * which left 6b nothing cached under the text it then read; 6b no longer reads the file, so that
     * leaves a cluster nothing short.
     *
     * @return what was on record for that document, so a test can put it back
     */
    private ConversionOffTheRecordFixture.Held aClusterNothingCanBeSentFor(RunId arrangement, Path root)
            throws IOException {
        return aClusterNothingCanBeSentFor(arrangement, root, A_CLUSTER_WITH_NOTHING_TO_SEND);
    }

    /** The same, under a name of the caller's choosing, so two tests can tell their clusters apart. */
    private ConversionOffTheRecordFixture.Held aClusterNothingCanBeSentFor(
            RunId arrangement, Path root, String label) throws IOException {
        jdbcTemplate.update(
                "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                        + " document_count, partition_order, cluster_order)"
                        + " SELECT run_id, winning_seed_occurrence_id, cluster_ordinal + 1, ?,"
                        + " 1, partition_order, cluster_order + 1"
                        + " FROM cluster WHERE run_id = ?",
                label,
                arrangement.value());
        jdbcTemplate.update(
                "UPDATE cluster SET document_count = 1 WHERE run_id = ? AND label <> ?", arrangement.value(), label);
        long moved = theLastDocumentOf(arrangement);
        jdbcTemplate.update(
                "UPDATE document_cluster SET cluster_ordinal = cluster_ordinal + 1 WHERE occurrence_id = ?"
                        + " AND run_id = (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                moved,
                arrangement.value());
        return ConversionOffTheRecordFixture.takenOffTheRecord(jdbcTemplate, root.resolve(pathOf(moved)));
    }

    /**
     * The document of the arrangement's membership with the highest occurrence id -- the one moved into
     * the second cluster. Which document moves does not matter, so the query names one by taking the last.
     */
    private long theLastDocumentOf(RunId arrangement) {
        return jdbcTemplate.queryForObject(
                "SELECT occurrence_id FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)"
                        + " ORDER BY occurrence_id DESC LIMIT 1",
                Long.class,
                arrangement.value());
    }

    private String pathOf(long occurrence) {
        return jdbcTemplate.queryForObject(
                "SELECT path FROM file_occurrence WHERE id = ?", String.class, occurrence);
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /** Writes the reading window into the profile, leaving every other key as it was. */
    private void setTheReadingWindowTo(String window) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(window, "set by this test")
                .build());
    }

    /** The same corpus with a second document in it, so a cluster holds more than one. */
    private void aCorpusOfTwoDocuments(Path root, Path seeds) throws IOException {
        aCorpus(root, seeds);
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
    }

    /** One corpus document and one exemplar, with every gate before this one open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        Profile loaded = profileStore.load();
        profileStore.save(ProfileFixture.profileFrom(loaded)
                .arrangementApproved(approval, approval == null ? null : "read by this test")
                .build());
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
     * claim about every corpus any method in this class ever walked — and "the step recorded its work
     * once" would quietly become a statement about test execution order.
     */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }

    private List<String> upstreamOf(String runId) {
        return jdbcTemplate.queryForList(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, runId);
    }
}
