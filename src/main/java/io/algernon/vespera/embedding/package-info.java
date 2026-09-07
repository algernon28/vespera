/**
 * Stage 5's own record: the seed set's usability, and — as later slices arrive — vectors, relevance
 * scores and cluster membership (ADR-083 through ADR-088).
 *
 * <p>The first module this project has added since the original nine were named (ADR-040), and a
 * capability module like the rest: it may depend on {@code ledger} and nothing else horizontal,
 * which is what the declaration below enforces. It therefore <b>cannot read {@code Profile}</b> —
 * the seed folder, the embedding model and the relevance floor are all read in {@code pipeline} and
 * handed down as plain values, the shape {@code RedundancyGate} and {@code
 * DegenerateOutputConfidenceFloor} already use for stage 4's and stage 2's keys. It does not know
 * what a stage is.
 *
 * <p>{@code ModuleBoundariesTest} fails a module that ships without an {@code allowedDependencies}
 * declaration, because the attribute defaults to {@code "*"} and an undeclared module is wide open —
 * so the declaration below is not optional decoration.
 *
 * <p>Stage 5 composes {@code extraction} (seed extraction, chunking) and this module without either
 * calling the other, the same shape stage 2 already uses for {@code extraction} plus
 * {@code similarity}: {@code pipeline} hands each what it needs.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "ledger")
package io.algernon.vespera.embedding;
