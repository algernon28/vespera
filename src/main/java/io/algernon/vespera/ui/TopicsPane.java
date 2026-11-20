package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The sidebar's Topics section: the names {@code topics} states, one a row in the order the file lists them, and their
 * Save into the working directory (ADR-435 §9). Built as the Code sets section is (ADR-377 §8): it opens with {@code
 * topics}' title, whose tooltip holds the provenance the file states; Add puts a name at the end and is disabled for a
 * blank name and for one that is a listed topic again, saying why; and Save writes {@code topics} by ADR-377 §2 and
 * nothing else, refused where the file changed the value since the section was loaded (ADR-330 §5).
 *
 * <p>A line the jar would refuse, blank or a topic again, is drawn as a row of its own in the error colour with the
 * reason as its tooltip, and is taken out by Remove.
 */
final class TopicsPane {

    /** The style class a row the jar would refuse carries, drawn in the error colour. */
    static final String REFUSED = "refused";

    private final ControlPanelState state;
    private final WorkingFolders folders;
    private final Runnable saved;
    private final VBox topicList = new VBox();
    /** The entries as listed now, one a line, in order, as written. */
    private final List<String> entries = new ArrayList<>();
    private final SelectableText.Line topicTitle = SelectableText.line(ProfileKey.TOPICS.title());
    private final SelectableText.Line warning = SelectableText.line("");
    private final SelectableText.Wrapping topicOutcome = SelectableText.wrapping("");
    private final TextField name = new TextField();
    private final Button add = new Button("Add");
    /** What the file stated for {@code topics} as the section was loaded, which a Save is checked against. */
    private Optional<ProfileEdit.Stated> loaded = Optional.empty();

    /** {@code saved} runs after a Save has written. */
    TopicsPane(ControlPanelState state, WorkingFolders folders, Runnable saved) {
        this.state = state;
        this.folders = folders;
        this.saved = saved;
    }

    Node pane() {
        topicList.setId("topic-list");
        name.setId("topic-name");
        name.setPromptText("A topic's name");
        name.textProperty().addListener((observed, was, now) -> check());
        add.setId("topic-add");
        add.setOnAction(event -> {
            check();
            if (add.isDisabled()) {
                return;
            }
            entries.add(name.getText().strip());
            name.clear();
            topicOutcome.setText("");
            draw();
        });
        warning.setId("topic-warning");
        topicTitle.setId("topic-title");
        topicTitle.getStyleClass().add("key-name");
        topicTitle.setTooltip(new Tooltip(ProfileKey.TOPICS.tooltip()));
        Button save = new Button("Save");
        save.setId("topics-save");
        save.setOnAction(event -> saveTopics());
        topicOutcome.setId("topic-outcome");
        HBox.setHgrow(name, Priority.ALWAYS);
        check();
        VBox pane = new VBox(topicTitle, topicList, row(name, add), warning, save, topicOutcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** Lists the names the working directory's profile states for {@code topics}, a line the jar would refuse among them. */
    void load(Map<ProfileKey, ProfileEdit.Stated> stated) {
        entries.clear();
        ProfileEdit.Stated topics = stated.get(ProfileKey.TOPICS);
        if (topics != null && topics.value() != null) {
            entries.addAll(TopicEntries.linesOf(topics.value()));
        }
        loaded = Optional.ofNullable(topics);
        topicTitle.getTooltip().setText(ProfileRow.tooltipOf(ProfileKey.TOPICS, loaded));
        draw();
    }

    /** Sets Add and the line beneath the box by what the box holds against the names listed (ADR-435 §9). */
    private void check() {
        String typed = name.getText() == null ? "" : name.getText();
        if (typed.isEmpty()) {
            add.setDisable(true);
            warning.setText("");
        } else if (typed.isBlank()) {
            add.setDisable(true);
            warning.setText("A topic needs a name.");
        } else {
            String plain = TopicEntries.identityOf(typed);
            Optional<String> listed = entries.stream().map(String::strip)
                    .filter(each -> !each.isEmpty() && TopicEntries.identityOf(each).equals(plain)).findFirst();
            add.setDisable(listed.isPresent());
            warning.setText(listed.map(each -> each + " is already a topic").orElse(""));
        }
    }

    private void draw() {
        topicList.getChildren().clear();
        List<String> shown = new ArrayList<>(entries);
        List<Optional<String>> refusals = TopicEntries.refusalsOf(shown);
        for (int i = 0; i < shown.size(); i++) {
            int at = i;
            Button remove = new Button("Remove");
            remove.setOnAction(event -> {
                entries.remove(at);
                draw();
            });
            HBox each = row(SelectableText.line(shown.get(i).strip()), remove);
            if (refusals.get(i).isPresent()) {
                each.getStyleClass().add(REFUSED);
                Tooltip.install(each, new Tooltip(refusals.get(i).get()));
            }
            topicList.getChildren().add(each);
        }
        check();
    }

    /** Writes {@code topics} by ADR-377 §2, in {@code ProfileRow.save}'s words, and nothing else (ADR-435 §9). */
    private void saveTopics() {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            topicOutcome.setText("name the working directory first");
            return;
        }
        String listed = String.join("\n", entries.stream().map(String::strip).toList());
        Map<ProfileKey, ProfileEdit.Stated> stated;
        try {
            stated = ProfileRow.statedIn(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            topicOutcome.setText(String.valueOf(unreadable.getMessage()));
            return;
        }
        // compared with what the section was loaded from, and refused where the file moved since (ADR-330 §5)
        Optional<ProfileEdit.Stated> inFile = Optional.ofNullable(stated.get(ProfileKey.TOPICS));
        Optional<String> provenance = ProfileEdit.provenanceToSave(ProfileKey.TOPICS, listed, loaded);
        if (provenance.isEmpty()) {
            topicOutcome.setText("nothing was written: every value is as profile.yaml states it");
            return;
        }
        String drawn = ProfileEdit.drawnOf(ProfileKey.TOPICS, loaded.map(ProfileEdit.Stated::value).orElse(null),
                loaded.map(ProfileEdit.Stated::provenance).orElse(null));
        Optional<String> refusal = ProfileEdit.refusalOfSave(ProfileKey.TOPICS, listed, provenance.get(), inFile,
                Optional.of(drawn));
        if (refusal.isPresent()) {
            topicOutcome.setText(refusal.get());
            return;
        }
        Optional<String> failed = state.write(folder.get(), ProfileEdit.FILE_NAME,
                current -> ProfileEdit.rewrite(current, ProfileKey.TOPICS, listed, provenance.get()));
        if (failed.isPresent()) {
            topicOutcome.setText(failed.get());
            return;
        }
        topicOutcome.setText(ProfileKey.TOPICS.key() + " saved in " + folder.get().resolve(ProfileEdit.FILE_NAME));
        saved.run();
    }
}
