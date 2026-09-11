/**
 * Docling's client and extraction cache: converting one document per call, and never converting the
 * same content under the same engine twice.
 *
 * <p>A capability module: it may depend on {@code ledger} and nothing else horizontal (ADR-040). It
 * does not know what a stage is — only {@code pipeline} does, and no verdict is written here (that
 * is {@code pipeline}'s stage-2 step).
 *
 * <p><strong>One horizontal dependency is declared beyond that, and it is the only one in the
 * tree</strong>: {@code corpus}, for {@code DetectedFormat} and {@code DetectedSubtype} alone
 * (ADR-100). Docling offers no way to state an input format, so the filename is the one lever there
 * is, and deriving it from what stage 1 found is knowledge about Docling — which of its pipelines a
 * class of file reaches — rather than knowledge about a stage. It belongs behind this module's
 * client, beside the {@code sentOptions} the extractor identity is built from, so that the two
 * cannot drift. The alternative of a second enumeration here, translated by {@code pipeline}, would
 * buy the unbroken rule with a copy of the vocabulary that has to track the original.
 *
 * <p>The dependency is on two closed enumerations that hold no behaviour and depend on nothing.
 * Widening it to anything else in {@code corpus} is a decision, not a convenience.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"ledger", "corpus"})
package io.algernon.vespera.extraction;
