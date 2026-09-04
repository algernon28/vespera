package io.algernon.vespera.extraction;

import java.util.List;

/**
 * The seam ADR-029's "measured LLM fallback" is built behind: what {@link HybridChunker} calls when
 * a document carries no structural text items to chunk over ({@code document.json_content.texts} is
 * empty — Docling found no structure at all, the scanned/structureless case). Ships disabled by
 * default (the LLM-based implementation is wired only behind
 * {@code vespera.extraction.structureless-chunking.engine=llm}, unset by default), per this
 * ticket's "built but shipped disabled" requirement — the seam exists without committing to an
 * unmeasured behavior on day one.
 */
interface StructurelessChunkingFallback {

    /**
     * Splits {@code text} into token-budgeted chunks with no structural boundaries to respect.
     * {@code text} is empty exactly when Docling reported no structural items and no other text
     * source exists, in which case the result is empty too.
     */
    List<String> chunk(String text, Tokenizer tokenizer, int maxChunkTokens);
}
