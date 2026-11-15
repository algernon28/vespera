package io.algernon.vespera.engine.embedding;

/** A topic's key: its first passage in the visiting order (ADR-399 section 5). */
public record TopicKey(SeedPassage firstPassage) {}
