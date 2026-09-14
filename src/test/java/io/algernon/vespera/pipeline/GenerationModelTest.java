package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Where the generation model's name comes from (ADR-114, #179).
 *
 * <p>Deliberately not a gate, and that is the whole of what separates this from {@link
 * EmbeddingModelGateTest}: an unanswered key here resolves to a value and the run proceeds, where an
 * unanswered embedding model ends the invocation. The two models are sourced differently because one
 * keys every vector and every relevance verdict and the other keys a run whose output is prose.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({GenerationModel.class, ProfileStore.class})
@Epic("Synthesis")
@Feature("Generation")
@Issue("179")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
class GenerationModelTest {

    /**
     * What this test context configures as the shipped default. Deliberately not the value
     * {@code application.yaml} actually carries: a test that named the real default would pass
     * against a resolver that ignored configuration entirely and returned a constant.
     */
    private static final String CONFIGURED_DEFAULT = "a-configured-default:1b";

    /** What an operator wrote into {@code generationModel}, distinct from the configured default. */
    private static final String AN_OPERATOR_CHOICE = "an-operator-choice:70b";

    /** A value written and left empty, which {@code ProfileValue.isSet()} already reads as unset. */
    private static final String BLANK = "";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("spring.ai.ollama.chat.options.model", () -> CONFIGURED_DEFAULT);
    }

    @Autowired
    private GenerationModel generationModel;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("Unset means the default, never a stop")
    @DisplayName("With no model written in the profile, generation runs under the configured default")
    void resolvesTheConfiguredDefaultWhenTheProfileNamesNone() {
        profile(null);

        claim(
                "an unanswered generationModel resolves to the name application configuration carries"
                        + " rather than ending the invocation: the operator's path is already five stops,"
                        + " and a sixth for a value most operators have no basis to choose is not worth one",
                () -> assertThat(generationModel.name()).isEqualTo(CONFIGURED_DEFAULT));
    }

    @Test
    @Story("The profile overrides the configured default")
    @DisplayName("A model written into the profile is the one generation runs under")
    void prefersTheProfileOverTheConfiguredDefault() {
        profile(AN_OPERATOR_CHOICE);

        claim(
                "the key the operator answered wins over the shipped name: a corpus generated under"
                        + " something other than the default is a judgement about that corpus, and the"
                        + " profile is where a judgement travels with its provenance",
                () -> assertThat(generationModel.name()).isEqualTo(AN_OPERATOR_CHOICE));
    }

    @Test
    @Story("Unset means the default, never a stop")
    @DisplayName("A blank model in the profile is an unanswered one, so the configured default applies")
    void treatsABlankProfileValueAsUnanswered() {
        profile(BLANK);

        claim(
                "a key written and left empty resolves to the default rather than to a fault, which is"
                        + " what keeps the resolved name from ever being blank -- so the rule that an"
                        + " identity refuses a blank value is satisfied without a check on the profile",
                () -> assertThat(generationModel.name()).isEqualTo(CONFIGURED_DEFAULT));
    }

    @Test
    @Story("A misconfiguration stops the run, because it poisons every cluster")
    @DisplayName("With the configured default blanked and nothing in the profile, resolving refuses")
    void refusesWhenNeitherConfigurationNorProfileNamesAModel() {
        profile(null);
        GenerationModel withNoDefault = new GenerationModel(profileStore, BLANK);

        claim(
                "there is no honest name to compose a generator identity around, and the fault poisons"
                        + " every cluster rather than one -- so it stops the run here rather than faulting"
                        + " each cluster in turn once the calls have started. The refusal is its own kind"
                        + " rather than a general one, because the closing line singles this out to swallow"
                        + " and would turn any other kind into a stack trace on a successful invocation",
                () -> assertThatThrownBy(withNoDefault::name)
                        .isInstanceOf(NoGenerationModelNamedException.class)
                        .hasMessageContaining("generationModel"));
    }

    /** Writes {@code generationModel}, leaving every other key as census created it. */
    private void profile(String modelName) {
        Profile loaded = profileStore.load();
        profileStore.save(new Profile(
                loaded.seedFolder(),
                loaded.degenerateOutputConfidenceFloor(),
                loaded.boilerplateDocumentFrequencyFloor(),
                loaded.embeddingModel(),
                loaded.relevanceScoreFloor(),
                loaded.arrangementApproved(),
                new ProfileValue(modelName, modelName == null ? null : "set by this test", null)));
    }
}
