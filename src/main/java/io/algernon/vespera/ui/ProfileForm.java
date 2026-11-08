package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The Profile tab: a row for each key of profile.yaml, the Save and Reset of the form, and the repair offered for a file not in the form the jar writes (ADR-344 §1). */
final class ProfileForm {

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final Consumer<ProfileKey> pointTo;
    private final Consumer<String> openReport;
    private final Runnable reload;
    private final VBox profileBody = new VBox();
    private final Label profileOutcome = new Label();
    private final List<ProfileRow> profileRows = new ArrayList<>();

    /**
     * {@code pointTo} takes the operator to where a key not edited in the form is edited, {@code openReport} opens a
     * report, and {@code reload} runs after the form has written the file.
     */
    ProfileForm(ControlPanelState state, Dialogs dialogs, WorkingFolders folders, Consumer<ProfileKey> pointTo,
            Consumer<String> openReport, Runnable reload) {
        this.state = state;
        this.dialogs = dialogs;
        this.folders = folders;
        this.pointTo = pointTo;
        this.openReport = openReport;
        this.reload = reload;
    }

    Node content() {
        profileBody.setId("profile-form");
        Button save = new Button("Save");
        save.setId("profile-save");
        Button reset = new Button("Reset");
        reset.setId("profile-reset");
        save.setOnAction(event -> {
            ProfileRow.Saved saved = ProfileRow.save(state, folders.workingDirectory(), profileRows);
            profileOutcome.setText(saved.line());
            if (saved.written()) {
                reload.run();
            }
        });
        reset.setOnAction(event -> {
            profileRows.forEach(ProfileRow::reset);
            profileOutcome.setText("the form is back to profile.yaml as last saved");
        });
        profileOutcome.setId("profile-outcome");
        profileOutcome.setWrapText(true);
        ScrollPane scroll = new ScrollPane(profileBody);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox content = new VBox(profileOutcome, scroll, row(save, reset));
        content.getStyleClass().add("tab-content");
        return content;
    }

    /**
     * Draws a row for each key from {@code stated}, after the problem with the file where it has one: the working
     * directory not named, {@code problem} where it could not be read, or {@code notInForm}'s repair of {@code text}.
     */
    void draw(Map<ProfileKey, ProfileEdit.Stated> stated, String problem, Optional<ProfileRepair.Analysis> notInForm,
            String text, Map<String, Boolean> written) {
        Optional<EffectiveValues> effective = state.effectiveValues(folders.workingDirectory());
        List<String> models = state.lastChecks().ollama().models();
        profileRows.clear();
        profileBody.getChildren().clear();
        if (folders.workingDirectory().isEmpty()) {
            profileBody.getChildren().add(new Label("Name the working directory: profile.yaml lies in it."));
        }
        if (notInForm.isPresent()) {
            profileBody.getChildren().add(formProblem(text, notInForm.get()));
        }
        if (!problem.isEmpty()) {
            profileBody.getChildren().add(new Label(problem));
        }
        for (ProfileKey key : ProfileKey.values()) {
            Optional<EffectiveValues.Value> beneath = key.hasAValueBeneath()
                    ? effective.flatMap(said -> said.named(key.key())) : Optional.empty();
            ProfileRow row = new ProfileRow(key, "profile-", models, beneath, dialogs, pointTo, openReport, written);
            row.load(Optional.ofNullable(stated.get(key)));
            profileRows.add(row);
            profileBody.getChildren().add(row.node());
        }
    }

    /** Shows which reports are written on each row. */
    void showReports(Map<String, Boolean> written) {
        profileRows.forEach(each -> each.showReports(written));
    }

    private Node formProblem(String text, ProfileRepair.Analysis analysis) {
        VBox box = new VBox();
        box.getStyleClass().add("repair");
        box.setId("profile-problem");
        box.getChildren().add(new Label(ProfileEdit.FILE_NAME + " is not in the form the jar writes it: the first line"
                + " that is not is line " + analysis.firstLine() + "."
                + (analysis.repairable() ? " The changes below put it in form and lose no value or provenance."
                        : " The repair cannot put it right; it is left to you.")));
        if (analysis.repairable()) {
            analysis.changes().forEach(change -> box.getChildren().add(
                    new Label("line " + change.line() + ": " + change.words())));
            Button repair = new Button("Repair");
            repair.setId("profile-repair");
            String digest = ProfileEdit.digestOf(text);
            repair.setOnAction(event -> repair(digest));
            box.getChildren().add(repair);
        }
        return box;
    }

    private void repair(String drawn) {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        String[] copy = {""};
        Optional<String> refusal = state.write(folder.get(), ProfileEdit.FILE_NAME, current -> {
            String text = current.orElseThrow(() -> new IllegalArgumentException(
                    ProfileEdit.FILE_NAME + " is not there, so there is nothing to repair"));
            if (!ProfileEdit.digestOf(text).equals(drawn)) {
                throw new IllegalArgumentException(
                        ProfileEdit.FILE_NAME + " has changed since it was shown, so it was not repaired");
            }
            ProfileRepair.Analysis analysis = ProfileRepair.analyse(text).filter(ProfileRepair.Analysis::repairable)
                    .orElseThrow(() -> new IllegalArgumentException(ProfileEdit.FILE_NAME
                            + " is in no form that the repair can put right, so it was not repaired"));
            copy[0] = WorkingDirectoryWrite.keepCopyBeforeRepair(folder.get(), ProfileEdit.FILE_NAME);
            return analysis.repaired();
        });
        profileOutcome.setText(refusal.orElse("profile.yaml is repaired; the file as it stood is kept as " + copy[0]));
        reload.run();
    }
}
