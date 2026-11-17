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
import java.nio.file.Files;
import java.nio.file.Path;
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
 * A naming model bound blank is no model (ADR-407 §2): the invocation stops where the arrangement run would be
 * minted, naming {@code vespera.naming.model}, as a blank generation model stops the generation run (ADR-114).
 *
 * <p><b>A class of its own, for the property</b>: a machine's configuration binding the key blank is what this holds,
 * and no other test's context may carry it.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Arrangement")
@Feature("Topic names")
@Issue("829")
@Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class ABlankNamingModelStopsTheInvocationTest {

    /** The key a machine names the naming model under, which the stop names. */
    private static final String THE_KEY = "vespera.naming.model";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void aNamingModelBoundBlank(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(THE_KEY, () -> "");
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void forgetWhatWasAsked() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("The topics are named by a model the machine names")
    @DisplayName("With the naming model bound blank, the invocation stops before the arrangement is recorded, naming the key")
    void aBlankNamingModelStopsWhereTheArrangementIsMinted(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero: a machine that names no model to name the topics by is misconfigured",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("what it says names the key, " + THE_KEY + ", that is blank",
                () -> assertThat(said).contains(THE_KEY));
        claim("the scoring before the arrangement was done in this invocation, so it stopped at the arrangement",
                () -> assertThat(runsOver(root, "embedding-scoring")).hasSize(1));
        claim("no topic was asked for its name", () -> assertThat(GenerationScriptedBeans.namingCallsMade()).isZero());
        claim("and no arrangement was recorded", () -> assertThat(runsOver(root, "arrangement")).isEmpty());
    }

    private List<String> runsOver(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }
}
