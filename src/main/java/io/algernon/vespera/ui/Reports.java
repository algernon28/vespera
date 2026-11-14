package io.algernon.vespera.ui;

import java.util.List;
import java.util.Map;

/**
 * The reports the jar writes in the working directory, and the ones each profile key is read off (ADR-344 §6, which
 * keeps ADR-306 §2 and §3): the Reports section lists {@link #ALL} and a key's row lists {@link #OF_A_KEY}. A report is
 * named by its name, {@code format-mix} to {@code cluster-page-sizes} (ADR-362 §8); the window serves none and reads none:
 * its page is {@code <name>.html} and its JSON {@code <name>.json}, both in the working directory.
 */
public final class Reports {

    /**
     * The twelve analysis reports by name, in the order of ADR-302 §3 and ADR-306 §2, without the progress pages the
     * Report tab replaces (ADR-362 §8).
     */
    public static final List<String> ALL = List.of("format-mix", "extraction-failures", "extraction-survivors",
            "confidence-distribution", "document-frequency", "word-count-distribution",
            "seed-corpus-comparison", "translations", "cluster-sizes", "relevance-labelling",
            "arrangement", "cluster-page-sizes");

    /** Each analysis report's title by its name, in {@link #ALL}'s order (ADR-377 §5). */
    public static final Map<String, String> TITLES = titles(
            "format-mix", "What the files turned out to be",
            "extraction-failures", "Files whose text could not be read",
            "extraction-survivors", "Files whose text was read and kept",
            "confidence-distribution", "Text quality distribution",
            "document-frequency", "Repeated text across documents",
            "word-count-distribution", "Word count distribution",
            "seed-corpus-comparison", "Your documents compared with your seed documents",
            "translations", "Copies in another language",
            "cluster-sizes", "Group sizes",
            "relevance-labelling", "Choosing the relevance cut-off",
            "arrangement", "Grouping to approve",
            "cluster-page-sizes", "Group page sizes");

    /** The reports each key is read off, in the order the README's table of values names them. */
    public static final Map<String, List<String>> OF_A_KEY = Map.ofEntries(
            Map.entry("boilerplateDocumentFrequencyFloor", List.of("document-frequency")),
            Map.entry("relevanceScoreFloor",
                    List.of("relevance-labelling", "cluster-sizes", "seed-corpus-comparison")),
            Map.entry("degenerateOutputConfidenceFloor", List.of("confidence-distribution")),
            Map.entry("degenerateOutputWordFloor", List.of("word-count-distribution")),
            Map.entry("extractionAttempt", List.of("extraction-failures")),
            Map.entry("arrangementApproved", List.of("arrangement")),
            Map.entry("logTimestampShareFloor", List.of("format-mix")),
            Map.entry("codeNameEndings", List.of("format-mix")),
            Map.entry("outOfScopeNameEndings", List.of("format-mix")),
            Map.entry("configurationSettingShareFloor", List.of("format-mix")),
            Map.entry("preferredLanguages", List.of("translations")),
            Map.entry("translationSimilarityFloor", List.of("translations")),
            Map.entry("clusterPageByteCeiling", List.of("cluster-page-sizes")),
            Map.entry("clusterResolution", List.of("cluster-sizes")));

    /** The file name of {@code name}'s page, which an operator opens outside Vespera. */
    public static String pageOf(String name) {
        return name + ".html";
    }

    /** The file name of {@code name}'s JSON, which the window draws it from. */
    public static String jsonOf(String name) {
        return name + ".json";
    }

    /** The name of the report whose page is {@code file}, empty where it is no analysis report's page. */
    public static java.util.Optional<String> nameOfPage(String file) {
        return ALL.stream().filter(name -> pageOf(name).equals(file)).findFirst();
    }

    private static Map<String, String> titles(String... namesAndTitles) {
        java.util.LinkedHashMap<String, String> titles = new java.util.LinkedHashMap<>();
        for (int i = 0; i < namesAndTitles.length; i += 2) {
            titles.put(namesAndTitles[i], namesAndTitles[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(titles);
    }

    private Reports() {
    }
}
