package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The folders not relevant, a list beneath the Inclusions tree, with the one action that changes it (ADR-345 §2, ADR-334 §6). */
final class NotRelevantPane {

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final ListView<String> notRelevantList = new ListView<>();
    private final TextField notRelevantFolder = new TextField();
    private final SelectableText.Wrapping notRelevantOutcome = SelectableText.wrapping("");

    NotRelevantPane(ControlPanelState state, Dialogs dialogs, WorkingFolders folders) {
        this.state = state;
        this.dialogs = dialogs;
        this.folders = folders;
    }

    Node pane() {
        notRelevantList.setId("not-relevant-list");
        notRelevantList.setPrefHeight(120);
        notRelevantFolder.setId("not-relevant-folder");
        notRelevantFolder.setPromptText("A folder, from the corpus root, / between its names");
        HBox.setHgrow(notRelevantFolder, Priority.ALWAYS);
        Button browse = new Button("Browse");
        browse.setId("not-relevant-browse");
        browse.setOnAction(event -> dialogs.folder("Choose a folder not relevant",
                folders.corpusRoot().map(Path::toString).orElse("")).ifPresent(chosen -> notRelevantFolder.setText(
                        fromTheCorpusRoot(chosen))));
        Button add = new Button("Add");
        add.setId("not-relevant-add");
        add.setOnAction(event -> changeNotRelevant(true, null));
        Button remove = new Button("Remove");
        remove.setId("not-relevant-remove");
        remove.setOnAction(event -> changeNotRelevant(false, notRelevantList.getSelectionModel().getSelectedItem()));
        notRelevantOutcome.setId("not-relevant-outcome");
        SelectableText.Wrapping words = SelectableText.wrapping("Folders not relevant are still read: the relevance questions below them are"
                + " answered not relevant, and no run changes.");
        VBox pane = new VBox(SelectableText.line("Folders not relevant"), words, notRelevantList,
                row(notRelevantFolder, browse, add, remove), notRelevantOutcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** {@code chosen} as a folder not relevant is spelt: from the corpus root with {@code /} between its names where it lies below it. */
    private String fromTheCorpusRoot(Path chosen) {
        Optional<Path> root = folders.corpusRoot();
        Path absolute = chosen.toAbsolutePath().normalize();
        if (root.isPresent() && absolute.startsWith(root.get().toAbsolutePath().normalize())
                && !absolute.equals(root.get().toAbsolutePath().normalize())) {
            return root.get().toAbsolutePath().normalize().relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString().replace('\\', '/');
    }

    /** Draws the list the working directory's file holds, or says why it cannot be read. */
    void draw() {
        try {
            notRelevantList.getItems().setAll(NotRelevantFolders.read(folders.readFile(NotRelevantFolders.FILE_NAME)));
        } catch (IOException | IllegalArgumentException unreadable) {
            notRelevantList.getItems().clear();
            notRelevantOutcome.setText(String.valueOf(unreadable.getMessage()));
        }
    }

    /**
     * ADR-334 §6's one action, in one hold of the lock: the folder typed added to the list the file holds as read then, or
     * {@code removed} taken out of it, and every question of the label file answered from the list, all or none.
     */
    private void changeNotRelevant(boolean adding, String removed) {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            notRelevantOutcome.setText("name the working directory first");
            return;
        }
        if (!adding && removed == null) {
            notRelevantOutcome.setText("select a folder of the list to remove");
            return;
        }
        String typed = notRelevantFolder.getText().strip();
        if (adding && typed.isEmpty()) {
            notRelevantOutcome.setText("type a folder first, or select one of the list to remove");
            return;
        }
        if (adding) {
            Optional<String> refusal = refusalOfNotRelevantFolder(typed, List.of());
            if (refusal.isPresent()) {
                notRelevantOutcome.setText(refusal.get());
                return;
            }
        }
        List<String> kept = new ArrayList<>();
        int[] counts = new int[2];
        boolean[] answered = {false};
        List<WorkingDirectoryWrite.Change> changes = List.of(
                new WorkingDirectoryWrite.Change(NotRelevantFolders.FILE_NAME, current -> {
                    // rebuilt from the file as read under the lock, never from the list as drawn (ADR-334 §6)
                    List<String> list = new ArrayList<>(NotRelevantFolders.read(current));
                    if (!adding) {
                        list.removeIf(listed -> listed.equals(removed));
                    } else {
                        Optional<String> refusal = refusalOfNotRelevantFolder(typed, list);
                        if (refusal.isPresent()) {
                            throw new IllegalArgumentException(refusal.get());
                        }
                        list.add(typed);
                    }
                    kept.clear();
                    kept.addAll(list);
                    return NotRelevantFolders.render(list);
                }),
                new WorkingDirectoryWrite.Change(LabelAnswers.FILE_NAME, current -> {
                    if (current.isEmpty()) {
                        return null;
                    }
                    answered[0] = true;
                    return answersGivenBy(current.get(), kept, counts);
                }));
        Optional<String> refusal = state.write(folder.get(), changes);
        if (refusal.isPresent()) {
            notRelevantOutcome.setText(refusal.get());
            return;
        }
        if (adding) {
            notRelevantFolder.clear();
        }
        Path file = folder.get().resolve(NotRelevantFolders.FILE_NAME);
        draw();
        notRelevantOutcome.setText(answered[0]
                ? "the folders not relevant are saved in " + file + ", and the " + counts[0] + " questions answered: "
                        + counts[1] + " not relevant, " + (counts[0] - counts[1]) + " relevant"
                : "the folders not relevant are saved in " + file + "; there are no questions to answer yet");
    }

    /**
     * The label file's text with each question answered by the folders listed (ADR-334 §6): {@code false} where its
     * path lies at or below one, {@code true} otherwise. Only a {@code relevant} line whose answer changes is rewritten.
     *
     * @param counts set to the number of questions and the number answered not relevant
     */
    private static String answersGivenBy(String text, List<String> listed, int[] counts) {
        List<LabelAnswers.Question> questions = LabelAnswers.read(text);
        Map<String, Optional<Boolean>> changed = new LinkedHashMap<>();
        counts[0] = questions.size();
        counts[1] = 0;
        for (LabelAnswers.Question question : questions) {
            boolean relevant = listed.stream().noneMatch(folder -> NotRelevantFolders.lies(question.path(), folder));
            if (!relevant) {
                counts[1]++;
            }
            if (!question.relevant().equals(Optional.of(relevant))) {
                changed.put(question.path(), Optional.of(relevant));
            }
        }
        return LabelAnswers.rewrite(Optional.of(text), changed);
    }

    /** The line refusing {@code typed} as a folder not relevant, or none (ADR-334 §6). */
    private Optional<String> refusalOfNotRelevantFolder(String typed, List<String> listed) {
        String refused = "the folder " + typed + " is not added: ";
        if (typed.equals(".") || typed.equals("./") || typed.equals(".\\") || namesTheCorpusRoot(typed)) {
            return Optional.of(refused + "the corpus root holds every question; name a folder below it");
        }
        boolean belowACodeSetFolder = NotRelevantFolders.isAbsolute(typed)
                && codeSetFoldersStated().stream().map(CodeSetFolderChecks::spelt)
                        .anyMatch(stated -> NotRelevantFolders.lies(typed, stated));
        if (!NotRelevantFolders.isWritten(typed) || (NotRelevantFolders.isAbsolute(typed) && !belowACodeSetFolder)) {
            return Optional.of(refused
                    + "it is not a folder below the corpus root, written with / between its elements");
        }
        if (listed.stream().anyMatch(stated -> NotRelevantFolders.lies(typed, stated)
                && NotRelevantFolders.lies(stated, typed))) {
            return Optional.of(refused + "it is listed already");
        }
        return Optional.empty();
    }

    /** The code set folders the profile states, outside the corpus root; none where it states none or cannot be read. */
    private List<String> codeSetFoldersStated() {
        try {
            Optional<String> text = folders.readFile(ProfileEdit.FILE_NAME);
            if (text.isEmpty()) {
                return List.of();
            }
            ProfileEdit.Stated stated = ProfileEdit.read(text.get()).get(ProfileKey.CODE_SET_FOLDERS);
            return stated == null || stated.value() == null ? List.of() : CodeSetFolderChecks.statedIn(stated.value());
        } catch (IOException | IllegalArgumentException unreadable) {
            return List.of();
        }
    }

    /** Whether {@code typed} spells the corpus root itself, with either separator, with or without a trailing one. */
    private boolean namesTheCorpusRoot(String typed) {
        Optional<Path> root = folders.corpusRoot();
        if (root.isEmpty() || !NotRelevantFolders.isAbsolute(typed.replace('\\', '/'))) {
            return false;
        }
        String given = withoutTrailingSeparators(typed);
        String own = withoutTrailingSeparators(root.get().toString());
        if (NotRelevantFolders.lies(given, own) && NotRelevantFolders.lies(own, given)) {
            return true;
        }
        try {
            return Files.isSameFile(Path.of(typed), root.get());
        } catch (IOException | RuntimeException cannotBeCompared) {
            return false;
        }
    }

    private static String withoutTrailingSeparators(String path) {
        String slashed = path.strip().replace('\\', '/');
        while (slashed.length() > 1 && slashed.endsWith("/")) {
            slashed = slashed.substring(0, slashed.length() - 1);
        }
        return slashed;
    }
}
