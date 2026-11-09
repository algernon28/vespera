package io.algernon.vespera.ui;

import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * A text of the window outside a control, drawn as the label it replaces and copied as the output panel's text is
 * (ADR-364 §8): a {@link Line} for a text on one line, a {@link Wrapping} for one that wraps. Each is itself the node,
 * not editable, not reached by the Tab key, in the style class {@code selectable-text}, which the stylesheet draws with
 * no border and no background. Each measures its text with a {@link Text} of its own font whenever it is asked for a
 * size, and asks for a new layout whenever its text or its font changes.
 */
sealed interface SelectableText permits SelectableText.Line, SelectableText.Wrapping {

    /** The style class the stylesheet draws as the label it replaces. */
    String STYLE_CLASS = "selectable-text";

    /** The ellipsis a {@link Label} draws where its text is cut. */
    String ELLIPSIS = "...";

    /** A text drawn on one line. */
    static Line line(String text) {
        return new Line(text);
    }

    /** A text that wraps at the width its parent gives it. */
    static Wrapping wrapping(String text) {
        return new Wrapping(text);
    }

    private static void prepare(TextInputControl control, String text) {
        control.setEditable(false);
        control.setFocusTraversable(false);
        control.getStyleClass().add(STYLE_CLASS);
        control.setText(text);
        control.textProperty().addListener((observed, was, now) -> control.requestLayout());
        control.fontProperty().addListener((observed, was, now) -> control.requestLayout());
        // a TextArea holds a ScrollPane that would take a wheel's turn even with nothing to scroll: the parent gets it
        control.addEventFilter(ScrollEvent.SCROLL, event -> {
            event.consume();
            Node parent = control.getParent();
            if (parent != null) {
                Event.fireEvent(parent, event.copyFor(parent, parent));
            }
        });
    }

    private static double width(String text, Font font) {
        Text measure = new Text(text == null ? "" : text);
        measure.setFont(font);
        return measure.getLayoutBounds().getWidth();
    }

    private static double height(String text, Font font, double wrappingWidth) {
        Text measure = new Text(text == null ? "" : text);
        measure.setFont(font);
        measure.setWrappingWidth(wrappingWidth);
        return measure.getLayoutBounds().getHeight();
    }

    /** The width the control's own insets and those of the nodes its skin draws the text in take from its sides. */
    private static double across(TextInputControl control) {
        Insets own = control.getInsets();
        double taken = own.getLeft() + own.getRight();
        for (String inner : new String[] {".scroll-pane", ".content"}) {
            if (control.lookup(inner) instanceof Region region) {
                taken += region.getInsets().getLeft() + region.getInsets().getRight();
            }
        }
        return taken;
    }

    /** The height the control's own insets and those of the nodes its skin draws the text in take from its top and bottom. */
    private static double down(TextInputControl control) {
        Insets own = control.getInsets();
        double taken = own.getTop() + own.getBottom();
        for (String inner : new String[] {".scroll-pane", ".content"}) {
            if (control.lookup(inner) instanceof Region region) {
                taken += region.getInsets().getTop() + region.getInsets().getBottom();
            }
        }
        return taken;
    }

    /** What a {@link Label} of the same text and style asks as its least width: the ellipsis, or the text where that is less. */
    private static double leastWidth(TextInputControl control) {
        return across(control) + Math.min(width(control.getText(), control.getFont()),
                width(ELLIPSIS, control.getFont()));
    }

    /** A text on one line (ADR-364 §8): as wide as its text, shrinking to the width a label would, its start shown. */
    final class Line extends TextField implements SelectableText {

        private Line(String text) {
            SelectableText.prepare(this, text);
            // the skin keeps the caret in view, which after Select All would leave the end of a shrunk text shown
            textProperty().addListener((observed, was, now) -> selectRange(0, 0));
            focusedProperty().addListener((observed, was, now) -> {
                if (!now) {
                    selectRange(0, 0);
                }
            });
        }

        @Override
        protected double computePrefWidth(double height) {
            return across(this) + width(getText(), getFont());
        }

        @Override
        protected double computePrefHeight(double width) {
            return down(this) + height(getText(), getFont(), 0);
        }

        @Override
        protected double computeMinWidth(double height) {
            return leastWidth(this);
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxWidth(double height) {
            return computePrefWidth(height);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }
    }

    /** A text that wraps (ADR-364 §8): the width its parent gives it, the height its text takes wrapped there. */
    final class Wrapping extends TextArea implements SelectableText {

        private Wrapping(String text) {
            SelectableText.prepare(this, text);
            setWrapText(true);
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computePrefWidth(double height) {
            return across(this) + width(getText(), getFont());
        }

        @Override
        protected double computePrefHeight(double width) {
            double wrapAt = width < 0 ? 0 : Math.max(1, width - across(this));
            return down(this) + height(getText(), getFont(), wrapAt);
        }

        @Override
        protected double computeMinWidth(double height) {
            return leastWidth(this);
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxWidth(double height) {
            return computePrefWidth(height);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return Double.MAX_VALUE;
        }
    }
}
