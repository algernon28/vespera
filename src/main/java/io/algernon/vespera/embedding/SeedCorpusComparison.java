package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.StatementSteps;
import io.algernon.vespera.ledger.WalkId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * How far the seed set resembles the survivors it will be scored against (ADR-086): four comparisons
 * read off stored {@code extraction_metric} columns, medians and quartiles rather than means, stated
 * in words that name both figures and judge neither. A measurement and a report — never a verdict,
 * never a gate, never a block (ADR-086's own words).
 *
 * <p><b>Corpus side is survivors</b>, not every occurrence: {@code Verdicts#survivors} over the
 * measurement run, whose upstream is stage 4's run (ADR-089), because what matters is whether the
 * seeds resemble what would actually be scored (ADR-156).
 *
 * <p><b>Seed side is measured, usable seeds</b> (ADR-092, amending the premise ADR-086 stated).
 * {@code Occurrences#occurrencesOf} the seed walk names every candidate; a candidate with no {@code
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

    /** The most survivors one read of rows names: the ledger's own page. */
    private static final int PAGE = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final Ledger ledger;

    public SeedCorpusComparison(JdbcTemplate jdbcTemplate, Ledger ledger) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledger = ledger;
    }

    /**
     * Measures how far the seed walk's usable, measured seeds resemble {@code measurementRunId}'s
     * survivors, writes the comparison under {@code measurementRunId}, and returns the same value —
     * the value {@code pipeline} then renders as the HTML report (ADR-075). {@code extractionRunId}
     * names the run whose {@code extraction_metric} rows carry the corpus side's measurements
     * (ADR-086); it is not the run survivors are read through (ADR-156). {@code forms} reads those rows,
     * which are {@code extraction}'s (ADR-209 section 3.2).
     */
    public Comparison measure(
            RunId measurementRunId, RunId extractionRunId, WalkId seedWalkId, MeasuredForms forms) {
        return measure(measurementRunId, extractionRunId, seedWalkId, forms, EmbeddingStatementProgress.NONE);
    }

    /**
     * As {@link #measure(RunId, RunId, WalkId, MeasuredForms)}, and tells {@code progress} about the statements that
     * wait, each started once before it and ended once after it, in the order they are issued (ADR-193
     * section 7): the drain {@link EmbeddingStatement#SEED_OCCURRENCES}, timed, so with no total; then the
     * counted {@link EmbeddingStatement#UNUSABLE_SEEDS}, started with the span of its run's rows, or an empty
     * total where the run holds none, and given its steps at each callback of SQLite's handler; then the reads
     * of the corpus side, made a page of the survivors at a time, the first {@link
     * EmbeddingStatement#CORPUS_METRICS} and each later one {@link EmbeddingStatement#CORPUS_METRICS_AGAIN},
     * each started with the span of stage 2's rows once a page has a survivor, told its rows read after each
     * page, and ended (ADR-211 sections 2, 4 and 9); and last {@link EmbeddingStatement#SEED_METRICS}, counted
     * by steps, like {@link EmbeddingStatement#UNUSABLE_SEEDS}. A read of metrics over a population that is empty is not issued and makes no call. A
     * statement that throws is not told to have ended.
     */
    public Comparison measure(
            RunId measurementRunId,
            RunId extractionRunId,
            WalkId seedWalkId,
            MeasuredForms forms,
            EmbeddingStatementProgress progress) {
        progress.statementStarting(EmbeddingStatement.SEED_OCCURRENCES, OptionalLong.empty());
        Set<Long> seedCandidateIds = idsOf(ledger.occurrences().occurrencesOf(seedWalkId));
        progress.statementEnded(EmbeddingStatement.SEED_OCCURRENCES);
        Set<Long> unusableSeedIds = unusableSeedIds(measurementRunId, progress);

        CorpusSide corpus = corpusSide(forms, measurementRunId, extractionRunId, progress);
        List<MetricRow> allSeedRows =
                metricRows(forms, measurementRunId, seedCandidateIds, EmbeddingStatement.SEED_METRICS, progress);
        List<MetricRow> seedRows = allSeedRows.stream()
                .filter(row -> !unusableSeedIds.contains(row.occurrenceId()))
                .toList();

        long seedDocumentCount = seedRows.size();
        long corpusDocumentCount = corpus.documentCount();
        long unmeasuredSeedDocumentCount = seedCandidateIds.size() - allSeedRows.size();

        List<Proportion> languageMix = proportions(
                LANGUAGE_COMPARISON,
                seedRows,
                corpus.languageCounts(),
                seedDocumentCount,
                corpusDocumentCount,
                row -> row.primaryLanguage() == null ? UNDETERMINED : row.primaryLanguage());
        List<Proportion> provenanceMix = proportions(
                PROVENANCE_COMPARISON,
                seedRows,
                corpus.provenanceCounts(),
                seedDocumentCount,
                corpusDocumentCount,
                row -> row.meanScoreIsNull() ? BORN_DIGITAL : CONVERTED);

        List<Spread> spreads = List.of(
                spread(WORD_COUNT, "word count", seedRows, corpus.words(), MetricRow::wordCountAsDouble),
                spread(PAGE_COUNT, "page count", seedRows, corpus.pages(), MetricRow::pageCountAsDouble),
                spread(
                        VOWELLESS_WORD_RATIO,
                        "vowelless-word ratio",
                        seedRows,
                        corpus.vowellessRatios(),
                        MetricRow::vowellessWordRatio),
                spread(
                        SINGLE_CHARACTER_WORD_RATIO,
                        "single-character-word ratio",
                        seedRows,
                        corpus.singleCharacterRatios(),
                        MetricRow::singleCharacterWordRatio));

        Comparison comparison = new Comparison(
                seedDocumentCount, corpusDocumentCount, unmeasuredSeedDocumentCount, languageMix, provenanceMix,
                spreads);
        write(measurementRunId, comparison);
        return comparison;
    }

    /**
     * Deletes every comparison row recorded under {@code measurementRunId} — the discard half of
     * ADR-115/ADR-116, for a step whose completion under this run is not recorded.
     */
    public void discardForRun(RunId measurementRunId) {
        jdbcTemplate.update("DELETE FROM seed_corpus_comparison WHERE run_id = ?", measurementRunId.value());
    }

    private List<Proportion> proportions(
            String comparisonName,
            List<MetricRow> seedRows,
            Map<String, Long> corpusCounts,
            long seedDocumentCount,
            long corpusDocumentCount,
            Function<MetricRow, String> categoryOf) {
        Map<String, Long> seedCounts = countByCategory(seedRows, categoryOf);
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
            SignalQuartiles corpus,
            Function<MetricRow, Double> valueOf) {
        Optional<Quartiles> seedQuartiles = Quartiles.of(values(seedRows, valueOf));
        Optional<Quartiles> corpusQuartiles = corpus.quartiles();
        String statement = String.format(
                Locale.ROOT,
                "%s: %s; %s.",
                capitalize(label),
                clause("the seed set", "no usable seed document", label, seedQuartiles.orElse(null)),
                clause("the corpus", "no surviving corpus document", label, corpusQuartiles.orElse(null)));
        return new Spread(signal, seedQuartiles.orElse(null), corpusQuartiles.orElse(null), statement);
    }

    /**
     * One side of a spread's sentence — and where that side measured nothing at all, a sentence saying
     * so rather than a middle figure of zero. ADR-086's page-count rule reaches the whole population
     * and not only one document in it: a born-digital seed folder reports no page count, and "the
     * typical page count is 0.0" would be a measurement of nothing presented as a measured zero.
     *
     * @param quartiles what this side measured, or {@code null} where it measured nothing at all
     */
    private static String clause(String side, String noDocument, String label, Quartiles quartiles) {
        if (quartiles == null) {
            return String.format(Locale.ROOT, "%s reports a %s", noDocument, label);
        }
        return String.format(
                Locale.ROOT,
                "%s's typical %s is %s (%s to %s)",
                side,
                label,
                format(quartiles.median()),
                format(quartiles.lowerQuartile()),
                format(quartiles.upperQuartile()));
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
                lowerQuartileOf(spread.seed()),
                medianOf(spread.seed()),
                upperQuartileOf(spread.seed()),
                lowerQuartileOf(spread.corpus()),
                medianOf(spread.corpus()),
                upperQuartileOf(spread.corpus()),
                comparison.seedDocumentCount(),
                comparison.corpusDocumentCount(),
                comparison.unmeasuredSeedDocumentCount(),
                spread.statement());
    }

    /**
     * The three columns of one side, null together where that side measured nothing (ADR-086) — the
     * absence is stored as absence rather than as a zero any later query would read as a figure.
     */
    private static Double lowerQuartileOf(Quartiles quartiles) {
        return quartiles == null ? null : quartiles.lowerQuartile();
    }

    private static Double medianOf(Quartiles quartiles) {
        return quartiles == null ? null : quartiles.median();
    }

    private static Double upperQuartileOf(Quartiles quartiles) {
        return quartiles == null ? null : quartiles.upperQuartile();
    }

    /**
     * Counted by SQLite's progress handler (ADR-193): the read runs on the connection the template hands over
     * and is never handed back to it, so the handler counts the read and nothing else.
     */
    private Set<Long> unusableSeedIds(RunId measurementRunId, EmbeddingStatementProgress progress) {
        Set<Long> ids = new HashSet<>();
        progress.statementStarting(EmbeddingStatement.UNUSABLE_SEEDS, spanOfRun("unusable_seed", measurementRunId));
        StatementSteps.counted(
                jdbcTemplate,
                steps -> progress.stepsTaken(EmbeddingStatement.UNUSABLE_SEEDS, steps),
                connection -> {
                    try (PreparedStatement select = connection.prepareStatement(
                            "SELECT occurrence_id FROM unusable_seed WHERE run_id = ?")) {
                        select.setString(1, measurementRunId.value());
                        try (ResultSet rows = select.executeQuery()) {
                            while (rows.next()) {
                                ids.add(rows.getLong("occurrence_id"));
                            }
                        }
                    }
                    return null;
                });
        progress.statementEnded(EmbeddingStatement.UNUSABLE_SEEDS);
        return ids;
    }

    /**
     * The metric rows of {@code runId} that belong to {@code candidateIds}, read as {@code statement}, counted
     * by SQLite's progress handler on the connection the template hands over (ADR-193). Not issued, and no
     * call made, where there is no candidate.
     */
    private List<MetricRow> metricRows(
            MeasuredForms forms,
            RunId runId,
            Set<Long> candidateIds,
            EmbeddingStatement statement,
            EmbeddingStatementProgress progress) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        progress.statementStarting(statement, forms.rowsUpTo(runId));
        List<MetricRow> rows = new ArrayList<>();
        forms.each(
                runId,
                steps -> progress.stepsTaken(statement, steps),
                (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) -> {
                    if (candidateIds.contains(occurrence.value())) {
                        rows.add(new MetricRow(
                                occurrence.value(),
                                language,
                                meanScoreIsNull,
                                words,
                                pages,
                                vowelless,
                                singleCharacter));
                    }
                });
        progress.statementEnded(statement);
        return rows;
    }

    /**
     * The corpus side of the comparison: the survivors' metric rows, read a page of survivors at a time and
     * never held (ADR-211 sections 2 and 4). The first read counts the documents and the language and
     * provenance categories, and starts every signal's quartiles; each later read, reported as {@link
     * EmbeddingStatement#CORPUS_METRICS_AGAIN}, narrows what the quartiles still need, at most three more.
     */
    private CorpusSide corpusSide(
            MeasuredForms forms,
            RunId measurementRunId,
            RunId extractionRunId,
            EmbeddingStatementProgress progress) {
        CorpusSide side = new CorpusSide();
        List<SignalQuartiles> signals = List.of(side.words, side.pages, side.vowellessRatios, side.singleCharacterRatios);
        boolean firstRead = true;
        EmbeddingStatement statement = EmbeddingStatement.CORPUS_METRICS;
        while (firstRead || signals.stream().anyMatch(SignalQuartiles::needsAnotherRead)) {
            boolean counting = firstRead;
            boolean anySurvivor = readCorpusSurvivors(
                    forms,
                    measurementRunId,
                    extractionRunId,
                    statement,
                    progress,
                    (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) -> {
                        MetricRow row = new MetricRow(
                                occurrence.value(), language, meanScoreIsNull, words, pages, vowelless,
                                singleCharacter);
                        if (counting) {
                            side.documentCount++;
                            side.languageCounts.merge(
                                    row.primaryLanguage() == null ? UNDETERMINED : row.primaryLanguage(), 1L, Long::sum);
                            side.provenanceCounts.merge(row.meanScoreIsNull() ? BORN_DIGITAL : CONVERTED, 1L, Long::sum);
                        }
                        side.words.accept(row.wordCountAsDouble());
                        side.pages.accept(row.pageCountAsDouble());
                        side.vowellessRatios.accept(row.vowellessWordRatio());
                        side.singleCharacterRatios.accept(row.singleCharacterWordRatio());
                    });
            if (!anySurvivor) {
                break;
            }
            signals.forEach(SignalQuartiles::endRead);
            firstRead = false;
            statement = EmbeddingStatement.CORPUS_METRICS_AGAIN;
        }
        return side;
    }

    /**
     * One read of the metric rows of the measurement run's survivors, a page of at most 1,000 at a time: the
     * survivors from the ledger, then that page's rows from {@code forms}. Started with the span of stage 2's
     * rows once the first page has a survivor, told the rows read so far after each page, and ended. Returns
     * whether there was a survivor; with none nothing is started and no call is made.
     */
    private boolean readCorpusSurvivors(
            MeasuredForms forms,
            RunId measurementRunId,
            RunId extractionRunId,
            EmbeddingStatement statement,
            EmbeddingStatementProgress progress,
            MeasuredForms.Row row) {
        long[] rowsRead = {0};
        boolean[] started = {false};
        MeasuredForms.Row counted =
                (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) -> {
                    rowsRead[0]++;
                    row.read(occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter);
                };
        Consumer<List<OccurrenceId>> readPage = names -> {
            if (!started[0]) {
                progress.statementStarting(statement, forms.rowsUpTo(extractionRunId));
                started[0] = true;
            }
            forms.eachOf(extractionRunId, names, counted);
            progress.rowsRead(statement, rowsRead[0]);
        };
        List<OccurrenceId> page = new ArrayList<>(PAGE);
        for (OccurrenceId survivor : ledger.verdicts().survivors(measurementRunId)) {
            page.add(survivor);
            if (page.size() == PAGE) {
                readPage.accept(page);
                page.clear();
            }
        }
        if (!page.isEmpty()) {
            readPage.accept(page);
        }
        if (started[0]) {
            progress.statementEnded(statement);
        }
        return started[0];
    }

    /** What the first read of the corpus side counts, and the four signals' quartiles, found over its reads. */
    private static final class CorpusSide {

        long documentCount;
        final Map<String, Long> languageCounts = new LinkedHashMap<>();
        final Map<String, Long> provenanceCounts = new LinkedHashMap<>();
        final SignalQuartiles words = new SignalQuartiles();
        final SignalQuartiles pages = new SignalQuartiles();
        final SignalQuartiles vowellessRatios = new SignalQuartiles();
        final SignalQuartiles singleCharacterRatios = new SignalQuartiles();

        long documentCount() {
            return documentCount;
        }

        Map<String, Long> languageCounts() {
            return languageCounts;
        }

        Map<String, Long> provenanceCounts() {
            return provenanceCounts;
        }

        SignalQuartiles words() {
            return words;
        }

        SignalQuartiles pages() {
            return pages;
        }

        SignalQuartiles vowellessRatios() {
            return vowellessRatios;
        }

        SignalQuartiles singleCharacterRatios() {
            return singleCharacterRatios;
        }
    }

    /**
     * The span of {@code run}'s rowids in {@code table}, greatest less least plus one, as two statements and
     * never one (ADR-191 section 2): each is one descent of the index on {@code run_id} alone. Empty for a
     * run that holds no row. {@code table} is a constant of this class, never a caller's text, and one
     * of this module's own (ADR-209 section 3).
     */
    private OptionalLong spanOfRun(String table, RunId run) {
        Long least = jdbcTemplate.queryForObject(
                "SELECT MIN(rowid) FROM " + table + " WHERE run_id = ?", Long.class, run.value());
        Long greatest = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) FROM " + table + " WHERE run_id = ?", Long.class, run.value());
        if (least == null || greatest == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(greatest - least + 1);
    }

    /**
     * Every id of {@code occurrences} in a set: the seed walk's, which is the operator's seed folder and not
     * the corpus (ADR-211 section 10).
     */
    private static Set<Long> idsOf(Iterable<OccurrenceId> occurrences) {
        Set<Long> ids = new HashSet<>();
        for (OccurrenceId id : occurrences) {
            ids.add(id.value());
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

    /**
     * One signal's middle value and quartiles, on each side, judging neither.
     *
     * <p>Either side is {@code null} where no document in that population reported the signal at all —
     * a corpus of born-digital files has no page count, and no page count is not a page count of zero
     * (ADR-086). The columns are written null too, so a reader of the table cannot mistake the absence
     * for a measured figure either.
     */
    public record Spread(String signal, Quartiles seed, Quartiles corpus, String statement) {}

    /** A median with a quartile either side of it — never a mean (ADR-086). */
    public record Quartiles(double lowerQuartile, double median, double upperQuartile) {

        /** Empty where nothing in the population reported this signal, never a zero (ADR-086). */
        static Optional<Quartiles> of(List<Double> values) {
            if (values.isEmpty()) {
                return Optional.empty();
            }
            List<Double> sorted = values.stream().sorted().toList();
            int size = sorted.size();
            double median = middle(sorted);
            List<Double> lowerHalf = sorted.subList(0, size / 2);
            List<Double> upperHalf = sorted.subList((size + 1) / 2, size);
            double lowerQuartile = lowerHalf.isEmpty() ? median : middle(lowerHalf);
            double upperQuartile = upperHalf.isEmpty() ? median : middle(upperHalf);
            return Optional.of(new Quartiles(lowerQuartile, median, upperQuartile));
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
