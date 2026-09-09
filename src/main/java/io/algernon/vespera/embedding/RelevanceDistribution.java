package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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

    /** How the scores under {@code scoringRunId} are spread, and which documents to put to a person. */
    public Distribution measure(RunId scoringRunId) {
        // Ordered by occurrence so the list handed to the shuffle is the same list every time: a
        // deterministic draw over an order the database chose freely would not be deterministic at all.
        List<Scored> scored = scoredUnder(scoringRunId);

        double lowest = scored.stream().mapToDouble(Scored::score).min().orElseThrow();
        double highest = scored.stream().mapToDouble(Scored::score).max().orElseThrow();
        double width = (highest - lowest) / BANDS;

        List<Band> bands = new ArrayList<>();
        List<Sampled> sample = new ArrayList<>();
        for (int ordinal = 0; ordinal < BANDS; ordinal++) {
            List<Scored> inBand = new ArrayList<>();
            for (Scored candidate : scored) {
                if (bandOf(candidate.score(), lowest, width, BANDS) == ordinal) {
                    inBand.add(candidate);
                }
            }

            // Shuffled rather than taken in order: a sample taken from the front would be reproducible
            // too, and would still show a reader only one corner of the band.
            Collections.shuffle(inBand, new Random(seedFor(scoringRunId, ordinal)));
            int sampledCount = Math.min(SAMPLED_PER_BAND, inBand.size());
            for (Scored drawn : inBand.subList(0, sampledCount)) {
                sample.add(new Sampled(drawn.occurrenceId(), drawn.score(), drawn.winningSeedOccurrenceId(), ordinal));
            }

            double lowerBound = lowest + ordinal * width;
            double upperBound = ordinal == BANDS - 1 ? highest : lowerBound + width;
            bands.add(new Band(
                    ordinal,
                    lowerBound,
                    upperBound,
                    inBand.size(),
                    sampledCount,
                    SAMPLED_PER_BAND - sampledCount));
        }
        return new Distribution(scored.size(), lowest, highest, List.copyOf(bands), List.copyOf(sample));
    }

    /**
     * The embedder identity the stored vectors carry, when they all carry the same one.
     *
     * <p>ADR-084 makes a vector carry its whole embedder identity, composed from what the runtime
     * reported when the vector was made. It is knowable here and nowhere cheaper: recomputing it
     * would mean asking the runtime what it is today, which is a different question from what it was
     * when these vectors were made.
     *
     * <p>Empty when nothing has been embedded. Where more than one identity is present the rows do
     * not say which is the newest, so a caller should treat this as a stamp on a file rather than as
     * proof of what scored a document.
     */
    public Optional<String> anyEmbedderIdentity() {
        return jdbcTemplate
                .query(
                        "SELECT DISTINCT embedder_identity FROM vector ORDER BY embedder_identity",
                        (resultSet, rowNumber) -> resultSet.getString("embedder_identity"))
                .stream()
                .findFirst();
    }

    /** Every scored document under {@code runId}, in occurrence order — the rows {@link #measure} bands. */
    List<Scored> scoredUnder(RunId scoringRunId) {
        return jdbcTemplate.query(
                "SELECT occurrence_id, score, winning_seed_occurrence_id FROM relevance_score"
                        + " WHERE run_id = ? ORDER BY occurrence_id",
                (resultSet, rowNumber) -> new Scored(
                        new OccurrenceId(resultSet.getLong("occurrence_id")),
                        resultSet.getDouble("score"),
                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id"))),
                scoringRunId.value());
    }

    /**
     * How {@code answers} fall across {@code scoringRunId}'s bands, and what each candidate cut would
     * do (ADR-088, #112) — the arithmetic, never the choice.
     */
    public LabelledSpread.Spread spreadOf(RunId scoringRunId, Map<OccurrenceId, Boolean> answers) {
        return LabelledSpread.of(measure(scoringRunId), scoredUnder(scoringRunId), answers);
    }

    /**
     * The embedder identity the vectors of {@code modelName} carry, when exactly one identity
     * answers to that name — this run's own scale, as opposed to {@link #anyEmbedderIdentity}'s
     * stamp over every model the database has ever held.
     *
     * <p>Empty where nothing has been embedded under the name, and empty too where more than one
     * identity answers to it: a model whose manifest digest, dtype or dimension changed under the
     * same name has produced two scales, and naming either as the current one would be a guess. The
     * caller that asks this in order to decide whether a threshold applies reads empty as "do not
     * remove", which is the direction that loses no archive.
     */
    public Optional<String> embedderIdentityFor(String modelName) {
        List<String> identities = jdbcTemplate.query(
                "SELECT DISTINCT embedder_identity FROM vector WHERE embedder_identity LIKE ? ESCAPE '\\'"
                        + " ORDER BY embedder_identity",
                (resultSet, rowNumber) -> resultSet.getString("embedder_identity"),
                "model=" + escapeLikePattern(modelName) + ";%");
        return identities.size() == 1 ? Optional.of(identities.getFirst()) : Optional.empty();
    }

    /** Escapes {@code %}, {@code _} and the escape character itself, so a name matches only literally. */
    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * The seed one band draws with: the run id folded into 64 bits, mixed with the band.
     *
     * <p>FNV-1a rather than {@code String.hashCode}, because this has to give the same answer on
     * another machine and in another version, and because a 32-bit hash of ids that share a prefix
     * collides more readily than the sample deserves. Mixed with the ordinal so the five bands do not
     * all draw the same positions out of their own lists.
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
