package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.PriorityQueue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * How relevance scores are spread across a corpus, and which sixty documents a person is asked to
 * judge (ADR-088).
 *
 * <p><b>The bands are cut over the observed range</b> — the lowest and highest score actually
 * present — and never over 0 to 1. A cosine similarity over one archive usually occupies a narrow
 * part of the possible range, so bands cut over the whole of it would leave most of them empty and
 * say nothing about where a boundary might lie.
 *
 * <p><b>This measures and never refuses.</b> ADR-088 turns ADR-028's go/no-go from an engine refusal
 * into a report a person reads: a mechanical shape test would itself be an unmeasured threshold,
 * since a sound seed set very often produces a tight, unimodal distribution.
 *
 * <p><b>The sample is drawn from the run id</b>, so the same run always asks about the same sixty
 * documents. That is what lets the labelling loop span days: a person who answers half of them today
 * and half tomorrow is answering about the same documents both times, and ADR-047's
 * terminate-and-resume shape needs no state kept between the two sittings.
 *
 * <p><b>Nothing here holds the scored documents of a run (ADR-220 section 13).</b> The scores are read a
 * page at a time; each band counts its documents and keeps the twelve of least key it has met, the key of a
 * document in a band being SplitMix64's finaliser applied to the band's seed exclusive-or the document's
 * number, compared as an unsigned number. That is as uniform and as reproducible for a run as a shuffle
 * seeded from it was, and unlike a shuffle it needs no band held whole.
 */
@Component
public class RelevanceDistribution {

    /** ADR-088 fixes five, so one archive is always divided the same number of ways as another. */
    static final int BANDS = 5;

    /**
     * ADR-088 fixes twelve from each band. Sixty in total is about two hours at roughly two minutes a
     * document — the outer edge of one sitting, and the ceiling is the point: an open-ended task gets
     * abandoned halfway and leaves a threshold calibrated from a partial pass, with nothing recording
     * that it was partial.
     */
    static final int SAMPLED_PER_BAND = 12;

    /**
     * How far a score may sit below a band boundary and still be counted as being on it. Subtracting
     * two doubles and dividing rarely lands exactly on a whole number: a score of 0.30 against a
     * lowest of 0.20 and a width of 0.10 divides to 0.9999999999999998, which truncates to the band
     * below the one it belongs in. The tolerance is far smaller than any difference between two
     * scores that would matter to a reader, and far larger than the error the arithmetic introduces.
     */
    private static final double BOUNDARY_TOLERANCE = 1e-9;

    /** FNV-1a's 64-bit starting value, used to turn a run id into a seed. */
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;

    /** FNV-1a's 64-bit multiplier. */
    private static final long FNV_PRIME = 0x100000001b3L;

