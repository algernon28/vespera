/**
 * Boilerplate-detection shingles (ADR-038, ADR-073): raw shingle hashes computed over extracted text
 * during stage 2's pass, so stage 3's corpus-wide document-frequency pass has data to aggregate over
 * without a second traversal of the archive.
 *
 * <p>A capability module: it may depend on {@code ledger} and nothing else horizontal (ADR-040), which
 * is what the declaration below enforces — {@code extraction} therefore never calls into this module;
 * {@code pipeline} composes the two by handing extracted text to both, in the same open-document pass
 * (ADR-073). MinHash signature computation over these hashes is stage 4's own decision (ADR-018), not
 * built here.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "ledger")
package io.algernon.vespera.similarity;
