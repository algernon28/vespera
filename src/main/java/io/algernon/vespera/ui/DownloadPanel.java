package io.algernon.vespera.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The download panel (ADR-430 §7): a panel that is not modal, floating at its parent's top right over what lies beneath
 * and moving nothing of it, which downloads the models it is given one at a time, each with its name, a bar and its
 * words. <i>Hide</i> takes it away and the download goes on, <i>Cancel</i> stops it and drops the models waiting,
 * a lost connection brings it back with the reason and <i>Retry</i>, and once every model is done it offers <i>Close</i>.
 * It tells the window each time a download stops, so that the window checks at once, and which percent a model's row
 * reads while the panel is hidden. What Ollama sends is told on another thread and is handed to the toolkit's thread
 * before anything is drawn. Every colour, size and space is the stylesheet's.
 */
final class DownloadPanel {

    /** One model of the panel: the row it was found missing in, its kind, its name and where its download is. */
    private static final class Entry {
        final String row;
        final String kind;
        final String model;
        final SelectableText.Line name;
        final ProgressBar bar = new ProgressBar(0);
        final SelectableText.Line words = SelectableText.line("waiting");
        final VBox box;
        DockerControl.Downloaded.State state;
        boolean started;
        long completed;
        long total;
        int attempt;
        DockerControl.Download handle;

        Entry(String row, String kind, String model) {
            this.row = row;
            this.kind = kind;
            this.model = model;
            name = SelectableText.line(kind + ": " + model);
            name.setId("download-" + row + "-name");
            bar.setId("download-" + row + "-bar");
            words.setId("download-" + row + "-words");
            box = new VBox(name, new HBox(bar, words));
            box.getStyleClass().add("download-row");
            ((HBox) box.getChildren().get(1)).getStyleClass().add("spaced-row");
        }
    }

    /** A model the panel is asked to download: the row it was missing in, its kind and its name. */
    record Wanted(String row, String kind, String model) {
    }

    private static final double GB = 1024.0 * 1024.0 * 1024.0;

    private final VBox panel = new VBox();
    private final VBox rows = new VBox();
    private final SelectableText.Wrapping reason = SelectableText.wrapping("");
    private final Button hide = new Button("Hide");
    private final Button cancel = new Button("Cancel");
    private final Button retry = new Button("Retry");
    private final Button close = new Button("Close");
    private final List<Entry> entries = new ArrayList<>();
    private final StartDownload startDownload;
    private final Runnable stopped;
    private final Runnable changed;
    private final BiConsumer<String, Integer> percentShown;

    /** Starts the download of one model, telling {@code told} as it goes. */
    interface StartDownload {
        DockerControl.Download of(String model, java.util.function.Consumer<DockerControl.Downloaded> told);
    }

    /**
     * @param startDownload asks Docker for a model's download
     * @param stopped runs when a download has stopped, done, cancelled or lost, so that the window checks at once
     * @param changed runs when whether a download runs may have changed, so that the window's buttons follow
     * @param percentShown gets a row's id and the percent its mark reads beside it while the panel is hidden, negative where none
     */
    DownloadPanel(StartDownload startDownload, Runnable stopped, Runnable changed,
            BiConsumer<String, Integer> percentShown) {
        this.startDownload = startDownload;
        this.stopped = stopped;
        this.changed = changed;
        this.percentShown = percentShown;
        panel.setId("download-panel");
        panel.getStyleClass().add("download-panel");
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        SelectableText.Line title = SelectableText.line("Downloading into Ollama");
        title.getStyleClass().add("field-name");
        reason.setId("download-reason");
        hide.setId("download-hide");
        cancel.setId("download-cancel");
        retry.setId("download-retry");
        close.setId("download-close");
        Captions.on(hide, "Hides this panel; the download goes on.");
        Captions.on(cancel, "Stops the download; what was downloaded stays in Ollama, and the next download resumes from it.");
        Captions.on(retry, "Downloads it again from where it stopped.");
        hide.setOnAction(event -> hide());
        cancel.setOnAction(event -> cancelEverything());
        retry.setOnAction(event -> retry());
        close.setOnAction(event -> closeIt());
        HBox buttons = new HBox(hide, cancel, retry, close);
        buttons.getStyleClass().add("spaced-row");
        buttons.setAlignment(Pos.CENTER_LEFT);
        panel.getChildren().addAll(title, rows, reason, buttons);
        shown(false);
        showTheButtons();
    }

    /** The panel, to stand over the content at its top right. */
    Node node() {
        StackPane.setAlignment(panel, Pos.TOP_RIGHT);
        return panel;
    }

    /** Whether a download runs now. */
    boolean running() {
        return entries.stream().anyMatch(each -> each.state == DockerControl.Downloaded.State.DOWNLOADING);
    }

    /** Whether the panel has work to show: a download running, or one that was lost and waits for Retry. */
    boolean hasWork() {
        return entries.stream().anyMatch(each -> each.state != DockerControl.Downloaded.State.DONE);
    }

    /** Opens the panel and downloads {@code wanted} one at a time, the first of them first. */
    void begin(List<Wanted> wanted) {
        entries.clear();
        rows.getChildren().clear();
        reason.setText("");
        for (Wanted each : wanted) {
            Entry entry = new Entry(each.row(), each.kind(), each.model());
            entries.add(entry);
            rows.getChildren().add(entry.box);
        }
        shown(true);
        startNext();
    }

