package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The sidebar's list of the reports the working directory holds, and the tab each one opens in (ADR-344 §6). */
final class ReportsPane {

    private final TabPane tabs;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final Consumer<String> say;
    private final Map<String, Tab> reportTabs = new LinkedHashMap<>();
    private final VBox reportsList = new VBox();

    /** {@code tabs} is where the reports open, each drawn from its JSON; {@code say} is the outcome line (ADR-362 §10). */
    ReportsPane(TabPane tabs, Dialogs dialogs, WorkingFolders folders, Consumer<String> say) {
        this.tabs = tabs;
        this.dialogs = dialogs;
        this.folders = folders;
        this.say = say;
    }

    Node pane() {
        reportsList.setId("reports-list");
        reportsList.getStyleClass().add("section");
        draw();
        return reportsList;
    }

    /** Draws a link for each report written and a grey line for each not. */
    void draw() {
        reportsList.getChildren().clear();
        Map<String, Boolean> written = folders.writtenReports();
        for (String report : Reports.ALL) {
            if (written.get(report)) {
                Hyperlink link = new Hyperlink(report);
                link.setOnAction(event -> open(report));
                reportsList.getChildren().add(link);
            } else {
                SelectableText.Line grey = SelectableText.line(report + " (not written yet)");
                grey.getStyleClass().add("grey");
                reportsList.getChildren().add(grey);
            }
        }
    }

    /**
     * Opens {@code report}, by its name, in a tab of its own, {@code analysis-report-<name>}, or selects the tab it
     * already has (ADR-362 §10), drawn from its JSON.
     */
    void open(String report) {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        Tab open = reportTabs.get(report);
        if (open != null) {
            tabs.getSelectionModel().select(open);
            return;
        }
        openNatively(report, folder.get().toAbsolutePath().normalize());
    }

    /** Opens {@code report} as an {@code analysisReport} cell leads to it: where it is written, or says that it is not. */
    private void leadTo(String report) {
        if (Reports.ALL.contains(report) && folders.writtenReports().getOrDefault(report, false)) {
            open(report);
        } else {
            say.accept(report + " is not written yet, so it is not opened");
        }
    }

    private void openNatively(String report, Path folder) {
        Path file = folder.resolve(Reports.jsonOf(report));
        Optional<AnalysisReportFile.Report> read = read(report, file);
        if (read.isEmpty()) {
            return;
        }
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        Button reload = new Button("Reload");
        reload.setId("reload-" + report);
        VBox content = new VBox(reload, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Tab tab = new Tab(read.get().title(), content);
        draw(scroll, tab, report, read.get());
        reload.setOnAction(event -> read(report, file).ifPresent(again -> draw(scroll, tab, report, again)));
        tab.setId("analysis-report-" + report);
        tab.setOnClosed(event -> reportTabs.remove(report));
        reportTabs.put(report, tab);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    private void draw(ScrollPane scroll, Tab tab, String report, AnalysisReportFile.Report read) {
        tab.setText(read.title());
        scroll.setContent(AnalysisReportView.of(report, read, Reports.pageOf(report),
                this::leadTo, dialogs::handToTheSystem));
    }

    /** The report's JSON, or none, the outcome line then saying why it cannot be drawn. */
    private Optional<AnalysisReportFile.Report> read(String report, Path file) {
        try {
            return Optional.of(AnalysisReportFile.read(report,
                    Files.readString(file, java.nio.charset.StandardCharsets.UTF_8)));
        } catch (AnalysisReportFile.FormatNotKnownException notKnown) {
            say.accept(Reports.jsonOf(report) + " is of format " + notKnown.format() + ", which this window does not read");
        } catch (IOException | RuntimeException unreadable) {
            say.accept(Reports.jsonOf(report) + " could not be read");
        }
        return Optional.empty();
    }

    /** The reports' tabs belong to the working directory: another one closes them. */
    void closeTabs() {
        for (Tab tab : List.copyOf(reportTabs.values())) {
            tabs.getTabs().remove(tab);
        }
        reportTabs.clear();
    }
}
