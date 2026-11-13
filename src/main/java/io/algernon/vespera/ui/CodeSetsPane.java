package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;

import java.io.IOException;
import java.nio.file.Files;
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
 * The sidebar's Code sets section: the code sets the operator lists, a folder under the corpus root with a name or one
 * outside it, and their Save into the working directory (ADR-344 §1). It opens with {@code codeSetFolders}' title, whose
 * tooltip holds the provenance the file states, and its Save writes {@code codeSetFolders} by ADR-377 §2, refused
 * where the file changed the value since the section was loaded (ADR-377 §8, ADR-330 §5).
 */
final class CodeSetsPane {

    /** A code set the operator listed: a folder under the corpus root with a name, or one outside it. */
    private record CodeSet(String folder, String name, boolean outside) {
    }

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final Supplier<List<CodeSetFolderChecks.Apart>> otherFolders;
    private final Runnable saved;
    private final VBox codeSets = new VBox();
    private final List<CodeSet> codeSetEntries = new ArrayList<>();
    private final SelectableText.Line codeSetTitle = SelectableText.line(ProfileKey.CODE_SET_FOLDERS.title());
    private final SelectableText.Wrapping codeSetOutcome = SelectableText.wrapping("");
    /** What the file stated for {@code codeSetFolders} as the section was loaded, which a Save is checked against. */
    private Optional<ProfileEdit.Stated> loaded = Optional.empty();

    /**
     * {@code otherFolders} are the folders of the window a code set outside the corpus root must stay apart from, as
     * they stand now; {@code saved} runs after a Save has written.
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
        folder.setPromptText("A folder, under the corpus root or outside it");
        TextField name = new TextField();
        name.setId("code-set-name");
        name.setPromptText("A name for the deliverable (optional)");
        Button browse = new Button("Browse");
        browse.setId("code-set-browse");
        browse.setOnAction(event -> dialogs.folder("Choose a folder of code", folder.getText())
                .ifPresent(chosen -> folder.setText(chosen.toString())));
        Button add = new Button("+");
        add.setId("code-set-add");
        add.setOnAction(event -> {
            Optional<String> refusal = addCodeSet(folder.getText().strip(), name.getText().strip());
            codeSetOutcome.setText(refusal.orElse(""));
            if (refusal.isEmpty()) {
                folder.clear();
                name.clear();
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
        VBox pane = new VBox(codeSetTitle, codeSets, row(folder, browse, add), name, save, codeSetOutcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** Lists the code sets the working directory's files state: the renames, then the folders outside the corpus root. */
    void load(Map<ProfileKey, ProfileEdit.Stated> stated) {
        codeSetEntries.clear();
        try {
            for (CodeSetRenames.Rename rename : CodeSetRenames.read(folders.readFile(CodeSetRenames.FILE_NAME))) {
                codeSetEntries.add(new CodeSet(rename.folder(), rename.name(), false));
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            codeSetOutcome.setText(String.valueOf(unreadable.getMessage()));
        }
        ProfileEdit.Stated codeFolders = stated.get(ProfileKey.CODE_SET_FOLDERS);
        if (codeFolders != null && codeFolders.value() != null) {
            CodeSetFolderChecks.statedIn(codeFolders.value()).forEach(each -> codeSetEntries.add(new CodeSet(each, "", true)));
        }
        loaded = Optional.ofNullable(codeFolders);
        codeSetTitle.getTooltip().setText(ProfileRow.tooltipOf(ProfileKey.CODE_SET_FOLDERS, loaded));
        draw();
    }

    private Optional<String> addCodeSet(String typed, String name) {
        if (typed.isEmpty()) {
            return Optional.of("choose a folder first");
        }
        Optional<Path> chosen = InclusionsTree.pathOf(typed);
        if (chosen.isEmpty() || !chosen.get().isAbsolute()) {
            return Optional.of("the folder " + typed + " is not added: it is not the whole path of a folder");
        }
        Optional<Path> root = folders.corpusRoot();
        Path real = realOf(chosen.get());
        if (root.isPresent() && Files.isDirectory(root.get())) {
            Path realRoot = realOf(root.get());
            if (real.startsWith(realRoot) && !real.equals(realRoot)) {
                String relative = realRoot.relativize(real).toString().replace('\\', '/');
                codeSetEntries.add(new CodeSet(relative, name, false));
                draw();
                return Optional.empty();
            }
        }
        List<CodeSetFolderChecks.Apart> apart = new ArrayList<>(otherFolders.get());
        for (CodeSet each : codeSetEntries) {
            if (each.outside()) {
                apart.add(new CodeSetFolderChecks.Apart("folder already stated", each.folder()));
            }
        }
        Optional<String> refusal = CodeSetFolderChecks.refusal(typed, apart);
        if (refusal.isPresent()) {
            return refusal;
        }
        codeSetEntries.add(new CodeSet(typed, "", true));
        draw();
        return Optional.empty();
    }

    private static Path realOf(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException unreal) {
            return path.toAbsolutePath().normalize();
        }
    }

    private void draw() {
        codeSets.getChildren().clear();
        for (CodeSet each : new ArrayList<>(codeSetEntries)) {
            SelectableText.Line words = SelectableText.line(each.outside() ? each.folder() + "  (outside the corpus root)"
                    : each.name().isEmpty() ? each.folder() + "  (under the corpus root, no name)"
                    : each.folder() + "  named " + each.name());
            Button remove = new Button("Remove");
            remove.setOnAction(event -> {
                codeSetEntries.remove(each);
                draw();
            });
            codeSets.getChildren().add(row(words, remove));
        }
    }

    private void saveCodeSets() {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            codeSetOutcome.setText("name the working directory first");
            return;
        }
        List<CodeSetRenames.Rename> renames = codeSetEntries.stream()
                .filter(each -> !each.outside() && !each.name().isEmpty())
                .map(each -> new CodeSetRenames.Rename(each.folder(), each.name())).toList();
        String outside = String.join(java.io.File.pathSeparator,
                codeSetEntries.stream().filter(CodeSet::outside).map(CodeSet::folder).toList());
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
        String drawn = ProfileEdit.drawnOf(ProfileKey.CODE_SET_FOLDERS, loaded.map(ProfileEdit.Stated::value).orElse(null),
                loaded.map(ProfileEdit.Stated::provenance).orElse(null));
        Optional<String> refusal = provenance.flatMap(words -> ProfileEdit.refusalOfSave(ProfileKey.CODE_SET_FOLDERS, outside,
                words, inFile, Optional.of(drawn)));
        if (refusal.isPresent()) {
            codeSetOutcome.setText(refusal.get());
            return;
        }
        List<WorkingDirectoryWrite.Change> changes = new ArrayList<>();
        changes.add(new WorkingDirectoryWrite.Change(CodeSetRenames.FILE_NAME,
                current -> CodeSetRenames.render(renames)));
        provenance.ifPresent(words -> changes.add(new WorkingDirectoryWrite.Change(ProfileEdit.FILE_NAME,
                current -> ProfileEdit.rewrite(current, ProfileKey.CODE_SET_FOLDERS, outside, words))));
        Optional<String> failed = state.write(folder.get(), changes);
        codeSetOutcome.setText(failed.orElse("code sets saved in " + folder.get()));
        saved.run();
    }
}
