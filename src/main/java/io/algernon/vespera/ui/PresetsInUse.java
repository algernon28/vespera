package io.algernon.vespera.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

/**
 * The block of Command options that shows, read-only, the presets with no profile key: a list for each run they enter,
 * each row the key and the value the working directory's file names, its tooltip the source of the value and the record
 * behind the preset (ADR-427 §1 to §4).
 */
final class PresetsInUse {

    /** The sentence a row says where the jar has not said a value, which the naming model's line says too (ADR-427 §3). */
    static final String NOT_SAID_YET = ProfileRow.NOT_SAID_YET;

    private static final String ADR_330 = "ADR-330";

    /** The twenty presets by the run they enter, each list in the order its rows stand (ADR-427 §2). */
    private static final Map<ProfileKey.Run, List<String>> KEYS_BY_RUN = keysByRun();

    private static final Map<ProfileKey.Run, String> HEADINGS = Map.of(
            ProfileKey.Run.EXTRACTION, "Stage 2: reading the text out of your files",
            ProfileKey.Run.CONTENT_REDUNDANCY, "Stage 4: finding documents that say the same thing",
            ProfileKey.Run.EMBEDDING_SCORING, "Stage 5: measuring closeness, the topics and the groups");

    /** The record behind a preset where it is not ADR-330 (ADR-427 §4). */
    private static final Map<String, String> RECORDS = Map.of(
            "vespera.topics.neighbours", "ADR-399",
            "vespera.topics.resolution", "ADR-399",
            "vespera.topics.see-also-share", "ADR-400",
            "vespera.topics.breadth-floor", "ADR-401",
            "vespera.clustering.subtopic-floor", "ADR-406");

    private final Map<String, SelectableText.Wrapping> rows = new LinkedHashMap<>();

    private static Map<ProfileKey.Run, List<String>> keysByRun() {
        Map<ProfileKey.Run, List<String>> byRun = new LinkedHashMap<>();
        byRun.put(ProfileKey.Run.EXTRACTION, List.of(
                "vespera.docling.ocr-preset",
                "vespera.docling.timeouts-in-a-row",
                "vespera.redundancy.shingle-words"));
        byRun.put(ProfileKey.Run.CONTENT_REDUNDANCY, List.of(
                "vespera.redundancy.minhash.permutations",
                "vespera.redundancy.minhash.bands",
                "vespera.redundancy.minhash.rows-per-band",
                "vespera.redundancy.minhash.seed",
                "vespera.redundancy.near-duplicate-jaccard",
                "vespera.redundancy.containment-index",
                "vespera.redundancy.rare-shingle-sample",
                "vespera.redundancy.rare-shingle-hits"));
        byRun.put(ProfileKey.Run.EMBEDDING_SCORING, List.of(
                "vespera.relevance.top-similarities",
                "vespera.topics.neighbours",
                "vespera.topics.resolution",
                "vespera.topics.breadth-floor",
                "vespera.topics.see-also-share",
                "vespera.clustering.neighbours",
                "vespera.clustering.subtopic-floor",
                "vespera.relevance.sample.bands",
                "vespera.relevance.sample.per-band"));
        return byRun;
    }

    PresetsInUse() {
        KEYS_BY_RUN.values().stream().flatMap(List::stream).forEach(key -> {
            SelectableText.Wrapping row = SelectableText.wrapping(key + "  " + NOT_SAID_YET);
            row.setId("in-use-" + key);
            row.setTooltip(new Tooltip(tooltipOf(key, Optional.empty())));
            rows.put(key, row);
        });
    }

    /** The block of §1, its lists in the cascade's order. */
    Node block() {
        VBox block = new VBox();
        block.setId("presets-in-use");
        SelectableText.Line heading = SelectableText.line("Presets in use");
        heading.getStyleClass().add("field-name");
        SelectableText.Wrapping caption = SelectableText.wrapping(
                "Vespera's settings name these, and no control here changes them.");
        caption.getStyleClass().add("small");
        block.getChildren().addAll(heading, caption);
        KEYS_BY_RUN.forEach((run, keys) -> {
            VBox list = new VBox();
            list.setId("presets-in-use-" + run.stageName());
            SelectableText.Line listHeading = SelectableText.line(HEADINGS.get(run));
            listHeading.getStyleClass().add("field-name");
            list.getChildren().add(listHeading);
            keys.forEach(key -> list.getChildren().add(rows.get(key)));
            block.getChildren().add(list);
        });
        block.getStyleClass().add("field");
        return block;
    }

    /** Sets each row's text and tooltip from the file, an empty {@code said} being no file (ADR-427 §3, §4). */
    void show(Optional<EffectiveValues> said) {
        rows.forEach((key, row) -> {
            Optional<EffectiveValues.Value> value = said.flatMap(values -> values.named(key));
            row.setText(key + "  " + value.map(EffectiveValues.Value::value).orElse(NOT_SAID_YET));
            row.getTooltip().setText(tooltipOf(key, value));
        });
    }

    private static String tooltipOf(String key, Optional<EffectiveValues.Value> said) {
        String decided = "Decided in " + RECORDS.getOrDefault(key, ADR_330) + ".";
        return said.flatMap(value -> ProfileRow.sourceInWords(value.source()))
                .map(source -> "From " + source + ". " + decided).orElse(decided);
    }
}
