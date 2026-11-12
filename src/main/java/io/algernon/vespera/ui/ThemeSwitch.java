package io.algernon.vespera.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

/**
 * The header's light and dark switch: it puts the {@code dark} style class on the cockpit's root and keeps the choice (ADR-344 §1).
 *
 * <p>Whenever it moves the root between the themes, every view of the window follows at once, nothing reopened, and so does
 * every popup window showing whose owner is the window, or a popup of it (a tooltip, a context menu): each takes its style
 * afresh, through a style class added to its root and removed again, never a style and never a colour (ADR-374 §1).
 */
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
            restylePopups(root);
        });
        SelectableText.Line lightWords = SelectableText.line("Light");
        lightWords.setOnMouseClicked(event -> dark.setSelected(false));
        SelectableText.Line darkWords = SelectableText.line("Dark");
        darkWords.setOnMouseClicked(event -> dark.setSelected(true));
        HBox switchRow = new HBox(8, lightWords, dark, darkWords);
        switchRow.setAlignment(Pos.CENTER);
        SelectableText.Line themeWords = SelectableText.line("Theme");
        themeWords.getStyleClass().add("field-name");
        VBox themeBox = new VBox(4, themeWords, switchRow);
        themeBox.setAlignment(Pos.TOP_CENTER);
        return themeBox;
    }

    /** Each popup window showing over {@code root}'s window takes its style afresh, from the root as it now is (ADR-374 §1); the root's own style is applied first, since a popup reads its tokens from it. */
    private static void restylePopups(BorderPane root) {
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        if (owner == null) {
            return;
        }
        root.applyCss();
        for (Window each : Window.getWindows()) {
            if (each instanceof PopupWindow popup && popup.isShowing() && popup.getScene() != null) {
                Window up = popup.getOwnerWindow();
                while (up instanceof PopupWindow inner) {
                    up = inner.getOwnerWindow();
                }
                if (up == owner) {
                    var popupRoot = popup.getScene().getRoot();
                    popupRoot.getStyleClass().add("theme-moved");
                    popupRoot.getStyleClass().remove("theme-moved");
                }
            }
        }
    }
}
