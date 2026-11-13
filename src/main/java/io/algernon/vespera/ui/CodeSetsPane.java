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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The sidebar's Code sets section: the folders of code outside the corpus root that {@code codeSetFolders} states, and
 * their Save into the working directory (ADR-344 §1, as ADR-377 §8 amends it). It opens with {@code codeSetFolders}'
 * title, whose tooltip holds the provenance the file states; it lists those folders alone, a folder under the corpus
 * root being refused, since stage 1 finds the sets under it by their build files and nothing names them (ADR-377 §8,
 * §9); and its Save writes {@code codeSetFolders} by ADR-377 §2 and nothing else, refused where the file changed the
 * value since the section was loaded (ADR-330 §5).
 */
final class CodeSetsPane {

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final Supplier<List<CodeSetFolderChecks.Apart>> otherFolders;
    private final Runnable saved;
    private final VBox codeSets = new VBox();
    /** The folders outside the corpus root, as listed now, in order. */
    private final List<String> outsideFolders = new ArrayList<>();
    private final SelectableText.Line codeSetTitle = SelectableText.line(ProfileKey.CODE_SET_FOLDERS.title());
    private final SelectableText.Wrapping codeSetOutcome = SelectableText.wrapping("");
    /** What the file stated for {@code codeSetFolders} as the section was loaded, which a Save is checked against. */
    private Optional<ProfileEdit.Stated> loaded = Optional.empty();

    /**
     * {@code otherFolders} are the folders of the window a code set outside the corpus root must stay apart from, the
     * corpus root among them, as they stand now; {@code saved} runs after a Save has written.
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
            Optional<String> refusal = addFolder(folder.getText().strip());
            codeSetOutcome.setText(refusal.orElse(""));
            if (refusal.isEmpty()) {
                folder.clear();
            }
        });
        codeSetTitle.setId("code-set-title");
        codeSetTitle.getStyleClass().add("key-name");
        codeSetTitle.setTooltip(new Tooltip(ProfileKey.CODE_SET_FOLDERS.tooltip()));
        Button save = new Button("Save");
        save.setId("code-sets-save");
        save.setOnAction(event -> saveCodeSets());
        codeSetOutcome.setId("code-set-outcome");
        HBox.setHgrow(folder, Priority.ALWAYS);
        VBox pane = new VBox(codeSetTitle, codeSets, row(folder, browse, add), save, codeSetOutcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** Lists the folders outside the corpus root the working directory's profile states for {@code codeSetFolders}. */
    void load(Map<ProfileKey, ProfileEdit.Stated> stated) {
        outsideFolders.clear();
        ProfileEdit.Stated codeFolders = stated.get(ProfileKey.CODE_SET_FOLDERS);
        if (codeFolders != null && codeFolders.value() != null) {
            outsideFolders.addAll(CodeSetFolderChecks.statedIn(codeFolders.value()));
        }
        loaded = Optional.ofNullable(codeFolders);
        codeSetTitle.getTooltip().setText(ProfileRow.tooltipOf(ProfileKey.CODE_SET_FOLDERS, loaded));
        draw();
    }

    /**
     * Adds {@code typed} where {@link CodeSetFolderChecks#refusal} refuses it nothing: a folder under the corpus root is
     * refused as inside it, as every folder the window keeps apart is (ADR-377 §8).
     */
    private Optional<String> addFolder(String typed) {
        if (typed.isEmpty()) {
            return Optional.of("choose a folder first");
        }
        List<CodeSetFolderChecks.Apart> apart = new ArrayList<>(otherFolders.get());
        for (String each : outsideFolders) {
            apart.add(new CodeSetFolderChecks.Apart("folder already stated", each));
        }
        Optional<String> refusal = CodeSetFolderChecks.refusal(typed, apart);
        if (refusal.isPresent()) {
            return refusal;
        }
        outsideFolders.add(typed);
        draw();
        return Optional.empty();
    }

    private void draw() {
        codeSets.getChildren().clear();
        for (String each : new ArrayList<>(outsideFolders)) {
            Button remove = new Button("Remove");
            remove.setOnAction(event -> {
                outsideFolders.remove(each);
                draw();
            });
            codeSets.getChildren().add(row(SelectableText.line(each), remove));
        }
    }

    /** Writes {@code codeSetFolders} by ADR-377 §2, in {@code ProfileRow.save}'s words, and nothing else (ADR-377 §8). */
    private void saveCodeSets() {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            codeSetOutcome.setText("name the working directory first");
            return;
        }
        String outside = String.join(java.io.File.pathSeparator, outsideFolders);
        Map<ProfileKey, ProfileEdit.Stated> stated;
        try {
            stated = ProfileRow.statedIn(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            codeSetOutcome.setText(String.valueOf(unreadable.getMessage()));
            return;
        }
        // compared with what the section was loaded from, and refused where the file moved since (ADR-377 section 8)
        Optional<ProfileEdit.Stated> inFile = Optional.ofNullable(stated.get(ProfileKey.CODE_SET_FOLDERS));
        Optional<String> provenance = ProfileEdit.provenanceToSave(ProfileKey.CODE_SET_FOLDERS, outside, loaded);
        if (provenance.isEmpty()) {
            codeSetOutcome.setText("nothing was written: every value is as profile.yaml states it");
            return;
        }
        String drawn = ProfileEdit.drawnOf(ProfileKey.CODE_SET_FOLDERS, loaded.map(ProfileEdit.Stated::value).orElse(null),
                loaded.map(ProfileEdit.Stated::provenance).orElse(null));
        Optional<String> refusal = ProfileEdit.refusalOfSave(ProfileKey.CODE_SET_FOLDERS, outside, provenance.get(),
                inFile, Optional.of(drawn));
        if (refusal.isPresent()) {
            codeSetOutcome.setText(refusal.get());
            return;
        }
        Optional<String> failed = state.write(folder.get(), ProfileEdit.FILE_NAME,
                current -> ProfileEdit.rewrite(current, ProfileKey.CODE_SET_FOLDERS, outside, provenance.get()));
        if (failed.isPresent()) {
            codeSetOutcome.setText(failed.get());
            return;
        }
        codeSetOutcome.setText(ProfileKey.CODE_SET_FOLDERS.key() + " saved in "
                + folder.get().resolve(ProfileEdit.FILE_NAME));
        saved.run();
    }
}
