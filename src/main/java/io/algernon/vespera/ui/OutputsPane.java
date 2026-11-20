package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

/**
 * The sidebar's Outputs section (ADR-434 §11): a checkbox for each output stage 6b may draw, and a Save that writes the
 * names ticked into {@code outputs} in the working directory's {@code profile.yaml}, through the same checks as every
 * other Save (ADR-330 §5). The names are those of the jar's outputs, which a test holds this list to; the control panel
 * links no class of the jar (ADR-285 §3).
 *
 * <p>Unticking {@code output-tree} unticks and disables {@code output-confluence} and {@code output-website}, which
 * draw from the HTML pages; ticking it enables them and ticks neither.
 */
final class OutputsPane {

    /** The outputs in the order the boxes stand, each by its name and its box's words. */
    static final Map<String, String> BOXES = boxes();

    /** What is drawn where the key is unset, blank or names nothing. */
    static final List<String> DEFAULT = List.of("tree", "confluence", "website");

    /** The outputs drawn from the HTML pages, which need it beside them. */
    static final List<String> DRAWN_FROM_THE_TREE = List.of("confluence", "website");

    private static Map<String, String> boxes() {
        Map<String, String> boxes = new LinkedHashMap<>();
        boxes.put("tree", "HTML pages");
        boxes.put("confluence", "Confluence");
        boxes.put("website", "Website");
        boxes.put("retrieval", "Retrieval records");
        return java.util.Collections.unmodifiableMap(boxes);
    }

    private final ControlPanelState state;
    private final WorkingFolders folders;
    private final Runnable saved;
    private final Map<String, CheckBox> checks = new LinkedHashMap<>();
    private final SelectableText.Line title = SelectableText.line(ProfileKey.OUTPUTS.title());
    private final SelectableText.Wrapping outcome = SelectableText.wrapping("");
    /** What the file stated for {@code outputs} as the section was loaded, which a Save is checked against. */
    private Optional<ProfileEdit.Stated> loaded = Optional.empty();

    OutputsPane(ControlPanelState state, WorkingFolders folders, Runnable saved) {
        this.state = state;
        this.folders = folders;
        this.saved = saved;
    }

    Node pane() {
        title.setId("outputs-title");
        title.getStyleClass().add("key-name");
        title.setTooltip(new Tooltip(ProfileKey.OUTPUTS.tooltip()));
        VBox pane = new VBox(title);
        BOXES.forEach((name, words) -> {
            CheckBox box = new CheckBox(words);
            box.setId("output-" + name);
            checks.put(name, box);
            pane.getChildren().add(box);
        });
        checks.get("tree").selectedProperty().addListener((observed, was, now) -> treeChanged(now));
        Button save = new Button("Save");
        save.setId("outputs-save");
        save.setOnAction(event -> save());
        outcome.setId("outputs-outcome");
        pane.getChildren().addAll(row(save), outcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** Ticks the boxes the working directory's profile names for {@code outputs}, the three of the default where it is unset. */
    void load(Map<ProfileKey, ProfileEdit.Stated> stated) {
        ProfileEdit.Stated key = stated.get(ProfileKey.OUTPUTS);
        loaded = Optional.ofNullable(key);
        title.getTooltip().setText(ProfileRow.tooltipOf(ProfileKey.OUTPUTS, loaded));
        List<String> named = read(key == null ? null : key.value());
        checks.forEach((name, box) -> box.setSelected(named.contains(name)));
        treeChanged(checks.get("tree").isSelected());
    }

    /** The names {@code value} gives, as the jar reads them: split at commas, stripped, lower-cased, each once. */
    static List<String> read(String value) {
        List<String> read = new ArrayList<>();
        if (value != null) {
            for (String piece : value.split(",")) {
                String name = piece.strip().toLowerCase(Locale.ROOT);
                if (!name.isEmpty() && !read.contains(name)) {
                    read.add(name);
                }
            }
        }
        return read.isEmpty() ? DEFAULT : List.copyOf(read);
    }

    private void treeChanged(boolean drawn) {
        for (String name : DRAWN_FROM_THE_TREE) {
            CheckBox box = checks.get(name);
            if (!drawn) {
                box.setSelected(false);
            }
            box.setDisable(!drawn);
        }
    }

    /** Writes the names ticked, in the boxes' order, {@code ", "} between them, as a row's Save writes its value. */
    private void save() {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            outcome.setText("name the working directory first");
            return;
        }
        List<String> ticked = new ArrayList<>();
        checks.forEach((name, box) -> {
            if (box.isSelected()) {
                ticked.add(name);
            }
        });
        if (ticked.isEmpty()) {
            outcome.setText("tick at least one output: an empty value would draw the default");
            return;
        }
        String value = String.join(", ", ticked);
        Map<ProfileKey, ProfileEdit.Stated> stated;
        try {
            stated = ProfileRow.statedIn(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            outcome.setText(String.valueOf(unreadable.getMessage()));
            return;
        }
        Optional<ProfileEdit.Stated> inFile = Optional.ofNullable(stated.get(ProfileKey.OUTPUTS));
        Optional<String> provenance = ProfileEdit.provenanceToSave(ProfileKey.OUTPUTS, value, loaded);
        if (provenance.isEmpty()) {
            outcome.setText("nothing was written: every value is as profile.yaml states it");
            return;
        }
        String drawn = ProfileEdit.drawnOf(ProfileKey.OUTPUTS, loaded.map(ProfileEdit.Stated::value).orElse(null),
                loaded.map(ProfileEdit.Stated::provenance).orElse(null));
        Optional<String> refusal = ProfileEdit.refusalOfSave(ProfileKey.OUTPUTS, value, provenance.get(), inFile,
                Optional.of(drawn));
        if (refusal.isPresent()) {
            outcome.setText(refusal.get());
            return;
        }
        Optional<String> failed = state.write(folder.get(), ProfileEdit.FILE_NAME,
                current -> ProfileEdit.rewrite(current, ProfileKey.OUTPUTS, value, provenance.get()));
        if (failed.isPresent()) {
            outcome.setText(failed.get());
            return;
        }
        outcome.setText(ProfileKey.OUTPUTS.key() + " saved in " + folder.get().resolve(ProfileEdit.FILE_NAME));
        saved.run();
    }
}
