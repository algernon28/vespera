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
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
 * A scoring run is identified by the embedding model's name as resolved, not by where it was named (ADR-286 §3): a
 * profile naming a model and a profile naming none, under a configured default of that same name, arrive at one
 * scoring run.
 *
 * <p>Assembled by hand, as {@link TheScoringRunIsMintedFromOneReadingOfTheProfileTest} assembles its holder: a corpus
 * root and a seed folder walked once, and two invocations over that walk, each recording its own content redundancy
 * run and minting the scoring run through {@link StageRuns#embeddingScoring()}, which asks {@link EmbeddingModelGate}
 * for the name. The first reads a profile naming {@link #MODEL} under a blank configured key; the second reads a
 * profile naming no model under a configured key of {@link #MODEL}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-286", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class ADefaultAndTheSameModelInTheProfileAreOneScoringRunTest {

    /** The one embedding model both invocations score under; the double serving it answers for any name. */
    private static final String MODEL = "a-scoring-model:0.6b";

    /** What the double serving the model reports for it. */
    private static final ModelArtefact ARTEFACT = new ModelArtefact("sha256:5c0r1ng", "F16");

    /** A configured key bound empty, so only the profile can name a model. */
    private static final String NO_CONFIGURED_DEFAULT = "";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A piece of scoring work is identified by the model it scores with, not by where that model was named")
    @DisplayName("A model named in the profile and the same model shipped as the default are one piece of scoring work")
    void theModelsSourceIsNotPartOfTheScoringRunsIdentity(
            @TempDir Path root,
            @TempDir Path seeds,
            @TempDir Path gatesWorking,
            @TempDir Path namedInTheProfile,
            @TempDir Path namedNowhere)
            throws Exception {
        Files.writeString(root.resolve("corpus.txt"), "a corpus text");
        Files.writeString(seeds.resolve("seed.txt"), "a seed text");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkRecorder walks = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
        WalkId walk = walks.walk(root);
        walks.walk(seeds);
        ProfileStore gates = new ProfileStore(gatesWorking);
        gates.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test, so the seed gate is open")
                .boilerplateDocumentFrequencyFloor("1.0", "set by this test, so stage 4's gate is open")
                .build());
        ProfileStore profileNamingTheModel = new ProfileStore(namedInTheProfile);
        profileNamingTheModel.save(ProfileFixture.profile().embeddingModel(MODEL, "set by this test").build());
        ProfileStore profileNamingNoModel = new ProfileStore(namedNowhere);
        profileNamingNoModel.save(ProfileFixture.profile().build());

        RunId underTheProfilesName = scoringRun(
                ledger, walk, root, gates, new EmbeddingModelGate(profileNamingTheModel, NO_CONFIGURED_DEFAULT));
        RunId underTheDefault = scoringRun(
                ledger, walk, root, gates, new EmbeddingModelGate(profileNamingNoModel, MODEL));

        claim(
                "both invocations arrive at the one piece of scoring work: the model's name is the same, and"
                        + " whether the operator wrote it or the application shipped it is recorded nowhere in"
                        + " what identifies the work",
                () -> assertThat(underTheDefault).isEqualTo(underTheProfilesName));
        String configConsumed = jdbcTemplate.queryForObject(
                "SELECT config_consumed FROM run WHERE id = ?", String.class, underTheDefault.value());
        claim(
                "and the work records the model's name as resolved",
                () -> assertThat(configConsumed).contains("\"embeddingModel\":\"" + MODEL + "\""));
    }

    /**
     * One invocation over {@code walk}: its own content redundancy run recorded, then the scoring run minted through
     * the gate's name.
     */
    private static RunId scoringRun(
            Ledger ledger, WalkId walk, Path root, ProfileStore gates, EmbeddingModelGate embeddingModelGate) {
        ImplementationVersions versions = new ImplementationVersions();
        ExecutionContext invocation = new ExecutionContext();
        new RunMint(ledger, versions, new InvocationRuns(invocation))
                .mint(StageModules.CONTENT_REDUNDANCY, "{}", walk, Optional.empty());
        RedundancyGate redundancyGate = new RedundancyGate(gates);
        StageRuns stageRuns = new StageRuns(
                ledger,
                versions,
                null,
                null,
                redundancyGate,
                new SeedGate(gates, ledger, redundancyGate),
                embeddingModelGate,
                gates,
                null,
                null,
                null,
                servingTheModel(),
                root,
                invocation);
        return stageRuns.embeddingScoring();
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
}
