package io.algernon.controlpanel.prototype;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * PROTOTYPE. One control per profile key, each constraining what can be entered: a bounded
 * number picker, a pick-list, or a list built with "+". A value left unset is the empty string.
 */
final class KeyControls {

    record Editor(Node node, Supplier<String> value) {}

    private static final List<String> LANGUAGES = List.of("it", "en", "de", "fr", "es", "pt", "nl", "zh", "ja");

    private KeyControls() {}

    static Editor of(Stub stub, Stub.Key key, String current) {
        return switch (key.type()) {
            case "share", "whole", "decimal" -> number(key, current);
            case "model" -> pick(current, models(stub, current), "not set: the model Vespera ships with");
            case "arrangement" -> pick(current, List.of("auto"), "not set: asked for each arrangement");
            case "choice" -> language(current);
            case "endings" -> list(current, key, false);
            case "languages" -> list(current, key, true);
            default -> {
                TextField field = new TextField(current);
                yield new Editor(field, field::getText);
            }
        };
    }

    /** A spinner bounded by the key's range, behind a box that says whether the value is set at all. */
    private static Editor number(Stub.Key key, String current) {
        double initial = parse(current, parse(key.example(), key.min()));
        Spinner<Double> spinner = new Spinner<>();
        SpinnerValueFactory.DoubleSpinnerValueFactory factory = new SpinnerValueFactory.DoubleSpinnerValueFactory(
                key.min(), key.max(), Math.clamp(initial, key.min(), key.max()), key.step());
        String format = switch (key.type()) {
            case "whole" -> "%.0f";
            case "decimal" -> "%.1f";
            default -> "%.2f";
        };
        factory.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : String.format(Locale.ROOT, format, value);
            }

            @Override
            public Double fromString(String text) {
                return parse(text.strip(), factory.getValue());
            }
        });
        spinner.setValueFactory(factory);
        spinner.setEditable(true);
        spinner.setPrefWidth(160);
        // typed text is taken when the field is left, not only on Enter
        spinner.focusedProperty().addListener((o, was, is) -> {
            if (!is) {
                spinner.increment(0);
            }
        });
        CheckBox set = new CheckBox("Set");
        set.setSelected(!current.isBlank());
        spinner.disableProperty().bind(set.selectedProperty().not());
        String range = key.type().equals("share") ? "between 0 and 1"
                : "from " + factory.getConverter().toString(key.min()) + " to " + factory.getConverter().toString(key.max());
        Label bounds = Ui.label(range, "source");
        HBox box = new HBox(8, set, spinner, bounds);
        box.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(box, new Tooltip("Untick Set to leave it unset. " + range + "."));
        return new Editor(box, () -> set.isSelected() ? factory.getConverter().toString(spinner.getValue()) : "");
    }

    private static Editor pick(String current, List<String> items, String unset) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().add("");
        items.stream().filter(i -> !i.isBlank() && !box.getItems().contains(i)).forEach(box.getItems()::add);
        if (!current.isBlank() && !box.getItems().contains(current)) {
            box.getItems().add(current);
        }
        box.setEditable(true);
        box.setValue(current);
        box.setPromptText(unset);
        box.setPrefWidth(280);
        return new Editor(box, () -> box.getEditor().getText() == null ? "" : box.getEditor().getText().strip());
    }

    private static Editor language(String current) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList("", "en", "it"));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return code == null || code.isEmpty() ? "not set (English)" : code.equals("en") ? "English (en)" : "Italiano (it)";
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        });
        box.setValue(current.isBlank() ? "" : current);
        return new Editor(box, () -> box.getValue() == null ? "" : box.getValue());
    }

    /** A list built one item at a time: the entry and "+" above, the items below, each removable. */
    private static Editor list(String current, Stub.Key key, boolean languages) {
        ObservableList<String> items = FXCollections.observableArrayList(Arrays.stream(current.split(languages ? "[,\\s]+" : "\\s+"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList());
        Node entry;
        Supplier<String> typed;
        if (languages) {
            ComboBox<String> codes = new ComboBox<>(FXCollections.observableArrayList(LANGUAGES));
            codes.setEditable(true);
            codes.setPromptText("e.g. it");
            codes.setPrefWidth(110);
            entry = codes;
            typed = () -> codes.getEditor().getText() == null ? "" : codes.getEditor().getText().strip().toLowerCase(Locale.ROOT);
        } else {
            TextField field = new TextField();
            field.setPromptText("e.g. " + key.example().split(" ")[0]);
            field.setPrefWidth(110);
            entry = field;
            typed = () -> field.getText().strip();
        }
        Label problem = Ui.label("", "bad-text");
        Button add = new Button("+");
        add.setTooltip(new Tooltip(languages ? "Add the language; the first listed is preferred" : "Add the ending"));
        FlowPane chips = new FlowPane(6, 6);
        Runnable draw = () -> {
            chips.getChildren().clear();
            for (int i = 0; i < items.size(); i++) {
                String item = items.get(i);
                Button remove = new Button("✕");
                remove.getStyleClass().add("chip-remove");
                remove.setOnAction(e -> items.remove(item));
                Label text = new Label((languages ? (i + 1) + ". " : "") + item);
                HBox chip = new HBox(4, text, remove);
                chip.setAlignment(Pos.CENTER_LEFT);
                chip.getStyleClass().add("value-chip");
                chips.getChildren().add(chip);
            }
            if (items.isEmpty()) {
                chips.getChildren().add(Ui.label("none: not set", "hint"));
            }
        };
        items.addListener((javafx.collections.ListChangeListener<String>) c -> draw.run());
        draw.run();
        add.setOnAction(e -> {
            String value = typed.get();
            String refusal = languages
                    ? (value.matches("[a-z]{2}") ? null : "a language is two letters, such as it or en")
                    : (value.matches("\\.[^\\s.][^\\s]*") ? null : "an ending begins with a dot, such as .java");
            if (refusal == null && items.stream().anyMatch(value::equalsIgnoreCase)) {
                refusal = "it is listed already";
            }
            problem.setText(refusal == null ? "" : refusal);
            if (refusal == null) {
                items.add(value);
                if (entry instanceof TextField field) {
                    field.clear();
                } else if (entry instanceof ComboBox<?> combo) {
                    combo.getEditor().clear();
                }
            }
        });
        HBox top = new HBox(6, entry, add, problem);
        top.setAlignment(Pos.CENTER_LEFT);
        return new Editor(new VBox(6, top, chips), () -> String.join(languages ? ", " : " ", items));
    }

    private static List<String> models(Stub stub, String current) {
        return List.of(stub.config.appValue("spring.ai.ollama.chat.options.model", "qwen3:8b"),
                stub.config.appValue("spring.ai.ollama.embedding.options.model", "bge-m3:latest"),
                stub.labelModel.get(), current);
    }

    private static double parse(String text, double fallback) {
        try {
            return Double.parseDouble(text.replace(',', '.'));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
