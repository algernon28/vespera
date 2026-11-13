package io.algernon.vespera.ui;

import javafx.scene.control.TextField;

/** A launch value's field: its text, the line saying where it started, and, for a folder, its status (ADR-344 §3, ADR-345 §1). */
final class LaunchField {

    final String property;
    final TextField input = new TextField();
    final SelectableText.Line startingLine = SelectableText.line("");
    final SelectableText.Line status = SelectableText.line("");
    final StartingValues.Starting starting;
    private final ControlPanelState state;
    private final boolean createdByTheJar;

    /** {@code edited} runs whenever the text changes, after a folder's status has been drawn anew. */
    LaunchField(ControlPanelState state, String property, boolean folder, boolean createdByTheJar, Runnable edited) {
        this.state = state;
        this.property = property;
        this.createdByTheJar = createdByTheJar;
        this.starting = StartingValues.of(property, state.sources());
        input.setId(property);
        input.setText(starting.value());
        input.setPromptText(LaunchValues.exampleOf(property));
        startingLine.setId("starting-" + property);
        startingLine.setText(startingWords(starting));
        startingLine.getStyleClass().add("small");
        status.setId("status-" + property);
        status.getStyleClass().add("small");
        if (folder) {
            input.textProperty().addListener((observed, was, now) -> status.setText(statusWords(now)));
            status.setText(statusWords(input.getText()));
        }
        input.textProperty().addListener((observed, was, now) -> edited.run());
    }

    String text() {
        return input.getText().strip();
    }

    /** What the line saying where a value started says of its source. */
    static String startingWords(StartingValues.Starting starting) {
        return switch (starting.source()) {
            case LAUNCH_VALUE -> "Starting value from the launch values";
            case ENVIRONMENT -> "Starting value from the environment";
            case DOT_ENV -> "Starting value from .env";
            case GPU_PRESELECTION -> "Starting value from the GPU image, preselected";
            case APPLICATION_YAML -> "Starting value from application.yaml";
            case NONE -> "No source names a starting value";
        };
    }

    /** What the field says of its folder; an empty working directory says the folder it stands for (ADR-345 §1). */
    private String statusWords(String text) {
        if (property.equals("vespera.working-dir") && text.isBlank()) {
            String theDefault = state.workingDirectoryDefault().folder();
            return "In use: " + theDefault + " (" + FolderStatus.of(theDefault, true).words() + ")";
        }
        return FolderStatus.of(text, createdByTheJar).words();
    }
}
