/**
 * The corpus profile: what census measured, what the operator decided, and where each of those came
 * from.
 *
 * <p>A capability module: it may depend on {@code ledger} and nothing else horizontal (ADR-040).
 * It owns the profile's shape — Java records, YAML on disk (ADR-061) — and the merge rule that keeps
 * an operator's judgement safe from a later census run (ADR-062).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "ledger")
package io.algernon.vespera.profile;
