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
}
