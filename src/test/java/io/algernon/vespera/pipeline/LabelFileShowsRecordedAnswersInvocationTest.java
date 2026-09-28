package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
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

    /** A floor of 1.0 opens stage 4's gate, the way every stage-5 invocation fixture does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 and the steps behind it open. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The document each test answers as relevant first. */
    private static final String FIRST_DOCUMENT = "first.txt";

    /** The document answered as not relevant, where a test answers it at all. */
    private static final String SECOND_DOCUMENT = "second.txt";

    /** The document no test answers, so it shows what an unanswered question looks like after all this. */
    private static final String THIRD_DOCUMENT = "third.txt";

    /** Every document the fixture corpus holds, and so every question the sample can ask. */
    private static final List<String> EVERY_DOCUMENT = List.of(FIRST_DOCUMENT, SECOND_DOCUMENT, THIRD_DOCUMENT);

    /** How the file writes an answer nobody has given. */
    private static final String BLANK = "null";

    /** What the file used to say when it had nothing answered, and what a file of recorded answers must not. */
    private static final String NO_ANSWERS_YET = "no answers in it yet";

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
    @Story("The label file shows the answers already given")
    @DisplayName("After answers are recorded, the next run writes them into the file, and leaves the rest blank")
    void theNextRunWritesTheRecordedAnswersIntoTheFile(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        claim(
                "the fixture's three documents are all asked about, so each of the three answers below"
                        + " has an entry to appear in",
                () -> assertThat(pathsInTheFile()).containsExactlyInAnyOrderElementsOf(EVERY_DOCUMENT));
        answerInTheFile(FIRST_DOCUMENT, true);
        answerInTheFile(SECOND_DOCUMENT, false);
        cli.run("label");

        cli.run("run", root.toString());

        claim("the second run reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the document answered as relevant shows that answer in the rewritten file, rather than"
                        + " asking the person who gave it for it again",
                () -> assertThat(answerShownFor(FIRST_DOCUMENT)).isEqualTo("true"));
        claim(
                "the document answered as not relevant shows that answer too: both answers are shown,"
                        + " not only the ones that keep a document",
                () -> assertThat(answerShownFor(SECOND_DOCUMENT)).isEqualTo("false"));
        claim(
                "and the document nobody answered is still blank, waiting for its answer",
                () -> assertThat(answerShownFor(THIRD_DOCUMENT)).isEqualTo(BLANK));
    }

    @Test
    @Story("A changed answer replaces the one recorded, and says so")
    @DisplayName("An answer changed in the file replaces the recorded one, and the report names the document with both answers")
    void aChangedAnswerReplacesTheRecordedOneAndIsNamed(CapturedOutput output, @TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerInTheFile(FIRST_DOCUMENT, true);
        cli.run("label");
        cli.run("run", root.toString());
        answerInTheFile(FIRST_DOCUMENT, false);

        String said = whatLabelSays(output);

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the new answer replaced the old one: the file is the person's own statement, and changing"
                        + " an answer in it is how they correct one",
                () -> assertThat(answerRecordedFor(FIRST_DOCUMENT, seeds)).isEqualTo(false));
        claim(
                "the report counts it as the one changed answer, with nothing new and nothing unchanged,"
                        + " because the file carried that one answer and no other",
                () -> assertThat(said).contains("0 new, 1 changed, 0 unchanged"));
        claim(
                "and it names the document with the answer it had and the answer it has now, so a"
                        + " correction is never made without the person seeing it",
                () -> assertThat(said).contains(FIRST_DOCUMENT + " was true, now false"));
    }

    @Test
    @Story("A blank answer takes nothing back")
    @DisplayName("Blanking an answer in the file leaves the recorded answer standing")
    void aBlankedAnswerLeavesTheRecordedOneStanding(CapturedOutput output, @TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerInTheFile(FIRST_DOCUMENT, true);
        cli.run("label");
        cli.run("run", root.toString());
        leaveBlankInTheFile(FIRST_DOCUMENT);
        answerInTheFile(SECOND_DOCUMENT, false);

        String said = whatLabelSays(output);

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the blanked answer is still recorded as it was: blank means not answered in this file,"
                        + " and a person part-way through a file would otherwise lose answers from an"
                        + " earlier sitting",
                () -> assertThat(answerRecordedFor(FIRST_DOCUMENT, seeds)).isEqualTo(true));
        claim(
                "the report counts the one answer the file carried as new, and nothing as changed",
                () -> assertThat(said).contains("1 new, 0 changed, 0 unchanged"));
        claim(
                "and it counts the blanked entry with the untouched one as the two questions still"
                        + " blank, never as an answer taken back",
                () -> assertThat(said)
                        .contains("2 question(s) are still blank")
                        .doesNotContain(FIRST_DOCUMENT + " was"));
    }

    @Test
    @Story("A file of answers already recorded is not a file with no answers")
    @DisplayName("Recording a rewritten file nobody edited records nothing new, and says so without claiming it holds no answers")
    void anUneditedFileRecordsNothingNewAndSaysSo(CapturedOutput output, @TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerInTheFile(FIRST_DOCUMENT, true);
        answerInTheFile(SECOND_DOCUMENT, false);
        cli.run("label");
        cli.run("run", root.toString());

        int before = output.getAll().length();
        String said = whatLabelSays(output);
        String everythingItPrinted = output.getAll().substring(before);

        claim(
                "the invocation reports success: every answer in the file is one already recorded, and"
                        + " that is not a mistake to stop the person for",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the report says the file's two answers are unchanged and none is new",
                () -> assertThat(said).contains("2 answer(s) unchanged, none new"));
        claim(
                "and nothing it printed says the file has no answers, which it plainly does",
                () -> assertThat(everythingItPrinted).doesNotContain(NO_ANSWERS_YET));
        claim(
                "both answers stand as they were given",
                () -> {
                    assertThat(answerRecordedFor(FIRST_DOCUMENT, seeds)).isEqualTo(true);
                    assertThat(answerRecordedFor(SECOND_DOCUMENT, seeds)).isEqualTo(false);
                });
    }

    @Test
    @Story("A changed answer replaces the one recorded, and says so")
    @DisplayName("A file mixing a new answer with one already recorded counts both, the recorded one as unchanged")
    void aNewAnswerBesideARecordedOneCountsBoth(CapturedOutput output, @TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerInTheFile(FIRST_DOCUMENT, true);
        cli.run("label");
        cli.run("run", root.toString());
        answerInTheFile(SECOND_DOCUMENT, false);

        String said = whatLabelSays(output);

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the report's total counts every answer the file carried, the one shown back to the person"
                        + " as well as the one they added, so the total matches the file they are looking at",
                () -> assertThat(said).contains("recorded 2 answer(s) about the seed set at "));
        claim(
                "and it splits that total into the one new answer and the one unchanged, with nothing changed",
                () -> assertThat(said).contains("1 new, 0 changed, 1 unchanged"));
        claim(
                "both answers are recorded as the file gives them",
                () -> {
                    assertThat(answerRecordedFor(FIRST_DOCUMENT, seeds)).isEqualTo(true);
                    assertThat(answerRecordedFor(SECOND_DOCUMENT, seeds)).isEqualTo(false);
                });
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

    @Test
    @Story("A file of answers about one seed set is refused under another")
    @DisplayName("A file that names no seed set is read against the seed folder the profile names now, as it always was")
    void aFileNamingNoSeedSetIsReadAgainstTheProfilesSeedSet(
            @TempDir Path root, @TempDir Path seeds, @TempDir Path otherSeeds) throws IOException {
        aScoredCorpus(root, seeds);
        String generated = Files.readString(labelFile());
        claim(
                "the file as written names its seed set, so removing that line below is what makes it a"
                        + " file written before ADR-169",
                () -> assertThat(generated).containsPattern("(?m)^generatedUnderSeedSet: "));
        Files.writeString(labelFile(), generated.replaceFirst("(?m)^generatedUnderSeedSet: .*\\R", ""));
        answerInTheFile(FIRST_DOCUMENT, true);
        Files.writeString(otherSeeds.resolve("seed.txt"), "a different seed document");
        nameTheSeedFolder(otherSeeds);

        cli.run("label");

        claim(
                "the invocation reports success: a file with no seed set named was written blank, so every"
                        + " answer in it was typed by the operator, and refusing it would stop someone who"
                        + " upgraded part-way through a file",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the answer is recorded against the seed set the profile names now, which is how such a"
                        + " file was always read",
                () -> assertThat(answerRecordedFor(FIRST_DOCUMENT, otherSeeds)).isEqualTo(true));
        claim(
                "and nothing is recorded against the seed set the file was generated under, since the file"
                        + " no longer says which that was",
                () -> assertThat(answerRecordedFor(FIRST_DOCUMENT, seeds)).isNull());
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
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** What {@code vespera label} printed, and only that: the run before it printed to the same streams. */
    private String whatLabelSays(CapturedOutput output) {
        int before = output.getOut().length();
        cli.run("label");
        return output.getOut().substring(before);
    }

    /** Writes an answer into one entry of the label file, the way a person editing it would. */
    private void answerInTheFile(String path, boolean relevant) throws IOException {
        entryFor(path, entry -> entry.put("relevant", relevant));
    }

    /** Empties one entry's answer, the way a person deleting what the file showed them would. */
    private void leaveBlankInTheFile(String path) throws IOException {
        entryFor(path, entry -> entry.putNull("relevant"));
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

    /** The answer the label file shows for {@code path}, as the file spells it. */
    private String answerShownFor(String path) throws IOException {
        for (JsonNode entry : YAML.readTree(Files.readString(labelFile())).path("documents")) {
            if (path.equals(entry.path("path").asString())) {
                JsonNode relevant = entry.path("relevant");
                return relevant.isMissingNode() || relevant.isNull() ? BLANK : relevant.asString();
            }
        }
        return "(no entry)";
    }

    private List<String> pathsInTheFile() throws IOException {
        List<String> paths = new java.util.ArrayList<>();
        for (JsonNode entry : YAML.readTree(Files.readString(labelFile())).path("documents")) {
            paths.add(entry.path("path").asString());
        }
        return paths;
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
