/**
 * Walking a filesystem, and the byte-level facts a walk observes.
 *
 * <p>A capability module: it may depend on {@code ledger} and nothing else horizontal (ADR-040),
 * which is what the declaration below enforces. It does not know what a stage is — only
 * {@code pipeline} does.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "ledger")
package io.algernon.vespera.corpus;
