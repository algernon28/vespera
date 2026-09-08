package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Gate 3 (ADR-084, #107): whether an operator has named an embedding model. The narrowest of the
 * three conditions in play — the seed and boilerplate-floor gates stage 5's earlier steps already
 * check are pinned by {@link SeedGateTest}, not repeated here.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({EmbeddingModelGate.class, ProfileStore.class})
@Epic("Relevance")
@Feature("Embedding")
@Issue("107")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
class EmbeddingModelGateTest {

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private EmbeddingModelGate embeddingModelGate;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("A gate closes rather than failing")
    @DisplayName("No model named closes the gate")
    void closesWhenNoModelIsNamed() {
        profile(null);

        claim(
                "with no model named there is nothing to score against yet -- the corpus can be walked,"
                        + " extracted and deduplicated before an operator has chosen an embedder",
                () -> assertThat(embeddingModelGate.modelName()).isEmpty());
    }

    @Test
    @Story("The gate opens only when everything it needs is there")
    @DisplayName("A named model opens the gate")
    void opensWhenAModelIsNamed() {
        profile("qwen3-embedding:0.6b");

        claim(
                "the gate opens and hands back the name the operator supplied",
                () -> assertThat(embeddingModelGate.modelName()).contains("qwen3-embedding:0.6b"));
    }

    private void profile(String modelName) {
        Profile loaded = profileStore.load();
        profileStore.save(new Profile(
                loaded.seedFolder(),
                loaded.degenerateOutputConfidenceFloor(),
                loaded.boilerplateDocumentFrequencyFloor(),
                new ProfileValue(modelName, modelName == null ? null : "set by this test", null)));
    }
}
