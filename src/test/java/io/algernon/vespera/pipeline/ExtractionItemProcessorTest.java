package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.extraction.ScriptedExtractor;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.similarity.Shingler;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * What stage 2's item processor still does itself once the rules that judge an answer live in {@code
 * extraction} (ADR-189): it chooses what the converter is sent, it writes a converted occurrence's text to
 * the shingle table, and it carries on past an occurrence with no recorded format without sending it.
 *
 * <p><b>The rest of this class moved</b> (#407). Its eighteen tests were claims about how an answer is
 * judged, and that is {@code extraction}'s {@code OccurrenceJudge} now; they are ported to {@code
 * extraction.OccurrenceJudgeTest}, one for one. What is left here is the half of three of them that is
 * about the processor rather than the judge -- the shingles of a conversion and of a spreadsheet, and the
 * occurrence with no recorded format -- and the whole of {@code convertsAsTheFormatStageOneRecorded},
 * which never judged anything.
 *
 * <p>The ledger is real, over the test database, and stage 1 really runs first, because stage 2's run
 * names stage 1's as upstream and a foreign key enforces that the row exists.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("407")
@Link(name = "ADR-189", url = Adr.STAGE_2_RULES_LIVE_IN_EXTRACTION, type = "adr")
class ExtractionItemProcessorTest {

    /**
     * Stage 1's working directory, where its {@code profile.yaml} is written: a temporary folder of
     * this test's own, outside the corpus root so the walk never reads it, and shared with no other
     * test, class, run or branch.
     */
    @TempDir
    Path stage1Working;

