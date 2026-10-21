/**
 * The publish (ADR-308): sends the Confluence rendering of one generation run, as its page list lists it (ADR-316),
 * to the operator's Confluence Cloud site, under one parent in one space, and the files its pages carry.
 *
 * <p>It is the one module that opens a connection to Confluence, and the one place under {@code src/main} that names a
 * Confluence REST path ({@code NothingShippedReachesConfluenceTest}). It reads the page list and the files it names
 * from disk, by the page list's contract, and names no class of {@code deliverable} (ADR-308 §7). It declares {@code
 * ledger} for the move that puts {@code confluence-not-carried.csv} in place (ADR-313 §3) and nothing else.
 *
 * <p>No stage's version names this module, so a change here moves no run id (ADR-308 §10).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "ledger")
package io.algernon.vespera.confluence;
