package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileStore;
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
import java.util.Optional;
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
 * directory, a folder above the corpus root, a folder inside it, the seed folder, a folder listed before it, the
 * working directory's {@code deliverable} folder, and a folder that is not there. The deliverable root named is
 * {@code ACodeSetFolderOverlappingTheDeliverableRootIsRefusedInvocationTest}'s.
 *
 * <p>One static working directory serves the class, as in {@code TheDeliverableRootInvocationTest}, and each test writes
 * the whole profile as text, naming its folders, so that no test's folder reaches another's and this compiles before
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
    @DisplayName("A folder inside the corpus root named as a folder of code is refused in one line, the corpus root's walk reading it already")
    void aFolderInsideTheCorpusRootIsRefused(@TempDir Path corpusRoot) throws IOException {
        Files.writeString(corpusRoot.resolve("corpus.txt"), "a corpus document");
        Path inside = Files.createDirectories(corpusRoot.resolve("code"));
        theProfileNames(inside);

        String printed = standardErrorOf(() -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("and says why in " + ONE_LINE + " line on standard error, naming the folder and the corpus root it lies in",
                () -> assertThat(printed.lines().toList())
                        .containsExactly("vespera run refused the code set folder " + inside.toRealPath()
                                + ": it is inside the corpus root " + corpusRoot.toRealPath() + NAME_A_FOLDER_APART));
        claim("nothing was walked", () -> assertThat(walksOver(corpusRoot)).isZero());
    }

    /** The whole profile, naming {@code folder} alone as a code set's folder, as the operator would write it. */
    private void theProfileNames(Path folder) throws IOException {
        theProfileNames(folder.toAbsolutePath().toString(), Optional.empty());
    }

    /**
     * The whole profile, naming {@code folders} as the code set folders, written as the operator would write them, and
     * {@code seedFolder} where it is given.
     */
    private void theProfileNames(String folders, Optional<Path> seedFolder) throws IOException {
        TheProfileAsText.write(profileStore, folders, seedFolder);
    }

    /** How many walks of {@code corpusRoot} are recorded. */
    private long walksOver(Path corpusRoot) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Long.class, Walk.canonicalRoot(corpusRoot).toString());
    }

    /** What {@code invocation} wrote on standard error. */
    static String standardErrorOf(Runnable invocation) {
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

    /** The whole profile written as text, so that the tests compile whatever {@code Profile}'s components are. */
    static final class TheProfileAsText {

        private TheProfileAsText() {
        }

        static void write(ProfileStore profileStore, String folders, Optional<Path> seedFolder) throws IOException {
            StringBuilder text = new StringBuilder("---\n");
            seedFolder.ifPresent(seeds -> text.append("seedFolder:\n  value: '").append(seeds.toAbsolutePath())
                    .append("'\n  provenance: \"set by this test\"\n  measurement: null\n"));
            text.append("codeSetFolders:\n  value: '").append(folders)
                    .append("'\n  provenance: \"set by this test\"\n  measurement: null\n");
            Files.createDirectories(profileStore.file().getParent());
            Files.writeString(profileStore.file(), text.toString(), StandardCharsets.UTF_8);
        }
    }
}
