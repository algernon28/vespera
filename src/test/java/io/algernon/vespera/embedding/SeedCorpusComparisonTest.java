package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * How much the seed set resembles the survivors it will be scored against (ADR-086): four
 * comparisons over stored {@code extraction_metric} columns, written under the measurement run's own
 * id and stated in words that judge nothing.
 *
 * <p>Every claim here is about the measurement, so the fixture writes {@code extraction_metric} rows
 * directly on both sides rather than converting anything — the corpus side under the extraction run
 * that produced it, the seed side under the measurement run, which is the only run a seed occurrence
 * is ever measured under (ADR-083 mints no other). ADR-086 assumed those seed rows already existed;
 * they did not, and ADR-092 settles where they come from — stage 5's seed extraction pass writes them
 * from where it already writes its unusable rows, which is the first point at which the run exists.
 * The fixture below states the shape the comparison expects to read.
 *
 * <p>The corpus side is survivors and not every occurrence (ADR-086): what is being asked is whether
 * the seeds resemble what would actually be scored, so a blocked occurrence is out of the population
 * whatever its columns say.
 *
 * <p>What this class deliberately does <em>not</em> pin is the quartile interpolation rule. ADR-086
 * settles that the figure is a median with quartiles either side of it and never a mean; which of the
 * several textbook quartile conventions is used was not decided, so the claims below bracket the
 * quartiles rather than fixing them to a value.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Seed set")
