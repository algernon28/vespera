package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The one Reports window of a cockpit (ADR-414): a stage of its own, with no owner and no modality, whose scene's root
 * holds one tab pane, {@code analysis-report-tabs}, a closable tab for each analysis report opened, drawn from its JSON
 * (ADR-362 §10). It opens on the first report opened and on none that cannot be read, selects the tab of a report open
 * and shows the window again in front, closes with its last tab and closes its tabs with it, keeps its size and place
 * for the cockpit's next opening of it and nowhere else, and follows the cockpit's stylesheet and {@code dark} style
 * class. The lines it has to say are the cockpit's outcome line's.
 */
final class AnalysisReportsWindow {

    private static final String TITLE = "Reports";
    private static final double FIRST_WIDTH = 900;
    private static final double FIRST_HEIGHT = 700;

    private record Placed(double x, double y, double width, double height) {
    }

    private final BorderPane cockpitRoot;
    private final Dialogs dialogs;
    private final WorkingFolders folders;
    private final Consumer<String> say;
    private final BorderPane root = new BorderPane();
    private final TabPane tabs = new TabPane();
    private final Map<String, Tab> reportTabs = new LinkedHashMap<>();
    private Stage stage;
    private Placed kept;

    /** {@code say} is the cockpit's outcome line (ADR-414 §3). */
    AnalysisReportsWindow(BorderPane cockpitRoot, Dialogs dialogs, WorkingFolders folders, Consumer<String> say) {
        this.cockpitRoot = cockpitRoot;
        this.dialogs = dialogs;
        this.folders = folders;
        this.say = say;
        tabs.setId("analysis-report-tabs");
        root.setCenter(tabs);
        root.getStyleClass().add("cockpit");
        follow();
        cockpitRoot.getStyleClass().addListener((ListChangeListener<String>) change -> follow());
    }

    /** Takes the cockpit's stylesheet and, as it has it or not, the {@code dark} style class. */
    private void follow() {
        root.getStylesheets().setAll(cockpitRoot.getStylesheets());
        boolean dark = cockpitRoot.getStyleClass().contains("dark");
        root.getStyleClass().remove("dark");
        if (dark) {
            root.getStyleClass().add("dark");
        }
    }

    /**
     * Opens {@code report}, by its name, in a tab of the window, {@code analysis-report-<name>}, or selects the tab it
     * already has, and in either case shows the window, in front (ADR-414 §3).
     */
    void open(String report) {
        Optional<Path> folder = folders.workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        Tab open = reportTabs.get(report);
        if (open == null) {
            open = openNatively(report, folder.get().toAbsolutePath().normalize());
            if (open == null) {
                return;
            }
        }
        tabs.getSelectionModel().select(open);
        show();
    }

    /** Opens {@code report} as an {@code analysisReport} cell leads to it: where it is written, or says that it is not. */
    private void leadTo(String report) {
        if (Reports.ALL.contains(report) && folders.writtenReports().getOrDefault(report, false)) {
            open(report);
        } else {
            say.accept(report + " is not written yet, so it is not opened");
        }
    }

    private Tab openNatively(String report, Path folder) {
        Path file = folder.resolve(Reports.jsonOf(report));
        Optional<AnalysisReportFile.Report> read = read(report, file);
        if (read.isEmpty()) {
            return null;
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
        tab.setOnClosed(event -> {
            reportTabs.remove(report);
            if (reportTabs.isEmpty()) {
                close();
            }
        });
        reportTabs.put(report, tab);
        tabs.getTabs().add(tab);
        return tab;
    }

    private void draw(ScrollPane scroll, Tab tab, String report, AnalysisReportFile.Report read) {
        tab.setText(read.title());
        scroll.setContent(AnalysisReportView.of(report, read, Reports.pageOf(report),
                this::leadTo, dialogs::handToTheSystem));
    }

    /** The report's JSON, or none, the outcome line then saying why it cannot be drawn. */
    private Optional<AnalysisReportFile.Report> read(String report, Path file) {
        try {
            return Optional.of(AnalysisReportFile.read(report, Files.readString(file, StandardCharsets.UTF_8)));
        } catch (AnalysisReportFile.FormatNotKnownException notKnown) {
            say.accept(Reports.jsonOf(report) + " is of format " + notKnown.format() + ", which this window does not read");
        } catch (IOException | RuntimeException unreadable) {
            say.accept(Reports.jsonOf(report) + " could not be read");
        }
        return Optional.empty();
    }

    /** Shows the window, or shows the one open again where it is minimized, brings it to the front and gives it the focus. */
    private void show() {
        if (stage == null) {
            stage = new Stage();
            stage.setTitle(TITLE);
            stage.setScene(new Scene(root, FIRST_WIDTH, FIRST_HEIGHT));
            stage.setResizable(true);
            stage.setOnHiding(event -> {
                kept = new Placed(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
                for (Tab tab : List.copyOf(reportTabs.values())) {
                    tabs.getTabs().remove(tab);
                }
                reportTabs.clear();
            });
        }
        follow();
        if (!stage.isShowing() && kept != null) {
            stage.setX(kept.x());
            stage.setY(kept.y());
            stage.setWidth(kept.width());
            stage.setHeight(kept.height());
        }
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    boolean isOpen() {
        return stage != null && stage.isShowing();
    }

    /** Closes the window, its tabs with it, asking nothing: where its last tab closes, the working directory changes or the cockpit closes. */
    void close() {
        if (stage != null) {
            stage.hide();
        }
    }
}
