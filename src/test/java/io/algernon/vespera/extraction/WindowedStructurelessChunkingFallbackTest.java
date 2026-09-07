package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The structureless-chunking seam's active default (ADR-029): a plain, token-budgeted word window,
 * standing in while the LLM-based fallback ships disabled.
 */
@Epic("Extraction")
@Feature("Chunking")
@Issue("49")
@Link(name = "ADR-029", url = Adr.CHUNKING_STRUCTURE_FIRST_WITH_A_MEASURED_LLM_FALLBACK, type = "adr")
@Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
class WindowedStructurelessChunkingFallbackTest {

    private final WindowedStructurelessChunkingFallback fallback = new WindowedStructurelessChunkingFallback();

    @Test
    @Story("A structureless document falls back safely")
    @DisplayName("Empty text produces no chunks")
    void emptyTextProducesNoChunks() {
        claim(
                "no text means no chunks — there is nothing for the seam to split",
                () -> assertThat(fallback.chunk("", new ChunkingRule(10))).isEmpty());
    }

    @Test
    @Story("A chunk never exceeds its word budget")
    @DisplayName("Text longer than the budget splits into multiple chunks, none exceeding it")
    void splitsLongTextWithinBudget() {
        int budget = 10;
        String text = "word ".repeat(25).trim();

        List<String> chunks = fallback.chunk(text, new ChunkingRule(budget));

        claim(
                "25 words under a 10-word budget need at least three chunks",
                () -> assertThat(chunks.size()).isGreaterThanOrEqualTo(3));
        claim(
                "and no chunk exceeds the budget",
                () -> assertThat(chunks).allSatisfy(
                        chunk -> assertThat(new ChunkingRule(budget).size(chunk)).isLessThanOrEqualTo(budget)));
    }
}
