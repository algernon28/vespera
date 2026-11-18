package io.algernon.vespera.ui;

import java.util.Map;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.VBox;

/**
 * The sidebar's list of the reports the working directory holds (ADR-344 §6): a link for each written, which opens that
 * report in the Reports window, {@link AnalysisReportsWindow} (ADR-414), and a grey line for each not.
 */
final class ReportsPane {

    private final WorkingFolders folders;
    private final Consumer<String> open;
    private final VBox reportsList = new VBox();

    /** {@code open} opens a report, by its name, in the Reports window. */
    ReportsPane(WorkingFolders folders, Consumer<String> open) {
        this.folders = folders;
        this.open = open;
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
                link.setOnAction(event -> open.accept(report));
                reportsList.getChildren().add(link);
            } else {
                SelectableText.Line grey = SelectableText.line(report + " (not written yet)");
                grey.getStyleClass().add("grey");
                reportsList.getChildren().add(grey);
            }
        }
    }
}
