package io.algernon.vespera.extraction;

/**
 * One chunk {@link HybridChunker} produced from a document, in document order.
 *
 * @param ordinal this chunk's position among the document's chunks, starting at 0
 * @param text the chunk's text, structure-first (ADR-029) rather than an arbitrary character window
 * @param tokenCount {@code text}'s size under the tokenizer it was chunked with — stored so a reader
 *     never has to re-tokenize just to answer how big a chunk is
 */
public record Chunk(int ordinal, String text, int tokenCount) {}
