package io.algernon.vespera.engine.synthesis;

import java.util.Optional;

/** A topic's name, where it came from and, where the model's answer was not used, why (ADR-403 sections 6 and 7). */
public record TopicName(String value, TopicNameSource source, Optional<NameFallbackReason> fallbackReason) {}
