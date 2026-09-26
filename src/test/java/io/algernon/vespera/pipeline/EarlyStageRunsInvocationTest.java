package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.Ledger;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What extraction and the content census record about themselves, and which archive each reads
 * (ADR-048, ADR-012, ADR-154).
 *
 * <p>Two things are pinned for each. First, that what the stage consumed is written down: for
 * extraction, the engine it was configured against and the tier-2 confidence floor. A judgement made
 * under one engine is not a judgement under another (ADR-012), and a floor retuned later has to be told
 * apart from the old one. Second, that what it read is the run the stage before it did in this
 * invocation, over the archive it was handed and no other.
 *
 * <p><b>These claims used to be made against the two classes that minted these runs</b>, built by hand
 * in {@code ExtractionRunTest} and {@code ContentCensusRunTest}. ADR-157 folds both classes, with every
 * other stage's mint, into one holder, so the claims now read the rows a whole invocation writes. That
 * is also the stronger form: the question is what the job records, whichever class does the recording.
 *
 * <p><b>The confidence floor is written into {@code profile.yaml} before the application starts.</b> It
 * is read once, while the tool wires itself up (ADR-120), so it cannot be changed from inside a test
 * method. Setting it where an operator sets it, before the context exists, is what makes the claim
 * about the floor a claim about the path an operator's value takes. Census adds keys the file lacks and
 * never touches one already there (ADR-062), so the value survives every invocation below.
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("303")
@Link(name = "ADR-048", url = Adr.WALK_AND_RUN_IDENTITY, type = "adr")
@Link(name = "ADR-012", url = Adr.EXTRACTION_ENGINE_IS_CONFIGURABLE, type = "adr")
@Link(name = "ADR-154", url = Adr.A_STAGE_READS_THE_UPSTREAM_RUN_THIS_INVOCATION_ARRIVED_AT, type = "adr")
class EarlyStageRunsInvocationTest {

    /**
     * The tier-2 confidence floor this class runs under (#48): a value an operator could have typed,
     * chosen so it is not the unset the golden text is pinned with.
     */
    private static final String CONFIDENCE_FLOOR = "0.5";

    /** Documents written under the first archive, chosen to differ from the second archive's count. */
    private static final int FILES_IN_THE_FIRST_ARCHIVE = 2;

    /** Documents written under the second archive. */
    private static final int FILES_IN_THE_SECOND_ARCHIVE = 3;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        new ProfileStore(workingDirectory).save(ProfileFixture.profile()
                .degenerateOutputConfidenceFloor(CONFIDENCE_FLOOR, "set by this test before the tool started")
                .build());
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private Ledger ledger;

    @Autowired
    private ExtractorIdentity extractorIdentity;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What extraction records about itself")
    @DisplayName("Extraction writes down which engine it was configured against")
    void extractionRecordsTheEngineItWasConfiguredAgainst(@TempDir Path root) throws IOException {
        anArchiveOf(root, FILES_IN_THE_FIRST_ARCHIVE);

        cli.run("run", root.toString());

        claim(
                "what extraction consumed names the engine it ran against, so a judgement made under one"
                        + " engine is never mistaken for one made under another",
                () -> assertThat(configConsumedBy(theRunOf("extraction", root))).contains(extractorIdentity.value()));
    }

    @Test
    @Story("What extraction records about itself")
    @DisplayName("Extraction writes down the tier-2 confidence floor it was configured against (#48)")
    void extractionRecordsTheConfidenceFloorItWasConfiguredAgainst(@TempDir Path root) throws IOException {
        anArchiveOf(root, FILES_IN_THE_FIRST_ARCHIVE);

        cli.run("run", root.toString());

        claim(
                "what extraction consumed also names the tier-2 threshold of " + CONFIDENCE_FLOOR + " it ran"
                        + " against, so a run's own row answers what shaped its judgements without re-reading"
                        + " the profile",
                () -> assertThat(configConsumedBy(theRunOf("extraction", root)))
                        .contains("\"degenerateOutputConfidenceFloor\":" + CONFIDENCE_FLOOR));
    }

