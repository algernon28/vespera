package io.algernon.vespera.embedding;

/**
 * A usable seed's content hash and how many chunks the chunker cut it into, handed to {@link
 * RelevanceScoring#residentSeedVectors} (ADR-231 section 2a). A count of zero is a seed whose only text is
 * in page headers and footers, which are not embedded.
 */
public record SeedChunks(String contentHash, int chunkCount) {}
