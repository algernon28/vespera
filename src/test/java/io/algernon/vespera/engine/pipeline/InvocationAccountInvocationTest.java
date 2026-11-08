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
 * <p>The account is found by listing the working directory's {@code logs} folder, where it is written with
 * no setting (ADR-270), for {@value #ACCOUNT_PREFIX}*, never by a name the test computes: one working
 * directory serves the whole class, so what a test claims about is the file its own invocation added.
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

    /** How an account's file name starts, in the working directory's logs folder beside the log. */
    private static final String ACCOUNT_PREFIX = "invocation-account-";

    /** The working directory's folder every log is written in, the account among them (ADR-270 section 1). */
    private static final String LOGS = "logs";

    /** The folder, inside the corpus root, every corpus file is in, and which carries the marker. */
    private static final String MARKED_FOLDER = MARKER + "-folder";

    /** The two corpus files the converter reads normally, and which carry the marker in name and text. */
    private static final List<String> MARKED_READABLE_FILES =
            List.of(MARKER + "-first.txt", MARKER + "-second.txt");

    /** The corpus file the scripted converter fails on while blaming itself, so a fault row is written. */
    private static final String FAULTED = SeedScriptedExtractionBeans.CONVERTER_FAULT;

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

    /** Every account in the working directory's logs folder now, found by its prefix; none before it is made. */
    private List<Path> accounts() throws IOException {
        Path logs = workingDirectory.resolve(LOGS);
        if (!Files.isDirectory(logs)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(logs)) {
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
}