    @Test
    @Story("What extraction records about itself")
    @DisplayName("Extraction records that it read what the previous stage left, and names that same work")
    void extractionNamesThePreviousStagesWorkAsWhatItRead(@TempDir Path root) throws IOException {
        anArchiveOf(root, FILES_IN_THE_FIRST_ARCHIVE);

        cli.run("run", root.toString());

        claim(
                "extraction records as what it read exactly one piece of work, the byte-level reduction"
                        + " this invocation did over this archive, so what a judgement was derived from is"
                        + " answerable later without re-deriving it",
                () -> assertThat(ledger.upstreamRuns(theRunOf("extraction", root)))
                        .containsExactly(theRunOf("byte-level-reduction", root)));
    }

    @Test
    @Story("What stage 3 records about itself")
    @DisplayName("Stage 3's run names the extraction run this invocation did as its upstream")
    void theContentCensusNamesTheExtractionRunAsItsUpstream(@TempDir Path root) throws IOException {
        anArchiveOf(root, FILES_IN_THE_FIRST_ARCHIVE);

        cli.run("run", root.toString());

        claim(
                "stage 3 records as what it read the extraction this invocation did over this archive, so"
                        + " a later question about what its document frequency was measured over is"
                        + " answerable from the run row itself",
                () -> assertThat(ledger.upstreamRuns(theRunOf("content-census", root)))
                        .containsExactly(theRunOf("extraction", root)));
    }

    @Test
    @Story("Whichever archive it is given")
    @DisplayName("Extraction and stage 3 read the archive they were handed, not a particular one")
    void eachReadsTheArchiveItWasHanded(@TempDir Path firstArchive, @TempDir Path secondArchive)
            throws IOException {
        anArchiveOf(firstArchive, FILES_IN_THE_FIRST_ARCHIVE);
        anArchiveOf(secondArchive, FILES_IN_THE_SECOND_ARCHIVE);

        cli.run("run", firstArchive.toString());
        cli.run("run", secondArchive.toString());

        claim(
                "handed the first archive, extraction measured exactly the " + FILES_IN_THE_FIRST_ARCHIVE
                        + " documents that archive holds",
                () -> assertThat(occurrencesMeasuredUnder(theRunOf("extraction", firstArchive)))
                        .containsExactlyInAnyOrderElementsOf(occurrencesOf(firstArchive)));
        claim(
                "handed the second, it measured exactly the " + FILES_IN_THE_SECOND_ARCHIVE + " that one"
                        + " holds -- so which archive gets examined is the one an operator named, never one"
                        + " the engine was built around",
                () -> assertThat(occurrencesMeasuredUnder(theRunOf("extraction", secondArchive)))
                        .containsExactlyInAnyOrderElementsOf(occurrencesOf(secondArchive)));
        claim(
                "and stage 3 over each archive names that archive's own extraction -- nothing in it"
                        + " hard-codes which archive was walked",
                () -> {
                    assertThat(ledger.upstreamRuns(theRunOf("content-census", firstArchive)))
                            .containsExactly(theRunOf("extraction", firstArchive));
                    assertThat(ledger.upstreamRuns(theRunOf("content-census", secondArchive)))
                            .containsExactly(theRunOf("extraction", secondArchive));
                });
    }

    /**
     * An archive of {@code files} documents, each with content of its own. Distinct contents, so the
     * byte-level reduction resolving identical documents to one representative (ADR-069) does not reduce
     * the count, and the archive's name in each, so no other test's archive shares a content hash.
     */
    private static void anArchiveOf(Path root, int files) throws IOException {
        for (int i = 0; i < files; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "the content of " + root.getFileName() + " document " + i);
        }
    }

    /** The one run of {@code stage} over this archive's walk. */
    private RunId theRunOf(String stage, Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString()));
    }

    private String configConsumedBy(RunId runId) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, runId.value());
    }

    /** The occurrences extraction measured under {@code runId}: one {@code extraction_metric} row each. */
    private List<Long> occurrencesMeasuredUnder(RunId runId) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM extraction_metric WHERE run_id = ?", Long.class, runId.value());
    }

    /** Every file occurrence the walk of {@code root} recorded. */
    private List<Long> occurrencesOf(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT f.id FROM file_occurrence f JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Long.class,
                Walk.canonicalRoot(root).toString());
    }
}
