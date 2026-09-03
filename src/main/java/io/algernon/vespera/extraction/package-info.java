/**
 * Docling's client and extraction cache: converting one document per call, and never converting the
 * same content under the same engine twice.
 *
 * <p>A capability module: it may depend on {@code ledger} and nothing else horizontal (ADR-040),
 * which is what the declaration below enforces. It does not know what a stage is — only
 * {@code pipeline} does, and no verdict is written here (that is {@code pipeline}'s stage-2 step,
 * a later ticket).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "ledger")
package io.algernon.vespera.extraction;