    /** Brings the panel back. */
    void reopen() {
        shown(true);
        showProgress();
    }

    /** Cancels the download running, as the window closes; nothing more is asked. */
    void cancelWhatRuns() {
        for (Entry each : entries) {
            if (each.state == DockerControl.Downloaded.State.DOWNLOADING && each.handle != null) {
                each.attempt++;
                each.handle.cancel();
            }
        }
        entries.clear();
    }

    private void shown(boolean on) {
        panel.setVisible(on);
        panel.setManaged(on);
    }

    private void startNext() {
        Entry next = entries.stream().filter(each -> each.state != DockerControl.Downloaded.State.DONE
                && each.state != DockerControl.Downloaded.State.LOST && !each.started).findFirst().orElse(null);
        if (next == null) {
            if (!entries.isEmpty() && entries.stream().allMatch(each -> each.state == DockerControl.Downloaded.State.DONE)) {
                stopped.run();
            }
            showTheButtons();
            showProgress();
            changed.run();
            return;
        }
        download(next);
    }

    private void download(Entry entry) {
        entry.started = true;
        entry.state = DockerControl.Downloaded.State.DOWNLOADING;
        entry.attempt++;
        int attempt = entry.attempt;
        reason.setText("");
        show(entry);
        entry.handle = startDownload.of(entry.model,
                told -> Platform.runLater(() -> heard(entry, attempt, told)));
        showTheButtons();
        showProgress();
        changed.run();
    }

    private void heard(Entry entry, int attempt, DockerControl.Downloaded told) {
        if (attempt != entry.attempt || !entries.contains(entry)) {
            return;
        }
        switch (told.state()) {
            case DOWNLOADING -> {
                entry.completed = told.completed();
                entry.total = told.total();
                show(entry);
                showProgress();
            }
            case DONE -> {
                entry.state = DockerControl.Downloaded.State.DONE;
                entry.completed = Math.max(told.total(), told.completed());
                entry.total = entry.completed;
                show(entry);
                stopped.run();
                startNext();
            }
            case LOST -> {
                entry.state = DockerControl.Downloaded.State.LOST;
                entry.completed = told.completed();
                entry.total = told.total();
                reason.setText(told.reason());
                shown(true);
                show(entry);
                showTheButtons();
                showProgress();
                stopped.run();
                changed.run();
            }
        }
    }

    /** Draws one model's bar and words. */
    private void show(Entry entry) {
        String state = switch (entry.state == null ? DockerControl.Downloaded.State.DOWNLOADING : entry.state) {
            case DOWNLOADING -> "downloading";
            case DONE -> "done";
            case LOST -> "lost";
        };
        if (!entry.started) {
            entry.words.setText("waiting");
            return;
        }
        entry.bar.setProgress(entry.total > 0 ? Math.min(1.0, (double) entry.completed / entry.total)
                : entry.state == DockerControl.Downloaded.State.DONE ? 1.0 : 0.0);
        entry.words.setText(String.format(Locale.ROOT, "%.1f of %.1f GB · %s", entry.completed / GB,
                entry.total / GB, state));
    }

    /** Tells each row's percent while the panel is hidden and its download runs, and none otherwise. */
    private void showProgress() {
        for (String row : List.of("embedding-model", "generative-model")) {
            Entry each = entries.stream().filter(entry -> entry.row.equals(row)).findFirst().orElse(null);
            boolean show = each != null && !panel.isVisible()
                    && each.state == DockerControl.Downloaded.State.DOWNLOADING;
            percentShown.accept(row, show ? (each.total > 0 ? (int) (each.completed * 100 / each.total) : 0) : -1);
        }
    }

    private void showTheButtons() {
        boolean lost = entries.stream().anyMatch(each -> each.state == DockerControl.Downloaded.State.LOST);
        boolean allDone = !entries.isEmpty()
                && entries.stream().allMatch(each -> each.state == DockerControl.Downloaded.State.DONE);
        reason.setVisible(lost);
        reason.setManaged(lost);
        retry.setVisible(lost);
        retry.setManaged(lost);
        close.setVisible(allDone);
        close.setManaged(allDone);
        hide.setVisible(!allDone);
        hide.setManaged(!allDone);
        cancel.setVisible(!allDone);
        cancel.setManaged(!allDone);
    }

    private void hide() {
        shown(false);
        showProgress();
    }

    private void cancelEverything() {
        for (Entry each : entries) {
            if (each.state == DockerControl.Downloaded.State.DOWNLOADING && each.handle != null) {
                each.attempt++;
                each.handle.cancel();
            }
        }
        entries.clear();
        rows.getChildren().clear();
        shown(false);
        showTheButtons();
        showProgress();
        stopped.run();
        changed.run();
    }

    private void retry() {
        Entry lost = entries.stream().filter(each -> each.state == DockerControl.Downloaded.State.LOST).findFirst()
                .orElse(null);
        if (lost == null) {
            return;
        }
        download(lost);
    }

    private void closeIt() {
        entries.clear();
        rows.getChildren().clear();
        shown(false);
        showTheButtons();
        showProgress();
    }
}
