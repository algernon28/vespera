package io.algernon.vespera.ui;

import java.util.List;
import java.util.Map;

/**
 * The reports the jar writes in the working directory, and the ones each profile key is read off (ADR-344 §6, which
 * keeps ADR-306 §2 and §3): the Reports section lists {@link #ALL} and a key's row lists {@link #OF_A_KEY}. A report is
 * named by its file name; the window serves none and reads none.
 */
public final class Reports {

    /**
     * The twelve analysis reports by file name, in the order of ADR-302 §3 and ADR-306 §2, without the progress pages the
     * Report tab replaces (ADR-362 §8).
     */
    public static final List<String> ALL = List.of("format-mix.html", "extraction-failures.html", "extraction-survivors.html",
            "confidence-distribution.html", "document-frequency.html", "word-count-distribution.html",
            "seed-corpus-comparison.html", "translations.html", "cluster-sizes.html", "relevance-labelling.html",
            "arrangement.html", "cluster-page-sizes.html");

    /** The reports each key is read off, in the order the README's table of values names them. */
    public static final Map<String, List<String>> OF_A_KEY = Map.ofEntries(
            Map.entry("boilerplateDocumentFrequencyFloor", List.of("document-frequency.html")),
            Map.entry("relevanceScoreFloor",
                    List.of("relevance-labelling.html", "cluster-sizes.html", "seed-corpus-comparison.html")),
            Map.entry("degenerateOutputConfidenceFloor", List.of("confidence-distribution.html")),
            Map.entry("degenerateOutputWordFloor", List.of("word-count-distribution.html")),
            Map.entry("extractionAttempt", List.of("extraction-failures.html")),
            Map.entry("arrangementApproved", List.of("arrangement.html")),
            Map.entry("logTimestampShareFloor", List.of("format-mix.html")),
            Map.entry("codeNameEndings", List.of("format-mix.html")),
            Map.entry("outOfScopeNameEndings", List.of("format-mix.html")),
            Map.entry("configurationSettingShareFloor", List.of("format-mix.html")),
            Map.entry("preferredLanguages", List.of("translations.html")),
            Map.entry("translationSimilarityFloor", List.of("translations.html")),
            Map.entry("clusterPageByteCeiling", List.of("cluster-page-sizes.html")),
            Map.entry("clusterResolution", List.of("cluster-sizes.html")));

    private Reports() {
    }
}
