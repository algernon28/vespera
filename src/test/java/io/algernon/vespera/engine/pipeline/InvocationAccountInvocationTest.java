package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * What an invocation's report keeps of what its account kept for a hosted model to read (ADR-198, #424, ADR-362
 * sections 3 and 4), reached through a real invocation: what the copy in the {@code logs} folder holds, and above all
 * what it never holds. The account itself is no longer written.
 *
 * <p><b>The guarantee is the point.</b> Every directory name, file name and text in the fixture carries
 * {@link #MARKER}, a word nobody would write into a diagnostic by chance, and so does the file the
 * converter fails on, the folder the seed set lives in and a key written into a profile that does not
 * load. The claim is that the marker occurs nowhere in the report's copy. The report names the places an
 * invocation was pointed at (ADR-362 section 3), so no claim of absence is made of them. A test that only looked for
 * the members it expected would pass with a leak in every other member.
 *
 * <p>The copy is found by listing the working directory's {@code logs} folder for {@code report-*}, never by a
 * name the test computes: one working directory serves the whole class, so what a test claims about is the file its
 * own invocation added.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("424")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class InvocationAccountInvocationTest {

    /** What every private name and text in the fixture carries, so one search finds any leak of any of them. */
    private static final String MARKER = "ZXQ-the-operators-private-word";


    /** The folder, inside the corpus root, every corpus file is in, and which carries the marker. */
    private static final String MARKED_FOLDER = MARKER + "-folder";

    /** The two corpus files the converter reads normally, and which carry the marker in name and text. */
    private static final List<String> MARKED_READABLE_FILES =
            List.of(MARKER + "-first.txt", MARKER + "-second.txt");

    /** The corpus file the scripted converter fails on while blaming itself, so a fault row is written. */
    private static final String FAULTED = SeedScriptedExtractionBeans.CONVERTER_FAULT;

    /** The corpus files the fixture writes: the two readable ones and the one the converter faults on. */
    private static final int CORPUS_FILES = MARKED_READABLE_FILES.size() + 1;

    /** The one file the converter faulted on, and so the one extraction-failed verdict this fixture earns. */
    private static final long ONE_FAULTED_FILE = 1;

    /** The stage whose progress line this fixture can read: one seed, recorded, so one of one. */
    private static final String SEEDS_RECORDED_LINE =
            "Stage 5a (seed extraction, seeds recorded): 1 of 1 (100%)";

    @TempDir
    static Path workingDirectory;

    /** The working directory alone: no {@code vespera.account-dir}, which is no longer read (ADR-270 section 3). */
    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    /** The working directory is static, so a profile left unreadable by one method would break the next. */
    @BeforeEach
    @AfterEach
    void putAProfileThatLoadsBack() {
        profileStore.save(ProfileFixture.profile().build());
    }

    @Test
    @Story("A diagnosis never needs a document's name or words")
    @DisplayName("The marker in every name and text of a whole invocation occurs nowhere in its report's copy")
    void theMarkerNeverReachesTheReport(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        List<Path> before = TheReportFile.copiesIn(workingDirectory);

        cli.run("run", root.toString());

        List<String> written = copiesWrittenSince(before);
        claim(
                "the invocation reached its end, so the copy being checked covers the whole of a"
                        + " successful run: stage by stage, the faulted file, the groups drawn from marked names",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the invocation wrote exactly one copy, so there is something to check the marker against"
                        + " -- an invocation that wrote none would pass every absence claim below with nothing read",
                () -> assertThat(written).hasSize(1));
        for (String forbidden : List.of(MARKER, MARKED_FOLDER, FAULTED)) {
            claim(
                    "the copy does not contain '" + forbidden + "', in any letter case, which the invocation"
                            + " was given or read",
                    () -> assertThat(written.getFirst()).doesNotContainIgnoringCase(forbidden));
        }
    }

    @Test
    @Story("A report says which steps ran, how they ended and how long each took")
    @DisplayName("The report names every step of the job that ran, with its state and its duration")
    void theReportNamesEveryStepAndHowItEnded(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        List<Path> before = TheReportFile.copiesIn(workingDirectory);

        cli.run("run", root.toString());

        JsonNode report = theOneCopyWrittenSince(before);
        for (String step : List.of(
                StepNames.CENSUS,
                StepNames.BYTE_LEVEL_REDUCTION,
                StepNames.EXTRACTION,
                StepNames.CONTENT_CENSUS,
                StepNames.SEED_EXTRACTION,
                StepNames.ARRANGEMENT)) {
            claim(
                    "the report has the " + step + " step, carrying that it finished and how long it took",
                    () -> {
                        JsonNode each = TheReportFile.step(report, step);
                        assertThat(each.path("state").asString()).isEqualTo("finished");
                        assertThat(each.path("seconds").isIntegralNumber()).isTrue();
                    });
        }
        claim(
                "the report says the invocation ended, and how it ended",
                () -> {
                    assertThat(report.path("invocation").path("state").asString()).isEqualTo("ended");
                    assertThat(report.path("invocation").path("outcome").asString()).isEqualTo("completed");
                });
    }

    @Test
    @Story("A report carries the counts a run is diagnosed from")
    @DisplayName("The report counts occurrences, verdicts by kind and faults by category, and names none of them")
    void theReportCarriesCounts(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        List<Path> before = TheReportFile.copiesIn(workingDirectory);

        cli.run("run", root.toString());

        JsonNode counts = theOneCopyWrittenSince(before).path("counts");
        claim(
                "it counts the " + CORPUS_FILES + " occurrences the walk recorded, as a number and nothing else",
                () -> assertThat(counts.path("occurrences").asLong()).isEqualTo(CORPUS_FILES));
        claim(
                "it counts the " + ONE_FAULTED_FILE + " extraction-failed verdict the faulted file earned, by"
                        + " the kind the verdict vocabulary names",
                () -> assertThat(counts.path("verdicts").path("extraction-failed").asLong()).isEqualTo(ONE_FAULTED_FILE));
        claim(
                "it counts the " + ONE_FAULTED_FILE + " extraction fault by the category the converter reported,"
                        + " and holds none of the converter's message about the file",
                () -> {
                    assertThat(counts.path("extractionFaults").path("internal").asLong()).isEqualTo(ONE_FAULTED_FILE);
                    assertThat(counts.toString()).doesNotContain(SeedScriptedExtractionBeans.CONVERTER_FAULT_MESSAGE);
                });
        claim(
                "it counts at least one group arranged, drawn from documents whose names carry the marker, and"
                        + " names none of them",
                () -> assertThat(counts.path("clusters").asLong()).isPositive());
    }

    @Test
    @Story("A report carries each loop's progress, which is the label its stage spelled and counts")
    @DisplayName("The report carries the last progress line of a step, word for word")
    void theReportCarriesProgressLines(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        List<Path> before = TheReportFile.copiesIn(workingDirectory);

        cli.run("run", root.toString());

        JsonNode report = theOneCopyWrittenSince(before);
        claim(
                "the seed recording loop's one line is in the report as it was written to the log: its"
                        + " label and the count, one of one",
                () -> assertThat(TheReportFile.step(report, StepNames.SEED_EXTRACTION).path("progress").path("line").asString())
                        .isEqualTo(SEEDS_RECORDED_LINE));
    }

    @Test
    @Story("A failed step is named by the type of what failed, and its message stays in the operator's log")
    @DisplayName("A step that fails on a message naming a marked file is written by exception type alone")
    void aFailureIsKeptAsItsTypeAndNeverItsMessage(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        Files.writeString(profileStore.file(), MARKER + "-key-written-flat: a value\n");
        List<Path> before = TheReportFile.copiesIn(workingDirectory);

        cli.run("run", root.toString());

        List<String> written = copiesWrittenSince(before);
        JsonNode report = TheReportFile.parse(written.getFirst());
        claim(
                "the invocation failed, because a profile that does not load stops the census step: the"
                        + " exception it throws names the profile's file and the key, which is the message"
                        + " this test is about",
                () -> assertThat(cli.getExitCode()).isNotZero());
        List<JsonNode> faults = new ArrayList<>();
        report.path("faults").forEach(faults::add);
        claim(
                "the report names the failed step and the exception's own type, which is what says what failed",
                () -> assertThat(faults)
                        .anyMatch(fault -> fault.path("step").asString().equals(StepNames.CENSUS)
                                && fault.path("type").asString()
                                        .equals("io.algernon.vespera.engine.profile.MisshapenProfileException")));
        claim(
                "the report ends with the invocation having failed, and has none of the marker the message carried",
                () -> {
                    assertThat(report.path("invocation").path("outcome").asString()).isEqualTo("failed");
                    assertThat(written.getFirst()).doesNotContain(MARKER);
                });
    }

    /** Marks every directory, file name and text the invocation is given, and one file the converter faults on. */
    private void aMarkedCorpus(Path root, Path seeds) throws IOException {
        Path folder = Files.createDirectory(root.resolve(MARKED_FOLDER));
        for (String name : MARKED_READABLE_FILES) {
            Files.writeString(folder.resolve(name), "the text of " + name + " says " + MARKER);
        }
        Files.writeString(folder.resolve(FAULTED), "a file the converter faults on, and " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed that says " + MARKER);
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and no threshold chosen yet. */
    private void profileNaming(Path seeds) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /** The text of each copy of a report the working directory holds now and did not hold in {@code before}. */
    private List<String> copiesWrittenSince(List<Path> before) throws IOException {
        List<String> written = new ArrayList<>();
        for (Path file : TheReportFile.copiesIn(workingDirectory)) {
            if (!before.contains(file)) {
                written.add(Files.readString(file));
            }
        }
        return written;
    }

    /** The one copy this invocation added, failing with a plain message when it added none or several. */
    private JsonNode theOneCopyWrittenSince(List<Path> before) throws IOException {
        List<String> written = copiesWrittenSince(before);
        claim(
                "the invocation wrote exactly one copy of its report, in the working directory's logs folder",
                () -> assertThat(written).hasSize(1));
        return TheReportFile.parse(written.getFirst());
    }
}
