package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code vespera label} writes the progress page too, with what it counted and nothing it was given (ADR-266 section
 * 3).
 *
 * <p>The working directory is static, so each test removes the page an earlier one left before its own label
 * command, after the run that writes the label file. Every name and text the invocation is given carries one marked
 * word, so one search finds any of them on the page.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-266", url = Adr.EVERY_INVOCATION_NAMES_AND_OPENS_ITS_PROGRESS_PAGE, type = "adr")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
class LabelWritesTheProgressPageInvocationTest {

    private static final String PAGE = "progress.html";

    /** What a page that reloads itself carries in its head, up to the number of seconds. */
    private static final String RELOAD = "<meta http-equiv=\"refresh\" content=\"";

    private static final String ENDED_RECORDED = "This invocation has ended: recorded.";

    private static final String ENDED_REFUSED = "This invocation has ended: refused.";

    /** The line naming the command the page is about. */
    private static final String THE_LABEL_COMMAND = "<span class=\"label\">Command</span><span class=\"value\">vespera label</span>";

    /** The header of the table of what the label command counted. */
    private static final String ANSWERS_HEADER = ReportPage.headerRow("Answers", "Count");

    /** The fixture corpus is one document, so the label file asks one question. */
    private static final int ONE_ANSWER = 1;

    private static final int NONE = 0;

    private static final String MARKER = "ZXQ-the-operators-private-word";

    private static final String CORPUS_DOCUMENT = MARKER + "-corpus.txt";

    private static final String SEED_DOCUMENT = MARKER + "-seed.txt";

    private static final String LABEL_FILE = "relevance-labels.yaml";

    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

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
    @Story("The label command writes what it recorded")
    @DisplayName("After the label command records one new answer, the page says it recorded, names the command, counts the answers and no longer reloads")
    void theLabelCommandWritesWhatItRecordedAndStopsTheReload(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestionWith(true);
        Files.deleteIfExists(thePagesPath());

        cli.run("label");

        claim("the label command reported success", () -> assertThat(cli.getExitCode()).isZero());
        String page = thePage();
        claim(
                "the page names the label command, says it ended having recorded, and carries no reload",
                () -> assertThat(page).contains(THE_LABEL_COMMAND).contains(ENDED_RECORDED).doesNotContain(RELOAD));
        claim(
                "and it counts, in order, the " + ONE_ANSWER + " answer the file gave as new, and none changed,"
                        + " unchanged or still blank",
                () -> assertThat(page)
                        .contains(ANSWERS_HEADER)
                        .containsSubsequence(
                                count("new", ONE_ANSWER),
                                count("changed", NONE),
                                count("unchanged", NONE),
                                count("still blank", NONE)));
    }

    @Test
    @Story("The label command writes what it recorded")
    @DisplayName("A changed answer is counted as changed, and no name or text the invocation was given, and no folder, is on the page")
    void aChangedAnswerIsCountedAndNoPathOrTextIsOnThePage(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestionWith(true);
        cli.run("label");
        answerEveryQuestionWith(false);
        Files.deleteIfExists(thePagesPath());

        cli.run("label");

        claim("the second label command reported success", () -> assertThat(cli.getExitCode()).isZero());
        String page = thePage();
        claim(
                "the page counts the " + ONE_ANSWER + " answer given the other way as changed, and none as new",
                () -> assertThat(page).contains(count("changed", ONE_ANSWER)).contains(count("new", NONE)));
        for (String forbidden : List.of(
                MARKER,
                root.toString(),
                seeds.toString(),
                Walk.canonicalRoot(seeds).toString(),
                workingDirectory.toString())) {
            claim(
                    "the page does not contain '" + forbidden + "', in any letter case, although the command's own"
                            + " message names the seed folder and the document whose answer changed",
                    () -> assertThat(page).doesNotContainIgnoringCase(forbidden));
        }
    }

    @Test
    @Story("The label command writes what it recorded")
    @DisplayName("A label file with no answer in it is refused, and the page says it was refused, counts nothing and names no path")
    void aRefusedLabelCommandSaysItWasRefusedAndNotWhy(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        Files.deleteIfExists(thePagesPath());

        cli.run("label");

        claim(
                "the label command reported failure, since every answer in the file is still blank",
                () -> assertThat(cli.getExitCode()).isNotZero());
        String page = thePage();
        claim(
                "the page says it ended refused, carries no reload and no table of answers",
                () -> assertThat(page).contains(ENDED_REFUSED).doesNotContain(RELOAD).doesNotContain(ANSWERS_HEADER));
        for (String forbidden : List.of(
                MARKER, LABEL_FILE, root.toString(), seeds.toString(), workingDirectory.toString())) {
            claim(
                    "the page does not contain '" + forbidden + "', in any letter case: the reason the command"
                            + " refused is on the console, not on the page",
                    () -> assertThat(page).doesNotContainIgnoringCase(forbidden));
        }
    }

    /** A row of the table of answers: the count's words, then the count. */
    private static String count(String words, int value) {
        return ReportPage.row(ReportPage.textCell(words), ReportPage.numberCell(value));
    }

    private static Path thePagesPath() {
        return workingDirectory.resolve(PAGE);
    }

    /** The page as the last write left it, failing with a plain message when there is none. */
    private static String thePage() throws IOException {
        claim(
                "the invocation left " + PAGE + " in the working directory, so there is a page to read",
                () -> assertThat(thePagesPath()).isRegularFile());
        return Files.readString(thePagesPath(), StandardCharsets.UTF_8);
    }

    /** Runs the pipeline over one marked document and one marked seed, far enough that a label file is written. */
    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(CORPUS_DOCUMENT), "a corpus document that says " + MARKER);
        Files.writeString(seeds.resolve(SEED_DOCUMENT), "a seed document that says " + MARKER);
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
        cli.run("run", root.toString());
        claim(
                "the run wrote the label file, so there are questions to answer",
                () -> assertThat(workingDirectory.resolve(LABEL_FILE)).isRegularFile());
    }

    /** Sets every answer in the label file, blank or given, to {@code relevant}, as a person editing it would. */
    private static void answerEveryQuestionWith(boolean relevant) throws IOException {
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(
                labels,
                Files.readString(labels).replaceAll("relevant: (null|true|false)", "relevant: " + relevant));
    }
}
