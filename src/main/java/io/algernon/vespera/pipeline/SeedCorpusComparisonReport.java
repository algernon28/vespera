package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.SeedCorpusComparison;

/**
 * Renders a {@link SeedCorpusComparison.Comparison} as one self-contained HTML file (ADR-086) —
 * plain, hand-assembled HTML, no templating library, the same shape {@link
 * ConfidenceDistributionReport} already established (ADR-046).
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
        StringBuilder statements = new StringBuilder();
        for (String statement : comparison.statements()) {
            statements.append("<li>").append(escape(statement)).append("</li>\n");
        }

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<title>Seed/corpus mismatch</title>\n"
                + "<style>\n"
                + "body { font-family: sans-serif; margin: 2em; max-width: 900px; }\n"
                + "li { margin-bottom: 0.6em; }\n"
                + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<h1>How the seed set compares to the corpus</h1>\n"
                + "<p>Measured over " + comparison.seedDocumentCount() + " usable, measured seed document(s) and "
                + comparison.corpusDocumentCount() + " surviving corpus document(s). "
                + comparison.unmeasuredSeedDocumentCount()
                + " seed document(s) carried no measurement at all and are not part of the comparison below.</p>\n"
                + "<ul>\n"
                + statements
                + "</ul>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
