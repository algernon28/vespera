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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code vespera label --auto} through the command an operator types (ADR-197): a local labeller
 * answers the sample's questions, its answers say it set them, the floor is the rule over the labels,
 * and anything a person answered or wrote stays theirs.
 *
 * <p>The labeller is a scripted double that says relevant to everything and counts its questions. No
 * test here reaches a model.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AutoLabelling.class, LocalLabellingInvocationTest.Beans.class})
@Epic("Relevance")
@Feature("Local labelling")
@Issue("423")
@Link(name = "ADR-197", url = Adr.A_LOCAL_MODEL_LABELS_AND_THE_FLOOR_IS_A_RULE, type = "adr")
@ExtendWith(OutputCaptureExtension.class)
class LocalLabellingInvocationTest {

    /** The one document of the sample, and so the one answer the file can carry. */
    private static final long THE_ONE_ANSWER = 1;

    private static final String LABEL_FILE
 = "relevance-labels.yaml";
    private static final String THE_LABELLER = "scripted-labeller";

    /** Questions the scripted labeller was asked since the test began. */
    static final AtomicInteger QUESTIONS = new AtomicInteger();

    /** The opening each of those questions put to the labeller, empty where none could be read. */
    static final List<Optional<String>> OPENINGS_PUT = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller scriptedLabeller() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return THE_LABELLER;
                }

                @Override
                public Optional<String> refusal() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    QUESTIONS.incrementAndGet();
                    OPENINGS_PUT.add(question.opening());
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void forgetQuestions() {
        QUESTIONS.set(0);
        OPENINGS_PUT.clear();
    }

    @Test
    @Story("An operator can overrule any answer")
    @DisplayName("A floor the operator wrote is never overwritten")
    void leavesAFloorAPersonWrote(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor("0.123", "set by the operator, by hand")
                .build());

        cli.run("label", "--auto");

        claim(
                "the value and the provenance are the operator's still",
                () -> {
                    assertThat(profileStore.load().relevanceScoreFloor().value()).isEqualTo("0.123");
                    assertThat(profileStore.load().relevanceScoreFloor().provenance())
                            .isEqualTo("set by the operator, by hand");
                });
    }

    @Test
    @Story("An operator can overrule any answer")
    @DisplayName("An answer a person gave is not asked again and not replaced")
    void aPersonsAnswerIsNotReplaced(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: false"));
        cli.run("label");

        cli.run("label", "--auto");

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the model was not asked about it", () -> assertThat(QUESTIONS.get()).isZero());
        claim(
                "the person's no stands, " + THE_ONE_ANSWER + " answer recorded as no, unmarked",
                () -> {
                    assertThat(irrelevantAnswers(seeds)).isEqualTo(THE_ONE_ANSWER);
                    assertThat(relevantAnswers(seeds)).isZero();
                    assertThat(labeller(seeds)).isNull();
                });
    }

    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
    }

    private String seedSetOf(Path seeds) {
        return io.algernon.vespera.corpus.Walk.canonicalRoot(seeds).toString();
    }

    private long relevantAnswers(Path seeds) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_label WHERE seed_set = ? AND relevant = 1",
                Long.class,
                seedSetOf(seeds));
    }

    private long irrelevantAnswers(Path seeds) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_label WHERE seed_set = ? AND relevant = 0",
                Long.class,
                seedSetOf(seeds));
    }

    private String labeller(Path seeds) {
        return jdbcTemplate
                .queryForList(
                        "SELECT labelled_by FROM relevance_label_provenance WHERE seed_set = ?",
                        String.class,
                        seedSetOf(seeds))
                .stream()
                .findFirst()
                .orElse(null);
    }
}
