package io.algernon.vespera.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/** The sidebar's Preflight section: what the folders and the sidecars are, and a Check that asks the sidecars (ADR-344 §1). */
final class PreflightPane {

    private final ControlPanelState state;
    private final WorkingFolders folders;
    private final Function<String, String> fieldText;
    private final VBox preflight = new VBox();

    /** {@code fieldText} gives the text of the launch field of a property. */
    PreflightPane(ControlPanelState state, WorkingFolders folders, Function<String, String> fieldText) {
        this.state = state;
        this.folders = folders;
        this.fieldText = fieldText;
    }

    Node pane() {
        preflight.setId("preflight-list");
        preflight.getStyleClass().add("section");
        Button check = new Button("Check");
        check.setId("check");
        check.setOnAction(event -> askTheSidecars());
        VBox pane = new VBox(preflight, check);
        pane.getStyleClass().add("section");
        return pane;
    }

    private void askTheSidecars() {
        String converter = fieldText.apply("vespera.docling.base-url");
        String image = fieldText.apply("vespera.docling.image");
        String ollama = fieldText.apply("spring.ai.ollama.base-url");
        String model = fieldText.apply("vespera.label.model");
        Thread.ofPlatform().daemon().name("the control panel asks the sidecars").start(() -> {
            SidecarChecks.Checks asked = SidecarChecks.ask(converter, image, ollama,
                    model.isBlank() ? List.of() : List.of(model));
            Platform.runLater(() -> {
                state.keepChecks(asked);
                draw();
            });
        });
    }

    private static String describe(SidecarChecks.Result result) {
        return switch (result) {
            case NOT_CHECKED -> "not checked";
            case MATCHES -> "answers, and runs the image named";
            case ANOTHER_IMAGE -> "answers, and runs another image";
            case NOT_ANSWERING -> "does not answer";
            case SERVES_ALL -> "answers, and serves every model named";
            case MISSING -> "answers, and is missing a model named";
        };
    }

    /** Draws a line for each folder, each sidecar, each holder and the free space, then the converter's warnings. */
    void draw() {
        preflight.getChildren().clear();
        ControlPanelState.Holders holders = state.holders(folders.workingDirectory(), folders.corpusRoot());
        SidecarChecks.Checks checks = state.lastChecks();
        String working = fieldText.apply("vespera.working-dir");
        List<String> lines = new ArrayList<>(List.of(
                "Corpus root: " + FolderStatus.of(fieldText.apply("vespera.corpus-root"), false).words(),
                "Working directory: " + FolderStatus.of(working, false).words(),
                "Converter: " + describe(checks.docling().result()),
                "Ollama: " + describe(checks.ollama().result()),
                "Working directory held by: " + holders.workingDirectory().orElse("nobody"),
                "Corpus root held by: " + holders.corpusRoot().orElse("nobody"),
                "Free space: " + FolderStatus.usable(working).map(FolderStatus::gib).orElse("not known")));
        for (String line : lines) {
            preflight.getChildren().add(SelectableText.line(line));
        }
        checks.docling().warnings().forEach(warning -> preflight.getChildren().add(SelectableText.line(warning)));
    }
}
