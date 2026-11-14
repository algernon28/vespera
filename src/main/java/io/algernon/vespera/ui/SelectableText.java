package io.algernon.vespera.ui;

import java.util.List;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
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
        if (text == null || text.isEmpty()) {
            // The empty Label this replaces draws no text and so has no text height, only its padding.
            return 0;
        }
        Text measure = new Text(text == null ? "" : text);
        measure.setFont(font);
        measure.setWrappingWidth(wrappingWidth);
        return measure.getLayoutBounds().getHeight();
    }

    /** A wrapping label with no padding, in a scene of its own so that it has a skin to measure with. */
    private static Label measuringLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.setPadding(Insets.EMPTY);
        new Scene(new Group(label));
        return label;
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

    /**
     * A text on one line (ADR-364 §8): as wide as its text, shrinking to the width a label would, its start shown. Where
     * its text runs past its right edge it shows "…" there (ADR-377 §6): a {@link Text} of its own font and colour, on
     * the ground beneath it, laid over its right edge inside its padding, taking no mouse event and holding no part of
     * its text, visible exactly while the end of the text is out of view.
     */
    final class Line extends TextField implements SelectableText {

        /** What the "…" reads. */
        static final String CUT = "…";

        /** How far past the inner right edge the end of the text may lie and still be in view: the caret's width. */
        private static final double IN_VIEW = 1.0;

        private final Text ellipsis = new Text(CUT);

        /** The ground beneath the "…", so that the cut text does not show through it. */
        private final Rectangle ground = new Rectangle();

        /** The text the skin draws, once it is found. */
        private Text drawn;

        private Line(String text) {
            SelectableText.prepare(this, text);
            // the skin keeps the caret in view, which after Select All would leave the end of a shrunk text shown
            textProperty().addListener((observed, was, now) -> selectRange(0, 0));
            focusedProperty().addListener((observed, was, now) -> {
                if (!now) {
                    selectRange(0, 0);
                }
            });
            ellipsis.getStyleClass().add("ellipsis");
            ellipsis.fontProperty().bind(fontProperty());
            for (Node each : List.of(ground, ellipsis)) {
                each.setManaged(false);
                each.setMouseTransparent(true);
                each.setVisible(false);
            }
        }

        /** The "…" this line shows while the end of its text is out of view. */
        Text ellipsis() {
            return ellipsis;
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            ObservableList<Node> children = getChildren();
            int count = children.size();
            if (count < 2 || children.get(count - 2) != ground || children.get(count - 1) != ellipsis) {
                children.removeAll(ground, ellipsis);
                children.addAll(ground, ellipsis);
            }
            if (drawn == null && lookup(".text") instanceof Text found && found != ellipsis) {
                drawn = found;
                // the skin scrolls its text as the caret moves, and draws it in the theme's colour, outside a layout
                drawn.boundsInParentProperty().addListener((observed, was, now) -> placeEllipsis());
                drawn.fillProperty().addListener((observed, was, now) -> placeEllipsis());
            }
            placeEllipsis();
        }

        private void placeEllipsis() {
            if (drawn == null) {
                return;
            }
            Insets inside = getInsets();
            double right = getWidth() - inside.getRight();
            Bounds text = sceneToLocal(drawn.localToScene(drawn.getLayoutBounds()));
            boolean cut = text != null && text.getMaxX() > right + IN_VIEW;
            ellipsis.setVisible(cut);
            ground.setVisible(cut);
            if (!cut) {
                return;
            }
            double wide = ellipsis.getLayoutBounds().getWidth();
            Point2D baseline = sceneToLocal(drawn.localToScene(0, drawn.getY()));
            ellipsis.setFill(drawn.getFill());
            // a line narrower than the "…" keeps it inside its own left edge, so its bounds are still its own
            double left = Math.max(inside.getLeft(), right - wide);
            ellipsis.setLayoutX(left);
            ellipsis.setLayoutY(baseline.getY());
            ground.setX(left);
            ground.setY(inside.getTop());
            ground.setWidth(Math.max(0, right - left));
            ground.setHeight(Math.max(0, getHeight() - inside.getTop() - inside.getBottom()));
            groundBeneath();
        }

        /**
         * Fills the ground with the topmost opaque fill of this line's background or the nearest parent's that has one,
         * else the scene's: a colour the stylesheet gave, never one of this class's own (ADR-342 §3).
         */
        private void groundBeneath() {
            for (Node at = this; at != null; at = at.getParent()) {
                if (at instanceof Region region && region.getBackground() != null) {
                    List<BackgroundFill> fills = region.getBackground().getFills();
                    for (int i = fills.size() - 1; i >= 0; i--) {
                        if (fills.get(i).getFill().isOpaque()) {
                            ground.setFill(fills.get(i).getFill());
                            return;
                        }
                    }
                }
            }
            Scene scene = getScene();
            ground.setFill(scene == null ? null : scene.getFill());
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

        /**
         * A label of its own, in a scene of its own, which measures the text wrapped as the {@link Label} this replaces
         * would: a label's height is not a {@link Text}'s, since it snaps each line it draws.
         */
        private final Label measure = measuringLabel();

        @Override
        protected double computePrefHeight(double width) {
            String text = getText();
            if (text == null || text.isEmpty()) {
                return down(this);
            }
            double wrapAt = width < 0 ? -1 : Math.max(1, width - across(this));
            measure.setFont(getFont());
            measure.setText(text);
            measure.applyCss();
            return down(this) + measure.prefHeight(wrapAt);
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
