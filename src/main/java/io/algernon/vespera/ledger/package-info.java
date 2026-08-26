/**
 * Occurrence identity, the verdict vocabulary and its rows, run identity, and the survivors query.
 *
 * <p>The one module every capability may depend on, and the only one that may (ADR-040). It owns the
 * tables that say what exists and what was judged; every other capability owns its own tables, keyed
 * by occurrence id where that is the right key (ADR-041).
 *
 * <p>It depends on no other module. The empty allowed-dependencies list is what makes that a rule
 * rather than a habit.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package io.algernon.vespera.ledger;
