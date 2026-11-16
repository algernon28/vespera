package io.algernon.vespera.engine.synthesis;

import java.util.List;

/**
 * One name to ask for (ADR-403 section 4): the passages' texts strongest first, and the name the topic takes where
 * the model's is turned down.
 */
public record NameCall(List<String> passagesStrongestFirst, String fallback) {}
