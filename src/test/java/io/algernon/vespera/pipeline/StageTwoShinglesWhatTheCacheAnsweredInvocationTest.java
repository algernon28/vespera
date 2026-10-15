package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
 * A stage 2 whose every conversion comes from the extraction cache (ADR-183) still writes the shingles of
 * each, under its own run, and stage 3 over that run measures them (ADR-038, ADR-074).
 *
 * <p>Two folders hold the same corpus: two walks, so two stage-2 runs, and the second asks the converter
 * about nothing. The texts share one sentence and differ elsewhere, so the shared sentence's shingles have
 * a document frequency of every document, and no text is a copy of another.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@Epic("Pipeline")
@Feature("Content census")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
@Link(name = "ADR-183", url = Adr.THE_EXTRACTION_CACHE_KEEPS_ONLY_ANSWERS_ABOUT_THE_DOCUMENT, type = "adr")
class StageTwoShinglesWhatTheCacheAnsweredInvocationTest {

    private static final int CORPUS_SIZE = 3;

    private static final String SHARED_SENTENCE =
            "This memo is kept with the harbour office records and is not to be copied.";

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

    /** The converter's script, pins and count are static and shared with every class importing it. */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(List.of(), List.of(), List.of());
        profileStore.save(ProfileFixture.profile().build());
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
    }

    @Test
    @Story("Every survivor's shingles are measured")
    @DisplayName("A stage 2 answered wholly from the cache writes each conversion's shingles under its own run, and stage 3 measures them")
    void aStageTwoAnsweredWhollyFromTheCacheWritesShinglesStageThreeMeasures(
            @TempDir Path firstRoot, @TempDir Path secondRoot) throws IOException {
        writeCorpus(firstRoot);
        writeCorpus(secondRoot);
        cli.run("run", firstRoot.toString());

        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", secondRoot.toString());
        int conversionsAsked = ConverterStopsPartwayBeans.conversions();

        String firstExtraction = onlyRunOf(firstRoot, StageModules.EXTRACTION);
        String secondExtraction = onlyRunOf(secondRoot, StageModules.EXTRACTION);
        String secondCensus = onlyRunOf(secondRoot, StageModules.CONTENT_CENSUS);

        claim(
                "the second folder is extracted under a run of its own",
                () -> assertThat(secondExtraction).isNotEqualTo(firstExtraction));
        claim(
                "and the converter is asked about none of its occurrences, so every answer came from the cache",
                () -> assertThat(conversionsAsked).isZero());
        claim(
                "every one of the " + CORPUS_SIZE + " occurrences has shingle rows under the second extraction's run",
                () -> assertThat(shingledOccurrences(secondExtraction)).isEqualTo(CORPUS_SIZE));
        claim(
                "the content census over that run counts " + CORPUS_SIZE + " shingled documents",
                () -> assertThat(shingledDocumentCount(secondCensus)).containsExactly((long) CORPUS_SIZE));
        claim(
                "and measures the shared sentence's passages as found in all " + CORPUS_SIZE + " documents",
                () -> assertThat(passagesFoundIn(secondCensus, CORPUS_SIZE)).isPositive());
    }

    private static void writeCorpus(Path root) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("%02d-memo.txt", position)),
                    "Memo " + position + " lists the moorings let in month " + position + " and the berths repainted"
                            + " after storm number " + position + ". " + SHARED_SENTENCE);
        }
    }

    private String onlyRunOf(Path root, StageModules stage) {
        List<String> runs = jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ? AND r.stage = ?",
                String.class,
                Walk.canonicalRoot(root).toString(),
                stage.stage());
        assertThat(runs).as("the runs of %s over %s", stage.stage(), root).hasSize(1);
        return runs.getFirst();
    }

    private long shingledOccurrences(String extractionRun) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT occurrence_id) FROM shingle WHERE run_id = ?", Long.class, extractionRun);
    }

    private List<Long> shingledDocumentCount(String censusRun) {
        return jdbcTemplate.queryForList(
                "SELECT shingled_document_count FROM shingle_corpus_size WHERE run_id = ?", Long.class, censusRun);
    }

    private long passagesFoundIn(String censusRun, int documents) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shingle_document_frequency WHERE run_id = ? AND document_count = ?",
                Long.class,
                censusRun,
                documents);
    }
}
