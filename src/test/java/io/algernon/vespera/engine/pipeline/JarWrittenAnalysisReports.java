package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.LabelledSpread;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * The analysis reports as the jar writes them over a fixture, for the tests of the control panel that open them (ADR-362
 * section 10): public, because those tests lie in the control panel's package and the writers lie in this one. A method
 * is added by the ticket of each report's native view.
 */
public final class JarWrittenAnalysisReports {

    /** A word no part of a report's own wording holds, put in each document's path. */
    static final String MARKER = "ZXQ-private-path-word";

    /** The instant every report of the fixture is written at. */
    public static final Instant WRITTEN_AT = Instant.parse("2026-11-09T10:15:30.123Z");

    private JarWrittenAnalysisReports() {
    }

    /** Writes {@code relevance-labelling.html} and its JSON into {@code directory} over the fixture of five bands. */
    public static void relevanceLabelling(Path directory) throws IOException {
        RelevanceLabellingReport.write(directory, aDistribution(), previews(), aSpread(), null, WRITTEN_AT);
    }

    static RelevanceDistribution.Distribution aDistribution() {
        List<RelevanceDistribution.Band> bands = List.of(
                new RelevanceDistribution.Band(0, 0.80, 0.82, 40, 12, 0),
                new RelevanceDistribution.Band(1, 0.82, 0.84, 44, 12, 1),
                new RelevanceDistribution.Band(2, 0.84, 0.86, 39, 12, 2),
                new RelevanceDistribution.Band(3, 0.86, 0.88, 41, 12, 0),
                new RelevanceDistribution.Band(4, 0.88, 0.90, 36, 12, 3));
        return new RelevanceDistribution.Distribution(200, 0.80, 0.90, bands,
                List.of(new RelevanceDistribution.Sampled(new OccurrenceId(1), 0.83, new OccurrenceId(2), 1)));
    }

    static LabelledSpread.Spread aSpread() {
        return new LabelledSpread.Spread(
                List.of(new LabelledSpread.BandLabels(0, 1, 1), new LabelledSpread.BandLabels(1, 2, 1)),
                List.of(new LabelledSpread.CandidateCut(0.84, 120, 80, 3, 2, 1, 0)),
                3);
    }

    static List<RelevanceLabellingReport.Preview> previews() {
        return List.of(new RelevanceLabellingReport.Preview(
                new OccurrenceId(1),
                "reports/" + MARKER + ".pdf",
                "seeds/exemplar.pdf",
                "The quarterly figures were tabled on the third."));
    }
}
