package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The account an invocation writes for a hosted model to read (ADR-198, #424), reached through a real
 * invocation: what it holds, and above all what it never holds.
 *
 * <p><b>The guarantee is the point.</b> Every directory name, file name and text in the fixture carries
 * {@link #MARKER}, a word nobody would write into a diagnostic by chance, and so does the file the
 * converter fails on, the folder the seed set lives in and a key written into a profile that does not
 * load. The claim is that the marker occurs nowhere in the account, and neither do the three directories
 * the invocation was pointed at. A test that only looked for the lines it expected would pass with a
 * leak in every other line.
 *
 * <p>The account is found by listing the folder {@code vespera.account-dir} names, which is a folder of
 * its own and no working directory, for {@value #ACCOUNT_PREFIX}*, never by a name the test computes: one
 * folder serves the whole class, so what a test claims about is the file its own invocation added.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The invocation account")
@Issue("424")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
class InvocationAccountInvocationTest {

    /** What every private name and text in the fixture carries, so one search finds any leak of any of them. */
    private static final String MARKER = "ZXQ-the-operators-private-word";

    /** How an account's file name starts, in the working directory beside the log. */
    private static final String ACCOUNT_PREFIX = "invocation-account-";

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
    private static final int ONE_FAULTED_FILE = 1;

    /** A floor of 1.0 opens stage 4's gate, as every invocation test reaching stage 5 sets it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for, so the scoring half of the job opens. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The stage whose progress line this fixture can read: one seed, recorded, so one of one. */
    private static final String SEEDS_RECORDED_LINE =
            "Stage 5a (seed extraction, seeds recorded): 1 of 1 (100%)";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path accountDirectory;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.account-dir", accountDirectory::toString);
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
    @DisplayName("The marker in every name and text of a whole invocation occurs nowhere in its account")
    void theMarkerNeverReachesTheAccount(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        List<Path> before = accounts();

        cli.run("run", root.toString());

        List<String> written = accountsWrittenSince(before);
        claim(
                "the invocation reached its end, so the account being checked covers the whole of a"
                        + " successful run: stage by stage, the faulted file, the groups drawn from marked names",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the invocation wrote exactly one account, so there is something to check the marker against"
                        + " -- an invocation that wrote none would pass every absence claim below with nothing read",
                () -> assertThat(written).hasSize(1));
        for (String forbidden : List.of(
                MARKER, root.toString(), seeds.toString(), workingDirectory.toString(), MARKED_FOLDER, FAULTED)) {
            claim(
                    "the account does not contain '" + forbidden + "', in any letter case, which the invocation"
                            + " was given or read",
                    () -> assertThat(written.getFirst()).doesNotContainIgnoringCase(forbidden));
        }
    }

    @Test
    @Story("An account says which steps ran, how they ended and how long each took")
    @DisplayName("The account names every step of the job that ran, with its outcome and its duration")
    void theAccountNamesEveryStepAndHowItEnded(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        List<Path> before = accounts();

        cli.run("run", root.toString());

        String account = theOneAccountWrittenSince(before);
        for (String step : List.of(
                StepNames.CENSUS,
                StepNames.BYTE_LEVEL_REDUCTION,
                StepNames.EXTRACTION,
                StepNames.CONTENT_CENSUS,
                StepNames.SEED_EXTRACTION,
                StepNames.ARRANGEMENT)) {
            claim(
                    "the account has a line for the " + step + " step carrying how it ended and how long it took",
                    () -> assertThat(linesOf(account))
                            .anyMatch(line -> line.contains(" step name=" + step + " ")
                                    && line.contains("status=COMPLETED")
                                    && line.contains("duration=PT")));
        }
        claim(
                "the account opens with the invocation starting and closes with it ending, with how it ended",
                () -> {
                    assertThat(linesOf(account).getFirst()).contains(" invocation started");
                    assertThat(linesOf(account).getLast()).contains(" invocation ended").contains("status=COMPLETED");
                });
    }

    @Test
    @Story("An account carries the counts a run is diagnosed from")
    @DisplayName("The account counts occurrences, verdicts by kind and faults by category, and names none of them")
    void theAccountCarriesCounts(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        List<Path> before = accounts();

        cli.run("run", root.toString());

        String account = theOneAccountWrittenSince(before);
        claim(
                "it counts the " + CORPUS_FILES + " occurrences the walk recorded, as a number and nothing else",
                () -> assertThat(account).contains("counts occurrences=" + CORPUS_FILES));
        claim(
                "it counts the " + ONE_FAULTED_FILE + " extraction-failed verdict the faulted file earned, by"
                        + " the kind the verdict vocabulary names",
                () -> assertThat(account)
                        .contains("counts verdict kind=EXTRACTION_FAILED count=" + ONE_FAULTED_FILE));
        claim(
                "it counts the " + ONE_FAULTED_FILE + " extraction fault by the category the converter reported,"
                        + " and writes none of the converter's message about the file",
                () -> assertThat(account)
                        .contains("counts extraction-fault category=internal count=" + ONE_FAULTED_FILE)
                        .doesNotContain(SeedScriptedExtractionBeans.CONVERTER_FAULT_MESSAGE));
        claim(
                "it counts at least one group arranged, drawn from documents whose names carry the marker, and"
                        + " names none of them",
                () -> assertThat(account).containsPattern("counts clusters arranged=[1-9][0-9]* "));
    }

    @Test
    @Story("An account carries each loop's progress, which is the label its stage spelled and counts")
    @DisplayName("The account carries the progress lines the invocation wrote, word for word")
    void theAccountCarriesProgressLines(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        List<Path> before = accounts();

        cli.run("run", root.toString());

        String account = theOneAccountWrittenSince(before);
        claim(
                "the seed recording loop's one line is in the account as it was written to the log: its"
                        + " label and the count, one of one",
                () -> assertThat(linesOf(account)).anyMatch(line -> line.endsWith(" progress " + SEEDS_RECORDED_LINE)));
        claim(
                "and every progress line in it has that shape and no other, so what was withheld is not here",
                () -> assertThat(linesOf(account).stream().filter(line -> line.contains(" progress ")))
                        .allMatch(line -> line.matches(".* progress Stage [0-9][a-z]? \\([a-z0-9, -]+\\): [0-9,]+"
                                + "( of [0-9,]+ \\([0-9]+%\\)| so far)")));
    }

    @Test
    @Story("A failed step is named by the type of what failed, and its message stays in the operator's log")
    @DisplayName("A step that fails on a message naming a marked file is written by exception type alone")
    void aFailureIsKeptAsItsTypeAndNeverItsMessage(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds);
        Files.writeString(profileStore.file(), MARKER + "-key-written-flat: a value\n");
        List<Path> before = accounts();

        cli.run("run", root.toString());

        String account = theOneAccountWrittenSince(before);
        claim(
                "the invocation failed, because a profile that does not load stops the census step: the"
                        + " exception it throws names the profile's file and the key, which is the message"
                        + " this test is about",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the account names the failed step and the exception's own type, which is what says what failed",
                () -> assertThat(linesOf(account))
                        .anyMatch(line -> line.contains(" failure step=" + StepNames.CENSUS + " ")
                                && line.contains("type=io.algernon.vespera.profile.MisshapenProfileException")));
        claim(
                "the account ends with the invocation having failed, and has none of the marker the message carried",
                () -> {
                    assertThat(linesOf(account).getLast()).contains(" invocation ended").contains("status=FAILED");
                    assertThat(account).doesNotContain(MARKER).doesNotContain(workingDirectory.toString());
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
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** Every account in the account folder now, found by its prefix. */
    private List<Path> accounts() throws IOException {
        try (Stream<Path> files = Files.list(accountDirectory)) {
            return files.filter(file -> file.getFileName().toString().startsWith(ACCOUNT_PREFIX))
                    .toList();
        }
    }

    /** The text of each account the working directory holds now and did not hold in {@code before}. */
    private List<String> accountsWrittenSince(List<Path> before) throws IOException {
        List<String> written = new java.util.ArrayList<>();
        for (Path file : accounts()) {
            if (!before.contains(file)) {
                written.add(Files.readString(file));
            }
        }
        return written;
    }

    /** The one account this invocation added, failing with a plain message when it added none or several. */
    private String theOneAccountWrittenSince(List<Path> before) throws IOException {
        List<String> written = accountsWrittenSince(before);
        claim(
                "the invocation wrote exactly one account, in the account folder",
                () -> assertThat(written).hasSize(1));
        return written.getFirst();
    }

    private static List<String> linesOf(String account) {
        return account.lines().toList();
    }
}
