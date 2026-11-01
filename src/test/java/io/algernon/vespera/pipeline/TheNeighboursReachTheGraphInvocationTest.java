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
 * The number of neighbours named by configuration reaches the graph (ADR-330 §9), which the identical vectors of {@code
 * EmbeddingScriptedBeans} cannot show: over them the count of clusters is the same at every {@code k}.
 *
 * <p>So the embedding double here answers each call with the next of {@value #DOCUMENTS} vectors, in two close pairs,
 * and then the seed's: scoring embeds the corpus survivors before the seeds, one chunk to each of these short texts. The
 * order the documents come in decides which holds which vector, and nothing else: the shape of the graph over them is
 * the same. Measured through {@code NearestNeighbourGraph.build} and {@code Communities.of} at the shipped resolution:
 * {@value #CLUSTERS_AT_THE_SHIPPED_NEIGHBOURS} cluster at the shipped 15, since 15 is floored to all three others and
 * the close pairs are linked across, and {@value #CLUSTERS_AT_ONE_NEIGHBOUR} at 1, each document keeping the other of
 * its pair alone.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TheNeighboursReachTheGraphInvocationTest.Beans.class})
@Epic("Relevance")
@Feature("Clustering")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the configured number of neighbours in the graph")
class TheNeighboursReachTheGraphInvocationTest {

    private static final int DOCUMENTS = 4;

    /** Named by configuration, in place of the shipped 15. */
    private static final int ONE_NEIGHBOUR = 1;

    /** What the measurement finds at one neighbour: a cluster of each close pair. */
    private static final int CLUSTERS_AT_ONE_NEIGHBOUR = 2;

    /** What it finds at the shipped 15, which a value not reaching the graph leaves here. */
    private static final int CLUSTERS_AT_THE_SHIPPED_NEIGHBOURS = 1;

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The vectors of the corpus survivors, in two close pairs, then the one seed's between them. */
    private static final List<float[]> VECTORS = List.of(
            new float[] {1f, 0f},
            new float[] {1f, 0.05f},
            new float[] {1f, 0.3f},
            new float[] {1f, 0.35f},
            new float[] {1f, 0.175f});

    /** Answers with the next of {@link #VECTORS} in place of the scripted double's one vector, and keeps the rest. */
    @TestConfiguration
    static class Beans {

        @Bean
        @Primary
        EmbeddingModel vectorsInTwoClosePairs() {
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
        registry.add("vespera.clustering.neighbours", () -> ONE_NEIGHBOUR);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("One neighbour named by configuration splits two close pairs of documents the shipped number would keep as one group")
    void oneNeighbourSplitsWhatTheShippedNumberKeepsTogether() throws IOException {
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

        claim("the invocation clustered all " + DOCUMENTS + " documents",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_cluster", Integer.class))
                        .isEqualTo(DOCUMENTS));
        claim("into " + CLUSTERS_AT_ONE_NEIGHBOUR + " groups, a group of each close pair, where the shipped number"
                        + " finds " + CLUSTERS_AT_THE_SHIPPED_NEIGHBOURS,
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(DISTINCT cluster_ordinal) FROM document_cluster", Integer.class))
                        .isEqualTo(CLUSTERS_AT_ONE_NEIGHBOUR));
    }
}
