package io.algernon.vespera.engine.synthesis;

import java.util.Optional;

/**
 * A topic's strongest contributing seed: its path relative to the seed folder and its Docling title, where it has one
 * (ADR-403 section 7).
 */
public record StrongestSeed(String seedPath, Optional<String> doclingTitle) {}
