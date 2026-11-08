package io.algernon.vespera.ui;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/** The sidebar's list of the reports the working directory holds, and the tab each one opens in (ADR-344 §6). */
final class ReportsPane {

    private final TabPane tabs;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final Consumer<String> say;
    private final Map<String, Tab> reportTabs = new LinkedHashMap<>();
    private final VBox reportsList = new VBox();

    /** {@code tabs} is where the viewers open; {@code say} is the outcome line. */
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
                Label grey = new Label(report + " (not written yet)");
                grey.getStyleClass().add("grey");
                reportsList.getChildren().add(grey);
            }
        }
    }

    /** Opens {@code report}, a file of the working directory, in a tab of its own, or selects the tab it already has (ADR-344 §6). */
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
        Path file = folder.get().toAbsolutePath().normalize().resolve(report);
        WebView view = new WebView();
        view.setId("viewer-" + report);
        WebEngine engine = view.getEngine();
        engine.setCreatePopupHandler(features -> null);
        engine.locationProperty().addListener((observed, was, now) -> followed(file, engine, now));
        Button reload = new Button("Reload");
        reload.setId("reload-" + report);
        reload.setOnAction(event -> engine.reload());
        VBox.setVgrow(view, Priority.ALWAYS);
        VBox content = new VBox(reload, view);
        content.getStyleClass().add("tab-content");
        Tab tab = new Tab(report, content);
        tab.setId("report-" + report);
        tab.setOnClosed(event -> reportTabs.remove(report));
        reportTabs.put(report, tab);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        engine.load(file.toUri().toString());
    }

    /**
     * Decides a location a viewer is about to show (ADR-344 §6). The same report, with or without a fragment, is
     * followed; another report of the working directory opens in its own tab; a report not written, or an address that
     * is not a file's, opens nothing and is said on the outcome line; any other file is handed to the system. Where it is
     * not followed the viewer goes back to its report.
     */
    private void followed(Path report, WebEngine engine, String location) {
        if (location == null || location.isBlank()) {
            return;
        }
        String bare = location.split("[#?]", 2)[0];
        Optional<Path> file = fileOf(bare);
        if (file.isPresent() && file.get().equals(report)) {
            return;
        }
        if (file.isEmpty()) {
            say.accept("not opened: " + location + " is not a file of this computer");
        } else {
            Path parent = file.get().getParent();
            String name = file.get().getFileName().toString();
            boolean aReport = parent != null && Reports.ALL.contains(name)
                    && folders.workingDirectory().map(folder -> folder.toAbsolutePath().normalize().equals(parent))
                            .orElse(false);
            if (!aReport) {
                dialogs.handToTheSystem(URI.create(bare));
            } else if (Files.isRegularFile(file.get())) {
                open(name);
            } else {
                say.accept(name + " is not written yet, so it is not opened");
            }
        }
        engine.getLoadWorker().cancel();
        Platform.runLater(() -> engine.load(report.toUri().toString()));
    }

    private static Optional<Path> fileOf(String address) {
        try {
            URI uri = URI.create(address);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(Path.of(uri).toAbsolutePath().normalize());
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException unusable) {
            return Optional.empty();
        }
    }

    /** The reports' tabs belong to the working directory: another one closes them. */
    void closeTabs() {
        for (Tab tab : List.copyOf(reportTabs.values())) {
            tabs.getTabs().remove(tab);
        }
        reportTabs.clear();
    }
}
