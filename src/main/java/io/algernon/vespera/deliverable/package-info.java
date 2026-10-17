/**
 * The tree the operator is handed (ADR-103, ADR-273): an HTML index, a page for each cluster, a manifest and a
 * verified copy of every survivor listed, drawn from the arrangement and the writing {@code synthesis} keeps.
 *
 * <p>It declares {@code ledger} and {@code synthesis}: it renders {@code synthesis}'s records and reads the
 * citation pattern and the filename stem written once there, the second declared exception to the module rule
 * beside {@code extraction}'s (ADR-273 §4). {@code synthesis} names nothing of it.
 *
 * <p>No stage's version names this module (ADR-273 §5), so a change here moves no run id. It declares no logger: it
 * tells {@code pipeline} what it did through {@code DeliverableProgress}, whose every method takes numbers alone
 * (ADR-273 §8).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"ledger", "synthesis"})
package io.algernon.vespera.deliverable;
