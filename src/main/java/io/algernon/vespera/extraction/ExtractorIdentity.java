package io.algernon.vespera.extraction;

/**
 * The full identity of the engine an extraction cache row was produced under (ADR-012): "the serving
 * runtime is config, not code," and the extraction cache's key must carry the whole of it, so that
 * changing the configured engine mints new rows instead of silently reusing another engine's output.
 *
 * <p>What goes into the string — engine name, model/pipeline options, the sidecar's own reported
 * version — is composed by whoever mints a run against a configured engine (the pipeline wiring this
 * ticket does not build); this type only carries the composed value and refuses a blank one, the same
 * discipline {@link io.algernon.vespera.ledger.RunId} and
 * {@link io.algernon.vespera.ledger.OccurrenceId} already apply to their own identities.
 */
public record ExtractorIdentity(String value) {

    public ExtractorIdentity {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("an extractor identity is never blank");
        }
    }
}
