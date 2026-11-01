package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The number of similarities named by configuration is the number a score is the mean of (ADR-330 §8, §9). One document
 * of two chunks, one along the seed's one vector and one at 45 degrees to it, so its two similarities are 1 and about
 * 0.707: the mean of the shipped three, there being two, is about 0.854, and the top one alone is 1.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AScoreIsTheMeanOfTheNumberConfiguredInvocationTest.Beans.class})
@Epic("Relevance")
@Feature("Relevance scoring")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the number of similarities in the step that scores")
class AScoreIsTheMeanOfTheNumberConfiguredInvocationTest {

    /** Named by configuration, in place of the shipped 3. */
    private static final int ONE_SIMILARITY = 1;

    /** The highest of the two similarities, which is the score at {@value #ONE_SIMILARITY}. */
    private static final double THE_HIGHEST = 1.0;

    /** Far below the distance between 1 and the mean of the two, about 0.146, and above a float's rounding. */
    private static final double CLOSE_ENOUGH = 1e-6;

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The document's two chunks in either order, then the seed's. */
    private static final List<float[]> VECTORS = List.of(new float[] {1f, 0f}, new float[] {1f, 1f}, new float[] {1f, 0f});

    /** Answers with the next of {@link #VECTORS} in place of the scripted double's one vector. */
    @TestConfiguration
    static class Beans {

        @Bean
        @Primary
        EmbeddingModel twoChunksAtDifferentSimilarities() {
            AtomicInteger calls = new AtomicInteger();
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    float[] vector = VECTORS.get(Math.min(calls.getAndIncrement(), VECTORS.size() - 1));
                    return new EmbeddingResponse(List.of(new Embedding(vector, 0)), new EmbeddingResponseMetadata());
                }

                @Override
                public float[] embed(Document document) {
                    throw new UnsupportedOperationException("not used by this fixture");
                }
            };
        }
    }

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.relevance.top-similarities", () -> ONE_SIMILARITY);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("How relevance is scored and sampled is a preset")
    @DisplayName("A score of one similarity is the highest of a document's, where the shipped number takes the mean")
    void aScoreOfOneSimilarityIsTheHighest() throws IOException {
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.TWO_CHUNKS_OF_TEXT), "a document of two chunks");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());

        cli.run("run", root.toString());

        claim("the one document's score is " + THE_HIGHEST + ", the highest of its two similarities",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT score FROM relevance_score", Double.class))
                        .isCloseTo(THE_HIGHEST, within(CLOSE_ENOUGH)));
    }
}
