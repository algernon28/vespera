package io.algernon.vespera.extraction;

/**
 * One chunk {@link HybridChunker} produced from a document, in document order.
 *
 * @param ordinal this chunk's position among the document's chunks, starting at 0
 * @param text the chunk's text, structure-first (ADR-029) rather than an arbitrary character window
 * @param wordCount {@code text}'s size in the unit its {@link ChunkingRule} budgets (ADR-091):
 *     whitespace-separated words, stored so a reader never has to re-measure a chunk to say how
 *     big it is. Not a token count — nothing here counts tokens.
 */
public record Chunk(int ordinal, String text, int wordCount) {}
