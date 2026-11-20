package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.publish.retrieval.RetrievalPlugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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

/**
 * A {@code vespera run} whose profile's {@code outputs} names what no drawing can draw stops at its start (ADR-434
 * §3): exit 1, one line on standard error of the shape of ADR-384 §2's, nothing walked, no run minted. The line's words
 * are held by {@code deliverable.TheOutputsAreReadFromTheProfilesKeyTest} and {@code
 * pipeline.TheStartRefusesOutputsNoDrawingCanDrawTest}; this holds that the invocation is stopped by it, with the
 * plugins the jar has, and that a key naming only what can be drawn stops nothing.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, OutputsAtTheStart.class, RetrievalPlugin.class})
@ExtendWith(OutputCaptureExtension.class)
@Epic("Pipeline")
@Feature("What is drawn")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
@Link(name = "ADR-384", url = Adr.WHETHER_THE_MODELS_RUN_LOCALLY_OR_IN_THE_CLOUD_IS_THE_PROFILES_TO_SAY, type = "adr")
class OutputsAtTheStartInvocationTest {

    /** The exit code of every refusal at the start of {@code vespera run}. */
    private static final int REFUSED = 1;

    private static final String STOPPED_AT_ITS_START = "vespera run stopped at its start: ";

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
    @AfterEach
    void everyModelIsServedHereAgain() {
        EmbeddingScriptedBeans.servesEveryModel();
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("Outputs no drawing can draw")
    @DisplayName("A profile whose outputs name Confluence alone stops the invocation at its start in the record's line, walking nothing")
    void confluenceAloneStopsTheStart(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aCorpusAsFarAsTheArrangement(root, seeds);
        stating("confluence");
        int before = output.getErr().length();

        cli.run("run", root.toString());
        List<String> lines = output.getErr().substring(before).lines().toList();

        claim("the start refused the invocation, exit " + REFUSED,
                () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("in one line, the record's, naming Confluence, saying it draws from the HTML tree and how to mend the key",
                () -> assertThat(lines.stream().filter(line -> line.startsWith(STOPPED_AT_ITS_START)))
                        .containsExactly(STOPPED_AT_ITS_START + "outputs names confluence but not tree, and it draws"
                                + " from the HTML tree, so nothing was walked and no run was minted -- add tree to"
                                + " outputs in profile.yaml, or take confluence out of it"));
        claim("and nothing was walked, so no run was minted over this corpus",
                () -> assertThat(walksOf(root)).isZero());
    }

    @Test
    @Story("Outputs no drawing can draw")
    @DisplayName("A profile naming retrieval alone, which draws from no tree, stops nothing")
    void retrievalAloneStopsNothing(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aCorpusAsFarAsTheArrangement(root, seeds);
        stating("retrieval");
        int before = output.getErr().length();

        cli.run("run", root.toString());
        List<String> lines = output.getErr().substring(before).lines().toList();

        claim("the start refused nothing and the corpus was walked once",
                () -> {
                    assertThat(lines).noneMatch(line -> line.startsWith(STOPPED_AT_ITS_START));
                    assertThat(walksOf(root)).isEqualTo(1);
                });
    }

    /** States {@code outputs} in the profile saved, as the file states a key. */
    private void stating(String value) throws IOException {
        Files.writeString(
                profileStore.file(),
                "\noutputs:\n  value: " + value + "\n  provenance: set by this test\n",
                StandardOpenOption.APPEND);
    }

    /** One corpus document and one seed, with every gate up to the arrangement open and no arrangement approved. */
    private void aCorpusAsFarAsTheArrangement(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    private int walksOf(Path root) {
        Integer walks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Integer.class, Walk.canonicalRoot(root).toString());
        return walks == null ? 0 : walks;
    }
}
