package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * How far the seed set resembles the survivors it will be scored against (ADR-086): four comparisons
 * read off stored {@code extraction_metric} columns, medians and quartiles rather than means, stated
 * in words that name both figures and judge neither. A measurement and a report — never a verdict,
 * never a gate, never a block (ADR-086's own words).
 *
 * <p><b>Corpus side is survivors</b>, not every occurrence: {@link Ledger#survivors} over the
 * extraction run, because what matters is whether the seeds resemble what would actually be scored.
 *
 * <p><b>Seed side is measured, usable seeds</b> (ADR-092, amending the premise ADR-086 stated).
 * {@link Ledger#occurrencesOf} the seed walk names every candidate; a candidate with no {@code
 * extraction_metric} row under the measurement run was never measured at all and is counted, never
 * silently folded into a category ("cannot say" is a measurement that was taken; no row is not); a
 * candidate recorded in {@code unusable_seed} under this run was measured but is outside the
 * population compared, because only usable seeds are ever scored (ADR-020's maximum is taken over the
 * seeds that survived extraction).
 *
 * <p>Two outputs, in ADR-075's shape: this class writes the re-analyzable {@code
 * seed_corpus_comparison} rows, keyed by the measurement run's own id — a fresh row set per run
 * (ADR-077), never an overwrite — and hands back the same {@link Comparison} value so {@code
 * pipeline} can render it as the HTML report without the two ever silently disagreeing.
 */
@Component
public class SeedCorpusComparison {

    /** The language-mix share for a document whose language detection declined to guess at all. */
    public static final String UNDETERMINED = "undetermined";

    /** The provenance share for a document no confidence figure was ever computed for. */
    public static final String BORN_DIGITAL = "born-digital";

    /** The provenance share for a document a confidence figure exists for, whatever it says. */
    public static final String CONVERTED = "converted";

    /** The length signal, measured in words. */
    public static final String WORD_COUNT = "word-count";

    /** The length signal, measured in pages — only over documents a page count was ever recorded for. */
    public static final String PAGE_COUNT = "page-count";

    /** The OCR-damage proxy: the share of a document's words carrying no vowel at all. */
    public static final String VOWELLESS_WORD_RATIO = "vowelless-word-ratio";

    /** The OCR-damage proxy: the share of a document's words that are a single character. */
    public static final String SINGLE_CHARACTER_WORD_RATIO = "single-character-word-ratio";

    private static final String LANGUAGE_COMPARISON = "language";
    private static final String PROVENANCE_COMPARISON = "provenance";
    private static final String SPREAD_COMPARISON = "spread";

    private final JdbcTemplate jdbcTemplate;
    private final Ledger ledger;

    public SeedCorpusComparison(JdbcTemplate jdbcTemplate, Ledger ledger) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledger = ledger;
    }

    /**
     * Measures how far the seed walk's usable, measured seeds resemble {@code extractionRunId}'s
     * survivors, writes the comparison under {@code measurementRunId}, and returns the same value —
     * the value {@code pipeline} then renders as the HTML report (ADR-075).
     */
    public Comparison measure(RunId measurementRunId, RunId extractionRunId, WalkId seedWalkId) {
        Set<Long> corpusSurvivorIds = drain(ledger.survivors(extractionRunId));
        Set<Long> seedCandidateIds = drain(ledger.occurrencesOf(seedWalkId));
        Set<Long> unusableSeedIds = unusableSeedIds(measurementRunId);

        List<MetricRow> corpusRows = metricRows(extractionRunId, corpusSurvivorIds);
        List<MetricRow> allSeedRows = metricRows(measurementRunId, seedCandidateIds);
        List<MetricRow> seedRows = allSeedRows.stream()
                .filter(row -> !unusableSeedIds.contains(row.occurrenceId()))
                .toList();

        long seedDocumentCount = seedRows.size();
        long corpusDocumentCount = corpusRows.size();
        long unmeasuredSeedDocumentCount = seedCandidateIds.size() - allSeedRows.size();

        List<Proportion> languageMix =
                proportions(LANGUAGE_COMPARISON, seedRows, corpusRows, seedDocumentCount, corpusDocumentCount, row ->
                        row.primaryLanguage() == null ? UNDETERMINED : row.primaryLanguage());
        List<Proportion> provenanceMix = proportions(
                PROVENANCE_COMPARISON,
                seedRows,
                corpusRows,
                seedDocumentCount,
                corpusDocumentCount,
                row -> row.meanScoreIsNull() ? BORN_DIGITAL : CONVERTED);

        List<Spread> spreads = List.of(
                spread(WORD_COUNT, "word count", seedRows, corpusRows, MetricRow::wordCountAsDouble),
                spread(PAGE_COUNT, "page count", seedRows, corpusRows, MetricRow::pageCountAsDouble),
                spread(
                        VOWELLESS_WORD_RATIO,
                        "vowelless-word ratio",
                        seedRows,
                        corpusRows,
                        MetricRow::vowellessWordRatio),
                spread(
                        SINGLE_CHARACTER_WORD_RATIO,
                        "single-character-word ratio",
                        seedRows,
                        corpusRows,
                        MetricRow::singleCharacterWordRatio));

        Comparison comparison = new Comparison(
                seedDocumentCount, corpusDocumentCount, unmeasuredSeedDocumentCount, languageMix, provenanceMix,
                spreads);
        write(measurementRunId, comparison);
        return comparison;
    }

    private List<Proportion> proportions(
            String comparisonName,
            List<MetricRow> seedRows,
            List<MetricRow> corpusRows,
            long seedDocumentCount,
            long corpusDocumentCount,
            Function<MetricRow, String> categoryOf) {
        Map<String, Long> seedCounts = countByCategory(seedRows, categoryOf);
        Map<String, Long> corpusCounts = countByCategory(corpusRows, categoryOf);
        Set<String> allCategories = new TreeSet<>();
        allCategories.addAll(seedCounts.keySet());
        allCategories.addAll(corpusCounts.keySet());

        List<Proportion> result = new ArrayList<>();
        for (String category : allCategories) {
            long seedDocuments = seedCounts.getOrDefault(category, 0L);
            long corpusDocuments = corpusCounts.getOrDefault(category, 0L);
            double seedShare = seedDocumentCount == 0 ? 0.0 : (double) seedDocuments / seedDocumentCount;
            double corpusShare = corpusDocumentCount == 0 ? 0.0 : (double) corpusDocuments / corpusDocumentCount;
            String statement = proportionStatement(
                    comparisonName, category, seedDocuments, seedDocumentCount, seedShare, corpusDocuments,
                    corpusDocumentCount, corpusShare);
            result.add(new Proportion(category, seedDocuments, corpusDocuments, seedShare, corpusShare, statement));
        }
        return result;
    }

    private static Map<String, Long> countByCategory(
            List<MetricRow> rows, Function<MetricRow, String> categoryOf) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MetricRow row : rows) {
            counts.merge(categoryOf.apply(row), 1L, Long::sum);
        }
        return counts;
    }

    private static String proportionStatement(
            String comparisonName,
            String category,
            long seedDocuments,
            long seedDocumentCount,
            double seedShare,
            long corpusDocuments,
            long corpusDocumentCount,
            double corpusShare) {
        return String.format(
                Locale.ROOT,
                "%s (%s): %s%% of the seed set (%d of %d) is %s; %s%% of the corpus (%d of %d) is %s.",
                capitalize(comparisonName),
                category,
                percent(seedShare),
                seedDocuments,
                seedDocumentCount,
                category,
                percent(corpusShare),
                corpusDocuments,
                corpusDocumentCount,
                category);
    }

    private Spread spread(
            String signal,
            String label,
            List<MetricRow> seedRows,
            List<MetricRow> corpusRows,
            Function<MetricRow, Double> valueOf) {
        Quartiles seedQuartiles = Quartiles.of(values(seedRows, valueOf));
        Quartiles corpusQuartiles = Quartiles.of(values(corpusRows, valueOf));
        String statement = String.format(
                Locale.ROOT,
                "%s: the seed set's typical %s is %s (%s to %s); the corpus's is %s (%s to %s).",
                capitalize(label),
                label,
                format(seedQuartiles.median()),
                format(seedQuartiles.lowerQuartile()),
                format(seedQuartiles.upperQuartile()),
                format(corpusQuartiles.median()),
                format(corpusQuartiles.lowerQuartile()),
                format(corpusQuartiles.upperQuartile()));
        return new Spread(signal, seedQuartiles, corpusQuartiles, statement);
    }

    private static List<Double> values(List<MetricRow> rows, Function<MetricRow, Double> valueOf) {
        List<Double> values = new ArrayList<>();
        for (MetricRow row : rows) {
            Double value = valueOf.apply(row);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.0f", share * 100);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void write(RunId runId, Comparison comparison) {
        for (Proportion proportion : comparison.languageMix()) {
            writeProportion(runId, comparison, LANGUAGE_COMPARISON, proportion);
        }
        for (Proportion proportion : comparison.provenanceMix()) {
            writeProportion(runId, comparison, PROVENANCE_COMPARISON, proportion);
        }
        for (Spread spread : comparison.spreads()) {
            writeSpread(runId, comparison, spread);
        }
    }

    private void writeProportion(RunId runId, Comparison comparison, String comparisonName, Proportion proportion) {
        jdbcTemplate.update(
                "INSERT INTO seed_corpus_comparison"
                        + " (run_id, comparison, category, seed_documents, corpus_documents, seed_share,"
                        + " corpus_share, seed_document_count, corpus_document_count,"
                        + " unmeasured_seed_document_count, statement)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                runId.value(),
                comparisonName,
                proportion.category(),
                proportion.seedDocuments(),
                proportion.corpusDocuments(),
                proportion.seedShare(),
                proportion.corpusShare(),
                comparison.seedDocumentCount(),
                comparison.corpusDocumentCount(),
                comparison.unmeasuredSeedDocumentCount(),
                proportion.statement());
    }

    private void writeSpread(RunId runId, Comparison comparison, Spread spread) {
        jdbcTemplate.update(
                "INSERT INTO seed_corpus_comparison"
                        + " (run_id, comparison, category, seed_lower_quartile, seed_median,"
                        + " seed_upper_quartile, corpus_lower_quartile, corpus_median, corpus_upper_quartile,"
                        + " seed_document_count, corpus_document_count, unmeasured_seed_document_count,"
                        + " statement)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                runId.value(),
                SPREAD_COMPARISON,
                spread.signal(),
                spread.seed().lowerQuartile(),
                spread.seed().median(),
                spread.seed().upperQuartile(),
                spread.corpus().lowerQuartile(),
                spread.corpus().median(),
                spread.corpus().upperQuartile(),
                comparison.seedDocumentCount(),
                comparison.corpusDocumentCount(),
                comparison.unmeasuredSeedDocumentCount(),
                spread.statement());
    }

    private Set<Long> unusableSeedIds(RunId measurementRunId) {
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM unusable_seed WHERE run_id = ?", Long.class, measurementRunId.value()));
    }

    private List<MetricRow> metricRows(RunId runId, Set<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT occurrence_id, primary_language, mean_score, word_count, page_count,"
                        + " vowelless_word_count, single_character_word_count FROM extraction_metric"
                        + " WHERE run_id = ?",
                (resultSet, rowNumber) -> {
                    long occurrenceId = resultSet.getLong("occurrence_id");
                    String primaryLanguage = resultSet.getString("primary_language");
                    resultSet.getDouble("mean_score");
                    boolean meanScoreIsNull = resultSet.wasNull();
                    int wordCount = resultSet.getInt("word_count");
                    int pageCount = resultSet.getInt("page_count");
                    boolean pageCountIsNull = resultSet.wasNull();
                    int vowellessWordCount = resultSet.getInt("vowelless_word_count");
                    int singleCharacterWordCount = resultSet.getInt("single_character_word_count");
                    return new MetricRow(
                            occurrenceId,
                            primaryLanguage,
                            meanScoreIsNull,
                            wordCount,
                            pageCountIsNull ? null : pageCount,
                            vowellessWordCount,
                            singleCharacterWordCount);
                },
                runId.value())
                .stream()
                .filter(row -> candidateIds.contains(row.occurrenceId()))
                .toList();
    }

    /**
     * Drains a survivor-shaped reader to exhaustion — the same shape {@code
     * extraction.ConfidenceDistribution.drainSurvivors} already uses — because the whole population is
     * needed to test every {@code extraction_metric} row against, not one chunk of it.
     */
    private static Set<Long> drain(ItemStreamReader<OccurrenceId> reader) {
        Set<Long> ids = new HashSet<>();
        try {
            reader.open(new ExecutionContext());
            try {
                for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                    ids.add(id.value());
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not read the seed/corpus comparison's own population", e);
        }
        return ids;
    }

    /** One occurrence's extraction-metric columns this comparison reads. */
    private record MetricRow(
            long occurrenceId,
            String primaryLanguage,
            boolean meanScoreIsNull,
            int wordCount,
            Integer pageCount,
            int vowellessWordCount,
            int singleCharacterWordCount) {

        Double wordCountAsDouble() {
            return (double) wordCount;
        }

        Double pageCountAsDouble() {
            return pageCount == null ? null : pageCount.doubleValue();
        }

        Double vowellessWordRatio() {
            return wordCount == 0 ? null : (double) vowellessWordCount / wordCount;
        }

        Double singleCharacterWordRatio() {
            return wordCount == 0 ? null : (double) singleCharacterWordCount / wordCount;
        }
    }

    /**
     * The whole computed comparison: the two populations it was taken over, how many seeds carried no
     * measurement at all, and the four comparisons ADR-086 names. No summarising figure stands beside
     * them — a single number would compress the one actionable fact, which signal diverged, into one
     * that cannot be acted on (ADR-086).
     */
    public record Comparison(
            long seedDocumentCount,
            long corpusDocumentCount,
            long unmeasuredSeedDocumentCount,
            List<Proportion> languageMix,
            List<Proportion> provenanceMix,
            List<Spread> spreads) {

        /** Every comparison's own statement, in words, judging none of them. */
        public List<String> statements() {
            List<String> statements = new ArrayList<>();
            languageMix.forEach(proportion -> statements.add(proportion.statement()));
            provenanceMix.forEach(proportion -> statements.add(proportion.statement()));
            spreads.forEach(spread -> statements.add(spread.statement()));
            return statements;
        }
    }

    /** One category's share of the seed set and of the corpus, both figures present, judging neither. */
    public record Proportion(
            String category,
            long seedDocuments,
            long corpusDocuments,
            double seedShare,
            double corpusShare,
            String statement) {}

    /** One signal's middle value and quartiles, on each side, both figures present, judging neither. */
    public record Spread(String signal, Quartiles seed, Quartiles corpus, String statement) {}

    /** A median with a quartile either side of it — never a mean (ADR-086). */
    public record Quartiles(double lowerQuartile, double median, double upperQuartile) {

        private static final Quartiles EMPTY = new Quartiles(0.0, 0.0, 0.0);

        static Quartiles of(List<Double> values) {
            if (values.isEmpty()) {
                return EMPTY;
            }
            List<Double> sorted = values.stream().sorted().toList();
            int size = sorted.size();
            double median = middle(sorted);
            List<Double> lowerHalf = sorted.subList(0, size / 2);
            List<Double> upperHalf = sorted.subList((size + 1) / 2, size);
            double lowerQuartile = lowerHalf.isEmpty() ? median : middle(lowerHalf);
            double upperQuartile = upperHalf.isEmpty() ? median : middle(upperHalf);
            return new Quartiles(lowerQuartile, median, upperQuartile);
        }

        private static double middle(List<Double> sortedValues) {
            int size = sortedValues.size();
            if (size % 2 == 1) {
                return sortedValues.get(size / 2);
            }
            return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
        }
    }
}
