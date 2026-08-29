package io.algernon.vespera.corpus;

/** A walk anomaly as {@code corpus} read it back, scoped to the walk it was queried against. */
public record RecordedAnomaly(String pathRendering, WalkAnomalyKind kind, String detail) {}
