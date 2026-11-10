package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.SeedCorpusComparison;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link SeedCorpusComparison.Comparison} as one self-contained HTML file (ADR-086) —
 * plain, hand-assembled HTML, no templating library, the shared {@link ReportPage} module the
 * reports beside the database all supply their title, prose and rows to (ADR-046, ADR-130) — and as
 * the JSON beside it that holds the same numbers and statements (ADR-362 section 9).
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

    /** The report's name, as the page and its JSON are named (ADR-362 section 9). */
    static final String NAME = SeedCorpusComparisonTasklet.SEED_CORPUS_COMPARISON_FILE_NAME.substring(
            0, SeedCorpusComparisonTasklet.SEED_CORPUS_COMPARISON_FILE_NAME.length() - ".html".length());

    /** The format its JSON is written in. */
    static final int FORMAT = 1;

    private static final String TITLE = "Seed/corpus mismatch";

    private SeedCorpusComparisonReport() {}

    static String render(SeedCorpusComparison.Comparison comparison) {
        String body = ReportPage.heading(1, "How the seed set compares to the corpus")
                + ReportPage.paragraph(populationSentence(comparison))
                + ReportPage.bulletList(comparison.statements());
        return ReportPage.render(TITLE, body);
    }

    /** The page's one paragraph of counts, which its JSON's first section holds whole. */
    private static String populationSentence(SeedCorpusComparison.Comparison comparison) {
        return "Measured over " + comparison.seedDocumentCount()
                + " usable, measured seed document(s) and " + comparison.corpusDocumentCount()
                + " surviving corpus document(s). " + comparison.unmeasuredSeedDocumentCount()
                + " seed document(s) carried no measurement at all and are not part of the"
                + " comparison below.";
    }

    /**
     * The JSON the page's numbers are held in (ADR-362 section 9): the three counts as the headline, the paragraph,
     * and a table for the language mix, one for the provenance mix and one for the four spreads, each row ending in
     * its statement, the tables in the order the page lists the statements.
     */
    static AnalysisReportJson.Document json(SeedCorpusComparison.Comparison comparison, Instant writtenAt) {
        List<AnalysisReportJson.Figure> headline = List.of(
                new AnalysisReportJson.Figure(
                        "seed-documents", "Usable, measured seed documents", comparison.seedDocumentCount(), "count"),
                new AnalysisReportJson.Figure(
                        "corpus-documents", "Surviving corpus documents", comparison.corpusDocumentCount(), "count"),
                new AnalysisReportJson.Figure(
                        "unmeasured-seed-documents", "Seed documents with no measurement",
                        comparison.unmeasuredSeedDocumentCount(), "count"));
        List<AnalysisReportJson.Section> sections = List.of(
                new AnalysisReportJson.Section("population", "Population", List.of(populationSentence(comparison)), null, null),
                new AnalysisReportJson.Section("language", "Language mix", List.of(), null, mixTable(comparison.languageMix())),
                new AnalysisReportJson.Section(
                        "provenance", "Provenance mix", List.of(), null, mixTable(comparison.provenanceMix())),
                new AnalysisReportJson.Section("spreads", "Spreads", List.of(), null, spreadTable(comparison.spreads())));
        return new AnalysisReportJson.Document(FORMAT, NAME, TITLE, writtenAt, headline, sections);
    }

    private static AnalysisReportJson.Table mixTable(List<SeedCorpusComparison.Proportion> proportions) {
        List<List<Object>> rows = new ArrayList<>();
        for (SeedCorpusComparison.Proportion proportion : proportions) {
            List<Object> row = new ArrayList<>();
            row.add(proportion.category());
            row.add(proportion.seedDocuments());
            row.add(proportion.corpusDocuments());
            row.add(proportion.seedShare());
            row.add(proportion.corpusShare());
            row.add(proportion.statement());
            rows.add(row);
        }
        return new AnalysisReportJson.Table(
                List.of(
                        new AnalysisReportJson.Column("category", "Category", "text"),
                        new AnalysisReportJson.Column("seedDocuments", "Seed documents", "count"),
                        new AnalysisReportJson.Column("corpusDocuments", "Corpus documents", "count"),
                        new AnalysisReportJson.Column("seedShare", "Share of the seed set", "share"),
                        new AnalysisReportJson.Column("corpusShare", "Share of the corpus", "share"),
                        new AnalysisReportJson.Column("statement", "Statement", "text")),
                rows);
    }

    private static AnalysisReportJson.Table spreadTable(List<SeedCorpusComparison.Spread> spreads) {
        List<List<Object>> rows = new ArrayList<>();
        for (SeedCorpusComparison.Spread spread : spreads) {
            List<Object> row = new ArrayList<>();
            row.add(spread.signal());
            quartiles(row, spread.seed());
            quartiles(row, spread.corpus());
            row.add(spread.statement());
            rows.add(row);
        }
        return new AnalysisReportJson.Table(
                List.of(
                        new AnalysisReportJson.Column("signal", "Signal", "text"),
                        new AnalysisReportJson.Column("seedLowerQuartile", "Seed lower quartile", "number"),
                        new AnalysisReportJson.Column("seedMedian", "Seed median", "number"),
                        new AnalysisReportJson.Column("seedUpperQuartile", "Seed upper quartile", "number"),
                        new AnalysisReportJson.Column("corpusLowerQuartile", "Corpus lower quartile", "number"),
                        new AnalysisReportJson.Column("corpusMedian", "Corpus median", "number"),
                        new AnalysisReportJson.Column("corpusUpperQuartile", "Corpus upper quartile", "number"),
                        new AnalysisReportJson.Column("statement", "Statement", "text")),
                rows);
    }

    /** The three cells of a side's quartiles, each null where no document of that side reported the signal. */
    private static void quartiles(List<Object> row, SeedCorpusComparison.Quartiles quartiles) {
        row.add(quartiles == null ? null : quartiles.lowerQuartile());
        row.add(quartiles == null ? null : quartiles.median());
        row.add(quartiles == null ? null : quartiles.upperQuartile());
    }
}
