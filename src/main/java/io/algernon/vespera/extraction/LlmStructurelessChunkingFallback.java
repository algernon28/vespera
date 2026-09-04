package io.algernon.vespera.extraction;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ADR-029's "measured LLM fallback," as a seam only: no property in {@code application.yaml} sets
 * {@code vespera.extraction.structureless-chunking.engine=llm}, so this bean never registers and
 * {@link WindowedStructurelessChunkingFallback} stays the one {@link HybridChunker} calls. Ships
 * disabled by default per this ticket's "built but shipped disabled" requirement: the seam exists,
 * the day-one behavior does not.
 */
@Component
@ConditionalOnProperty(prefix = "vespera.extraction.structureless-chunking", name = "engine", havingValue = "llm")
class LlmStructurelessChunkingFallback implements StructurelessChunkingFallback {

    @Override
    public List<String> chunk(String text, Tokenizer tokenizer, int maxChunkTokens) {
        throw new UnsupportedOperationException(
                "the LLM-based structureless chunking fallback is a seam only (ADR-029); it ships"
                        + " disabled and has no measured implementation yet");
    }
}