    /** The engine every response in this class is attributed to; which engine it is does not matter here. */
    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;scripted");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A conversion that succeeded")
    @DisplayName("A converted document's text reaches the shingle table under stage 2's own run")
    @Issue("103")
    @Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
    void aConvertedDocumentIsShingled(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(new DoclingResponse(
                        ConversionStatus.SUCCESS,
                        List.of(),
                        0d,
                        null,
                        "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"real content the chunker and"
                                + " the shingler both read\"}]}}}"));

        processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "the shingle table carries rows against this occurrence's own run, proving the shingler is"
                        + " called with the same run stage 2 minted",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM shingle WHERE occurrence_id = ? AND run_id = ?",
                                Integer.class,
                                corpus.occurrence(0).value(),
                                corpus.extractionRunId().value()))
                        .isPositive());
        claim(
                "and nothing was chunked: a chunk cut under a budget no embedding model has been named for is"
                        + " work guaranteed to be discarded, and stage 5 re-chunks from the extraction cache once a"
                        + " model exists",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk_cache", Integer.class))
                        .isZero());
    }

    /**
     * #275: every spreadsheet in the GesPOS corpus earned degenerate-output, because only {@code texts[]}
     * was read. Whether it clears the floor is {@code extraction.OccurrenceJudgeTest}'s claim now.
     */
    @Test
    @Story("Tier 1 — the hard zero-content floor")
    @DisplayName("A spreadsheet, which converts to a table and no text items, is shingled by its cells")
    @Issue("275")
    @Link(name = "ADR-145", url = Adr.TABLE_CELLS_ARE_EXTRACTED_TEXT, type = "adr")
    void aSpreadsheetIsShingledByItsCells(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(new DoclingResponse(
                        ConversionStatus.SUCCESS,
                        List.of(),
                        0d,
                        null,
                        "{\"document\":{\"json_content\":{\"body\":{\"children\":[{\"$ref\":\"#/tables/0\"}]},"
                                + "\"texts\":[],\"tables\":[{\"children\":[],\"data\":{\"table_cells\":["
                                + "{\"text\":\"Merchant\",\"start_row_offset_idx\":0,\"start_col_offset_idx\":0},"
                                + "{\"text\":\"Terminal\",\"start_row_offset_idx\":0,\"start_col_offset_idx\":1},"
                                + "{\"text\":\"ACME Srl\",\"start_row_offset_idx\":1,\"start_col_offset_idx\":0},"
                                + "{\"text\":\"TID-273273\",\"start_row_offset_idx\":1,\"start_col_offset_idx\":1}"
                                + "]}}]}}}"));

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "it earns no verdict and goes on as a survivor",
                () -> assertThat(outcome).isNull());
        claim(
                "and its cells reached the shingle table, so the stages after this one compare it by the"
                        + " same text the floor measured",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM shingle WHERE occurrence_id = ?",
                                Integer.class,
                                corpus.occurrence(0).value()))
                        .isPositive());
    }

    /**
     * ADR-094 narrows plain text by extension only for {@code .md}, {@code .html}, {@code .csv} and
     * {@code .adoc}, so the {@code .txt} fixture below is plain text with no subtype at all.
     */
    @Test
    @Story("The format sent is the one stage 1 read off the bytes")
    @DisplayName("The occurrence is converted as what stage 1 found it to be, not as what its path says")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    void convertsAsTheFormatStageOneRecorded(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor().answering(converted());

        processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "what reaches the converter is the format stage 1 read off the leading bytes, looked up"
                        + " under the stage-1 run this stage names upstream -- prose in a .txt file is plain"
                        + " text, and the path plays no part in saying so",
                () -> assertThat(docling.formatsAsked()).containsExactly(DetectedFormat.PLAIN_TEXT));
        claim(
                "and it carries no subtype, because .txt is not one of the extensions plain text is"
                        + " narrowed by -- an absent subtype is a legitimate answer, not a missing one",
                () -> assertThat(docling.subtypesAsked()).containsExactly(Optional.empty()));
    }

    /**
     * The second occurrence's verdict is the floor's -- the scripted response carries no text -- which
     * is what makes it evidence that the occurrence was judged rather than set aside. How the first
     * occurrence's reason is worded is {@code extraction.OccurrenceJudgeTest}'s claim; which run the
     * processor hands the judge to name in it is this one's.
     */
    @Test
    @Story("The format sent is the one stage 1 read off the bytes")
    @DisplayName("An occurrence whose format was never recorded is not sent to the converter, and the pass carries on")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    @Link(name = "ADR-057", url = Adr.VERDICT_VOCABULARY_IS_EIGHT_VALUES, type = "adr")
    void sendsNothingForAMissingFormatAndCarriesOn(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 2);
        jdbcTemplate.update("DELETE FROM detected_format WHERE occurrence_id = ?", corpus.occurrence(0).value());
        ScriptedExtractor docling = new ScriptedExtractor().thenAlwaysAnswering(converted());
        ExtractionItemProcessor processor = processorOver(corpus, docling);

        ExtractionOutcome unreadable = processor.process(corpus.occurrence(0));
        ExtractionOutcome next = processor.process(corpus.occurrence(1));

        claim(
                "the occurrence with no recorded format is recorded as a failure of its own",
                () -> assertThat(unreadable.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "and its reason names the occurrence and the stage-1 run the format was sought under -- not"
                        + " stage 2's own run -- because this is the one such verdict no converter answer produced,"
                        + " and in the ledger it is otherwise indistinguishable from a conversion that really failed",
                () -> assertThat(unreadable.reason())
                        .contains(corpus.byteLevelReductionRunId().value())
                        .doesNotContain(corpus.extractionRunId().value())
                        .contains(String.valueOf(corpus.occurrence(0).value())));
        claim(
                "and nothing was converted for it: a document whose format could not be read is not sent"
                        + " to Docling on a guess, so only the occurrence that kept its row was converted",
                () -> assertThat(docling.conversions()).isEqualTo(1));
        claim(
                "while the very next occurrence is put to the converter and judged on what came back --"
                        + " one unreadable row costs one occurrence, and a pass over hundreds of gigabytes"
                        + " does not abort on it",
                () -> assertThat(next.kind()).isEqualTo(VerdictKind.DEGENERATE_OUTPUT));
    }

    /**
     * The processor's own hash, the second of stage 2's two places that hash a file (ADR-206 section 2):
     * reached only where nothing was dispatched ahead, which in the job never happens, so it is held here
     * rather than by a whole invocation. One file, so stage 1 hashed nothing and this step must.
     */
    @Test
    @Story("A file stage 2 cannot hash")
    @DisplayName("A file gone when the processor hashes it is removed as one that could not be read, and is not sent to the converter")
    @Issue("452")
    @Link(name = "ADR-210", url = Adr.A_FILE_THAT_CANNOT_BE_READ_IS_MARKED_AND_THE_STEP_GOES_ON, type = "adr")
    void aFileGoneWhenTheProcessorHashesItIsMarkedAndNotSent(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor().thenAlwaysAnswering(converted());
        Files.delete(root.resolve("document-0.txt"));

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "the occurrence is removed as one extraction failed on, and its reason says the file could not"
                        + " be read",
                () -> {
                    assertThat(outcome.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED);
                    assertThat(outcome.reason()).startsWith("could not be read: ");
                });
        claim(
                "the converter was asked nothing: there were no bytes to send",
                () -> assertThat(docling.conversions()).isZero());
        claim(
                "and nothing was measured, and no key recorded, for it",
                () -> {
                    assertThat(jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM extraction_metric WHERE occurrence_id = ?",
                                    Integer.class,
                                    corpus.occurrence(0).value()))
                            .isZero();
                    assertThat(jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM extraction_cache_key WHERE occurrence_id = ?",
                                    Integer.class,
                                    corpus.occurrence(0).value()))
                            .isZero();
                });
    }

    @Test
    @Story("The folder being read is gone")
    @DisplayName("A folder gone when the processor hashes one of its files stops the step, naming the folder, and marks nothing")
    @Issue("452")
    @Link(name = "ADR-210", url = Adr.A_FILE_THAT_CANNOT_BE_READ_IS_MARKED_AND_THE_STEP_GOES_ON, type = "adr")
    void aFolderGoneWhenTheProcessorHashesStopsTheStep(@TempDir Path parent) throws Exception {
        Path root = Files.createDirectory(parent.resolve("corpus"));
        Corpus corpus = corpusOf(root, 1);
        String canonicalRoot = io.algernon.vespera.corpus.Walk.canonicalRoot(root).toString();
        ScriptedExtractor docling = new ScriptedExtractor().thenAlwaysAnswering(converted());
        ExtractionItemProcessor processor = processorOver(corpus, docling);
        Files.move(root, parent.resolve("moved-away"));

        Throwable stopped = org.assertj.core.api.Assertions.catchThrowable(() -> processor.process(corpus.occurrence(0)));

        claim(
                "the step is stopped, by a failure that names the folder that can no longer be listed",
                () -> assertThat(stopped)
                        .isNotNull()
                        .hasMessageContaining("the corpus root " + canonicalRoot + " can no longer be listed"));
        claim(
                "and the converter was asked nothing",
                () -> assertThat(docling.conversions()).isZero());
    }

    /** A response Docling answered cleanly with, carrying nothing for this step to judge. */
    private static DoclingResponse converted() {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, "{}");
    }

    /**
     * The processor as the step builds it: over the real ledger, over the run stage 2 mints for this
     * walk, and over the scripted converter this test wants it to read.
     */
    private ExtractionItemProcessor processorOver(Corpus corpus, ScriptedExtractor docling) {
        return new ExtractionItemProcessor(
                corpus.ledger(),
                new ContentIdentity(jdbcTemplate),
                new DetectedFormats(jdbcTemplate),
                docling,
                IDENTITY,
                corpus.stage2(),
                new ExtractionMetrics(jdbcTemplate, new LanguageDetection()),
                new DegenerateOutputConfidenceFloor(null),
                new Shingler(jdbcTemplate),
                // Nothing here drops a connection, so nothing is waited for: there is no sidecar to ask.
                new SidecarRecovery(null) {
                    @Override
                    void awaitHealthy() {}
                });
    }

    /**
     * A walked corpus of {@code files} distinct documents, with stage 1 already run over it and
     * stage 2's run minted. Distinct contents, because stage 1 resolves byte-identical documents to a
     * single representative (ADR-069).
     */
    private Corpus corpusOf(Path root, int files) throws Exception {
        List<OccurrencePath> paths = new ArrayList<>();
        for (int i = 0; i < files; i++) {
            String name = "document-" + i + ".txt";
            Files.writeString(root.resolve(name), "the content of document " + i);
            paths.add(new OccurrencePath(name));
        }
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource))
                .walk(root);
        ImplementationVersions versions = new ImplementationVersions();
        ChunkContext step = InvocationRecordFixture.aStepOfAFreshInvocation();
        io.algernon.vespera.profile.ProfileStore profileStore =
                new io.algernon.vespera.profile.ProfileStore(stage1Working);
        new ByteLevelReductionTasklet(ledger, new ContentIdentity(jdbcTemplate), new DetectedFormats(jdbcTemplate), versions, profileStore, root, stage1Working).execute(null, step);
        ExecutionContext invocation = InvocationRecordFixture.recordOf(step);
        // Stage 2 passes no gate, so the gates and the later stages' collaborators are left out: an
        // accessor that needed one would fail here rather than mint.
        StageRuns stageRuns = new StageRuns(
                ledger,
                versions,
                new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                        .getBeanProvider(ExtractorIdentity.class),
                new DegenerateOutputConfidenceFloor(null),
                null,
                null,
                null,
                profileStore,
                null,
                null,
                null,
                null,
                root,
                invocation);
        stageRuns.extraction();
        List<OccurrenceId> occurrences = paths.stream()
                .map(path -> ledger.occurrenceId(walkId, path).orElseThrow())
                .toList();
        return new Corpus(ledger, new InvocationRuns(invocation), stageRuns, occurrences);
    }

    /** One walked corpus and the run stage 2 judges it under. */
    private record Corpus(
            Ledger ledger, InvocationRuns invocation, StageRuns stage2, List<OccurrenceId> occurrences) {

        RunId extractionRunId() {
            return invocation.runOf("extraction").orElseThrow();
        }

        RunId byteLevelReductionRunId() {
            return invocation.runOf("byte-level-reduction").orElseThrow();
        }

        OccurrenceId occurrence(int index) {
            return occurrences.get(index);
        }
    }
}
