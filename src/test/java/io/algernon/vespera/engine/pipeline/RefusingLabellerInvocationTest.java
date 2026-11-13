package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.LabelQuestion;
import io.algernon.vespera.engine.embedding.RelevanceLabeller;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A labeller that refuses to be used ends {@code vespera label --auto} before anything is read or asked
 * (ADR-197 §6): exit 1, the reason named, no question put, and the label file byte for byte as it was. A profile
 * saying the models run in the cloud ends it at its start, before the labeller is asked anything (ADR-384 §4).
 *
 * <p>The labeller is a scripted double that refuses on the ground a hosted endpoint would. No test here
 * reaches a model.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AutoLabelling.class, RefusingLabellerInvocationTest.Beans.class})
@ExtendWith(OutputCaptureExtension.class)
@Epic("Relevance")
@Feature("Local labelling")
@Issue("423")
@Link(name = "ADR-197", url = Adr.A_LOCAL_MODEL_LABELS_AND_THE_FLOOR_IS_A_RULE, type = "adr")
class RefusingLabellerInvocationTest {

    private static final String HOSTED_URL = "https://hosted.example/v1";
    private static final String LABEL_FILE = "relevance-labels.yaml";

    static final AtomicInteger QUESTIONS = new AtomicInteger();

    /** How many times the labeller was asked whether it may be used. */
    static final AtomicInteger REFUSALS_ASKED = new AtomicInteger();

    /** The line {@code vespera label --auto} prints where the profile says the models run in the cloud. */
    private static final String IN_THE_CLOUD = "vespera label --auto recorded nothing: modelsRunOn is cloud, and models"
            + " that run in the cloud are not supported yet -- set modelsRunOn to local in profile.yaml, or leave it"
            + " unset, and the models run on this machine";

    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller refusingLabeller() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return "refusing-labeller";
                }

                @Override
                public Optional<String> refusal() {
                    REFUSALS_ASKED.incrementAndGet();
                    return Optional.of("the chat model's endpoint is " + HOSTED_URL + ", which is not this machine");
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    QUESTIONS.incrementAndGet();
                    return Optional.of(true);
                }
            };
        }
    }

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

    @BeforeEach
    void forgetQuestions() {
        QUESTIONS.set(0);
        REFUSALS_ASKED.set(0);
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("A labeller that refuses ends the command with the reason, no question and the file untouched")
    void aRefusingLabellerEndsTheCommandBeforeAnything(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        byte[] before = Files.readAllBytes(workingDirectory.resolve(LABEL_FILE));

        cli.run("label", "--auto");

        claim("the invocation exits 1", () -> assertThat(cli.getExitCode()).isEqualTo(1));
        claim(
                "the URL it refused is named, so the operator can see what was wrong",
                () -> assertThat(output.getAll()).contains(HOSTED_URL));
        claim("no question was put to the labeller", () -> assertThat(QUESTIONS.get()).isZero());
        claim(
                "the label file is byte for byte as it was",
                () -> assertThat(Files.readAllBytes(workingDirectory.resolve(LABEL_FILE))).isEqualTo(before));
    }

    @Test
    @Story("Where the models run is the operator's to say")
    @DisplayName("A profile saying the models run in the cloud ends the command at its start, the labeller asked nothing and the file untouched")
    @Link(name = "ADR-384", url = Adr.WHETHER_THE_MODELS_RUN_LOCALLY_OR_IN_THE_CLOUD_IS_THE_PROFILES_TO_SAY, type = "adr")
    void cloudInTheProfileEndsLabelAutoAtItsStart(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        byte[] before = Files.readAllBytes(workingDirectory.resolve(LABEL_FILE));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .modelsRunOn("cloud", "set by this test")
                .build());
        int beforeErr = output.getErr().length();
        String onStandardError;
        try {
            cli.run("label", "--auto");
            onStandardError = output.getErr().substring(beforeErr);
        } finally {
            profileStore.save(ProfileFixture.profileFrom(profileStore.load()).modelsRunOn(null, null).build());
        }

        claim("the invocation exits 1", () -> assertThat(cli.getExitCode()).isEqualTo(1));
        claim(
                "standard error carries the refusal once, whole: \"" + IN_THE_CLOUD + "\"",
                () -> assertThat(onStandardError.lines().filter(IN_THE_CLOUD::equals)).hasSize(1));
        claim(
                "the labeller was not asked whether it may be used, nor asked a question, so its own refusal is not"
                        + " named",
                () -> {
                    assertThat(REFUSALS_ASKED.get()).isZero();
                    assertThat(QUESTIONS.get()).isZero();
                    assertThat(onStandardError).doesNotContain(HOSTED_URL);
                });
        claim(
                "the label file is byte for byte as it was",
                () -> assertThat(Files.readAllBytes(workingDirectory.resolve(LABEL_FILE))).isEqualTo(before));
    }
}
