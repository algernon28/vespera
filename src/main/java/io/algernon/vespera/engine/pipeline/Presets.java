package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.extraction.DoclingClient;
import io.algernon.vespera.engine.profile.NumericValue;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.similarity.MinHashParameters;
import io.algernon.vespera.engine.similarity.RedundancyThresholds;
import io.algernon.vespera.engine.similarity.ShingleParameters;
import java.util.Optional;
import java.util.OptionalDouble;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The sixteen presets of ADR-330 §9, as configuration names them, resolved where a run is minted and handed on: the
 * technical choices that were constants of {@code extraction}, {@code similarity} and {@code embedding}, now shipped in
 * {@code application.yaml} and overridden by Spring's own layers for a machine. It holds the profile's {@code
 * clusterResolution} over the first, where that can be acted on.
 *
 * <p>It decides nothing a stage writes: {@link StageRuns} reads what it resolves as each run is minted, writes it into
 * that run's configuration where it differs from what every earlier run was minted under, and hands it to the steps that
 * use it, so the number that names the run is the number that works (ADR-117). It is a class of {@code pipeline} that
 * resolves, as {@code EmbeddingModelGate} resolves the model, and it holds no rule.
 *
 * <p><b>It reads lazily, and a bad value does not stop the context.</b> Nothing is parsed when it is built and nothing
 * is injected as a number: {@code vespera label} and {@code vespera publish} run under the same context and read no
 * preset, so a value they do not use must not stop them (ADR-330 §9). The refusal is {@link #refusal}, asked at the
 * start of {@code vespera run}; an accessor asked of a value that is refused throws {@link PresetRefusedException} with
 * the same line. This class holds the lines and checks each bound first, so that a line can name the key; the type or
 * method of the module that uses the value refuses it again when built or called, for a caller that is not this class
 * (ADR-332 §3).
 */
@Component
class Presets {

    static final String RESOLUTION = "vespera.clustering.resolution";
    static final String NEIGHBOURS_OF_A_DOCUMENT = "vespera.clustering.neighbours";
    static final String OCR_PRESET = "vespera.docling.ocr-preset";
    static final String TIMEOUTS_IN_A_ROW = "vespera.docling.timeouts-in-a-row";
    static final String SHINGLE_WORDS = "vespera.redundancy.shingle-words";
    static final String PERMUTATIONS = "vespera.redundancy.minhash.permutations";
    static final String BANDS_OF_A_SIGNATURE = "vespera.redundancy.minhash.bands";
    static final String ROWS_PER_BAND = "vespera.redundancy.minhash.rows-per-band";
    static final String SEED = "vespera.redundancy.minhash.seed";
    static final String NEAR_DUPLICATE_JACCARD = "vespera.redundancy.near-duplicate-jaccard";
    static final String CONTAINMENT_INDEX = "vespera.redundancy.containment-index";
    static final String RARE_SHINGLE_SAMPLE = "vespera.redundancy.rare-shingle-sample";
    static final String RARE_SHINGLE_HITS = "vespera.redundancy.rare-shingle-hits";
    static final String TOP_SIMILARITIES = "vespera.relevance.top-similarities";
    static final String BANDS_IN_THE_SAMPLE = "vespera.relevance.sample.bands";
    static final String DOCUMENTS_FROM_EACH_BAND = "vespera.relevance.sample.per-band";

    private static final String A_WHOLE_NUMBER = ": a whole number from 1 to 2147483647";

    private final Environment environment;

    Presets(Environment environment) {
        this.environment = environment;
    }

    /**
     * The line for the first value of §9's table, in its order, that no run can be made under, else empty. The
     * configured resolution is not read where {@code profile} holds a finite number above 0 for it, which then names the
     * run in its place; every other preset is checked whichever stage the invocation would reach.
     */
    Optional<String> refusal(Profile profile) {
        try {
            if (ownResolution(profile).isEmpty()) {
                configuredResolution();
            }
            clusterNeighbours();
            ocrPreset();
            timeoutsInARow();
            shingleWindow();
            minHash();
            redundancyThresholds();
            topSimilarities();
            sampleBands();
            sampledPerBand();
            return Optional.empty();
        } catch (PresetRefusedException refused) {
            return Optional.of(refused.getMessage());
        }
    }

    /**
     * The resolution a partition divides at: the profile's number where it is a finite number above 0, else the one
     * configuration names. An unset, blank or unreadable value, and a number written by hand that is none of that, fall
     * through to the preset as configuration resolves it (ADR-330 §9), as the shipped one is 1.0.
     */
    double clusterResolution(Profile profile) {
        OptionalDouble own = ownResolution(profile);
        return own.isPresent() ? own.getAsDouble() : configuredResolution();
    }

    /** The profile's own resolution, where there is one that can be acted on. */
    static OptionalDouble ownResolution(Profile profile) {
        if (profile.clusterResolution().reading() instanceof NumericValue.Answered answered
                && Double.isFinite(answered.number())
                && answered.number() > 0) {
            return OptionalDouble.of(answered.number());
        }
        return OptionalDouble.empty();
    }

    private double configuredResolution() {
        String raw = text(RESOLUTION);
        try {
            double resolution = Double.parseDouble(raw.trim());
            if (Double.isFinite(resolution) && resolution > 0) {
                return resolution;
            }
        } catch (NumberFormatException notANumber) {
            // refused below, as a value above 0 that is finite is the only one that is not
        }
        throw new PresetRefusedException(RESOLUTION + " is " + raw + ": a number above 0");
    }

    /** How many neighbours each document of a partition keeps in the graph (ADR-087's k). */
    int clusterNeighbours() {
        return wholeNumber(NEIGHBOURS_OF_A_DOCUMENT);
    }

    /** The OCR preset every conversion names. */
    String ocrPreset() {
        String raw = text(OCR_PRESET);
        if (!DoclingClient.isOnePresetsName(raw)) {
            throw new PresetRefusedException(OCR_PRESET + " is \"" + raw + "\": one preset's name, of letters, digits, '-', '_'"
                    + " and '.', and never auto, which the converter resolves where nothing records it");
        }
        return raw;
    }

    /** How many timeouts in a row set an occurrence aside, held to 2 to 4. */
    int timeoutsInARow() {
        String raw = text(TIMEOUTS_IN_A_ROW);
        try {
            int count = Integer.parseInt(raw.trim());
            if (count >= 2 && count <= 4) {
                return count;
            }
        } catch (NumberFormatException notANumber) {
            // refused below
        }
        throw new PresetRefusedException(TIMEOUTS_IN_A_ROW + " is " + raw + ": a whole number from 2 to 4, so that one slow"
                + " document can still earn its verdict and the count stays below the five failures in a row that stop"
                + " the step");
    }

    /** The shingle window. */
    ShingleParameters shingleWindow() {
        return new ShingleParameters(wholeNumber(SHINGLE_WORDS));
    }

    /** The signature's permutations, bands, rows and seed, in the order of §9's table. */
    MinHashParameters minHash() {
        int permutations = wholeNumber(PERMUTATIONS);
        int bands = wholeNumber(BANDS_OF_A_SIGNATURE);
        int rows = wholeNumber(ROWS_PER_BAND);
        // In whole numbers and not as an int cuts them down: 65537 times 65537 comes out as 131073 in an int.
        if ((long) bands * rows != permutations) {
            throw new PresetRefusedException("vespera.redundancy.minhash is " + permutations + " permutations in " + bands
                    + " bands of " + rows + " rows: the bands times their rows must be the permutations");
        }
        String rawSeed = text(SEED);
        try {
            return new MinHashParameters(permutations, bands, rows, Long.parseLong(rawSeed.trim()));
        } catch (NumberFormatException notALong) {
            throw new PresetRefusedException(SEED + " is " + rawSeed
                    + ": a whole number from -9223372036854775808 to 9223372036854775807");
        }
    }

    /** The two cuts and the containment path's two counts. */
    RedundancyThresholds redundancyThresholds() {
        double jaccard = cut(NEAR_DUPLICATE_JACCARD);
        double containment = cut(CONTAINMENT_INDEX);
        int sample = wholeNumber(RARE_SHINGLE_SAMPLE);
        String rawHits = text(RARE_SHINGLE_HITS);
        try {
            int hits = Integer.parseInt(rawHits.trim());
            if (hits >= 1 && hits <= sample) {
                return new RedundancyThresholds(jaccard, containment, sample, hits);
            }
        } catch (NumberFormatException notANumber) {
            // refused below
        }
        throw new PresetRefusedException(RARE_SHINGLE_HITS + " is " + rawHits
                + ": a whole number from 1 to vespera.redundancy.rare-shingle-sample, " + sample);
    }

    /** How many of the highest similarities a relevance score is the mean of. */
    int topSimilarities() {
        return wholeNumber(TOP_SIMILARITIES);
    }

    /** How many bands the relevance report cuts the observed range into. */
    int sampleBands() {
        return wholeNumber(BANDS_IN_THE_SAMPLE);
    }

    /** How many documents each band is asked for. */
    int sampledPerBand() {
        return wholeNumber(DOCUMENTS_FROM_EACH_BAND);
    }

    private int wholeNumber(String key) {
        String raw = text(key);
        try {
            int number = Integer.parseInt(raw.trim());
            if (number >= 1) {
                return number;
            }
        } catch (NumberFormatException notANumber) {
            // refused below
        }
        throw new PresetRefusedException(key + " is " + raw + A_WHOLE_NUMBER);
    }

    private double cut(String key) {
        String raw = text(key);
        try {
            double cut = Double.parseDouble(raw.trim());
            if (cut > 0 && cut <= 1) {
                return cut;
            }
        } catch (NumberFormatException notANumber) {
            // refused below
        }
        throw new PresetRefusedException(key + " is " + raw + ": a number above 0 and at most 1");
    }

    /**
     * What configuration names, as written: none named reads as nothing, and a placeholder nothing resolves as what
     * says so, each of which every check above refuses, so that it is a line and not a stack trace.
     */
    private String text(String key) {
        try {
            String named = environment.getProperty(key);
            return named == null ? "" : named;
        } catch (IllegalArgumentException nothingResolvesIt) {
            return nothingResolvesIt.getMessage();
        }
    }

    /** A value of §9's table that no run can be made under, with the one line that says so. */
    static final class PresetRefusedException extends IllegalStateException {

        PresetRefusedException(String line) {
            super(line, null);
        }
    }
}
