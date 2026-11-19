package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The lines stage 5's relevance floor and relevance report write call no relevance label an answer, and no document
 * drawn into the label file one put to a person (ADR-424 §1, §3): the reads of the recorded labels, of the scores
 * against them and of the labels a model set, the two counters over them, and the report's finishing line. Driven
 * through the commands an operator types, over a scored corpus of one document the local model labels, and run again
 * with a floor that removes nothing, so that every one of those lines is written.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AutoLabelling.class,
        TheRelevanceReportsLinesCallNoLabelAnAnswerInvocationTest.Beans.class})
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-424", url = Adr.NO_TEXT_FOR_THE_OPERATOR_CALLS_A_LABEL_AN_ANSWER, type = "adr")
@Link(name = "ADR-205", url = Adr.THE_REPORT_COUNTS_THE_ANSWERS_A_MODEL_GAVE, type = "adr")
@Issue("879")
class TheRelevanceReportsLinesCallNoLabelAnAnswerInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    private static final String THE_LABELLER = "scripted-labeller";

    /** The relevance floor's own name, which its lines open with. */
    private static final String THE_FLOOR = "Stage 5e (relevance floor)";

    /** The relevance report's own name, which its lines open with. */
    private static final String THE_REPORT = "Stage 5 (relevance report)";

    /** The counter over every label recorded for the seed set, looked up in this walk. */
    private static final String LABELS_MATCHED = "Stage 5 (relevance report, labels matched)";

    /** The counter over the labels a model set for the seed set, looked up in this walk. */
    private static final String MODEL_LABELS_MATCHED = "Stage 5 (relevance report, model labels matched)";

    /** A floor below every score the scripted runtime gives, so the floor's read is made and nothing is removed. */
    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    /** The one document of the corpus, and so the one label recorded and the one a model set. */
    private static final int ONE_LABEL = 1;

    /** Answer and question in any of their forms, as whole words, and the document put to a person. */
    private static final Pattern AN_ANSWER_A_QUESTION_OR_A_PERSON_ASKED =
            Pattern.compile("(?i)\\b(answer|question)\\w*|put to a person");

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

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /**
     * Disarms the seed fixture's move before as well as after: the hook is static and shared by every class importing
     * the fixture, and class order differs from one machine to the next.
     */
    @BeforeEach
    void captureOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("The relevance floor and the relevance report read, count and draw labels, and put nothing to a person")
    void theFloorAndTheReportSayLabels(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        cli.run("label", "--auto");
        List<String> labelling = lines();
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so the floor's read is made")
                .build());

        logged.list.clear();
        cli.run("run", root.toString());
        List<String> again = lines();

        claim("the premise: the local model labelled the one document, so the report has a label a model set to read",
                () -> assertThat(labelling).anyMatch(line -> line.contains("labelled 1 of 1 document(s) with "
                        + THE_LABELLER + ": 1 relevant, 0 not relevant")));
        claim("the relevance floor says it is reading the recorded labels, and how long reading them took",
                () -> assertThat(again)
                        .contains(THE_FLOOR + " is reading the recorded labels")
                        .anyMatch(line -> line.startsWith(THE_FLOOR + " read the recorded labels in ")));
        claim("the relevance report says it is reading the recorded labels and the scores against the labels",
                () -> assertThat(again)
                        .contains(THE_REPORT + " is reading the recorded labels")
                        .contains(THE_REPORT + " is reading the scores against the labels")
                        .anyMatch(line -> line.startsWith(THE_REPORT + " read the scores against the labels in ")));
        claim("it counts the " + ONE_LABEL + " recorded label looked up in this walk as " + LABELS_MATCHED,
                () -> assertThat(ProgressLines.of(logged.list, LABELS_MATCHED))
                        .containsExactlyElementsOf(ProgressLines.expected(LABELS_MATCHED, ONE_LABEL)));
        claim("it says it is reading the labels a model set, and counts the " + ONE_LABEL + " it looked up as "
                        + MODEL_LABELS_MATCHED,
                () -> {
                    assertThat(again)
                            .contains(THE_REPORT + " is reading the labels a model set")
                            .anyMatch(line -> line.startsWith(THE_REPORT + " read the labels a model set in "));
                    assertThat(ProgressLines.of(logged.list, MODEL_LABELS_MATCHED))
                            .containsExactlyElementsOf(ProgressLines.expected(MODEL_LABELS_MATCHED, ONE_LABEL));
                });
        claim("its finishing line counts the one document drawn to label",
                () -> assertThat(again).anyMatch(line -> line.startsWith(THE_REPORT + " finished under scoring run ")
                        && line.endsWith(" bands, 1 drawn to label")));
        claim("and no line the floor or the report writes says answer or question, in any form, or put to a person",
                () -> assertThat(again)
                        .filteredOn(line -> line.startsWith(THE_FLOOR) || line.startsWith("Stage 5 (relevance report"))
                        .noneMatch(line -> AN_ANSWER_A_QUESTION_OR_A_PERSON_ASKED.matcher(line).find()));
    }

    /** Every operator-facing line written since the list was last cleared, in the order written. */
    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
