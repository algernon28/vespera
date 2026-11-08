package io.algernon.controlpanel;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/** The header's light and dark switch: it puts the {@code dark} style class on the cockpit's root and keeps the choice (ADR-344 §1). */
final class ThemeSwitch {

    private ThemeSwitch() {
    }

    /** The switch drawn, selected as {@code root} is now, whose every change is applied to {@code root} and kept in {@code state}. */
    static Node of(BorderPane root, ControlPanelState state) {
        ToggleButton dark = new ToggleButton();
        dark.setId("theme-toggle");
        dark.getStyleClass().add("theme-switch");
        Circle knob = new Circle(8);
        knob.getStyleClass().add("knob");
        dark.setGraphic(knob);
        dark.setAccessibleText("Dark mode");
        dark.setFocusTraversable(true);
        dark.setSelected(root.getStyleClass().contains("dark"));
        dark.setAlignment(dark.isSelected() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        dark.selectedProperty().addListener((observed, was, now) -> {
            root.getStyleClass().remove("dark");
            if (now) {
                root.getStyleClass().add("dark");
            }
            dark.setAlignment(now ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            state.keepDark(now);
        });
        Label lightWords = new Label("Light");
        lightWords.setOnMouseClicked(event -> dark.setSelected(false));
        Label darkWords = new Label("Dark");
        darkWords.setOnMouseClicked(event -> dark.setSelected(true));
        HBox switchRow = new HBox(8, lightWords, dark, darkWords);
        switchRow.setAlignment(Pos.CENTER);
        Label themeWords = new Label("Theme");
        themeWords.getStyleClass().add("field-name");
        VBox themeBox = new VBox(4, themeWords, switchRow);
        themeBox.setAlignment(Pos.TOP_CENTER);
        return themeBox;
    }
}
