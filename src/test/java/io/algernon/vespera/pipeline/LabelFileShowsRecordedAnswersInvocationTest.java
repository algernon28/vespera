package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The label file after answers have been recorded, and {@code vespera label} over a file that shows
 * them (ADR-169, amending ADR-088's blank file; #354).
 *
 * <p>Every claim here goes through the commands an operator types, because the defect was only ever
 * visible across two of them: {@code label} recorded the answers, and the next {@code run} rewrote the
 * file with every one of them blank. The answers are keyed by path and seed set (ADR-097), so each
 * test scopes what it reads by its own seed folder. The working directory is static and shared by
 * every method, so each test starts by removing the two files a previous method's run left there.
 *
 * <p>A file that shows answers is also a file that can carry them somewhere they do not belong: the
 * answers it shows are about the seed set it was written under, and the profile can name another
 * one by the time {@code label} reads it. So the file names its seed set, and a file about one seed
 * set is refused when the profile names another.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Labelling")
@Issue("354")
@Link(name = "ADR-169", url = Adr.THE_LABEL_FILE_SHOWS_THE_ANSWERS_ALREADY_RECORDED, type = "adr")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
@ExtendWith(OutputCaptureExtension.class)
class LabelFileShowsRecordedAnswersInvocationTest {

    /** The document each test answers as relevant first. */
    private static final String FIRST_DOCUMENT = "first.txt";

    /** The document answered as not relevant, where a test answers it at all. */
    private static final String SECOND_DOCUMENT = "second.txt";

    /** The document no test answers, so it shows what an unanswered question looks like after all this. */
    private static final String THIRD_DOCUMENT = "third.txt";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

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
    void forgetWhatAnotherTestsRunWrote() throws IOException {
        // The working directory is static, so a label file written by another method's run would
        // otherwise still be there if this method's run stopped before writing its own.
        Files.deleteIfExists(labelFile());
        Files.deleteIfExists(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME));
    }

    @Test
    @Story("A file of answers about one seed set is refused under another")
    @DisplayName("A file written under one seed folder is refused once the profile names another, and nothing is recorded")
    void aFileWrittenUnderAnotherSeedSetIsRefused(
            CapturedOutput output, @TempDir Path root, @TempDir Path seeds, @TempDir Path otherSeeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerInTheFile(FIRST_DOCUMENT, true);
        cli.run("label");
        cli.run("run", root.toString());
        String generatedUnder = Walk.canonicalRoot(seeds).toString();
        String nowNamed = Walk.canonicalRoot(otherSeeds).toString();
        JsonNode stamp = YAML.readTree(Files.readString(labelFile())).path("generatedUnderSeedSet");
        String stampShown = stamp.isMissingNode() || stamp.isNull() ? null : stamp.asString();
        Files.writeString(otherSeeds.resolve("seed.txt"), "a different seed document");
        nameTheSeedFolder(otherSeeds);

        int before = output.getErr().length();
        cli.run("label");
        String complained = output.getErr().substring(before);

        claim(
                "the invocation reports failure: the file shows an answer given about one seed set, and"
                        + " recording it under the seed set the profile names now would make it an answer"
                        + " to a question nobody asked",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim(
                "it says why, naming both seed sets, so the operator can put the seed folder back or"
                        + " run again to get a file about the one they now name",
                () -> assertThat(complained)
                        .contains("vespera label recorded nothing: the label file was generated under the seed"
                                + " set at " + generatedUnder + ", but the profile now names the seed set at "
                                + nowNamed + "; answers about one seed set are not answers about another, so"
                                + " nothing in it was recorded"));
        claim(
                "nothing was recorded about the seed set the profile names now",
                () -> assertThat(answerRecordedFor(FIRST_DOCUMENT, otherSeeds)).isNull());
        claim(
                "and the answer given about the first seed set stands as it was",
                () -> assertThat(answerRecordedFor(FIRST_DOCUMENT, seeds)).isEqualTo(true));
        claim(
                "the file names the seed set it was written under, the way it names its run, which is what"
                        + " let it be told apart from the one the profile names now",
                () -> assertThat(stampShown).isEqualTo(generatedUnder));
    }

    /** Runs the pipeline far enough that a sample exists and a label file has been written. */
    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(FIRST_DOCUMENT), "a corpus document");
        Files.writeString(root.resolve(SECOND_DOCUMENT), "a second corpus document, worded differently");
        Files.writeString(root.resolve(THIRD_DOCUMENT), "a third corpus document, about something else");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        nameTheSeedFolder(seeds);
        cli.run("run", root.toString());
    }

    /** Writes the profile this fixture runs under, naming {@code seeds} as its seed folder. */
    private void nameTheSeedFolder(Path seeds) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /** Writes an answer into one entry of the label file, the way a person editing it would. */
    private void answerInTheFile(String path, boolean relevant) throws IOException {
        entryFor(path, entry -> entry.put("relevant", relevant));
    }

    private void entryFor(String path, java.util.function.Consumer<ObjectNode> edit) throws IOException {
        JsonNode root = YAML.readTree(Files.readString(labelFile()));
        boolean found = false;
        for (JsonNode entry : root.path("documents")) {
            if (path.equals(entry.path("path").asString())) {
                edit.accept((ObjectNode) entry);
                found = true;
            }
        }
        if (!found) {
            throw new IllegalStateException("the label file has no entry for " + path + " to edit");
        }
        Files.writeString(labelFile(), YAML.writeValueAsString(root));
    }

    /** The answer recorded for {@code path} against this test's own seed folder, or null where none is. */
    private Boolean answerRecordedFor(String path, Path seeds) {
        List<Boolean> answers = jdbcTemplate.query(
                "SELECT relevant FROM relevance_label WHERE path = ? AND seed_set = ?",
                (resultSet, rowNumber) -> resultSet.getBoolean("relevant"),
                path,
                Walk.canonicalRoot(seeds).toString());
        return answers.isEmpty() ? null : answers.getFirst();
    }

    private static Path labelFile() {
        return workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
    }
}
