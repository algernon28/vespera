package io.algernon.vespera.engine.synthesis;

import java.util.Optional;

/** A topic's name, where it came from, and, where it is a fallback, which turn-down put it there (ADR-403). */
public record GivenName(String name, NameOrigin origin, Optional<NameTurnDown> turnedDown) {}
