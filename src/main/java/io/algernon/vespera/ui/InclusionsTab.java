package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The Inclusions tab: the corpus root's tree with each folder the profile does not walk unticked, and the folders not relevant (ADR-345 §2). */
final class InclusionsTab {

    private final ControlPanelState state;
    private final WorkingFolders folders;
    private final Runnable saved;
    private final NotRelevantPane notRelevant;
    private final InclusionsTree inclusions = new InclusionsTree();
    private final SelectableText.Wrapping inclusionsOutcome = SelectableText.wrapping("");
    private List<String> notWalkedListed = List.of();

    /** {@code saved} runs after a Save has written the profile. */
    InclusionsTab(ControlPanelState state, Dialogs dialogs, WorkingFolders folders, Runnable saved) {
        this.state = state;
        this.folders = folders;
        this.saved = saved;
        this.notRelevant = new NotRelevantPane(state, dialogs, folders);
    }

    Node content() {
        Button save = new Button("Save");
        save.setId("inclusions-save");
        Button reset = new Button("Reset");
        reset.setId("inclusions-reset");
        inclusions.view().setId("inclusions-tree");
        save.setOnAction(event -> saveInclusions());
        reset.setOnAction(event -> {
            draw();
            inclusionsOutcome.setText("the tree is back to profile.yaml as last saved");
        });
        inclusionsOutcome.setId("inclusions-outcome");
        VBox.setVgrow(inclusions.view(), Priority.ALWAYS);
        SelectableText.Wrapping words = SelectableText.wrapping("Untick a folder holding nothing you want. An unticked folder is never read,"
                + " and a change makes the next walk another, so every run after it is new.");
        VBox content = new VBox(words, inclusions.view(), inclusionsOutcome, row(save, reset), notRelevant.pane());
        content.getStyleClass().add("tab-content");
        return content;
    }

    /**
     * Draws the tree of the corpus root the field names, with each folder the profile's {@code foldersNotWalked} lists
     * unticked (ADR-345 §2); a value the walk cannot read is drawn as the walk reads it, every folder ticked, and said.
     */
    void draw() {
        List<String> listed = List.of();
        try {
            Optional<String> text = folders.readFile(ProfileEdit.FILE_NAME);
            ProfileEdit.Stated stated = text.isEmpty() ? null
                    : ProfileEdit.read(text.get()).get(ProfileKey.FOLDERS_NOT_WALKED);
            if (stated != null && stated.value() != null && !stated.value().isBlank()) {
                if (ProfileEdit.isReadable(ProfileKey.FOLDERS_NOT_WALKED, stated.value())) {
                    listed = List.of(stated.value().split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                            .stream().map(String::strip).filter(piece -> !piece.isEmpty()).toList();
                } else {
                    inclusionsOutcome.setText(ProfileKey.FOLDERS_NOT_WALKED.key() + " in " + ProfileEdit.FILE_NAME
                            + " cannot be read, so the walk reads every folder and every folder is drawn ticked; a Save"
                            + " takes the value out");
                }
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            inclusionsOutcome.setText(String.valueOf(unreadable.getMessage()));
        }
        notWalkedListed = listed;
        inclusions.draw(folders.corpusRoot(), listed);
        notRelevant.draw();
    }

    /**
     * Writes the profile's {@code foldersNotWalked} and nothing else (ADR-345 §2): the highest unticked folder of each
     * branch, the pieces already listed first; a value as the file states it is not written, a changed one is written
     * with the provenance filled in, and with no folder unticked and none kept the key is taken out.
     */
    private void saveInclusions() {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            inclusionsOutcome.setText("name the working directory first");
            return;
        }
        ProfileKey key = ProfileKey.FOLDERS_NOT_WALKED;
        Map<ProfileKey, ProfileEdit.Stated> stated;
        try {
            stated = ProfileRow.statedIn(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            inclusionsOutcome.setText(String.valueOf(unreadable.getMessage()));
            return;
        }
        Optional<ProfileEdit.Stated> inFile = Optional.ofNullable(stated.get(key));
        List<String> pieces = inclusions.toWrite(notWalkedListed);
        String saidSo = "; the next walk is another, so every run after it is new";
        if (pieces.isEmpty()) {
            if (inFile.map(ProfileEdit.Stated::value).filter(value -> value != null && !value.isBlank()).isEmpty()) {
                inclusionsOutcome.setText("nothing was written: every folder is ticked and the profile lists none");
                return;
            }
            Optional<String> failed = state.write(folder.get(), ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.without(current.orElseThrow(), key));
            inclusionsOutcome.setText(failed.orElse(key.key() + " is taken out of " + folder.get().resolve(
                    ProfileEdit.FILE_NAME) + saidSo));
            saved.run();
            return;
        }
        String value = String.join(java.io.File.pathSeparator, pieces);
        Optional<String> provenance = ProfileEdit.provenanceToSave(key, value, ProfileEdit.provenanceShown(inFile), inFile);
        if (provenance.isEmpty()) {
            inclusionsOutcome.setText("nothing was written: the folders not walked are as " + ProfileEdit.FILE_NAME
                    + " states them");
            return;
        }
        Optional<String> refusal = ProfileEdit.refusalOfSave(key, value, provenance.get(), inFile, Optional.empty());
        if (refusal.isPresent()) {
            inclusionsOutcome.setText(refusal.get());
            return;
        }
        Optional<String> failed = state.write(folder.get(), ProfileEdit.FILE_NAME,
                current -> ProfileEdit.rewrite(current, key, value, provenance.get()));
        inclusionsOutcome.setText(failed.orElse(key.key() + " saved in " + folder.get().resolve(ProfileEdit.FILE_NAME)
                + saidSo));
        if (failed.isEmpty()) {
            saved.run();
        }
    }
}
