package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

/**
 * {@code vespera run} refuses at its start, in one line and before anything is walked, a code set's folder the profile
 * names that is no folder there or overlaps a folder it must stay apart from (ADR-334 §5.1): here the working
 * directory, a folder above the corpus root, and a folder that is not there.
 *
 * <p>One static working directory serves the class, as in {@code TheDeliverableRootInvocationTest}, and each test writes
 * the whole profile as text, naming its folder, so that no test's folder reaches another's and this compiles before
 * {@code Profile} has the key.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Census")
@Feature("Code sets")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class ACodeSetFolderOverlappingIsRefusedInvocationTest {

    /** The exit code of a refused invocation: 1. */
    private static final int REFUSED = CommandLine.ExitCode.SOFTWARE;

    /** The whole of what a refusal shows the operator: one line. */
    private static final int ONE_LINE = 1;

    /** What a refusal of an overlapping folder ends with. */
    private static final String NAME_A_FOLDER_APART = ". Name a folder outside the corpus root, the working directory, the"
            + " seed folder, the deliverable root and the other code set folders in codeSetFolders.";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void theWorkingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A folder of code that overlaps what Vespera writes or walks is refused")
    @DisplayName("The working directory named as a folder of code is refused in one line before anything is walked, and exits 1")
    void theWorkingDirectoryIsRefused(@TempDir Path corpusRoot) throws IOException {
        Files.writeString(corpusRoot.resolve("corpus.txt"), "a corpus document");
        theProfileNames(workingDirectory);

        String printed = standardErrorOf(() -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("and says why in " + ONE_LINE + " line on standard error, naming the folder and what it is",
                () -> assertThat(printed.lines().toList())
                        .containsExactly("vespera run refused the code set folder " + workingDirectory.toRealPath()
                                + ": it is the working directory " + workingDirectory.toRealPath() + NAME_A_FOLDER_APART));
        claim("nothing was walked", () -> assertThat(walksOver(corpusRoot)).isZero());
    }

    @Test
    @Story("A folder of code that overlaps what Vespera writes or walks is refused")
    @DisplayName("A folder above the corpus root named as a folder of code is refused in one line before anything is walked, and exits 1")
    void aFolderAboveTheCorpusRootIsRefused(@TempDir Path base) throws IOException {
        Path corpusRoot = Files.createDirectories(base.resolve("archive"));
        Files.writeString(corpusRoot.resolve("corpus.txt"), "a corpus document");
        theProfileNames(base);

        String printed = standardErrorOf(() -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("and says why in " + ONE_LINE + " line on standard error, naming the folder and the corpus root below it",
                () -> assertThat(printed.lines().toList())
                        .containsExactly("vespera run refused the code set folder " + base.toRealPath()
                                + ": it is above the corpus root " + corpusRoot.toRealPath() + NAME_A_FOLDER_APART));
        claim("nothing was walked", () -> assertThat(walksOver(corpusRoot)).isZero());
    }

    @Test
    @Story("A folder of code that overlaps what Vespera writes or walks is refused")
    @DisplayName("A folder of code that is not there is refused in one line before anything is walked, and exits 1")
    void aFolderThatIsNotThereIsRefused(@TempDir Path corpusRoot, @TempDir Path elsewhere) throws IOException {
        Files.writeString(corpusRoot.resolve("corpus.txt"), "a corpus document");
        Path nowhere = elsewhere.resolve("nowhere");
        theProfileNames(nowhere);

        String printed = standardErrorOf(() -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("and says why in " + ONE_LINE + " line on standard error, naming the folder",
                () -> assertThat(printed.lines().toList())
                        .containsExactly("vespera run refused the code set folder " + elsewhere.toRealPath().resolve("nowhere")
                                + ": it is no folder that is there. Name a folder that is there in codeSetFolders."));
        claim("nothing was walked", () -> assertThat(walksOver(corpusRoot)).isZero());
    }

    /** The whole profile, naming {@code folder} alone as a code set's folder, as the operator would write it. */
    private void theProfileNames(Path folder) throws IOException {
        Files.createDirectories(profileStore.file().getParent());
        Files.writeString(profileStore.file(), """
                ---
                codeSetFolders:
                  value: '%s'
                  provenance: "set by this test"
                  measurement: null
                """.formatted(folder.toAbsolutePath()), StandardCharsets.UTF_8);
    }

    /** How many walks of {@code corpusRoot} are recorded. */
    private long walksOver(Path corpusRoot) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Long.class, Walk.canonicalRoot(corpusRoot).toString());
    }

    /** What {@code invocation} wrote on standard error. */
    private static String standardErrorOf(Runnable invocation) {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            invocation.run();
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(original);
        }
    }
}
