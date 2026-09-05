package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 3's own run — content census, the document-frequency pass (ADR-038, ADR-074) — and what it
 * records about the extraction run it read.
 *
 * <p>The same shape {@link ExtractionRunTest} already pins for stage 2: what this class re-derives has
 * to be exact, since nothing hands the previous stage's run id across in-process. Here that previous
 * stage is extraction rather than byte-level reduction, and re-deriving its id in turn requires the
 * byte-level-reduction run's own, which is why {@link #walkedThroughExtraction} really runs both
 * earlier stages rather than inserting rows for them: the foreign key
 * {@code run_upstream.upstream_run_id} enforces that a run this class claims as upstream actually
 * exists, so a wrong derivation surfaces loudly rather than as a plausible-looking row nobody wrote.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Content census")
@Issue("58")
@Link(name = "ADR-048", url = Adr.WALK_AND_RUN_IDENTITY, type = "adr")
@Link(name = "ADR-038", url = Adr.SHINGLING_MOVES_TO_STAGE_3, type = "adr")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
class ContentCensusRunTest {

    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

    private static final int FILES_IN_THE_FIRST_ARCHIVE = 2;
    private static final int FILES_IN_THE_SECOND_ARCHIVE = 3;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("What stage 3 records about itself")
    @DisplayName("Stage 3's run names the extraction run as its upstream, correctly re-derived")
    void namesTheExtractionRunAsItsUpstream(@TempDir Path root) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtraction(ledger, versions, root, FILES_IN_THE_FIRST_ARCHIVE);
        RunId theExtractionRun = theOneRunOf(ExtractionRun.STAGE);

        ContentCensusRun contentCensusRun = new ContentCensusRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), root);

        claim(
                "stage 3 works out extraction's own run identity rather than being handed it, and what"
                        + " it works out is that run's own identity -- exact only if it matches",
                () -> assertThat(contentCensusRun.extractionRunId()).isEqualTo(theExtractionRun));
        claim(
                "and it records that run as what it read, so a later question about what stage 3's"
                        + " document frequency was measured over is answerable from the run row itself",
                () -> assertThat(ledger.upstreamRuns(contentCensusRun.runId())).containsExactly(theExtractionRun));
    }

    @Test
    @Story("Whichever archive extraction read")
    @DisplayName("Stage 3 reads the archive extraction read, not a particular one")
    void readsWhicheverArchiveExtractionRead(@TempDir Path firstArchive, @TempDir Path secondArchive)
            throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        RunId extractionOverTheFirst =
                walkedThroughExtraction(ledger, versions, firstArchive, FILES_IN_THE_FIRST_ARCHIVE);
        RunId extractionOverTheSecond =
                walkedThroughExtraction(ledger, versions, secondArchive, FILES_IN_THE_SECOND_ARCHIVE);

        ContentCensusRun overTheFirst = new ContentCensusRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), firstArchive);
        ContentCensusRun overTheSecond = new ContentCensusRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), secondArchive);

        claim(
                "handed the first archive, stage 3 re-derives the first archive's own extraction run --"
                        + " nothing here hard-codes which archive was walked",
                () -> assertThat(overTheFirst.extractionRunId()).isEqualTo(extractionOverTheFirst));
        claim(
                "handed the second, it re-derives the second archive's own extraction run instead",
                () -> assertThat(overTheSecond.extractionRunId()).isEqualTo(extractionOverTheSecond));
    }

    /**
     * Walks {@code root}, runs byte-level reduction and extraction for real against it, and returns
     * extraction's own minted run id -- the fixture {@link ContentCensusRun} needs actually present in
     * the ledger, since it re-derives that same id rather than being told it.
     */
    private RunId walkedThroughExtraction(Ledger ledger, ImplementationVersions versions, Path root, int files)
            throws Exception {
        for (int i = 0; i < files; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "the content of document " + i);
        }
        new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource)).walk(root);
        new ByteLevelReductionTasklet(ledger, new ContentIdentity(jdbcTemplate), versions, root).execute(null, null);
        ExtractionRun extractionRun =
                new ExtractionRun(ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), root);
        return extractionRun.runId();
    }

    private RunId theOneRunOf(String stage) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT id FROM run WHERE stage = ? ORDER BY rowid DESC LIMIT 1", String.class, stage));
    }
}
