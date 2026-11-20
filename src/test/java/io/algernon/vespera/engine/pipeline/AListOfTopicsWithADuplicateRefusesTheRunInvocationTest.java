package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
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
 * {@code vespera run} refuses, at its start and before anything is walked, a list of topics in {@code profile.yaml}
 * holding a name that is an earlier one again, or a blank line, in one line naming it and exit 1 (ADR-429 §3). The
 * words of the line are {@code TopicsValue}'s, held whole by {@code TheTopicsKeyTest}; here they are held as the
 * operator reads them, alone on standard error.
 *
 * <p>Each test writes the whole profile as text, as the operator would, so that this compiles whatever {@code Profile}'s
 * components are.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("898")
@Link(name = "ADR-429", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
class AListOfTopicsWithADuplicateRefusesTheRunInvocationTest {

    /** The exit code of a refused invocation: 1. */
    private static final int REFUSED = CommandLine.ExitCode.SOFTWARE;

    /** The whole of what a refusal shows the operator: one line. */
    private static final int ONE_LINE = 1;

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
    @Story("A list holding a blank entry or a duplicate is refused")
    @DisplayName("A list of topics naming one topic twice, once in another case and without its accent, refuses the run in one line before anything is walked, and exits 1")
    void aDuplicateIsRefusedBeforeAnythingIsWalked(@TempDir Path corpusRoot) throws IOException {
        Files.writeString(corpusRoot.resolve("corpus.txt"), "a corpus document");
        theProfileLists("Qualità\\nPagamenti\\nqualita");

        String printed = ACodeSetFolderOverlappingIsRefusedInvocationTest.standardErrorOf(
                () -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("and says why in " + ONE_LINE + " line on standard error, naming the third line as the first line's topic"
                        + " again",
                () -> assertThat(printed.lines().toList()).containsExactly("vespera run refused the topics in"
                        + " profile.yaml: line 3, \"qualita\", is the topic \"Qualità\" of line 1 again, two names being"
                        + " one topic where they differ only in case, accents or the spaces around them. Remove one of"
                        + " them."));
        claim("nothing was walked", () -> assertThat(walksOver(corpusRoot)).isZero());
    }

    @Test
    @Story("A list holding a blank entry or a duplicate is refused")
    @DisplayName("A list of topics holding a blank line refuses the run in one line before anything is walked, and exits 1")
    void aBlankEntryIsRefusedBeforeAnythingIsWalked(@TempDir Path corpusRoot) throws IOException {
        Files.writeString(corpusRoot.resolve("corpus.txt"), "a corpus document");
        theProfileLists("Pagamenti\\n\\nIncassi");

        String printed = ACodeSetFolderOverlappingIsRefusedInvocationTest.standardErrorOf(
                () -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("and says why in " + ONE_LINE + " line on standard error, naming the blank second line",
                () -> assertThat(printed.lines().toList()).containsExactly("vespera run refused the topics in"
                        + " profile.yaml: line 2 is blank, and each line names one topic. Remove it."));
        claim("nothing was walked", () -> assertThat(walksOver(corpusRoot)).isZero());
    }

    /**
     * The whole profile, its topics the double-quoted YAML scalar {@code escaped}, its line breaks written {@code \n}, as
     * the control panel writes a value; every other key arrives unset.
     */
    private void theProfileLists(String escaped) throws IOException {
        Files.createDirectories(profileStore.file().getParent());
        Files.writeString(profileStore.file(), "---\ntopics:\n  value: \"" + escaped + "\"\n"
                + "  provenance: \"set by this test\"\n  measurement: null\n", StandardCharsets.UTF_8);
    }

    /** How many walks of {@code corpusRoot} are recorded. */
    private long walksOver(Path corpusRoot) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Long.class, Walk.canonicalRoot(corpusRoot).toString());
    }
}
