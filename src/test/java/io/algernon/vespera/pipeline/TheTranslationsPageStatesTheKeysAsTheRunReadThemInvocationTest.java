package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.LanguagesValue;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The translations page states how the two keys read when the scoring run was minted, not how the profile reads
 * when the page is written (ADR-273 §3).
 *
 * <p>The whole job over {@code en/guide.txt} and {@code zh/guide.txt}, with {@code translationSimilarityFloor}
 * and {@code preferredLanguages} both unreadable as the job starts. The profile store every class of the job reads
 * is one that, the first time it is loaded inside the translation step, saves the profile with both keys unset
 * first: the scoring run was minted by then, in the embedding step before it, so the profile is saved after the mint
 * and before the page is written.
 */
@CascadeSliceTest
@Import({
    SeedScriptedExtractionBeans.class,
    TheTranslationsPageStatesTheKeysAsTheRunReadThemInvocationTest.SavedOnceTheTranslationStepStarts.class
})
@Epic("Relevance")
@Feature("Copies in another language")
@Issue("548")
@Link(name = "ADR-273", url = Adr.THE_TRANSLATION_STEP_READS_A_MEAN_VECTOR_ONLY_TO_COMPARE_IT_AND_THE_PROFILE_ONCE, type = "adr")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
class TheTranslationsPageStatesTheKeysAsTheRunReadThemInvocationTest {

    private static final String PAGE = "translations.html";

    /** A similarity floor no number can be read from. */
    private static final String NOT_A_NUMBER = "high";

    /** Preferred languages one piece of which is not two letters, so none of it is read. */
    private static final String NOT_A_CODE = "english";

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

    @Test
    @Story("The page the similarity floor is read off")
    @DisplayName("Both keys unreadable when the scoring work was started and unset by the time the page is written: the page calls both unreadable")
    void thePageStatesBothKeysAsTheScoringRunReadThem(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.createDirectories(root.resolve("en"));
        Files.createDirectories(root.resolve("zh"));
        Files.writeString(root.resolve("en/guide.txt"), "a guide written in English");
        Files.writeString(root.resolve("zh/guide.txt"), "一份用中文写的指南");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor("1.0", "set by this test, so stage 4's gate is open")
                .embeddingModel("qwen3-embedding:0.6b", "set by this test, so the embedding-model gate is open")
                .translationSimilarityFloor(NOT_A_NUMBER, "set by this test, so the floor is unreadable")
                .preferredLanguages(NOT_A_CODE, "set by this test, so the languages are unreadable")
                .build());

        cli.run("run", root.toString());

        Profile after = profileStore.load();
        claim(
                "the profile was saved during the translation step with both keys unset, so by the time the page"
                        + " was written neither read as unreadable any more",
                () -> assertThat(List.of(
                                after.translationSimilarityFloor().reading(), after.preferredLanguages().reading()))
                        .containsExactly(new NumericValue.Unset(), new LanguagesValue.Unset()));
        String page = Files.readString(workingDirectory.resolve(PAGE));
        claim(
                "the page states both keys as the scoring work read them when it was started: the similarity floor"
                        + " unreadable, so no likeness is applied, and the preferred languages unreadable, so nothing"
                        + " is removed",
                () -> assertThat(page)
                        .contains("<li>translationSimilarityFloor: unreadable, so no likeness is applied</li>")
                        .contains("<li>preferredLanguages: unreadable, so nothing is removed</li>"));
    }

    /**
     * The profile store of the whole job: the first time it is loaded inside the translation step, it first saves
     * the profile with {@code translationSimilarityFloor} and {@code preferredLanguages} unset, every other key as
     * it stood.
     */
    @TestConfiguration
    static class SavedOnceTheTranslationStepStarts {

        @Bean
        @Primary
        ProfileStore savedOnceTheTranslationStepStarts(@Value("${vespera.working-dir}") Path workingDirectory) {
            return new ProfileStore(workingDirectory) {

                private boolean saved;

                @Override
                public Profile load() {
                    StepContext step = StepSynchronizationManager.getContext();
                    if (!saved && step != null && StepNames.TRANSLATION.equals(step.getStepName())) {
                        saved = true;
                        save(ProfileFixture.profileFrom(super.load())
                                .translationSimilarityFloor(null, null)
                                .preferredLanguages(null, null)
                                .build());
                    }
                    return super.load();
                }
            };
        }
    }
}
