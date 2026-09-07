package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.CountingDoclingBeans;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.similarity.BoilerplateShingles;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.algernon.vespera.similarity.RedundancyResolution;
import io.algernon.vespera.similarity.RedundancySignatures;
import io.algernon.vespera.similarity.Shingler;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * A document that is both a seed and a corpus member costs one extraction, not two.
 *
 * <p>This is the claim the seed pass's whole shape rests on, and it holds <em>by construction</em>
 * rather than by anything checking: the extraction and chunk caches are content-addressed and carry no
 * run id, and both passes compute the same content identity for the same bytes, so the second pass to
 * ask for a document finds it already converted. The precondition — that the two modules' hashers
 * agree — is pinned separately by {@code ContentHashingTest}.
 *
 * <p><b>Why this class exists rather than one more test elsewhere.</b> Every other invocation-level
 * test here replaces the extractor itself, which answers without ever consulting the cache. Counting
 * conversions at that seam would prove nothing about caching, however many the count came to. This
 * class alone wires the real extractor over the real cache and counts one layer lower, at the client
 * ({@code CountingDoclingBeans}) — so the number it asserts is the number of conversions that would
 * have been HTTP calls to a live sidecar.
 *
 * <p>The corpus holds exactly one document, deliberately: a total conversion count is only readable as
 * a per-document claim when there is one document to count.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
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
    SeedMeasurementRun.class,
    SeedGate.class,
    RedundancySignatures.class,
    RedundancyResolution.class,
    BoilerplateShingles.class,
    DocumentFrequency.class,
    ConfidenceDistribution.class,
    UnusableSeeds.class,
    Shingler.class,
    HybridChunkerBeans.class,
    CountingDoclingBeans.class,
    ExtractionMetrics.class,
    LanguageDetection.class,
    ContentIdentity.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
    ImplementationVersions.class,
    ProfileStore.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Publish.class,
    VesperaCli.class
})
@Epic("Relevance")
@Feature("Seed set")
@Issue("104")
@Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
@Link(name = "ADR-010", url = Adr.EXTRACTION_VIA_DOCLING, type = "adr")
class SeedSharedWithCorpusTest {

    @TempDir
    static Path workingDirectory;

    /** The one document in the corpus, copied byte-for-byte into the seed folder as well. */
    private static final String SHARED_DOCUMENT = "shared.txt";

    private static final String SHARED_CONTENT = "a document that is both a seed and a corpus member";

    /** One conversion: the corpus pass converts it, and the seed pass finds it already converted. */
    private static final int ONE_CONVERSION = 1;

    /** The least aggressive floor that still opens stage 4's gate, so stage 5 has an upstream run. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("A seed that is also a corpus member costs nothing extra")
    @DisplayName("The same document in the corpus and the seed folder is converted once, not twice")
    void convertsADocumentSharedBetweenTheCorpusAndTheSeedFolderOnce(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.write(root.resolve(SHARED_DOCUMENT), SHARED_CONTENT.getBytes(StandardCharsets.UTF_8));
        Files.write(seeds.resolve(SHARED_DOCUMENT), SHARED_CONTENT.getBytes(StandardCharsets.UTF_8));
        openTheGates(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reported success, so both passes ran and the count below is what they"
                        + " between them actually asked the extractor for",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the document reached the converter exactly once, though two passes each asked for it."
                        + " The archive pass converted it; the seed pass found it already converted, because"
                        + " a conversion is filed under the content and the engine, not under which pass"
                        + " wanted it. A second call here would be the most expensive operation in the tool"
                        + " paid twice for one document, and would mean the two passes disagree about what"
                        + " identifies content",
                () -> assertThat(CountingDoclingBeans.CONVERSIONS.get()).isEqualTo(ONE_CONVERSION));
        claim(
                "and exactly one cached conversion is stored for it, rather than one row per pass -- the"
                        + " cache is keyed by content and engine, so the seed pass added no row of its own",
                () -> assertThat(cachedConversionCount()).isEqualTo(ONE_CONVERSION));
        claim(
                "the shared document was chunked, and under one chunker and tokenizer identity it holds"
                        + " one set of chunks rather than a second set the seed pass appended",
                () -> assertThat(distinctChunkedDocuments()).isEqualTo(ONE_CONVERSION));
    }

    /** The seed folder named and stage 4's gate open. */
    private void openTheGates(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seeds.toString(), "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null)));
    }

    private long cachedConversionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM extraction_cache", Long.class);
    }

    /** How many distinct documents have chunks stored, under any chunker and tokenizer identity. */
    private long distinctChunkedDocuments() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT content_hash || chunker_identity || tokenizer_identity) FROM chunk_cache",
                Long.class);
    }
}
