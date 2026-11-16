package io.algernon.vespera.engine.synthesis;

import java.util.List;

/**
 * What naming one topic is handed: its passages in the order of ADR-403 section 3, and its strongest contributing
 * seed.
 */
public record TopicNameCall(List<NamingPassage> passages, StrongestSeed strongestSeed) {}
