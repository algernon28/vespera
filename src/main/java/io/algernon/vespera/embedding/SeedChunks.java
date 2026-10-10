package io.algernon.vespera.embedding;

/**
 * A usable seed's content hash and how many chunks the chunker cut it into, handed to {@link
 * RelevanceScoring#residentSeedVectors} (ADR-231 section 2a). A count of zero is a usable seed the
 * embedding step never chunked: a usable seed has text outside page headers and footers (ADR-232).
 */
public record SeedChunks(String contentHash, int chunkCount) {}
