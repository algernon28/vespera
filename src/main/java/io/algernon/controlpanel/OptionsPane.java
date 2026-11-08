package io.algernon.controlpanel;

import static io.algernon.controlpanel.Layout.row;
import static io.algernon.controlpanel.Layout.tight;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The sidebar's Command options section: the label file, a control for each option the child's command line can take, the
 * way back to their defaults and the command line each kind of start would run (ADR-344 §4).
 */
final class OptionsPane {

    private static final Pattern A_DURATION = Pattern.compile("(\\d+)\\s*([smh]?)");

    private static final List<String> FLAGS = List.of("vespera.sidecars.start", "vespera.models.pull");

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final Runnable changed;
    private final Map<String, Preset> presets = new LinkedHashMap<>();
    private final Map<ControlPanelState.Start, Label> commandLines = new LinkedHashMap<>();
    private final TextField labelFile = new TextField();

    /** {@code changed} runs whenever a control of the pane changes what a command line would be. */
    OptionsPane(ControlPanelState state, Dialogs dialogs, Runnable changed) {
        this.state = state;
        this.dialogs = dialogs;
        this.changed = changed;
    }

    /** A preset passed as an option of the command line, its control and whether the operator changed it (ADR-344 §4). */
    private final class Preset {
        final String property;
        final StartingValues.Starting starting;
        final Node control;
        private final Supplier<String> read;
        private final Runnable back;
        private boolean touched;

        Preset(String property) {
            this.property = property;
            this.starting = StartingValues.of(property, state.sources());
            if (FLAGS.contains(property)) {
                CheckBox box = new CheckBox();
                box.setSelected(starting.value().isBlank() || Boolean.parseBoolean(starting.value()));
                box.selectedProperty().addListener((observed, was, now) -> touchedNow());
                read = () -> Boolean.toString(box.isSelected());
                back = () -> box.setSelected(starting.value().isBlank() || Boolean.parseBoolean(starting.value()));
                control = box;
            } else {
                Spinner<Integer> spinner = new Spinner<>(1, 1_000_000, secondsOf(starting.value()));
                spinner.setEditable(true);
                spinner.valueProperty().addListener((observed, was, now) -> touchedNow());
                read = () -> spinner.getValue() + "s";
                back = () -> spinner.getValueFactory().setValue(secondsOf(starting.value()));
                control = spinner;
            }
            control.setId(property);
            presets.put(property, this);
        }

        private void touchedNow() {
            touched = true;
            changed.run();
        }

        /** The text the preset holds: what it started as until the operator changes it. */
        String value() {
            return touched ? read.get() : starting.value();
        }

        void toTheDefault() {
            back.run();
            touched = false;
            changed.run();
        }
    }

    Node pane() {
        labelFile.setId("label-file");
        labelFile.setPromptText("The label file for Record my answers (optional)");
        labelFile.textProperty().addListener((observed, was, now) -> changed.run());
        Button browse = new Button("Browse");
        browse.setId("label-file.browse");
        browse.setOnAction(event -> dialogs.file("Choose the label file", "")
                .ifPresent(chosen -> labelFile.setText(chosen.toString())));
        HBox.setHgrow(labelFile, Priority.ALWAYS);
        VBox pane = new VBox(new Label("Label file"), row(labelFile, browse));
        for (ChildCommand.Option option : ChildCommand.OPTIONS) {
            Preset preset = new Preset(option.property());
            Label words = new Label(option.property() + "  (" + String.join(", ", option.commands()) + ")");
            words.getStyleClass().add("small");
            Label from = new Label(LaunchField.startingWords(preset.starting));
            from.getStyleClass().add("small");
            pane.getChildren().add(tight(words, preset.control, from));
        }
        Button defaults = new Button("Back to the defaults");
        defaults.setId("options-defaults");
        defaults.setOnAction(event -> {
            presets.values().forEach(Preset::toTheDefault);
            labelFile.clear();
        });
        pane.getChildren().add(defaults);
        for (ControlPanelState.Start kind : List.of(ControlPanelState.Start.RUN, ControlPanelState.Start.LABEL,
                ControlPanelState.Start.LABEL_AUTO, ControlPanelState.Start.PUBLISH)) {
            Label shown = new Label();
            shown.setWrapText(true);
            shown.setId("command-line-" + switch (kind) {
                case RUN -> "run";
                case LABEL -> "label";
                case LABEL_AUTO -> "label-auto";
                case PUBLISH -> "publish";
            });
            shown.getStyleClass().add("command-line");
            commandLines.put(kind, shown);
            pane.getChildren().add(shown);
        }
        pane.getStyleClass().add("section");
        return pane;
    }

    /** The label file's text as typed. */
    String labelFileText() {
        return labelFile.getText();
    }

    /** Whether the command lines are drawn yet. */
    boolean showsCommandLines() {
        return !commandLines.isEmpty();
    }

    /** The options whose value the child's command line is passed, with the value of each (ADR-344 §4). */
    Map<String, String> passed(StartingValues.Sources sources) {
        Map<String, String> options = new LinkedHashMap<>();
        for (ChildCommand.Option option : ChildCommand.OPTIONS) {
            Preset preset = presets.get(option.property());
            if (preset != null && StartingValues.passed(option.property(), preset.value(), preset.starting, sources)) {
                options.put(option.property(), preset.value());
            }
        }
        return options;
    }

    /** Writes the command line each kind of start would run for {@code plan}, or the words of the refusal. */
    void showCommandLines(ControlPanelState.Plan plan) {
        commandLines.forEach((kind, shown) -> {
            try {
                shown.setText(ControlPanelState.commandOf(state.runtime(), kind, plan).shown());
            } catch (RuntimeException refused) {
                shown.setText(refused.getMessage() == null ? refused.getClass().getSimpleName() : refused.getMessage());
            }
        });
    }

    /** How often the progress page is read, in whole seconds, from the progress interval as the preset holds it. */
    int intervalSeconds() {
        Preset interval = presets.get("vespera.progress-interval");
        return interval == null ? 5 : secondsOf(interval.value());
    }

    private static int secondsOf(String duration) {
        Matcher read = A_DURATION.matcher(duration == null ? "" : duration.strip());
        if (!read.matches()) {
            return 5;
        }
        long number = Long.parseLong(read.group(1));
        long seconds = switch (read.group(2)) {
            case "m" -> number * 60;
            case "h" -> number * 3600;
            default -> number;
        };
        return (int) Math.max(1, Math.min(seconds, 1_000_000));
    }
}
