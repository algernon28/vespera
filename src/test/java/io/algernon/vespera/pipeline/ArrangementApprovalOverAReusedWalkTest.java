package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What an arrangement approval opens once the arrangement it named is no longer the one the documents
 * are in (ADR-154, amending ADR-107; #296).
 *
 * <p>ADR-107 made the approval name one arrangement, so that a re-arrangement closes the gate again.
 * Since ADR-115 an unchanged archive keeps its walk and run rows are never deleted, so the arrangement
 * an old approval named is still an arrangement of that walk after a changed relevance floor has made a
 * new one (ADR-117). Matched against every arrangement of the walk, the old approval kept opening the
 * gate on the old arrangement while the page and the closing line spoke of the new one. ADR-154 matches
 * the approval against one arrangement only: the one this invocation made or continued.
 *
 * <p>Every test here changes the relevance floor between invocations over one unchanged archive,
 * which is the change ADR-117 made re-arrange the documents.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Arranging the documents")
@Issue("296")
@Link(name = "ADR-154", url = Adr.A_STAGE_READS_THE_UPSTREAM_RUN_THIS_INVOCATION_ARRIVED_AT, type = "adr")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
@Link(name = "ADR-117", url = Adr.THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY, type = "adr")
class ArrangementApprovalOverAReusedWalkTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** A floor of 1.0 opens stage 4's gate, so every stage up to the arrangement runs. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for, so the embedding gate opens. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The model the documents are written up with once an arrangement is approved. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    /** A relevance floor below every score this fixture's embedder produces, so it removes nothing. */
    private static final String THE_FIRST_FLOOR = "0.0";

    /**
     * A second relevance floor, also below every score here. It removes nothing either, and that is
     * not what it is for: a different floor names different scoring work, and so a different
     * arrangement, which is the change an approval has to notice.
     */
    private static final String THE_SECOND_FLOOR = "0.1";

    /** What every closing line that asks for something says before naming it. */
    private static final String THE_ACTION = "Next:";

    /** What the closing line says once nothing is left for the operator to do. */
    private static final String NOTHING_LEFT = "Nothing is left to set";

    /** What the closing line says where it names a deliverable already written. */
    private static final String A_DELIVERABLE_NAMED = "The deliverable is at";

    /** The profile key the arrangement is approved under. */
    private static final String THE_APPROVAL_KEY = "arrangementApproved";

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

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void anEmptyProfile() {
        profileStore.save(ProfileFixture.profile().build());
    }

    @BeforeEach
    void captureOperatorLines() {
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("An approval is of one arrangement, and does not carry over to the next")
    @DisplayName("An approval of the earlier arrangement does not write up the documents after they are re-arranged")
    void anApprovalOfAnEarlierArrangementDoesNotOpenGenerationOverIt(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        everyGateOpenAtTheRelevanceFloor(seeds, THE_FIRST_FLOOR);
        invoke(root);
        String approved = theOnlyArrangementOf(root);

        approve(approved);
        setTheRelevanceFloorTo(THE_SECOND_FLOOR);
        invoke(root);
        String rearranged = latestArrangementOf(root);

        claim(
                "the invocation reports success: a gate that is shut ends an invocation successfully",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "changing the relevance floor re-arranged the documents, so there are two arrangements"
                        + " of this one unchanged archive, the approved one and the new one",
                () -> assertThat(arrangementsOf(root)).containsExactly(approved, rearranged));
        claim(
                "and nothing was written up: the approval names the earlier arrangement, which is not the"
                        + " one the documents are in now, so the write-up the approval would have opened"
                        + " never started",
                () -> assertThat(generationRunsOf(root)).isEmpty());
        claim(
                "the closing line asks for the new arrangement to be approved, by the name the operator"
                        + " would copy into " + THE_APPROVAL_KEY,
                () -> assertThat(theClosingLine())
                        .contains(THE_ACTION)
                        .contains(quoted(shortNameOf(rearranged)))
                        .contains(THE_APPROVAL_KEY));
        claim(
                "and does not tell the operator that nothing is left to set, which is what it said while"
                        + " the earlier approval still counted",
                () -> assertThat(theClosingLine()).doesNotContain(NOTHING_LEFT));
    }

    @Test
    @Story("An approval is of one arrangement, and does not carry over to the next")
    @DisplayName("After the earlier arrangement was written up, re-arranging asks for the new one to be approved")
    void aWrittenUpArrangementDoesNotStandInForTheNewOne(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        everyGateOpenAtTheRelevanceFloor(seeds, THE_FIRST_FLOOR);
        invoke(root);
        String approved = theOnlyArrangementOf(root);
        approve(approved);
        invoke(root);
        claim(
                "the approved arrangement was written up once, so the operator has a deliverable from it",
                () -> assertThat(generationRunsOf(root)).hasSize(1));

        setTheRelevanceFloorTo(THE_SECOND_FLOOR);
        invoke(root);
        String rearranged = latestArrangementOf(root);

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the documents were re-arranged under the new floor",
                () -> assertThat(rearranged).isNotEqualTo(approved));
        claim(
                "nothing was written up a second time: there is still exactly the one write-up of the"
                        + " earlier arrangement",
                () -> assertThat(generationRunsOf(root)).hasSize(1));
        claim(
                "the closing line asks for the new arrangement to be approved, by name",
                () -> assertThat(theClosingLine())
                        .contains(THE_ACTION)
                        .contains(quoted(shortNameOf(rearranged)))
                        .contains(THE_APPROVAL_KEY));
        claim(
                "rather than reporting that nothing is left to set and pointing at the earlier"
                        + " arrangement's deliverable as though it were this one's",
                () -> assertThat(theClosingLine()).doesNotContain(NOTHING_LEFT).doesNotContain(A_DELIVERABLE_NAMED));
    }

    @Test
    @Story("Putting a value back chooses the work done under it")
    @DisplayName("Putting the relevance floor back offers the earlier arrangement again, and approving it writes it up")
    void puttingTheFloorBackOffersTheEarlierArrangementAgain(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        everyGateOpenAtTheRelevanceFloor(seeds, THE_FIRST_FLOOR);
        invoke(root);
        String first = theOnlyArrangementOf(root);
        setTheRelevanceFloorTo(THE_SECOND_FLOOR);
        invoke(root);
        String second = latestArrangementOf(root);

        setTheRelevanceFloorTo(THE_FIRST_FLOOR);
        invoke(root);

        claim(
                "putting the floor back arranged nothing new: the documents are in the first arrangement"
                        + " again, picked up rather than made a third time",
                () -> assertThat(arrangementsOf(root)).containsExactly(first, second));
        claim(
                "the page the operator is told to read shows the arrangement the documents are in now,"
                        + " the first, and not the one written most recently",
                () -> assertThat(theArrangementPage())
                        .contains(shortNameOf(first))
                        .doesNotContain(shortNameOf(second)));
        claim(
                "and the closing line asks for that same arrangement to be approved, so the name the"
                        + " operator copies is the name on the page they read",
                () -> assertThat(theClosingLine())
                        .contains(quoted(shortNameOf(first)))
                        .doesNotContain(shortNameOf(second)));

        approve(first);
        invoke(root);

        claim(
                "approving it by that name writes it up: the invocation succeeds",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the one write-up there is was made over the first arrangement",
                () -> assertThat(generationRunsOf(root))
                        .singleElement()
                        .satisfies(generation -> assertThat(upstreamOf(generation)).containsExactly(first)));
    }

    private void invoke(Path root) {
        logged.list.clear();
        cli.run("run", root.toString());
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        Files.writeString(root.resolve("corpus-1.txt"), "another corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** Every value the profile asks for before an arrangement, the relevance floor at {@code floor}. */
    private void everyGateOpenAtTheRelevanceFloor(Path seeds, String floor) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding gate is open")
                .relevanceScoreFloor(floor, "set by this test")
                .build());
    }

    private void setTheRelevanceFloorTo(String floor) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor(floor, "changed by this test")
                .build());
    }

    /** Approves {@code arrangement} the way an operator does: its short name, and a model to write with. */
    private void approve(String arrangement) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(shortNameOf(arrangement), "read arrangement.html, set by this test")
                .generationModel(GENERATION_MODEL, "set by this test")
                .build());
    }

    private static String shortNameOf(String arrangement) {
        return ArrangementGate.shortNameOf(new RunId(arrangement));
    }

    private static String quoted(String value) {
        return '"' + value + '"';
    }

    /** The last line the invocation wrote for an operator, which is its closing line. */
    private String theClosingLine() {
        List<String> lines = logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        return lines.getLast();
    }

    private String theArrangementPage() throws IOException {
        return Files.readString(
                workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME), StandardCharsets.UTF_8);
    }

    private String theOnlyArrangementOf(Path root) {
        List<String> arrangements = arrangementsOf(root);
        claim(
                "the first invocation arranged the documents once",
                () -> assertThat(arrangements).hasSize(1));
        return arrangements.getFirst();
    }

    private String latestArrangementOf(Path root) {
        return arrangementsOf(root).getLast();
    }

    private List<String> arrangementsOf(Path root) {
        return runsOf(root, "arrangement");
    }

    private List<String> generationRunsOf(Path root) {
        return runsOf(root, "generation");
    }

    /** Every run of {@code stage} recorded over this corpus, in the order written. */
    private List<String> runsOf(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = ? AND w.root = ? ORDER BY run.rowid",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }

    private List<String> upstreamOf(String run) {
        return jdbcTemplate.queryForList(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, run);
    }
}
