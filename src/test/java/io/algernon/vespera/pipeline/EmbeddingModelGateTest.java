package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
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
 * Gate 3 (ADR-084, #107), narrowed by ADR-286: the embedding model stage 5 scores under is the profile's {@code
 * embeddingModel} where it is set, and otherwise the name application configuration carries; the gate is shut only
 * where neither names one. The seed and boilerplate-floor gates stage 5's earlier steps check are pinned by {@link
 * SeedGateTest}, not repeated here.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({EmbeddingModelGate.class, ProfileStore.class})
@Epic("Relevance")
@Feature("Embedding")
@Issue("107")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
@Link(name = "ADR-286", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
class EmbeddingModelGateTest {

    /**
     * What this test context configures as the shipped default. Deliberately not the value {@code application.yaml}
     * carries: a test naming the real default would pass against a gate that ignored configuration and returned a
     * constant. {@code ShippedConfigurationTest} holds the real one.
     */
    private static final String CONFIGURED_DEFAULT = "a-configured-embedder:1b";

    /** What an operator wrote into {@code embeddingModel}, distinct from the configured default. */
    private static final String AN_OPERATOR_CHOICE = "an-operator-choice:8b";

    /** A value written and left empty, which {@code ProfileValue.isSet()} already reads as unset. */
    private static final String BLANK = "";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("spring.ai.ollama.embedding.options.model", () -> CONFIGURED_DEFAULT);
    }

    @Autowired
    private EmbeddingModelGate embeddingModelGate;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("Unset means the shipped model, not a stop")
    @DisplayName("With no model written in the profile, documents are scored under the configured default")
    void resolvesTheConfiguredDefaultWhenTheProfileNamesNone() {
        profile(null);

        claim(
                "an unanswered embeddingModel resolves to the name application configuration carries, so the gate"
                        + " is open and scoring runs without the operator having had to name a model",
                () -> assertThat(embeddingModelGate.modelName()).contains(CONFIGURED_DEFAULT));
    }

    @Test
    @Story("The profile overrides the configured default")
    @DisplayName("A model written into the profile is the one documents are scored under")
    void prefersTheProfileOverTheConfiguredDefault() {
        profile(AN_OPERATOR_CHOICE);

        claim(
                "the key the operator answered wins over the shipped name: a corpus scored under another model is a"
                        + " judgement about that corpus, and the profile is where it travels with its provenance",
                () -> assertThat(embeddingModelGate.modelName()).contains(AN_OPERATOR_CHOICE));
    }

    @Test
    @Story("Unset means the shipped model, not a stop")
    @DisplayName("A blank model in the profile is an unanswered one, so the configured default applies")
    void treatsABlankProfileValueAsUnanswered() {
        profile(BLANK);

        claim(
                "a key written and left empty resolves to the default rather than to a closed gate or a fault, so"
                        + " the name handed on is never blank",
                () -> assertThat(embeddingModelGate.modelName()).contains(CONFIGURED_DEFAULT));
    }

    @Test
    @Story("The gate opens only when everything it needs is there")
    @DisplayName("With the configured default blanked, a model written into the profile still opens the gate")
    void opensOnTheProfileAloneWhenConfigurationNamesNone() {
        profile(AN_OPERATOR_CHOICE);
        EmbeddingModelGate withNoDefault = new EmbeddingModelGate(profileStore, BLANK);

        claim(
                "the profile's model is enough on its own: an operator who blanked the shipped name and then named"
                        + " a model scores under the one they named",
                () -> assertThat(withNoDefault.modelName()).contains(AN_OPERATOR_CHOICE));
    }

    @Test
    @Story("A gate closes rather than failing")
    @DisplayName("With the configured default blanked and nothing in the profile, the gate closes")
    void closesOnlyWhenNeitherTheProfileNorConfigurationNamesAModel() {
        profile(null);
        EmbeddingModelGate withNoDefault = new EmbeddingModelGate(profileStore, BLANK);

        claim(
                "with no model named anywhere there is nothing to score against, and the gate closes rather than"
                        + " failing the invocation: the corpus can still be walked, extracted and deduplicated while"
                        + " the operator holds scoring back until they have chosen a model",
                () -> assertThat(withNoDefault.modelName()).isEmpty());
    }

    /** Writes {@code embeddingModel}, leaving every other key as census created it. */
    private void profile(String modelName) {
        Profile loaded = profileStore.load();
        profileStore.save(ProfileFixture.profileFrom(loaded)
                .embeddingModel(modelName, modelName == null ? null : "set by this test")
                .build());
    }
}
