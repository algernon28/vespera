package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The LLM-based structureless-chunking fallback ships as a seam, not a behavior (ADR-029): its bean
 * registers only behind {@code vespera.extraction.structureless-chunking.engine=llm}, which no
 * profile sets, so {@link WindowedStructurelessChunkingFallbackTest} pins the fallback actually in
 * effect today. This class pins the other half — that the seam itself exists and says clearly, if
 * it were ever reached, that it has no implementation yet, rather than silently doing nothing.
 */
@Epic("Extraction")
@Feature("Chunking")
@Issue("49")
@Link(name = "ADR-029", url = Adr.CHUNKING_STRUCTURE_FIRST_WITH_A_MEASURED_LLM_FALLBACK, type = "adr")
class LlmStructurelessChunkingFallbackTest {

    @Test
    @Story("The LLM fallback is a seam, not a shipped behavior")
    @DisplayName("Calling the LLM fallback fails loudly, naming itself as an unimplemented seam")
    void isNotYetImplemented() {
        LlmStructurelessChunkingFallback fallback = new LlmStructurelessChunkingFallback();

        claim(
                "the seam refuses to pretend it did something — it fails with a message that names"
                        + " what it is, rather than silently returning no chunks",
                () -> assertThatThrownBy(() -> fallback.chunk("some text", null, 10))
                        .isInstanceOf(UnsupportedOperationException.class)
                        .hasMessageContaining("seam"));
    }
}