    /** The most scores one statement of a read brings back. */
    private static final int SCORES_IN_A_PAGE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public RelevanceDistribution(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * One band of the observed score range.
     *
     * @param ordinal its position, counting from zero at the lowest-scoring end
     * @param lowerBound the score at which it starts, inclusive
     * @param upperBound the score at which it ends, exclusive except in the highest band, which
     *     takes its own upper bound so the top-scoring document is counted rather than dropped
     * @param documentCount how many scored documents fall in it
     * @param sampledCount how many of them are put to a person, at most {@link #SAMPLED_PER_BAND}
     * @param shortfall how many it could not supply, recorded rather than made up from a neighbour —
     *     a reader comparing proportions across bands has to know which of them rests on a handful of
     *     answers
     */
    public record Band(
            int ordinal,
            double lowerBound,
            double upperBound,
            int documentCount,
            int sampledCount,
            int shortfall) {}

    /**
     * One document a person is asked to judge.
     *
     * @param occurrenceId which document
     * @param score what it scored, and therefore which band it was drawn from
     * @param winningSeedOccurrenceId the seed its score was taken against (ADR-020's argmax)
     * @param bandOrdinal the band it represents
     */
    public record Sampled(OccurrenceId occurrenceId, double score, OccurrenceId winningSeedOccurrenceId, int bandOrdinal) {}

    /**
     * The spread of scores under one scoring run, and the documents drawn from it.
     *
     * @param scoredDocumentCount how many documents carry a score at all
     * @param lowestScore the least any document scored, and where the first band starts
     * @param highestScore the most any document scored, and where the last band ends
     * @param bands the five bands, lowest first
     * @param sample the documents to be judged, in band order — sixty when every band could supply
     *     its twelve, and fewer by exactly the recorded shortfalls when one could not
     */
    public record Distribution(
            int scoredDocumentCount,
            double lowestScore,
            double highestScore,
            List<Band> bands,
            List<Sampled> sample) {}

    /**
     * How the scores under {@code scoringRunId} are spread, and which documents to put to a person.
     *
     * <p>Two reads (ADR-220 section 13): the range and count of the run's scores by one aggregate statement,
     * then the scores themselves a page of up to {@value #SCORES_IN_A_PAGE} at a time, planned by {@code
     * relevance_score_by_run_id (run_id=? AND rowid>?)} and sorting nothing. The order the rows come in decides
     * nothing: a document's key depends on the run, its band and its number alone.
     *
     * @throws NoSuchElementException where the run scored nothing
     */
    public Distribution measure(RunId scoringRunId) {
        Range range = jdbcTemplate.queryForObject(
                "SELECT MIN(score), MAX(score), COUNT(*) FROM relevance_score WHERE run_id = ?",
                (resultSet, rowNumber) ->
                        new Range(resultSet.getDouble(1), resultSet.getDouble(2), resultSet.getLong(3)),
                scoringRunId.value());
        if (range == null || range.count() == 0) {
            throw new NoSuchElementException("No value present");
        }
        double lowest = range.lowest();
        double highest = range.highest();
        long scoredCount = range.count();
        double width = (highest - lowest) / BANDS;

        long[] bandCounts = new long[BANDS];
        long[] bandSeeds = new long[BANDS];
        List<PriorityQueue<Drawn>> leastKeys = new ArrayList<>();
        for (int ordinal = 0; ordinal < BANDS; ordinal++) {
            bandSeeds[ordinal] = seedFor(scoringRunId, ordinal);
            // The greatest key of those kept stands first, so it is the one given up when a lesser comes.
            leastKeys.add(new PriorityQueue<>(
                    SAMPLED_PER_BAND + 1, Comparator.comparing(Drawn::key, Long::compareUnsigned).reversed()));
        }
        for (Scored candidate : scoredUnder(scoringRunId)) {
            int ordinal = bandOf(candidate.score(), lowest, width, BANDS);
            bandCounts[ordinal]++;
            PriorityQueue<Drawn> kept = leastKeys.get(ordinal);
            kept.add(new Drawn(candidate, keyOf(bandSeeds[ordinal], candidate.occurrenceId())));
            if (kept.size() > SAMPLED_PER_BAND) {
                kept.poll();
            }
        }

        List<Band> bands = new ArrayList<>();
        List<Sampled> sample = new ArrayList<>();
        for (int ordinal = 0; ordinal < BANDS; ordinal++) {
            List<Drawn> inAscendingKey = new ArrayList<>(leastKeys.get(ordinal));
            inAscendingKey.sort(Comparator.comparing(Drawn::key, Long::compareUnsigned));
            int sampledCount = inAscendingKey.size();
            for (Drawn drawn : inAscendingKey) {
                Scored scored = drawn.scored();
                sample.add(new Sampled(scored.occurrenceId(), scored.score(), scored.winningSeedOccurrenceId(), ordinal));
            }

            double lowerBound = lowest + ordinal * width;
            double upperBound = ordinal == BANDS - 1 ? highest : lowerBound + width;
            bands.add(new Band(
                    ordinal,
                    lowerBound,
                    upperBound,
                    (int) bandCounts[ordinal],
                    sampledCount,
                    SAMPLED_PER_BAND - sampledCount));
        }
        return new Distribution((int) scoredCount, lowest, highest, List.copyOf(bands), List.copyOf(sample));
    }

    /** The lowest and highest score of a run and how many it holds, from one aggregate statement. */
    private record Range(double lowest, double highest, long count) {}

    /** A document held for the draw of its band, with the key it is ordered by. */
    private record Drawn(Scored scored, long key) {}

    /**
     * The key of {@code occurrence} in a band whose seed is {@code bandSeed}: SplitMix64's finaliser applied
     * to the seed exclusive-or the occurrence's number. The finaliser and the exclusive-or are each one to
     * one, so no two documents of a band share a key and no tie is broken.
     */
    private static long keyOf(long bandSeed, OccurrenceId occurrence) {
        long z = bandSeed ^ occurrence.value();
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /**
     * Every scored document under {@code scoringRunId}, handed out a page of up to {@value #SCORES_IN_A_PAGE}
     * at a time in the order the scores were written, each page one statement asked for only when the page in
     * hand has been gone through (ADR-220 section 13). Nothing is held between pages and nothing needs closing;
     * each iterator starts again at the first page.
     */
    Iterable<Scored> scoredUnder(RunId scoringRunId) {
        return () -> new Iterator<>() {

            private final ArrayDeque<Scored> page = new ArrayDeque<>();
            private long after = Long.MIN_VALUE;
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                if (page.isEmpty() && !exhausted) {
                    fetchNextPage();
                }
                return !page.isEmpty();
            }

            @Override
            public Scored next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return page.poll();
            }

            private void fetchNextPage() {
                long[] last = {after};
                List<Scored> fetched = jdbcTemplate.query(
                        "SELECT rowid, occurrence_id, score, winning_seed_occurrence_id FROM relevance_score"
                                + " WHERE run_id = ? AND rowid > ? ORDER BY rowid LIMIT " + SCORES_IN_A_PAGE,
                        (resultSet, rowNumber) -> {
                            last[0] = resultSet.getLong(1);
                            return new Scored(
                                    new OccurrenceId(resultSet.getLong("occurrence_id")),
                                    resultSet.getDouble("score"),
                                    new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")));
                        },
                        scoringRunId.value(),
                        after);
                page.addAll(fetched);
                if (fetched.size() < SCORES_IN_A_PAGE) {
                    exhausted = true;
                }
                after = last[0];
            }
        };
    }

    /**
     * How {@code answers} fall across {@code scoringRunId}'s bands, and what each candidate cut would
     * do (ADR-088, #112) -- the arithmetic, never the choice. The scores are read a page at a time,
     * once, after the range is measured (ADR-220 section 13).
     */
    public LabelledSpread.Spread spreadOf(RunId scoringRunId, Map<OccurrenceId, Boolean> answers) {
        return LabelledSpread.of(measure(scoringRunId), scoredUnder(scoringRunId), answers);
    }

    /**
     * The embedder identity the vectors of {@code modelName} under {@code artefact} carry, when exactly one
     * identity answers to that name, digest and weight dtype (ADR-228).
     *
     * <p>Empty where nothing has been embedded under them, and empty too where more than one identity
     * answers: two dimensions under one digest have produced two scales, and naming either as the current
     * one would be a guess. The caller that asks this in order to decide whether a threshold applies reads
     * empty as "do not remove", which is the direction that loses no archive.
     */
    public Optional<String> embedderIdentityFor(String modelName, ModelArtefact artefact) {
        // One identity answers to the name, digest and weight dtype when the least and the greatest are the same (ADR-224 section 1).
        return jdbcTemplate.queryForObject(
                "SELECT MIN(embedder_identity), MAX(embedder_identity) FROM vector"
                        + " WHERE embedder_identity LIKE ? ESCAPE '\\'",
                (resultSet, rowNumber) -> {
                    String least = resultSet.getString(1);
                    return least != null && least.equals(resultSet.getString(2))
                            ? Optional.of(least)
                            : Optional.<String>empty();
                },
                EmbedderIdentity.likePatternFor(modelName, artefact));
    }

    /**
     * The seed one band draws with: the run id folded into 64 bits, mixed with the band.
     *
     * <p>FNV-1a rather than {@code String.hashCode}, because this has to give the same answer on
     * another machine and in another version, and because a 32-bit hash of ids that share a prefix
     * collides more readily than the sample deserves. Mixed with the ordinal so each band gives its
     * occurrences keys of its own (ADR-220 section 13).
     */
    private static long seedFor(RunId scoringRunId, int bandOrdinal) {
        long hash = FNV_OFFSET_BASIS;
        for (byte b : scoringRunId.value().getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (b & 0xff)) * FNV_PRIME;
        }
        return hash + bandOrdinal;
    }

    /**
     * Which band {@code score} falls in: its offset from the lowest score, in band widths, with the
     * highest band taking everything from its lower bound upwards so the top score is not left out.
     *
     * <p>Every score sits in one band when the range has any width at all. When it has none — every
     * document scored identically — there is nothing to divide, and they all belong to the one band
     * rather than to five that cannot be told apart.
     */
    static int bandOf(double score, double lowest, double width, int bands) {
        if (width == 0) {
            return bands - 1;
        }
        return Math.min((int) ((score - lowest) / width + BOUNDARY_TOLERANCE), bands - 1);
    }

    /** One scored document, as read back — package-visible so {@link LabelledSpread} can band it too. */
    record Scored(OccurrenceId occurrenceId, double score, OccurrenceId winningSeedOccurrenceId) {}
}
