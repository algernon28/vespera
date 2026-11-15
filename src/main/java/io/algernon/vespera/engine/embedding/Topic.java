package io.algernon.vespera.engine.embedding;

import java.util.List;

/** A topic: its key and its passages, in the visiting order (ADR-399). */
public record Topic(TopicKey key, List<SeedPassage> passages) {}
