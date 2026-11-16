package io.algernon.vespera.engine.embedding;

import java.util.List;

/** A seed's content hash and its chunks' vectors in chunk order, as the scoring step holds them (ADR-399 section 1). */
public record SeedVectors(String contentHash, List<float[]> chunkVectors) {}
