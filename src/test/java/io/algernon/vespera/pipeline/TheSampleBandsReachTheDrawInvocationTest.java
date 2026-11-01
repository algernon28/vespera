package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
import org.yaml.snakeyaml.Yaml;

/**
 * The bands named by configuration divide the draw (ADR-330 §9), which alike scores cannot show: they have no width, so
 * every document lies in the last band whatever the number. Three documents at about 1.0, 0.9 and 0.707 of the seed's one
 * vector, one from a band: two bands hold the 0.707 in one and the others in the other, so {@value
 * #QUESTIONS_AT_TWO_BANDS} questions, where the shipped five hold each in a band of its own, so three.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TheSampleBandsReachTheDrawInvocationTest.Beans.class})
@Epic("Relevance")
@Feature("Relevance scoring")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the number of bands in the draw of the sample")
class TheSampleBandsReachTheDrawInvocationTest {

    private static final int DOCUMENTS = 3;

    /** In place of the shipped 5. */
    private static final int BANDS = 2;

    /** In place of the shipped 12: one from a band, so the questions count the bands that hold a document. */
    private static final int ONE_FROM_A_BAND = 1;

    private static final int QUESTIONS_AT_TWO_BANDS = BANDS * ONE_FROM_A_BAND;

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The three documents' vectors, whichever each is given, then the seed's. */
    private static final List<float[]> VECTORS = List.of(
            new float[] {1f, 0f},
            new float[] {1f, 0.4843f},
            new float[] {1f, 1f},
            new float[] {1f, 0f});

    /** Answers with the next of {@link #VECTORS} in place of the scripted double's one vector. */
    @TestConfiguration
    static class Beans {

        @Bean
        @Primary
        EmbeddingModel threeDocumentsAtDifferentScores() {
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
        registry.add("vespera.relevance.sample.bands", () -> BANDS);
        registry.add("vespera.relevance.sample.per-band", () -> ONE_FROM_A_BAND);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("How relevance is scored and sampled is a preset")
    @DisplayName("Two bands of one document each ask about two of three documents whose scores differ, where the shipped five ask about all three")
    void twoBandsAskAboutTheBandsThatHoldADocument() throws IOException {
        for (int document = 0; document < DOCUMENTS; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "a corpus document, the " + document + "th");
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());

        cli.run("run", root.toString());

        claim("the " + DOCUMENTS + " documents have " + DOCUMENTS + " scores, the precondition of what follows",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(DISTINCT score) FROM relevance_score", Integer.class))
                        .isEqualTo(DOCUMENTS));
        Map<String, Object> questions = new Yaml().load(
                Files.readString(workingDirectory.resolve("relevance-labels.yaml"), StandardCharsets.UTF_8));
        claim("and the draw asks about " + QUESTIONS_AT_TWO_BANDS + ", one from each of the " + BANDS + " bands",
                () -> assertThat((List<?>) questions.get("documents")).hasSize(QUESTIONS_AT_TWO_BANDS));
    }
}
