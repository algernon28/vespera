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
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * The identity extraction records its judgements under (ADR-048, ADR-012), and the archive it reads
 * them over.
 *
 * <p>Two things are worth pinning separately here. That what extraction consumed is written down —
 * the engine it was configured against, since a judgement derived under one engine is not a judgement
 * under another (ADR-012), and a threshold retuned later has to be able to tell them apart. And that
 * what it read is whatever archive it was handed: this stage inherits the previous stage's identity by
 * re-deriving it rather than by being told, which is exact only if every input to that derivation
 * really is fixed and known here — so a wrong derivation has to be visible as a wrong derivation
 * rather than as a missing row nobody notices.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("47")
@Link(name = "ADR-048", url = Adr.WALK_AND_RUN_IDENTITY, type = "adr")
@Link(name = "ADR-012", url = Adr.EXTRACTION_ENGINE_IS_CONFIGURABLE, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
class ExtractionRunTest {

    /** The configured engine, whose identity is the thing this class claims gets written down. */
    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

    /** How many pieces of work the previous stage has recorded by the time extraction reads it: one. */
    private static final int ONE_PREVIOUS_PIECE_OF_WORK = 1;

    /** Documents written under the first archive, chosen to differ from the second archive's count. */
    private static final int FILES_IN_THE_FIRST_ARCHIVE = 2;

    /** Documents written under the second archive. */
    private static final int FILES_IN_THE_SECOND_ARCHIVE = 3;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("What extraction records about itself")
    @DisplayName("Extraction writes down which engine it was configured against")
    void recordsTheEngineItWasConfiguredAgainst(@TempDir Path root) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        walked(ledger, root, FILES_IN_THE_FIRST_ARCHIVE);

        ExtractionRun extractionRun = new ExtractionRun(ledger, new ImplementationVersions(), IDENTITY, new DegenerateOutputConfidenceFloor(null), root);

        claim(
                "what extraction consumed names the engine it ran against, so a judgement made under one"
                        + " engine is never mistaken for one made under another",
                () -> assertThat(configConsumedBy(extractionRun.runId())).contains(IDENTITY.value()));
    }

    @Test
    @Story("What extraction records about itself")
    @DisplayName("Extraction records that it read what the previous stage left, and names that same work")
    void namesThePreviousStagesWorkAsWhatItRead(@TempDir Path root) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        walked(ledger, root, FILES_IN_THE_FIRST_ARCHIVE);
        RunId previousStage = theOneRunOf(ByteLevelReductionTasklet.STAGE);

        ExtractionRun extractionRun = new ExtractionRun(ledger, new ImplementationVersions(), IDENTITY, new DegenerateOutputConfidenceFloor(null), root);

        claim(
                "extraction works out the identity of the previous stage's work rather than being handed"
                        + " it, and what it works out is that work's own identity -- exact only if it matches",
                () -> assertThat(extractionRun.byteLevelReductionRunId()).isEqualTo(previousStage));
        claim(
                "and it records that " + ONE_PREVIOUS_PIECE_OF_WORK + " piece of work as what it read, so"
                        + " what a judgement was derived from is answerable later without re-deriving it",
                () -> assertThat(ledger.upstreamRuns(extractionRun.runId())).containsExactly(previousStage));
    }

    @Test
    @Story("What extraction records about itself")
    @DisplayName("Extraction writes down the tier-2 confidence floor it was configured against (#48)")
    void recordsTheTier2ConfidenceFloorItWasConfiguredAgainst(@TempDir Path root) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        walked(ledger, root, FILES_IN_THE_FIRST_ARCHIVE);

        ExtractionRun extractionRun =
                new ExtractionRun(ledger, new ImplementationVersions(), IDENTITY, new DegenerateOutputConfidenceFloor(0.5), root);

        claim(
                "what extraction consumed also names the tier-2 threshold it ran against, so a run's own"
                        + " row answers what shaped its degeneracy judgements without re-reading the profile",
                () -> assertThat(configConsumedBy(extractionRun.runId())).contains("0.5"));
    }

    @Test
    @Story("Whichever archive it is given")
    @DisplayName("Extraction reads the archive it was handed, not a particular one")
    void readsWhicheverArchiveItIsGiven(@TempDir Path firstArchive, @TempDir Path secondArchive) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        List<OccurrenceId> inTheFirst = walked(ledger, firstArchive, FILES_IN_THE_FIRST_ARCHIVE);
        List<OccurrenceId> inTheSecond = walked(ledger, secondArchive, FILES_IN_THE_SECOND_ARCHIVE);
        ImplementationVersions versions = new ImplementationVersions();
        ExtractionJobConfiguration configuration = new ExtractionJobConfiguration();

        ExtractionRun overTheFirst = new ExtractionRun(ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), firstArchive);
        ExtractionRun overTheSecond = new ExtractionRun(ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), secondArchive);

        claim(
                "handed the first archive, extraction reads exactly the " + FILES_IN_THE_FIRST_ARCHIVE
                        + " documents that archive holds",
                () -> assertThat(everythingRead(configuration.extractionReader(ledger, overTheFirst)))
                        .containsExactlyElementsOf(inTheFirst));
        claim(
                "handed the second, it reads exactly the " + FILES_IN_THE_SECOND_ARCHIVE + " that one holds"
                        + " -- so which archive gets examined is the one an operator named, never one the"
                        + " engine was built around",
                () -> assertThat(everythingRead(configuration.extractionReader(ledger, overTheSecond)))
                        .containsExactlyElementsOf(inTheSecond));
    }

    /** Everything a reader yields, opened and closed the way the step that was handed it does. */
    private static List<OccurrenceId> everythingRead(ItemStreamReader<OccurrenceId> reader) throws Exception {
        List<OccurrenceId> read = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                read.add(id);
            }
        } finally {
            reader.close();
        }
        return read;
    }

    /**
     * One archive of {@code files} distinct documents, walked and finished and then put through the
     * previous stage, with the ids of what it holds in the order a reader over it yields them.
     *
     * <p>The previous stage really runs, rather than having a row inserted for it, because extraction
     * declares that stage's work as what it read and the database enforces that the work exists. That
     * enforcement is exactly what makes working the identity out again safe instead of a guess, so a
     * test that bypassed it would prove nothing about the working out.
     *
     * <p>Distinct contents rather than repeated ones, so that the previous stage resolving
     * byte-identical documents to a single representative (ADR-069) does not quietly reduce the count
     * this returns.
     */
    private List<OccurrenceId> walked(Ledger ledger, Path root, int files) throws Exception {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < files; i++) {
            String name = "document-" + i + ".txt";
            Files.writeString(root.resolve(name), "the content of " + root.getFileName() + " document " + i);
            names.add(name);
        }
        WalkId walkId = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource))
                .walk(root);
        new ByteLevelReductionTasklet(ledger, new ContentIdentity(jdbcTemplate), new ImplementationVersions(), root)
                .execute(null, null);
        return names.stream()
                .map(name -> ledger.occurrenceId(walkId, new OccurrencePath(name)).orElseThrow())
                .sorted((left, right) -> Long.compare(left.value(), right.value()))
                .toList();
    }

    private String configConsumedBy(RunId runId) {
        return jdbcTemplate.queryForObject(
                "SELECT config_consumed FROM run WHERE id = ?", String.class, runId.value());
    }

    private RunId theOneRunOf(String stage) {
        return new RunId(jdbcTemplate.queryForObject("SELECT id FROM run WHERE stage = ?", String.class, stage));
    }
}
