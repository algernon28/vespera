/**
 * Stages 6a and 6b: the arrangement the survivors are given a name and an order in, and the
 * synthesis docs generated over it (ADR-022, ADR-105, ADR-110).
 *
 * <p>The terminal capability module, and the last of the eight the architecture names to exist as a
 * package. Like every other capability it may depend on {@code ledger} and nothing else horizontal,
 * which is what the declaration below enforces — so it <b>cannot read {@code Profile}</b> and
 * <b>cannot name {@code embedding}</b>. That second one is the constraint this module was designed
 * against rather than around: the clusters it arranges live in {@code embedding}'s
 * {@code document_cluster}, and the shortcut of reaching for them would have cost the module rule
 * its second exception (ADR-110). Instead {@code pipeline} reads that table and hands this module
 * one cluster's worth at a time, which forces the seam ADR-108 wanted anyway — <em>given this
 * cluster's exemplars, produce a synthesis doc</em>.
 *
 * <p>It therefore never names {@code OllamaClient} either. The manifest digest that makes a
 * generator identity honest (ADR-110) arrives as a plain string, the same shape the embedding model
 * name and the relevance floor already travel in.
 *
 * <p>One module covers both stages, as {@code docs/architecture.md} §1.4 records. 6a comes out thin
 * on purpose: it holds a table, a fallback chain and an ordering rule, because everything it derives
 * those from is gathered where the passes are assembled. It does not know what a stage is.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "ledger")
package io.algernon.vespera.synthesis;
