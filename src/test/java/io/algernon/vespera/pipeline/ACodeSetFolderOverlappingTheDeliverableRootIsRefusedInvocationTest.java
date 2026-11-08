package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.pipeline.ACodeSetFolderOverlappingIsRefusedInvocationTest.standardErrorOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.pipeline.ACodeSetFolderOverlappingIsRefusedInvocationTest.TheProfileAsText;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
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
 * {@code vespera run} refuses at its start a code set's folder that is, or lies above, the deliverable root named in
 * {@code vespera.deliverable-root} (ADR-334 §5.1), and checks the code set folders before the deliverable root's own
 * checks: where both would be refused, the code set folder's line is the one said.
 *
 * <p>A class of its own because the deliverable root is a property of the context, named once for the class, as in
 * {@code TheDeliverableRootInvocationTest}; each test writes the whole profile as text.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Census")
@Feature("Code sets")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-276", url = Adr.THE_DELIVERABLE_IS_DRAWN_AT_A_ROOT_THE_OPERATOR_MAY_NAME, type = "adr")
class ACodeSetFolderOverlappingTheDeliverableRootIsRefusedInvocationTest {

    /** The exit code of a refused invocation: 1. */
    private static final int REFUSED = CommandLine.ExitCode.SOFTWARE;

    /** The whole of what a refusal shows the operator: one line. */
    private static final int ONE_LINE = 1;

    /** What a refusal of an overlapping folder ends with. */
    private static final String NAME_A_FOLDER_APART = ". Name a folder outside the corpus root, the working directory, the"
            + " seed folder, the deliverable root and the other code set folders in codeSetFolders.";

    @TempDir
    static Path workingDirectory;

    /** Where the named deliverable root lies. */
    @TempDir
    static Path base;

    @DynamicPropertySource
    static void workingDirectoryAndDeliverableRoot(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(VesperaCommand.Run.DELIVERABLE_ROOT_PROPERTY, () -> deliverableRoot().toString());
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A folder of code that overlaps what Vespera writes or walks is refused")
    @DisplayName("The deliverable root named, named as a folder of code, is refused in one line before anything is walked, and exits 1")
    void theDeliverableRootNamedIsRefused(@TempDir Path corpusRoot) throws IOException {
        Files.writeString(corpusRoot.resolve("corpus.txt"), "a corpus document");
        Path root = Files.createDirectories(deliverableRoot());
        TheProfileAsText.write(profileStore, root.toAbsolutePath().toString(), Optional.empty());

        String printed = standardErrorOf(() -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("and says why in " + ONE_LINE + " line on standard error, naming the folder as the deliverable root it is",
                () -> assertThat(printed.lines().toList())
                        .containsExactly("vespera run refused the code set folder " + root.toRealPath()
                                + ": it is the deliverable root " + root.toRealPath() + NAME_A_FOLDER_APART));
        claim("nothing was walked", () -> assertThat(walksOver(corpusRoot)).isZero());
    }

    private static Path deliverableRoot() {
        return base.resolve("the deliverable root");
    }

    /** How many walks of {@code corpusRoot} are recorded. */
    private long walksOver(Path corpusRoot) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Long.class, Walk.canonicalRoot(corpusRoot).toString());
    }
}
