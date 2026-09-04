package io.algernon.vespera.pipeline;

/**
 * {@code pipeline}'s reading of the profile's tier-2 {@code degenerate-output} key (ADR-070), the
 * shape a bean carries it in rather than a bare {@code Double}: {@code extraction} may depend only on
 * {@code ledger} (ADR-040), so it never reads the profile itself — this is the value {@code pipeline}
 * hands it, {@code null} while the key ships unset.
 */
record DegenerateOutputConfidenceFloor(Double value) {}
