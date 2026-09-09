package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestcontainersConfiguration;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.ollama.OllamaContainer;

/**
 * Stage 5's last step against the real sidecars (ADR-088, #110).
 *
 * <p>{@code RelevanceReportInvocationTest} already drives a whole invocation and asserts both files
 * land beside the database, but it does so on scripted extraction and embedding beans. One claim it
 * cannot make is the one this class exists for: that the label file's embedder stamp is the identity
 * a real runtime reported, composed from a real digest, dtype and dimension — and not the model name
 * standing in for it.
 *
 * <p>That distinction is what #111 will refuse a stale label file on. A stamp that silently
 * degraded to a bare model name would still look like a stamp, and two runs under different builds
 * of the same model would then be indistinguishable — which is exactly the hole ADR-084 composed the
 * identity to close.
 *
 * <p>{@code all-minilm} rather than the model the profile names in production, for the reason
 * {@code OllamaClientIT} already gives: it is a few tens of megabytes against several hundred, and
 * what is under test is the shape of what comes back rather than the quality of the embedding.
 *
 * <p>An integration test, so {@code *IT} and failsafe rather than surefire (ADR-052): it needs a
 * Docker daemon and it pulls a model, which is why it is not in the unit suite.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Labelling")
@Issue("110")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
class RelevanceReportIT {

    /** Small enough to pull inside a test, and the one {@code OllamaClientIT} already uses. */
    private static final String MODEL = "all-minilm:latest";

    /** A floor of 1.0 opens stage 4's gate, the way every stage-5 invocation fixture does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The page the threshold is meant to be read off, and what the profile key points at. */
    private static final String PAGE = "relevance-labelling.html";

    /** The file the person writes their answers into. */
    private static final String LABEL_FILE = "relevance-labels.yaml";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private OllamaContainer ollama;

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("The stamp on the label file is what a real runtime reported")
    @DisplayName("A whole run against real sidecars stamps the label file with the composed embedder identity")
    void stampsTheLabelFileWithTheIdentityTheRuntimeReported(@TempDir Path root, @TempDir Path seeds)
            throws IOException, InterruptedException {
        ollama.execInContainer("ollama", "pull", MODEL);
        Files.writeString(root.resolve("corpus.txt"), "A short corpus document about quarterly reporting.");
        Files.writeString(seeds.resolve("seed.txt"), "An exemplar document about quarterly reporting.");
        profileNaming(seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "both files are written beside the database, as they are without the real sidecars",
                () -> assertThat(workingDirectory.resolve(PAGE))
                        .exists()
                        .satisfies(ignored -> assertThat(workingDirectory.resolve(LABEL_FILE))
                                .exists()));

        String labels = Files.readString(workingDirectory.resolve(LABEL_FILE));
        claim(
                "the stamp names the model that actually served the request, so a file generated under"
                        + " one model can never be read as answers about another",
                () -> assertThat(labels).contains(MODEL));
        claim(
                "and it carries the whole identity the runtime reported -- the manifest digest, the"
                        + " weight dtype and the vector width beside the name -- rather than the name"
                        + " alone: two builds of one model produce different vectors, and a stamp that"
                        + " cannot tell them apart is a stamp that cannot refuse a stale file",
                () -> assertThat(labels).contains("digest=").contains("dtype=").contains("dimension="));
    }

    /** The seed folder named, stage 4's gate open, and gate 3 naming the model pulled above. */
    private void profileNaming(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seeds.toString(), "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null),
                new ProfileValue(MODEL, "set by this test, so gate 3 is open", null)));
    }
}
