package io.algernon.vespera.ledger;

/**
 * One run as the ledger recorded it (ADR-099): its identity, the implementation version it was minted
 * under, and the configuration it consumed.
 *
 * <p>Not a bare id, deliberately. A caller looking a run up rather than recomputing it can meet more
 * than one row for the same stage over the same walk, and a refusal that names only two ids says
 * nothing about why they differ — the configurations are the only thing that does. Carrying them here
 * is what lets the refusal be written without a second query.
 *
 * <p>Read-only by construction: it is a row the ledger handed out, never something a caller mints.
 */
public record RecordedRun(RunId id, String implementationVersion, String configConsumed) {}
