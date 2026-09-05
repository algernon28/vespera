/**
 * Boilerplate-detection shingles (ADR-038, ADR-073, ADR-074): raw shingle hashes computed over
 * extracted text during stage 2's pass, and stage 3's own corpus-wide document-frequency pass over
 * them — both live here, so a second traversal of the archive is never needed for either.
 *
 * <p>A capability module: it may depend on {@code ledger} and nothing else horizontal (ADR-040), which
 * is what the declaration below enforces — {@code extraction} therefore never calls into this module;
 * {@code pipeline} composes the two by handing extracted text to both, in the same open-document pass
 * (ADR-073), and separately drives stage 3's document-frequency pass under its own run. MinHash
 * signature computation over these hashes is stage 4's own decision (ADR-018), not built here.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "ledger")
package io.algernon.vespera.similarity;
