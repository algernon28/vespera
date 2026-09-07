package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestcontainersConfiguration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.ollama.OllamaContainer;

/**
 * {@link OllamaClient} against the real Ollama sidecar (ADR-091): the claim this whole part of the
 * decision rests on is that {@code /api/tags} carries a manifest digest and a weight dtype as
 * first-class fields, and that is a claim about a wire nobody here controls.
 *
 * <p>The stubbed {@link OllamaClientTest} cannot make it. Its fixtures were written from the same
 * belief as the client, so they would confirm a field name that does not exist, a digest nested one
 * level deeper, or a {@code details} object the runtime stopped sending. This test is the one that
 * would notice — which matters more here than usual, because the alternative to reading these fields
 * was Spring AI's DTOs, and the reason they were rejected is precisely that they answer a changed
 * wire with silence rather than a failure.
 *
 * <p>{@code all-minilm} rather than the candidate in ADR-084: it is a few tens of megabytes against
 * several hundred, and what is under test is the shape of the answer, which is a property of the
 * runtime rather than of the model. Its digest is one of the three the decision's own measurement
 * recorded.
 *
 * <p>An integration test, so {@code *IT} and failsafe rather than surefire (ADR-052): it needs a
 * Docker daemon, and it pulls a model, which is why it is not in the unit suite.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Epic("Embedding")
@Feature("The Ollama client")
@Issue("103")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
@Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
class OllamaClientIT {

    /** Small enough to pull inside a test, and one of the three the decision's measurement covered. */
    private static final String MODEL = "all-minilm:latest";

    /** How many hexadecimal characters a SHA-256 digest is written as. */
    private static final int SHA_256_HEX_LENGTH = 64;

    @Autowired
    private OllamaContainer ollama;

    @Autowired
    private OllamaClient client;

    @Test
    @Story("The runtime reports what it is serving")
    @DisplayName("A real sidecar reports a manifest digest and a weight dtype for a pulled model")
    void aRealSidecarReportsADigestAndADtype() throws Exception {
        ollama.execInContainer("ollama", "pull", MODEL);

        ModelArtefact artefact = client.artefactOf(MODEL);

        claim(
                "the digest arrives as a first-class field, which is the claim ADR-091 rests on when it"
                        + " sends the reading here rather than through Spring AI's own records",
                () -> assertThat(artefact.digest()).isNotBlank());
        claim(
                "and it is a SHA-256 written as " + SHA_256_HEX_LENGTH + " hexadecimal characters, so a"
                        + " truncated or differently-encoded digest would be caught rather than stored",
                () -> assertThat(artefact.digest()).matches("[0-9a-f]{" + SHA_256_HEX_LENGTH + "}"));
        claim(
                "and the weight dtype arrives beside it in the same call, which is what lets one request"
                        + " compose both runtime-reported parts of an embedder identity",
                () -> assertThat(artefact.weightDtype()).isNotBlank());
    }
}
