/**
 * The website plugin (ADR-333): draws the website rendering beside the HTML tree through {@code deliverable}'s seam
 * ({@code WebsiteDrawing}, {@code WebsiteEntries}, {@code WebsitePage}), with navigation, a theme and a search over
 * titles, labels, the synthesis writing and entries' names, its script and theme shipped inside it, and delivers it on
 * {@code vespera publish --to website} by refreshing {@code <working directory>/website/} by digest ({@code
 * WebsiteDelivery}).
 *
 * <p>It opens no connection and reads no document: a copy is listed by the key stage 2 recorded, and the pages are
 * drawn from what the seam hands it. It declares {@code ledger} for the move that puts a file in place, {@code
 * deliverable}, whose seam it implements, and {@code synthesis}, whose records the seam hands it (ADR-333 §1).
 *
 * <p>No stage's version names this module, so a change here moves no run id (ADR-333 §14).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"ledger", "deliverable", "synthesis"})
package io.algernon.vespera.website;
