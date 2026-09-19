package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.SeedCorpusComparison;

/**
 * Renders a {@link SeedCorpusComparison.Comparison} as one self-contained HTML file (ADR-086) —
 * plain, hand-assembled HTML, no templating library, the shared {@link ReportPage} module the
 * reports beside the database all supply their title, prose and rows to (ADR-046, ADR-130).
 *
 * <p>Lives in {@code pipeline} rather than {@code embedding} for the same reason {@link
 * ConfidenceDistributionReport} does (ADR-040): rendering is composition over a value {@code
 * embedding} already computed and handed back.
 *
 * <p>Every sentence on the page is one of {@link SeedCorpusComparison.Comparison#statements()} — no
 * figure is computed a second time here, so the table {@code SeedCorpusComparison} wrote and this
 * page can never silently disagree (ADR-075's acceptance criterion applied here too). The page states
 * no single figure standing for the whole comparison and judges none of it (ADR-086).
 */
final class SeedCorpusComparisonReport {

    private SeedCorpusComparisonReport() {}

    static String render(SeedCorpusComparison.Comparison comparison) {
        String body = ReportPage.heading(1, "How the seed set compares to the corpus")
                + ReportPage.paragraph("Measured over " + comparison.seedDocumentCount()
                        + " usable, measured seed document(s) and " + comparison.corpusDocumentCount()
                        + " surviving corpus document(s). " + comparison.unmeasuredSeedDocumentCount()
                        + " seed document(s) carried no measurement at all and are not part of the"
                        + " comparison below.")
                + ReportPage.bulletList(comparison.statements());
        return ReportPage.render("Seed/corpus mismatch", body);
    }
}
