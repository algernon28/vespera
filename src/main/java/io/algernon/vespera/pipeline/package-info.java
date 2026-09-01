/**
 * The composition root: the CLI, the Spring Batch job, and the one place a stage is named.
 *
 * <p>Capability modules do not know what a stage is and never call each other (ADR-040); this is
 * where the cascade is assembled out of them. The declared dependencies are exactly the modules this
 * slice's pipeline actually calls into, because {@code ApplicationModules.verify()} checks compiled
 * references rather than intentions — the list widens when a slice adds a module the pipeline really
 * does drive, and not before.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"ledger", "corpus", "profile"})
package io.algernon.vespera.pipeline;
