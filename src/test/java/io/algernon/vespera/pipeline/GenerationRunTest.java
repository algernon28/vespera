package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a generation run's id is derived from (ADR-108, ADR-110, #179).
 *
 * <p>Asserted over the composed string rather than over the hash of it, because the hash is opaque by
 * construction: a part left out would still produce a plausible id, and the only way to see that it
 * was left out is to read what went in.
 */
@Epic("Synthesis")
@Feature("Generation")
@Issue("179")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-110", url = Adr.PIPELINE_HANDS_SYNTHESIS_ITS_INPUTS, type = "adr")
class GenerationRunTest {

    /** The corpus root this run reads, canonicalised as every other run records it. */
    private static final Path A_CORPUS_ROOT = Path.of("/corpus").toAbsolutePath();

    /** The arrangement the operator approved, which this run is chained to. */
    private static final RunId AN_ARRANGEMENT = new RunId("9f2c41ab77de0000000000000000000000000000000000000000000000000000");

    /** The generation model resolved for this invocation. */
    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    /** What the runtime reports for those weights — the part a mutable tag cannot fake. */
    private static final String THE_DIGEST = "sha256:0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0";

    /** Where the model happens to be served, which is deliberately not part of the identity. */
    private static final String A_SERVING_URL = "http://localhost:11434";

    /** How much each call was allowed to read, which is the first option this stage actually sends. */
    private static final int THE_READING_WINDOW = 8192;

    @Test
    @Story("The generator identity is what was asked and what answered, never where it was served")
    @DisplayName("The identity carries the model's name and its weights digest")
    void carriesTheModelNameAndItsDigest() {
        String identity = GenerationRun.configConsumed(
                A_CORPUS_ROOT, AN_ARRANGEMENT, THE_GENERATION_MODEL, THE_DIGEST, THE_READING_WINDOW);

        claim(
                "the model's name is in it, because two models write different documents over one"
                        + " cluster and a run that cannot tell them apart claims one wrote the other's",
                () -> assertThat(identity).contains(THE_GENERATION_MODEL));
        claim(
                "and the weights digest beside it: a mutable tag re-pulled after upstream republishes"
                        + " would otherwise mint the same run id over different weights, which is the"
                        + " failure the embedder identity exists to refuse",
                () -> assertThat(identity).contains(THE_DIGEST));
    }

    @Test
    @Story("The generator identity is what was asked and what answered, never where it was served")
    @DisplayName("The identity carries no trace of where the model was served")
    void carriesNoServingUrl() {
        String identity = GenerationRun.configConsumed(
                A_CORPUS_ROOT, AN_ARRANGEMENT, THE_GENERATION_MODEL, THE_DIGEST, THE_READING_WINDOW);

        claim(
                "two deployments answering alike are one instrument, and moving a port is not a change --"
                        + " an identity carrying the URL would mint a second run for the same work",
                () -> assertThat(identity).doesNotContain(A_SERVING_URL).doesNotContain("11434"));
    }

    @Test
    @Story("The generator identity is what was asked and what answered, never where it was served")
    @DisplayName("The identity carries how much each call was allowed to read")
    void carriesTheReadingWindow() {
        String identity = GenerationRun.configConsumed(
                A_CORPUS_ROOT, AN_ARRANGEMENT, THE_GENERATION_MODEL, THE_DIGEST, THE_READING_WINDOW);

        claim(
                "how much each call could read is part of what was asked, so it belongs in the identity"
                        + " the same way the model's name does: the same archive read in a smaller window"
                        + " is written from fewer documents, which is different work and has to be a"
                        + " different run rather than the same one twice",
                () -> assertThat(identity).contains(String.valueOf(THE_READING_WINDOW)));
    }

    @Test
    @Story("A run names the arrangement it was approved against")
    @DisplayName("The identity names the approved arrangement it reads")
    void namesTheApprovedArrangement() {
        String identity = GenerationRun.configConsumed(
                A_CORPUS_ROOT, AN_ARRANGEMENT, THE_GENERATION_MODEL, THE_DIGEST, THE_READING_WINDOW);

        claim(
                "the arrangement is in the identity as well as in the upstream chain, so a re-arrangement"
                        + " changes this run's id -- and therefore the tree it writes -- by construction",
                () -> assertThat(identity).contains(AN_ARRANGEMENT.value()));
    }
}
