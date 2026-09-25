package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Gate 3 open (ADR-084, #107): a corpus survivor is re-chunked from its cached Docling response once
 * an embedding model is named and stage 5's earlier gates are open, and each of its chunks is embedded
 * and stored as a vector under a scoring run, rather than left unchunked, unembedded, or re-converted
 * through Docling a second time.
 *
 * <p>A sibling of {@link SeedCorpusComparisonInvocationTest} for the same reason that class is a
 * sibling of {@link SeedExtractionInvocationTest}: this fixture additionally names an embedding
 * model, which every other invocation test in this package deliberately leaves unset.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Embedding")
@Issue("107")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
class EmbeddingScoringInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the same way {@link SeedCorpusComparisonInvocationTest} does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 opens too. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

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

    @Test
    @Story("A survivor is re-chunked once a model is named")
    @DisplayName("With a model named, a corpus survivor's chunks land in the chunk cache and each embeds")
    void chunksTheSurvivorFromTheExtractionCache(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the surviving corpus document was chunked, its chunks landing in the chunk cache"
                        + " rather than nowhere",
                () -> assertThat(chunkCacheRowCount()).isPositive());
        claim(
                "and each of those chunks was embedded, its vector landing in the vector table rather"
                        + " than the re-chunk being the last thing gate 3 does",
                () -> assertThat(vectorRowCount()).isEqualTo(chunkCacheRowCount()));
        claim(
                "and a scoring run was minted for it -- a run row for a pass that scored a survivor"
                        + " would otherwise be missing from the ledger entirely",
                () -> assertThat(runCount("embedding-scoring")).isEqualTo(1));
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    private long chunkCacheRowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk_cache", Long.class);
    }

    private long vectorRowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector", Long.class);
    }

    private long runCount(String stage) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run WHERE stage = ?", Long.class, stage);
    }
}
