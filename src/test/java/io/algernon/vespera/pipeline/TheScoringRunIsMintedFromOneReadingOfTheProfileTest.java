package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.ModelArtefact;
import io.algernon.vespera.embedding.OllamaClient;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * The scoring run's identity is read from one load of the profile (ADR-273 §2): the relevance floor, the similarity
 * floor for copies in other languages and the preferred languages are all read from the profile as it stood at that
 * one load, so that a profile saved while the run is minted cannot split one run's identity across two profiles.
 *
 * <p>Assembled by hand, as {@link StageThreeWritesTheDocumentFrequencyPageTest} assembles its holder: a corpus root
 * and a seed folder walked, a content redundancy run recorded in the invocation, and the gates the seed measurement
 * run is minted past reading a profile of their own. The holder reads the scoring run's profile through a store that
 * answers its first load with one profile and every later load with another, as though the operator saved the
 * profile again the moment it was first read.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Issue("548")
@Link(name = "ADR-273", url = Adr.THE_TRANSLATION_STEP_READS_A_MEAN_VECTOR_ONLY_TO_COMPARE_IT_AND_THE_PROFILE_ONCE, type = "adr")
@Link(name = "ADR-117", url = Adr.THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY, type = "adr")
class TheScoringRunIsMintedFromOneReadingOfTheProfileTest {

    /** The embedding model the scoring run is minted under; the double serving it answers for any name. */
    private static final String MODEL = "a-scoring-model:0.6b";

    /** What the double serving the model reports for it. */
    private static final ModelArtefact ARTEFACT = new ModelArtefact("sha256:5c0r1ng", "F16");

    /** The profile as the mint first reads it: a relevance floor of 0.5, a similarity floor of 0.9, English. */
    private static final Profile AS_FIRST_READ = ProfileFixture.profile()
            .relevanceScoreFloor("0.5", "set by this test")
            .translationSimilarityFloor("0.9", "set by this test")
            .preferredLanguages("en", "set by this test")
            .build();

    /** The profile as saved after that first read: each of the three keys answered otherwise. */
    private static final Profile AS_SAVED_AFTER_IT = ProfileFixture.profile()
            .relevanceScoreFloor("0.6", "saved again by this test")
            .translationSimilarityFloor("0.8", "saved again by this test")
            .preferredLanguages("zh", "saved again by this test")
            .build();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A piece of scoring work is identified by one reading of the profile")
    @DisplayName("A profile saved after the scoring work first reads it changes none of the three values the work is identified by")
    void theThreeValuesOfTheScoringRunsIdentityAreReadFromOneProfile(
            @TempDir Path root, @TempDir Path seeds, @TempDir Path gatesWorking, @TempDir Path scoringWorking)
            throws Exception {
        Files.writeString(root.resolve("corpus.txt"), "a corpus text");
        Files.writeString(seeds.resolve("seed.txt"), "a seed text");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkRecorder walks = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
        WalkId walk = walks.walk(root);
        walks.walk(seeds);
        ImplementationVersions versions = new ImplementationVersions();
        ExecutionContext invocation = new ExecutionContext();
        new RunMint(ledger, versions, new InvocationRuns(invocation))
                .mint(StageModules.CONTENT_REDUNDANCY, "{}", walk, Optional.empty());
        ProfileStore gates = new ProfileStore(gatesWorking);
        gates.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test, so the seed gate is open")
                .boilerplateDocumentFrequencyFloor("1.0", "set by this test, so stage 4's gate is open")
                .build());
        RedundancyGate redundancyGate = new RedundancyGate(gates);
        StageRuns stageRuns = new StageRuns(
                ledger,
                versions,
                null,
                null,
                redundancyGate,
                new SeedGate(gates, ledger, redundancyGate),
                null,
                new SavedAgainAfterItsFirstLoad(scoringWorking),
                null,
                null,
                null,
                servingTheModel(),
                root,
                invocation);

        RunId scoring = stageRuns.embeddingScoringUnder(MODEL);

        String configConsumed = jdbcTemplate.queryForObject(
                "SELECT config_consumed FROM run WHERE id = ?", String.class, scoring.value());
        claim(
                "the scoring work records, as its last three values, the relevance floor 0.5, the similarity floor"
                        + " 0.9 and the preferred language en: all three from the profile as first read, and none"
                        + " from the profile saved after it",
                () -> assertThat(configConsumed)
                        .endsWith("\"relevanceScoreFloor\":0.5,\"translationSimilarityFloor\":0.9,"
                                + "\"preferredLanguages\":[\"en\"]}"));
    }

    /** A runtime serving {@link #ARTEFACT} under any name, asked nothing else. */
    private static OllamaClient servingTheModel() {
        return new OllamaClient("unused") {
            @Override
            public ModelArtefact artefactOf(String modelName) {
                return ARTEFACT;
            }
        };
    }

    /**
     * A profile store answering its first load with {@link #AS_FIRST_READ} and every later one with {@link
     * #AS_SAVED_AFTER_IT}, as though the operator saved the profile again the moment it was first read.
     */
    private static final class SavedAgainAfterItsFirstLoad extends ProfileStore {

        private boolean loaded;

        SavedAgainAfterItsFirstLoad(Path workingDirectory) {
            super(workingDirectory);
        }

        @Override
        public Profile load() {
            Profile answer = loaded ? AS_SAVED_AFTER_IT : AS_FIRST_READ;
            loaded = true;
            return answer;
        }
    }
}
