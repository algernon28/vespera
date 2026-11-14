package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The sidebar's Code sets section (ADR-344 §1, ADR-377 §8): the folders of code the profile's {@code codeSetFolders}
 * states, outside the corpus root, and nothing else, and their Save into {@code profile.yaml}. A set is named by its
 * folder, so the section offers no name; a folder under the corpus root is refused as one inside it, stage 1 finding
 * the sets there by their build files (ADR-284 §2).
 */
final class CodeSetsPane {

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final Supplier<List<CodeSetFolderChecks.Apart>> otherFolders;
    private final Runnable saved;
    private final VBox codeSets = new VBox();
    private final List<String> outside = new ArrayList<>();
    private final TextField codeSetProvenance = new TextField();
    private final SelectableText.Wrapping codeSetOutcome = SelectableText.wrapping("");

    /**
     * {@code otherFolders} are the folders of the window a folder of code must stay apart from, as they stand now, the
     * corpus root among them; {@code saved} runs after a Save has written.
     */
    CodeSetsPane(ControlPanelState state, Dialogs dialogs, WorkingFolders folders,
            Supplier<List<CodeSetFolderChecks.Apart>> otherFolders, Runnable saved) {
        this.state = state;
        this.dialogs = dialogs;
        this.folders = folders;
        this.otherFolders = otherFolders;
        this.saved = saved;
    }

    Node pane() {
        codeSets.setId("code-set-list");
        TextField folder = new TextField();
        folder.setId("code-set-folder");
        folder.setPromptText("A folder of code outside the corpus root");
        Button browse = new Button("Browse");
        browse.setId("code-set-browse");
        browse.setOnAction(event -> dialogs.folder("Choose a folder of code", folder.getText())
                .ifPresent(chosen -> folder.setText(chosen.toString())));
        Button add = new Button("+");
        add.setId("code-set-add");
        add.setOnAction(event -> {
            Optional<String> refusal = addCodeSet(folder.getText().strip());
            codeSetOutcome.setText(refusal.orElse(""));
            if (refusal.isEmpty()) {
                folder.clear();
            }
        });
        codeSetProvenance.setId("code-set-provenance");
        codeSetProvenance.setPromptText(ProfileEdit.FILLED_IN_PROVENANCE);
        Button save = new Button("Save");
        save.setId("code-sets-save");
        save.setOnAction(event -> saveCodeSets());
        codeSetOutcome.setId("code-set-outcome");
        HBox.setHgrow(folder, Priority.ALWAYS);
        VBox pane = new VBox(codeSets, row(folder, browse, add),
                row(SelectableText.line("Note saved with the folders outside"), codeSetProvenance), save, codeSetOutcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** Lists the folders of code the profile states, in its order. */
    void load(Map<ProfileKey, ProfileEdit.Stated> stated) {
        outside.clear();
        ProfileEdit.Stated codeFolders = stated.get(ProfileKey.CODE_SET_FOLDERS);
        if (codeFolders != null && codeFolders.value() != null) {
            outside.addAll(CodeSetFolderChecks.statedIn(codeFolders.value()));
        }
        codeSetProvenance.setText(ProfileEdit.provenanceShown(Optional.ofNullable(codeFolders)));
        draw();
    }

    /**
     * Adds {@code typed} to the list, or says why not: it must be the whole path of a folder that is there, apart from
     * the corpus root and the window's other folders and from every folder listed (ADR-334 §5.2).
     */
    private Optional<String> addCodeSet(String typed) {
        if (typed.isEmpty()) {
            return Optional.of("choose a folder first");
        }
        List<CodeSetFolderChecks.Apart> apart = new ArrayList<>(otherFolders.get());
        for (String each : outside) {
            apart.add(new CodeSetFolderChecks.Apart("folder already stated", each));
        }
        Optional<String> refusal = CodeSetFolderChecks.refusal(typed, apart);
        if (refusal.isPresent()) {
            return refusal;
        }
        outside.add(typed);
        draw();
        return Optional.empty();
    }

    private void draw() {
        codeSets.getChildren().clear();
        for (String each : new ArrayList<>(outside)) {
            Button remove = new Button("Remove");
            remove.setOnAction(event -> {
                outside.remove(each);
                draw();
            });
            codeSets.getChildren().add(row(SelectableText.line(each), remove));
        }
    }

    /** Writes {@code codeSetFolders} and nothing else, with a profile row's checks and words (ADR-377 §8). */
    private void saveCodeSets() {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            codeSetOutcome.setText("name the working directory first: profile.yaml lies in it");
            return;
        }
        String value = String.join(java.io.File.pathSeparator, outside);
        Map<ProfileKey, ProfileEdit.Stated> stated;
        try {
            stated = ProfileRow.statedIn(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            codeSetOutcome.setText(unreadable.getMessage() == null ? ProfileEdit.FILE_NAME + " cannot be read"
                    : unreadable.getMessage());
            return;
        }
        Optional<ProfileEdit.Stated> inFile = Optional.ofNullable(stated.get(ProfileKey.CODE_SET_FOLDERS));
        Optional<String> provenance = ProfileEdit.provenanceToSave(ProfileKey.CODE_SET_FOLDERS, value,
                codeSetProvenance.getText(), inFile);
        if (provenance.isEmpty()) {
            codeSetOutcome.setText("nothing was written: every value is as profile.yaml states it");
            return;
        }
        Optional<String> refusal = ProfileEdit.refusalOfSave(ProfileKey.CODE_SET_FOLDERS, value, provenance.get(),
                inFile, Optional.empty());
        if (refusal.isPresent()) {
            codeSetOutcome.setText(refusal.get());
            return;
        }
        Optional<String> failed = state.write(folder.get(), ProfileEdit.FILE_NAME,
                current -> ProfileEdit.rewrite(current, ProfileKey.CODE_SET_FOLDERS, value, provenance.get()));
        if (failed.isPresent()) {
            codeSetOutcome.setText(failed.get());
            return;
        }
        codeSetOutcome.setText(ProfileKey.CODE_SET_FOLDERS.key() + " saved in "
                + folder.get().resolve(ProfileEdit.FILE_NAME));
        saved.run();
    }
}
