package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.LabelQuestion;
import io.algernon.vespera.embedding.RelevanceLabeller;
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
 * (ADR-197 §6): exit 1, the reason named, no question put, and the label file byte for byte as it was.
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
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("A labeller that refuses ends the command with the reason, no question and the file untouched")
    void aRefusingLabellerEndsTheCommandBeforeAnything(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor("1.0", "set by this test, so stage 4's gate is open")
                .embeddingModel("qwen3-embedding:0.6b", "set by this test, so gate 3 is open")
                .build());
        cli.run("run", root.toString());
        byte[] before = Files.readAllBytes(workingDirectory.resolve(LABEL_FILE));

        cli.run("label", "--auto", "--root", root.toString());

        claim("the invocation exits 1", () -> assertThat(cli.getExitCode()).isEqualTo(1));
        claim(
                "the URL it refused is named, so the operator can see what was wrong",
                () -> assertThat(output.getAll()).contains(HOSTED_URL));
        claim("no question was put to the labeller", () -> assertThat(QUESTIONS.get()).isZero());
        claim(
                "the label file is byte for byte as it was",
                () -> assertThat(Files.readAllBytes(workingDirectory.resolve(LABEL_FILE))).isEqualTo(before));
    }
}
