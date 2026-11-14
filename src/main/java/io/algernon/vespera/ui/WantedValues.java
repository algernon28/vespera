package io.algernon.vespera.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/** What the next invocation wants: a row for each profile value it asks for, beneath the actions, with its own Save (ADR-344 §1). */
final class WantedValues {

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final Consumer<String> say;
    private final Consumer<String> openReport;
    private final Runnable reload;
    private final Runnable rowsDrawn;
    private final VBox wanted = new VBox();
    private final List<ProfileRow> wantedRows = new ArrayList<>();

    /**
     * {@code say} is the outcome line, {@code openReport} opens a report, {@code reload} runs after a Save has written
     * and {@code rowsDrawn} after rows have been drawn.
     */
    WantedValues(ControlPanelState state, Dialogs dialogs, WorkingFolders folders, Consumer<String> say,
            Consumer<String> openReport, Runnable reload, Runnable rowsDrawn) {
        this.state = state;
        this.dialogs = dialogs;
        this.folders = folders;
        this.say = say;
        this.openReport = openReport;
        this.reload = reload;
        this.rowsDrawn = rowsDrawn;
        wanted.setId("wanted");
        wanted.getStyleClass().add("wanted");
    }

    VBox node() {
        return wanted;
    }

    /** Whether no row is drawn. */
    boolean isEmpty() {
        return wantedRows.isEmpty();
    }

    /** Shows which reports are written on each row. */
    void showReports(Map<String, Boolean> written) {
        wantedRows.forEach(each -> each.showReports(written));
    }

    /** Draws a row for each value {@code next} wants, or nothing where it wants none. */
    void draw(Optional<NextInvocation> next) {
        wanted.getChildren().clear();
        wantedRows.clear();
        if (next.isEmpty() || next.get().wants().isEmpty()) {
            return;
        }
        Map<ProfileKey, ProfileEdit.Stated> stated = Map.of();
        try {
            Optional<String> text = folders.readFile(ProfileEdit.FILE_NAME);
            if (text.isPresent() && ProfileRepair.analyse(text.get()).isEmpty()) {
                stated = ProfileEdit.read(text.get());
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            // the Profile window says so
        }
        Optional<EffectiveValues> effective = state.effectiveValues(folders.workingDirectory());
        Map<String, Boolean> written = folders.writtenReports();
        wanted.getChildren().add(SelectableText.line("The next invocation wants these values:"));
        for (String want : next.get().wants()) {
            Optional<ProfileKey> key = ProfileKey.named(want);
            if (key.isEmpty()) {
                continue;
            }
            Optional<EffectiveValues.Value> beneath = key.get().hasAValueBeneath()
                    ? effective.flatMap(said -> said.named(key.get().key())) : Optional.empty();
            ProfileRow row = new ProfileRow(key.get(), "wanted-", state.lastChecks().ollama().models(), beneath,
                    dialogs, null, openReport, written);
            row.load(Optional.ofNullable(stated.get(key.get())));
            if (key.get() == ProfileKey.ARRANGEMENT_APPROVED && next.get().arrangement().isPresent()) {
                // the arrangement the jar arrives at, offered for the operator to confirm (ADR-294 section 4)
                row.offer(next.get().arrangement().get());
            } else if (key.get() == ProfileKey.SEED_FOLDER && row.value().isBlank()
                    && !state.values().lastSeedFolder().isEmpty()) {
                // offered, not stated by the file: a save writes it (ADR-292 section 6)
                row.offer(state.values().lastSeedFolder());
            }
            wantedRows.add(row);
            wanted.getChildren().add(row.node());
        }
        Button save = new Button("Save");
        save.setId("wanted-save");
        save.setOnAction(event -> {
            ProfileRow.Saved saved = ProfileRow.save(state, folders.workingDirectory(), wantedRows);
            say.accept(saved.line());
            if (saved.written()) {
                reload.run();
            }
        });
        wanted.getChildren().add(save);
        rowsDrawn.run();
    }
}
