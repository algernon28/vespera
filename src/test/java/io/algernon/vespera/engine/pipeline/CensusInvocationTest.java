package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

/**
 * One invocation, from the command line down to the rows (ADR-047, ADR-054).
 *
 * <p>What this covers is the wiring, and only the wiring: that {@code vespera run <root>} reaches the
 * job, that the job's one step reaches census, and that the root the operator typed arrives as the
 * root that gets walked. Everything census then does is pinned by {@link CensusTaskletTest}.
 *
 * <p>It is a slice rather than the whole application, because the whole application builds Ollama's
 * clients and this question does not involve them. The one non-obvious piece is
 * {@code @Transactional(NOT_SUPPORTED)}: the census step deliberately runs outside a transaction so
 * that a walk commits at its own checkpoints, and a test-managed transaction wrapped around it would
 * be suspended and then hold the only connection the test datasource has.
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Census")
@Feature("Invocation")
@Issue("11")
@Link(name = "ADR-054", url = Adr.CORPUS_IS_ITS_ROOT_PATH, type = "adr")
class CensusInvocationTest {

    /** The working directory the profile is written to; the corpus root is the command's argument. */
    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private Ledger ledger;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private Job vesperaJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What census does in one invocation")
    @DisplayName("The stages run in cheapest-filter-first order, with the content census last")
    @Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
    @Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
    @Link(name = "ADR-086", url = Adr.SEED_CORPUS_MISMATCH_IS_MEASURED_AND_REPORTED, type = "adr")
    @Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
    @Issue("106")
    void runsTheStagesInOrderWithTheContentCensusAfterExtraction() {
        List<String> stagesInOrder = List.copyOf(((SimpleJob) vesperaJob).getStepNames());

        claim(
                "the stages built so far run in the order they filter in -- census, then the byte-level"
                        + " reduction, then extraction, then the content census, then redundancy in its two"
                        + " steps, then the seed set stage 5 scores against, then how far that seed set"
                        + " resembles the documents still standing, then every vector gate 3 embeds, then"
                        + " the copies that carry another's content in another language, then"
                        + " every survivor's relevance score, then the threshold that removes what"
                        + " scored under it, then each seed partition grouped into clusters, then the page"
                        + " and the questions a person needs in order to choose a cut, then the groups that"
                        + " page is written from, and last the step that writes over the groups a person"
                        + " approved -- so each pass only ever measures what the cheaper passes"
                        + " before it left standing, and nothing is put to a person until every score it"
                        + " would be read against exists",
                () -> assertThat(stagesInOrder)
                        .containsExactly(
                                "census",
                                "byte-level-reduction",
                                "extraction",
                                "content-census",
                                "redundancy-signature",
                                "content-redundancy",
                                "seed-extraction",
                                "seed-corpus-comparison",
                                "embedding-scoring",
                                "translation",
                                "relevance-scoring",
                                "relevance-floor",
                                "clustering",
                                "relevance-report",
                                "arrangement",
                                "generation"));
    }

    @Test
    @Story("The commands the tool offers")
    @DisplayName("A database directory that disagrees with the one actually opened stops the run")
    void refusesADatabaseDirectoryThatWasNotTheOneOpened(@TempDir Path root, @TempDir Path elsewhere)
            throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");
        // The context, and so the database, is shared with the other tests in this class, which do
        // record walks. What matters is that this invocation adds none.
        long walksBefore = walkCount();

        cli.run("run", root.toString(), "--db-dir=" + elsewhere);

        claim(
                "the run refuses rather than writing to one directory while the operator believes they"
                        + " named another",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim(
                "and this invocation recorded no walk of its own, the refusal coming before any walking",
                () -> assertThat(walkCount()).isEqualTo(walksBefore));
    }

    private long walkCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk", Long.class);
    }
}
