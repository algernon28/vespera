/**
 * The Confluence plugin (ADR-308, ADR-333): draws the Confluence rendering beside the HTML tree through {@code
 * deliverable}'s seam ({@code ConfluencePages}, ADR-301, ADR-312, ADR-316), and the publish (ADR-308), which sends
 * the rendering of one generation run, as its page list lists it (ADR-316), to the operator's Confluence Cloud site,
 * under one parent in one space, and the files its pages carry.
 *
 * <p>It is the one module that opens a connection to Confluence, and the one place under {@code src/main} that names a
 * Confluence REST path ({@code NothingShippedReachesConfluenceTest}). The publisher reads the page list and the files it
 * names from disk, by the page list's contract. It declares {@code ledger} for the move that puts {@code
 * confluence-not-carried.csv} in place (ADR-313 §3), {@code deliverable}, whose seam it implements, and {@code
 * synthesis}, whose records the seam hands it (ADR-333 §1).
 *
 * <p>No stage's version names this module, so a change here moves no run id (ADR-308 §10, ADR-333 §14).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"ledger", "deliverable", "synthesis"})
package io.algernon.vespera.confluence;