@Issue("106")
@Link(name = "ADR-086", url = Adr.SEED_CORPUS_MISMATCH_IS_MEASURED_AND_REPORTED, type = "adr")
@Link(name = "ADR-092", url = Adr.THE_SEED_SIDE_IS_MEASURED_BY_SEED_EXTRACTION, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
@Link(name = "ADR-077", url = Adr.A_REGENERATED_MEASUREMENT_IS_KEYED_PER_RUN, type = "adr")
class SeedCorpusComparisonTest {

    /**
     * The confidence a converted document comes back with here: excellent on Docling's own scale,
     * where 0.9 and above is its top grade. Chosen high on purpose — what says "converted" is that a
     * confidence figure exists at all, so a fixture whose converted documents scored badly would let a
     * value-reading implementation pass.
     */
    private static final double EXCELLENT_CONFIDENCE = 0.95;

    /** The other end of the same scale, below Docling's own 0.5 cut-point: still just as converted. */
    private static final double POOR_CONFIDENCE = 0.05;

    /** Any confidence in a language detection that did answer; nothing here reads the value. */
    private static final double DETECTION_CONFIDENCE = 0.99;

    /** Lingua's own name for the language, which is what the column stores. */
    private static final String ENGLISH = "ENGLISH";

    private static final String ITALIAN = "ITALIAN";

    /** Five ordinary documents' word counts, whose sorted middle value is exactly 120. */
    private static final int[] ORDINARY_WORD_COUNTS = {100, 110, 120, 130, 140};

    /** The third of the five sorted word counts above, and so their median. */
    private static final int MEDIAN_ORDINARY_WORD_COUNT = 120;

    /** The one 900-page scan among the memos: 900,000 words. */
    private static final int OUTLIER_WORD_COUNT = 900_000;

    /**
     * The arithmetic average of the five ordinary word counts plus the outlier — 150,100 words, which
     * is what a mean would report as the typical length of a folder of hundred-word memos.
     */
    private static final int MEAN_WITH_THE_OUTLIER = 150_100;

    /** Well below every ordinary word count's own order of magnitude, and far below that average. */
    private static final int A_THOUSAND_WORDS = 1_000;

    /** Three paginated documents' page counts, whose middle value is 20. */
    private static final int[] PAGE_COUNTS = {10, 20, 30};

    private static final double MEDIAN_PAGE_COUNT = 20d;

    /** How the sentence puts a population that reported this signal for no document at all. */
    private static final String NO_SEED_DOCUMENT_REPORTS_ONE = "no usable seed document reports a page count";

    /** The figure a reader must never be shown for a measurement nobody took (ADR-086). */
    private static final String A_PAGE_COUNT_OF_ZERO = "page count is 0.0";

    /**
     * What the median page count would collapse to if the two documents that were never paginated
     * counted as zero pages: the middle of 0, 0, 10, 20, 30.
     */
    private static final double MEDIAN_PAGE_COUNT_IF_ABSENCE_WERE_ZERO = 10d;

    /** Every damaged document has this many words, so a count divides into a ratio exactly. */
    private static final int WORDS_PER_DAMAGED_DOCUMENT = 100;

    /** Vowelless-word counts of 10, 20 and 30 words in 100: ratios of 0.1, 0.2 and 0.3. */
    private static final int[] VOWELLESS_WORD_COUNTS = {10, 20, 30};

    private static final double MEDIAN_VOWELLESS_WORD_RATIO = 0.2d;

    /** Single-character-word counts of 5, 10 and 15 words in 100: ratios of 0.05, 0.10 and 0.15. */
    private static final int[] SINGLE_CHARACTER_WORD_COUNTS = {5, 10, 15};

    private static final double MEDIAN_SINGLE_CHARACTER_WORD_RATIO = 0.10d;

    /** Nine of the ten seed documents are English, so the English seed share is 90%. */
    private static final int ENGLISH_SEEDS = 9;

    private static final int SEED_POPULATION = 10;

    /** One of the four surviving corpus documents is English, so the English corpus share is 25%. */
    private static final int CORPUS_POPULATION = 4;

    private static final String ENGLISH_SEED_PERCENTAGE = "90%";

    private static final String ENGLISH_CORPUS_PERCENTAGE = "25%";

    /** A share of the whole population, so the shares on one side add up to exactly this. */
    private static final double WHOLE_POPULATION = 1.0d;

    /** Shares are computed from counts, so they add up to within rounding of a double division. */
    private static final double SHARE_TOLERANCE = 1e-9;

    /** Both figures, and no third figure: a comparison names one side, then the other. */
    private static final int ONE_FIGURE_PER_SIDE = 2;

    /**
     * Words that would turn a stated difference into a judgement about it. ADR-086 leaves that
     * judgement to the operator, whose seed set is the thing that carries it.
     */
    private static final List<String> WORDS_THAT_JUDGE = List.of(
            "acceptable",
            "unacceptable",
            "should",
            "recommend",
            "warning",
            "problem",
            "suspicious",
            "wrong",
            "invalid",
            "poor",
            "risk");

    private static final Pattern A_FIGURE = Pattern.compile("\\d+");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Born-digital against converted")
    @DisplayName("A seed set of born-digital documents against a corpus of scans is read from the absence of a score")
    void readsBornDigitalAgainstConvertedFromTheAbsenceOfAConfidenceFigure() {
        Fixture fixture = fixture("born-digital-against-converted");
        for (int i = 0; i < 4; i++) {
            fixture.seed("memo-" + i + ".docx", MetricRow.bornDigital(ENGLISH, 200));
        }
        // Two excellent and two poor, so an implementation reading the value rather than its presence
        // has no single answer to land on: 0.95 is Docling's top grade and 0.05 its bottom, and both
        // mean the same thing here -- somebody converted the document.
        fixture.survivor("scan-0.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.survivor("scan-1.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.survivor("scan-2.pdf", MetricRow.converted(ENGLISH, 200, 10, POOR_CONFIDENCE));
        fixture.survivor("scan-3.pdf", MetricRow.converted(ENGLISH, 200, 10, POOR_CONFIDENCE));

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        SeedCorpusComparison.Proportion bornDigital =
                proportion(comparison.provenanceMix(), SeedCorpusComparison.BORN_DIGITAL);
        SeedCorpusComparison.Proportion converted =
                proportion(comparison.provenanceMix(), SeedCorpusComparison.CONVERTED);
        claim(
                "all four seed documents are reported as born-digital, because a confidence figure was"
                        + " never computed for any of them -- what marks a document as never converted is"
                        + " that no such figure exists, and a word processor's own file has none",
                () -> assertThat(bornDigital.seedShare()).isEqualTo(WHOLE_POPULATION));
        claim(
                "and all four surviving corpus documents are reported as converted, because each of them"
                        + " does carry one",
                () -> assertThat(converted.corpusShare()).isEqualTo(WHOLE_POPULATION));
        claim(
                "no corpus document is counted as born-digital, even the two whose own confidence figure"
                        + " is 0.05 -- a low figure is a badly converted document, never an unconverted one,"
                        + " so a comparison reading the figure instead of its absence would put those two on"
                        + " the wrong side",
                () -> assertThat(bornDigital.corpusShare()).isZero());
        claim(
                "and no seed document is counted as converted, even though the two comparisons together"
                        + " account for every document on each side",
                () -> assertThat(converted.seedShare()).isZero());
        claim(
                "the two figures account for the whole seed set between them, so no document sits outside"
                        + " both -- four born-digital seeds and none converted",
                () -> assertThat(bornDigital.seedDocuments() + converted.seedDocuments())
                        .isEqualTo(comparison.seedDocumentCount()));
        claim(
                "and for the whole surviving corpus likewise",
                () -> assertThat(bornDigital.corpusDocuments() + converted.corpusDocuments())
                        .isEqualTo(comparison.corpusDocumentCount()));
    }

    @Test
    @Story("Language mix")
    @DisplayName("A document whose language detection declined is its own undetermined share, not a dropped row")
    void countsDeclinedLanguageDetectionsAsTheirOwnShare() {
        Fixture fixture = fixture("undetermined-language");
        for (int i = 0; i < ENGLISH_SEEDS; i++) {
            fixture.seed("seed-" + i + ".docx", MetricRow.bornDigital(ENGLISH, 200));
        }
        fixture.seed("too-short-to-tell.docx", MetricRow.bornDigital(ENGLISH, 200).withDeclinedLanguage());
        fixture.survivor("english.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.survivor("italiano-0.pdf", MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.survivor("italiano-1.pdf", MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.survivor(
                "damaged.pdf",
                MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE).withDeclinedLanguage());

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        SeedCorpusComparison.Proportion undetermined =
                proportion(comparison.languageMix(), SeedCorpusComparison.UNDETERMINED);
        claim(
                "the one seed document nothing could be said about has a share of its own rather than"
                        + " being dropped: a seed set with a tenth of it unreadable is a fact about the seed"
                        + " set, and dropping the row would report the other nine as the whole of it",
                () -> assertThat(undetermined.seedDocuments()).isEqualTo(1));
        claim(
                "and so does the one surviving corpus document whose detection declined -- these are the"
                        + " documents most likely to be damaged scans, which is exactly what an operator"
                        + " comparing form against form needs to see",
                () -> assertThat(undetermined.corpusDocuments()).isEqualTo(1));
        claim(
                "the seed counts across every language and the undetermined share add up to all ten seed"
                        + " documents, so nothing was silently left out of the denominator",
                () -> assertThat(totalSeedDocuments(comparison.languageMix())).isEqualTo(SEED_POPULATION));
        claim(
                "and the corpus counts add up to all four surviving documents likewise",
                () -> assertThat(totalCorpusDocuments(comparison.languageMix())).isEqualTo(CORPUS_POPULATION));
        claim(
                "the seed shares add up to the whole seed set -- 1.0, not 0.9 with a tenth unaccounted"
                        + " for",
                () -> assertThat(totalSeedShare(comparison.languageMix()))
                        .isCloseTo(WHOLE_POPULATION, Offset.offset(SHARE_TOLERANCE)));
        claim(
                "and the corpus shares add up to the whole surviving corpus",
                () -> assertThat(totalCorpusShare(comparison.languageMix()))
                        .isCloseTo(WHOLE_POPULATION, Offset.offset(SHARE_TOLERANCE)));
        claim(
                "the populations the shares are taken over are the ten seeds and the four survivors"
                        + " themselves, so the denominator is stated and not implied",
                () -> assertThat(List.of(comparison.seedDocumentCount(), comparison.corpusDocumentCount()))
                        .containsExactly((long) SEED_POPULATION, (long) CORPUS_POPULATION));
    }

    @Test
    @Story("Length is a middle figure, never an average")
    @DisplayName("One 900,000-word scan among five short memos does not move the reported length")
    void reportsAMedianAndQuartilesThatOneOutlierDoesNotMove() {
        Fixture withoutTheOutlier = fixture("length-without-the-outlier");
        for (int wordCount : ORDINARY_WORD_COUNTS) {
            withoutTheOutlier.seed("memo-" + wordCount + ".docx", MetricRow.bornDigital(ENGLISH, wordCount));
        }
        withoutTheOutlier.survivor("corpus.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));

        SeedCorpusComparison.Quartiles beforeTheOutlier =
                spread(withoutTheOutlier.measure(), SeedCorpusComparison.WORD_COUNT).seed();

        claim(
                "the length reported for the five memos is 120 words, the middle value of 100, 110, 120,"
                        + " 130 and 140 sorted",
                () -> assertThat(beforeTheOutlier.median()).isEqualTo(MEDIAN_ORDINARY_WORD_COUNT));
        claim(
                "a quartile is reported either side of it, so the report says how spread out the lengths"
                        + " are and not only where their middle is",
                () -> assertThat(beforeTheOutlier.lowerQuartile()).isLessThanOrEqualTo(beforeTheOutlier.median()));
        claim(
                "with the upper quartile the other side of the middle figure",
                () -> assertThat(beforeTheOutlier.upperQuartile()).isGreaterThanOrEqualTo(beforeTheOutlier.median()));
        claim(
                "and both quartiles inside the range the five documents actually occupy, 100 to 140 words",
                () -> assertThat(List.of(beforeTheOutlier.lowerQuartile(), beforeTheOutlier.upperQuartile()))
                        .allSatisfy(quartile -> assertThat(quartile)
                                .isBetween((double) ORDINARY_WORD_COUNTS[0], (double)
                                        ORDINARY_WORD_COUNTS[ORDINARY_WORD_COUNTS.length - 1])));

        Fixture withTheOutlier = fixture("length-with-the-outlier");
        for (int wordCount : ORDINARY_WORD_COUNTS) {
            withTheOutlier.seed("memo-" + wordCount + ".docx", MetricRow.bornDigital(ENGLISH, wordCount));
        }
        withTheOutlier.seed("nine-hundred-page-scan.pdf", MetricRow.bornDigital(ENGLISH, OUTLIER_WORD_COUNT));
        withTheOutlier.survivor("corpus.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));

        SeedCorpusComparison.Quartiles afterTheOutlier =
                spread(withTheOutlier.measure(), SeedCorpusComparison.WORD_COUNT).seed();

        claim(
                "adding one 900,000-word document to the same five memos leaves the reported length"
                        + " between 120 and 130 words -- the middle of six values instead of five, and still"
                        + " a description of what a typical seed document looks like",
                () -> assertThat(afterTheOutlier.median())
                        .isBetween((double) MEDIAN_ORDINARY_WORD_COUNT, (double) ORDINARY_WORD_COUNTS[3]));
        claim(
                "and it stays under a thousand words, where the average of those same six lengths is"
                        + " 150,100 -- which is the whole reason the figure is a middle value: an average"
                        + " would report a folder of hundred-word memos as a folder of book-length documents",
                () -> assertThat(afterTheOutlier.median()).isLessThan(A_THOUSAND_WORDS));
        claim(
                "so the reported length moved by at most ten words where an average would have moved by"
                        + " nearly 150,000",
                () -> assertThat(Math.abs(afterTheOutlier.median() - beforeTheOutlier.median()))
                        .isLessThan(MEAN_WITH_THE_OUTLIER - MEDIAN_ORDINARY_WORD_COUNT));
    }

    @Test
    @Story("Length is a middle figure, never an average")
    @DisplayName("Page count is measured only over the documents that have one, never as a zero")
    void measuresPageCountOnlyWhereItWasRecorded() {
        Fixture fixture = fixture("page-count-where-recorded");
        fixture.seed("seed.docx", MetricRow.bornDigital(ENGLISH, 200));
        for (int pageCount : PAGE_COUNTS) {
            fixture.survivor(
                    "scan-" + pageCount + ".pdf", MetricRow.converted(ENGLISH, 200, pageCount, EXCELLENT_CONFIDENCE));
        }
        // Two documents that were never paginated at all, which is the .docx and .txt case: the
        // simple conversion pipeline reports no pages, and no pages is not a document of no length.
        fixture.survivor("notes-0.txt", MetricRow.bornDigital(ENGLISH, 200));
        fixture.survivor("notes-1.txt", MetricRow.bornDigital(ENGLISH, 200));

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        SeedCorpusComparison.Spread pageCount = spread(comparison, SeedCorpusComparison.PAGE_COUNT);
        SeedCorpusComparison.Quartiles pages = pageCount.corpus();
        claim(
                "the reported page count is 20, the middle of the three documents that were paginated --"
                        + " 10, 20 and 30 pages",
                () -> assertThat(pages.median()).isEqualTo(MEDIAN_PAGE_COUNT));
        claim(
                "and not 10, which is what the middle of 0, 0, 10, 20 and 30 would be if the two"
                        + " documents that were never paginated had been counted as documents of no pages:"
                        + " an absent measurement is not a measurement of nothing",
                () -> assertThat(pages.median()).isNotEqualTo(MEDIAN_PAGE_COUNT_IF_ABSENCE_WERE_ZERO));
        claim(
                "and the seed set, whose one document was never paginated either, reports no page count"
                        + " at all rather than a page count of zero: the rule reaches a whole population"
                        + " and not only one document inside one",
                () -> assertThat(pageCount.seed()).isNull());
        claim(
                "which the sentence says in words, so a reader is never shown a middle figure that was"
                        + " never measured",
                () -> assertThat(pageCount.statement())
                        .contains(NO_SEED_DOCUMENT_REPORTS_ONE)
                        .doesNotContain(A_PAGE_COUNT_OF_ZERO));
    }

    @Test
    @Story("Damage to the text is measured by proxy")
    @DisplayName("The two garbage-text ratios are reported as middle values over the counts and their denominator")
    void reportsMedianGarbageTextRatios() {
        Fixture fixture = fixture("garbage-text-ratios");
        fixture.seed("clean.docx", MetricRow.bornDigital(ENGLISH, WORDS_PER_DAMAGED_DOCUMENT));
        for (int i = 0; i < VOWELLESS_WORD_COUNTS.length; i++) {
            fixture.survivor(
                    "scan-" + i + ".pdf",
                    MetricRow.converted(ENGLISH, WORDS_PER_DAMAGED_DOCUMENT, 10, EXCELLENT_CONFIDENCE)
                            .withGarbageText(VOWELLESS_WORD_COUNTS[i], SINGLE_CHARACTER_WORD_COUNTS[i]));
        }

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        claim(
                "the vowelless-word figure for the corpus is 0.2: three documents of 100 words each with"
                        + " 10, 20 and 30 words carrying no vowel, whose middle ratio is 20 in 100 -- a word"
                        + " with no vowel in it is what a misread scan produces, and no converter reports an"
                        + " error rate to read instead",
                () -> assertThat(spread(comparison, SeedCorpusComparison.VOWELLESS_WORD_RATIO)
                                .corpus()
                                .median())
                        .isEqualTo(MEDIAN_VOWELLESS_WORD_RATIO));
        claim(
                "and the single-character-word figure is 0.10: 5, 10 and 15 one-letter words in 100, whose"
                        + " middle ratio is 10 in 100",
                () -> assertThat(spread(comparison, SeedCorpusComparison.SINGLE_CHARACTER_WORD_RATIO)
                                .corpus()
                                .median())
                        .isEqualTo(MEDIAN_SINGLE_CHARACTER_WORD_RATIO));
        claim(
                "the seed side of the same comparison is 0.0, because the one seed document has no such"
                        + " word in it -- and it is stated rather than left out, since a comparison with one"
                        + " side missing is not a comparison",
                () -> assertThat(spread(comparison, SeedCorpusComparison.VOWELLESS_WORD_RATIO)
                                .seed()
                                .median())
                        .isZero());
    }

    @Test
    @Story("Only what would be scored is compared")
    @DisplayName("A corpus document carrying a blocking verdict is outside the population compared against")
    void leavesABlockedOccurrenceOutOfTheCorpusPopulation() {
        Fixture fixture = fixture("survivors-only");
        fixture.seed("seed.docx", MetricRow.bornDigital(ENGLISH, 200));
        fixture.survivor("standing.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.blocked(
                "ruled-out.pdf",
                MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE),
                VerdictKind.EXTRACTION_FAILED);

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        claim(
                "one corpus document is compared, not two: what the comparison answers is whether the"
                        + " seeds resemble the documents that would actually be scored, and a document an"
                        + " earlier pass ruled out is never scored",
                () -> assertThat(comparison.corpusDocumentCount()).isEqualTo(1));
        claim(
                "so the language the ruled-out document was written in is not part of the corpus mix at"
                        + " all -- it would otherwise report a corpus half in Italian that nothing will read",
                () -> assertThat(comparison.languageMix().stream()
                                .map(SeedCorpusComparison.Proportion::category)
                                .toList())
                        .doesNotContain(ITALIAN));
    }

    @Test
    @Story("Only what would be scored is compared")
    @DisplayName("A seed that produced no usable text is outside the population compared against")
    void leavesAnUnusableSeedOutOfTheSeedPopulation() {
        Fixture fixture = fixture("usable-seeds-only");
        fixture.seed("usable.docx", MetricRow.bornDigital(ENGLISH, 200));
        fixture.unusableSeed("empty.pdf", MetricRow.converted(ITALIAN, 200, 10, POOR_CONFIDENCE));
        fixture.survivor("standing.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        claim(
                "one seed is compared, not two: a score is the best a document does against the seeds"
                        + " that produced text, so a seed that produced none is never scored against and"
                        + " what it looked like says nothing about what the scoring will use",
                () -> assertThat(comparison.seedDocumentCount()).isEqualTo(1));
        claim(
                "so the language that seed was written in is not part of the seed mix at all -- it would"
                        + " otherwise report a seed set half in Italian that nothing will score against",
                () -> assertThat(comparison.languageMix().stream()
                                .map(SeedCorpusComparison.Proportion::category)
                                .toList())
                        .doesNotContain(ITALIAN));
        claim(
                "and leaving it out is not a further count of documents nothing measured: it was measured,"
                        + " and the reason it is unusable is already reported to the operator by name",
                () -> assertThat(comparison.unmeasuredSeedDocumentCount()).isZero());
    }

    @Test
    @Story("Only what would be scored is compared")
    @DisplayName("A seed nothing measured is outside the population and is counted beside it")
    void leavesAnUnmeasuredSeedOutOfThePopulationAndCountsIt() {
        Fixture fixture = fixture("unmeasured-seed");
        fixture.seed("measured.docx", MetricRow.bornDigital(ENGLISH, 200));
        fixture.unmeasuredSeed("never-measured.docx");
        fixture.survivor("standing.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        claim(
                "one seed is compared: an absent measurement is not a measurement of nothing, so a seed"
                        + " nothing recorded a figure for cannot be given a share of any mix, and inventing a"
                        + " category for it would put a fact about the run inside a comparison about the form"
                        + " of documents",
                () -> assertThat(comparison.seedDocumentCount()).isEqualTo(1));
        claim(
                "but it is not left silent either -- the one seed nothing measured is counted beside the"
                        + " population, because a seed set that quietly shrank is exactly the failure an"
                        + " operator has to be told about before they read anything else here",
                () -> assertThat(comparison.unmeasuredSeedDocumentCount()).isEqualTo(1));
        claim(
                "and the count is a figure in a report and nothing more: no document is removed from"
                        + " anything and no verdict row stands anywhere as a result of it",
                () -> assertThat(verdictKindsRecorded()).isEmpty());
    }

    @Test
    @Story("Every comparison is stated in words")
    @DisplayName("Each comparison names both figures and says nothing about whether they are close enough")
    void statesBothFiguresAndJudgesNeither() {
        Fixture fixture = fixture("stated-in-words");
        for (int i = 0; i < ENGLISH_SEEDS; i++) {
            fixture.seed("seed-" + i + ".docx", MetricRow.bornDigital(ENGLISH, 200));
        }
        fixture.seed("undetermined.docx", MetricRow.bornDigital(ENGLISH, 200).withDeclinedLanguage());
        fixture.survivor("english.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.survivor("italiano-0.pdf", MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.survivor("italiano-1.pdf", MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.survivor("italiano-2.pdf", MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE));

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        List<String> statements = comparison.statements();
        claim(
                "every one of the four comparisons is stated in words, so a reader is told which of them"
                        + " diverged rather than handed two charts to compare by eye",
                () -> assertThat(statements).isNotEmpty().allSatisfy(statement -> assertThat(statement)
                        .isNotBlank()));
        claim(
                "and each statement names both sides, because a figure for one side alone says nothing"
                        + " about how alike they are",
                () -> assertThat(statements).allSatisfy(statement -> assertThat(statement)
                        .containsIgnoringCase("seed")
                        .containsIgnoringCase("corpus")));
        claim(
                "each statement carries at least two figures -- one for each side -- so no comparison"
                        + " reads as a single number with an unstated other half",
                () -> assertThat(statements).allSatisfy(statement -> assertThat(figuresIn(statement))
                        .hasSizeGreaterThanOrEqualTo(ONE_FIGURE_PER_SIDE)));
        claim(
                "the English comparison in particular carries 90% for the seed set -- nine of its ten"
                        + " documents -- as words a reader does not have to compute",
                () -> assertThat(proportion(comparison.languageMix(), ENGLISH).statement())
                        .contains(ENGLISH_SEED_PERCENTAGE));
        claim(
                "and 25% for the corpus, one of its four surviving documents, in the same sentence rather"
                        + " than in a second table the reader has to line up against the first",
                () -> assertThat(proportion(comparison.languageMix(), ENGLISH).statement())
                        .contains(ENGLISH_CORPUS_PERCENTAGE));
        claim(
                "and not one statement says whether the difference it just stated is a difference worth"
                        + " acting on: that judgement is the operator's, and the seed set is the thing they"
                        + " express it with",
                () -> assertThat(statements)
                        .allSatisfy(statement -> assertThat(statement)
                                .doesNotContainIgnoringCase(WORDS_THAT_JUDGE.toArray(String[]::new))));
    }

    @Test
    @Story("A measurement, never a judgement")
    @DisplayName("The comparison produces no single overall figure and no verdict row of any kind")
    void producesNoOverallFigureAndNoVerdictRow() {
        Fixture fixture = fixture("no-figure-no-verdict");
        fixture.seed("seed.docx", MetricRow.bornDigital(ENGLISH, 200));
        fixture.survivor("scan.pdf", MetricRow.converted(ITALIAN, 900, 40, POOR_CONFIDENCE));

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        claim(
                "the only figures the comparison hands back on its own account are the two populations it"
                        + " was taken over and how many seeds carried no measurement at all -- three counts"
                        + " of documents, and no further number summarising how different the two sides are,"
                        + " because such a number would compress the one thing anybody can act on, which"
                        + " signal diverged, into a figure nobody can",
                () -> assertThat(figureNamesOf(SeedCorpusComparison.Comparison.class))
                        .containsExactlyInAnyOrder(
                                "seedDocumentCount", "corpusDocumentCount", "unmeasuredSeedDocumentCount"));
        claim(
                "and nothing it hands back is named as an overall difference either, however it might be"
                        + " computed",
                () -> assertThat(componentNamesOf(SeedCorpusComparison.Comparison.class))
                        .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("mismatch")));
        claim(
                "no verdict row of any kind stands against any document on either side -- asserted"
                        + " against the whole closed vocabulary rather than the plausible kinds, because"
                        + " every word in it exists to remove a document from the survivor set and this"
                        + " pass removes nothing at all",
                () -> assertThat(verdictKindsRecorded()).isEmpty());
    }

    @Test
    @Story("A second measurement is a row set of its own")
    @DisplayName("Measuring again writes fresh rows under the new run and leaves the earlier run's alone")
    void writesAFreshRowSetUnderEachMeasurementRun() {
        Fixture first = fixture("first-measurement");
        first.seed("seed.docx", MetricRow.bornDigital(ENGLISH, 200));
        first.survivor("scan.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));
        first.measure();
        long firstRunRows = comparisonRowsFor(first.measurementRunId);

        Fixture second = fixture("second-measurement");
        second.seed("seed.docx", MetricRow.bornDigital(ITALIAN, 200));
        second.seed("altro.docx", MetricRow.bornDigital(ENGLISH, 200));
        second.survivor("scan.pdf", MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE));
        second.measure();

        claim(
                "the first measurement wrote rows of its own, keyed by the run that measured it",
                () -> assertThat(firstRunRows).isPositive());
        claim(
                "the second measurement's rows are keyed by its own run",
                () -> assertThat(comparisonRowsFor(second.measurementRunId)).isPositive());
        claim(
                "the first run's rows are exactly as many as they were before the second measurement ran:"
                        + " a re-measurement is a fresh row set beside the earlier one, never an overwrite,"
                        + " so what an earlier seed folder looked like stays answerable",
                () -> assertThat(comparisonRowsFor(first.measurementRunId)).isEqualTo(firstRunRows));
        claim(
                "and the two row sets together are every row in the table, so neither run's measurement"
                        + " landed anywhere the other's could reach",
                () -> assertThat(comparisonRowsFor(first.measurementRunId)
                                + comparisonRowsFor(second.measurementRunId))
                        .isEqualTo(allComparisonRows()));
        claim(
                "no row is keyed by the extraction run whose columns were read instead of by the run that"
                        + " did the measuring -- a re-measurement over the same extracted text has to be"
                        + " able to exist beside this one",
                () -> assertThat(comparisonRowsFor(first.extractionRunId)).isZero());
    }

    private SeedCorpusComparison.Proportion proportion(
            List<SeedCorpusComparison.Proportion> proportions, String category) {
        return proportions.stream()
                .filter(proportion -> proportion.category().equals(category))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the comparison reported no " + category + " share at all, among " + proportions));
    }

    private SeedCorpusComparison.Spread spread(SeedCorpusComparison.Comparison comparison, String signal) {
        return comparison.spreads().stream()
                .filter(candidate -> candidate.signal().equals(signal))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the comparison reported nothing for " + signal + ", among " + comparison.spreads()));
    }

    private long totalSeedDocuments(List<SeedCorpusComparison.Proportion> proportions) {
        return proportions.stream()
                .mapToLong(SeedCorpusComparison.Proportion::seedDocuments)
                .sum();
    }

    private long totalCorpusDocuments(List<SeedCorpusComparison.Proportion> proportions) {
        return proportions.stream()
                .mapToLong(SeedCorpusComparison.Proportion::corpusDocuments)
                .sum();
    }

    private double totalSeedShare(List<SeedCorpusComparison.Proportion> proportions) {
        return proportions.stream()
                .mapToDouble(SeedCorpusComparison.Proportion::seedShare)
                .sum();
    }

    private double totalCorpusShare(List<SeedCorpusComparison.Proportion> proportions) {
        return proportions.stream()
                .mapToDouble(SeedCorpusComparison.Proportion::corpusShare)
                .sum();
    }

    /** Every run of digits in one statement, which is how many figures it puts in front of a reader. */
    private List<String> figuresIn(String statement) {
        List<String> figures = new ArrayList<>();
        Matcher matcher = A_FIGURE.matcher(statement);
        while (matcher.find()) {
            figures.add(matcher.group());
        }
        return figures;
    }

    /** What the record's parts are called, whatever their types. */
    private List<String> componentNamesOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    /** The record's parts that are a bare number, which is what a summarising figure would be. */
    private List<String> figureNamesOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .filter(component -> component.getType().isPrimitive() || Number.class.isAssignableFrom(
                        component.getType()))
                .map(RecordComponent::getName)
                .toList();
    }

    /** Every verdict kind recorded anywhere in this test's own database, whatever it says. */
    private List<String> verdictKindsRecorded() {
        List<String> kinds = jdbcTemplate.queryForList("SELECT kind FROM verdict", String.class);
        List<String> vocabulary =
                Arrays.stream(VerdictKind.values()).map(Enum::name).toList();
        if (!vocabulary.containsAll(kinds)) {
            throw new IllegalStateException("a verdict row carries a kind outside the closed vocabulary: " + kinds);
        }
        return kinds;
    }

    private long comparisonRowsFor(RunId runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seed_corpus_comparison WHERE run_id = ?", Long.class, runId.value());
    }

    private long allComparisonRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seed_corpus_comparison", Long.class);
    }

    /**
     * One corpus walk with an extraction run over it, one seed walk, and the measurement run that
     * names the extraction run upstream — the shape stage 5 mints (ADR-089 for the upstream, ADR-083
     * for the run itself), assembled by hand because what is under test is the comparison and not the
     * wiring.
     *
     * <p>{@code name} keeps each fixture's walks and runs distinct, since a run's id is derived from
     * what it consumed and two identical runs would collide on the primary key.
     */
    private Fixture fixture(String name) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId corpusWalk = ledger.startWalk(Path.of("C:/corpus/" + name));
        WalkId seedWalk = ledger.startWalk(Path.of("C:/seeds/" + name));
        RunId extractionRunId = ledger.startRun("extraction", "extraction-" + name, "{}", corpusWalk, List.of());
        RunId measurementRunId = ledger.startRun(
                "seed-measurement", "measurement-" + name, "{}", corpusWalk, List.of(extractionRunId));
        return new Fixture(ledger, corpusWalk, seedWalk, extractionRunId, measurementRunId);
    }

    /**
     * One {@code extraction_metric} row's worth of the columns this comparison reads, so a fixture
     * line says which kind of document it is adding rather than listing twenty columns.
     */
    private record MetricRow(
            String primaryLanguage,
            Double languageConfidence,
            Double meanScore,
            int wordCount,
            Integer pageCount,
            int vowellessWordCount,
            int singleCharacterWordCount) {

        /** A word processor's own file: no confidence figure was ever computed, and no page count. */
        static MetricRow bornDigital(String language, int wordCount) {
            return new MetricRow(language, DETECTION_CONFIDENCE, null, wordCount, null, 0, 0);
        }

        /** A document somebody converted: a confidence figure exists, whatever it says. */
        static MetricRow converted(String language, int wordCount, int pageCount, double meanScore) {
            return new MetricRow(language, DETECTION_CONFIDENCE, meanScore, wordCount, pageCount, 0, 0);
        }

        /** The same document, with detection having declined to guess its language at all. */
        MetricRow withDeclinedLanguage() {
            return new MetricRow(
                    null, null, meanScore, wordCount, pageCount, vowellessWordCount, singleCharacterWordCount);
        }

        /** The same document, carrying the two counts a misread scan leaves behind. */
        MetricRow withGarbageText(int vowellessWords, int singleCharacterWords) {
            return new MetricRow(
                    primaryLanguage, languageConfidence, meanScore, wordCount, pageCount, vowellessWords,
                    singleCharacterWords);
        }
    }

    private class Fixture {
        private final Ledger ledger;
        private final WalkId corpusWalk;
        private final WalkId seedWalk;
        private final RunId extractionRunId;
        private final RunId measurementRunId;

        Fixture(Ledger ledger, WalkId corpusWalk, WalkId seedWalk, RunId extractionRunId, RunId measurementRunId) {
            this.ledger = ledger;
            this.corpusWalk = corpusWalk;
            this.seedWalk = seedWalk;
            this.extractionRunId = extractionRunId;
            this.measurementRunId = measurementRunId;
        }

        /** A seed document, measured under the measurement run — the only run a seed is measured under. */
        void seed(String path, MetricRow row) {
            insert(occurrence(seedWalk, path), measurementRunId, row);
        }

        /**
         * A seed the extractor answered for and tier 1 found unusable. It is measured like any other
         * seed — the row records what the converter reported and judges nothing (ADR-092) — and the
         * unusable row stands beside it, written where the seed pass already writes those (ADR-083).
         */
        void unusableSeed(String path, MetricRow row) {
            OccurrenceId occurrenceId = occurrence(seedWalk, path);
            insert(occurrenceId, measurementRunId, row);
            new UnusableSeeds(jdbcTemplate)
                    .record(occurrenceId, measurementRunId, "recorded unusable by this test's fixture");
        }

        /**
         * A seed occurrence the seed walk recorded and nothing ever measured: walked, and then no
         * metrics row written for it under any run.
         */
        void unmeasuredSeed(String path) {
            occurrence(seedWalk, path);
        }

        /** A corpus document carrying no blocking verdict, measured under the extraction run. */
        void survivor(String path, MetricRow row) {
            insert(occurrence(corpusWalk, path), extractionRunId, row);
        }

        /** A corpus document an earlier pass ruled out, which is therefore never scored. */
        void blocked(String path, MetricRow row, VerdictKind kind) {
            OccurrenceId occurrenceId = occurrence(corpusWalk, path);
            insert(occurrenceId, extractionRunId, row);
            ledger.verdict(occurrenceId, extractionRunId, kind, "ruled out by this test's fixture");
        }

        SeedCorpusComparison.Comparison measure() {
            return new SeedCorpusComparison(jdbcTemplate, ledger)
                    .measure(measurementRunId, extractionRunId, seedWalk);
        }

        private OccurrenceId occurrence(WalkId walkId, String path) {
            ledger.fileOccurrence(
                    walkId,
                    new OccurrencePath(path),
                    1L,
                    Instant.parse("2026-09-06T10:15:30Z"),
                    Instant.parse("2026-09-01T08:00:00Z"));
            return ledger.occurrenceId(walkId, new OccurrencePath(path))
                    .orElseThrow(() -> new IllegalStateException("the fixture's own occurrence was not recorded"));
        }

        private void insert(OccurrenceId occurrenceId, RunId runId, MetricRow row) {
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, page_count, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count, mean_score,"
                            + " primary_language, language_confidence)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrenceId.value(),
                    runId.value(),
                    "success",
                    0.5,
                    row.pageCount(),
                    row.wordCount() * 6,
                    row.wordCount() * 5,
                    row.wordCount(),
                    row.wordCount() * 5,
                    row.vowellessWordCount(),
                    row.singleCharacterWordCount(),
                    row.meanScore(),
                    row.primaryLanguage(),
                    row.languageConfidence());
        }
    }
}
