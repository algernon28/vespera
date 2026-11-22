package io.algernon.vespera.ui;

import io.algernon.vespera.ui.Architecture.Kind;
import io.algernon.vespera.ui.Architecture.Lit;
import io.algernon.vespera.ui.Architecture.Part;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polyline;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/**
 * The drawing of the Architecture window (ADR-442 §2, §4 to §6): the reference picture's groups, parts, wires and
 * legend, each part at the place the picture gives it, on a pane of a fixed size the window scales. Each part is its
 * own node, {@code architecture-<part>}, its texts {@link SelectableText}s, its state its style class; each wire its own,
 * {@code architecture-wire-<name>}, {@code flowing} while it carries data. Two things move, behind {@link #moving}: the
 * outline of a running part pulses and the dashes of a flowing wire move along it, both on one timeline.
 */
final class ArchitectureView extends Pane {

    /** The drawing's own size, which the window scales to fit. */
    static final double WIDTH = 1130;

    static final double HEIGHT = 530;

    /** How long a pulse of a running outline takes, and how far a flowing wire's dashes move in it: two of their periods. */
    private static final Duration PULSE = Duration.millis(1600);

    private static final double DASHES_MOVED = 24;

    /** The height at or below which a part's texts stand in a row: the ledger's. */
    private static final double FLAT = 40;

    /** The faintest a running outline gets as it pulses. */
    private static final double FAINTEST = 0.35;

    /** One part as drawn: its box, the outline over it that pulses, its words, and how far its step has got. */
    private record Drawn(StackPane box, Region outline, SelectableText.Line words, SelectableText.Line percent) {
    }

    private final Map<Part, Drawn> parts = new EnumMap<>(Part.class);
    private final Map<Architecture.Wire, Polyline> wires = new LinkedHashMap<>();
    private final DoubleProperty phase = new SimpleDoubleProperty();
    private final Timeline motion = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(phase, 0)),
            new KeyFrame(PULSE, new KeyValue(phase, 1, Interpolator.LINEAR)));
    private final Scale scale = new Scale(1, 1, 0, 0);
    private Architecture.Lighting drawn;

    ArchitectureView() {
        getTransforms().add(scale);
        setId("architecture-drawing");
        getStyleClass().add("architecture-drawing");
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        motion.setCycleCount(Animation.INDEFINITE);
        phase.addListener((observed, was, now) -> move(now.doubleValue()));

        group("inputs", "Inputs", Kind.INPUT, 10, 10, 130, 390);
        group("machine", "Operator's machine", Kind.CONTROL, 150, 10, 690, 390);
        group("control", "Control", Kind.CONTROL, 160, 34, 670, 68);
        group("application", "Vespera application", Kind.STAGE, 160, 108, 670, 200);
        SelectableText.Line gates = SelectableText.line("human gates: 6a, 6b");
        gates.setId("architecture-group-gates");
        gates.getStyleClass().addAll("architecture-group-title", kindClass(Kind.GATE));
        gates.relocate(700, 110);
        getChildren().add(gates);
        group("outputs", "Outputs", Kind.OUTPUT, 850, 10, 130, 390);
        group("connectors", "Connectors", Kind.CONNECTOR, 990, 10, 130, 390);
        group("docker", "Docker: local AI containers", Kind.CONTAINER, 150, 410, 690, 112);

        wire("archive", 130, 150, 160, 150);
        wire("seed-folder", 130, 280, 160, 280);
        wire("control-panel", 392, 79, 440, 79);
        wire("cli", 660, 79, 700, 79, 700, 108);
        for (int i = 0; i < Architecture.STAGES.size(); i++) {
            double middle = stageX(i) + 38;
            wire(Architecture.STAGES.get(i).id(), middle, 298, middle, 318);
        }
        wire("docling", 285, 352, 285, 440);
        wire("ollama", 545, 352, 545, 440);
        wire("gpu", 665, 473, 685, 473);
        wire("deliverable", 830, 120, 860, 120);
        wire("reports", 830, 290, 860, 290);
        wire("confluence", 970, 120, 1000, 120);
        wire("website", 970, 160, 985, 160, 985, 290, 1000, 290);

        part(Part.ARCHIVE, 20, 40, 110, 150);
        part(Part.SEED_FOLDER, 20, 205, 110, 150);
        part(Part.CONTROL_PANEL, 172, 56, 220, 46);
        part(Part.CLI, 440, 56, 220, 46);
        for (int i = 0; i < Architecture.STAGES.size(); i++) {
            part(Architecture.STAGES.get(i), stageX(i), 138, 76, 160);
        }
        part(Part.LEDGER, 160, 318, 670, 34);
        part(Part.DOCLING, 165, 440, 240, 70);
        part(Part.OLLAMA, 425, 440, 240, 70);
        part(Part.GPU, 685, 440, 140, 70);
        part(Part.DELIVERABLE, 860, 40, 110, 160);
        part(Part.REPORTS, 860, 215, 110, 150);
        part(Part.CONFLUENCE, 1000, 40, 110, 160);
        part(Part.WEBSITE, 1000, 215, 110, 150);
        legend(850, 410);
    }

    private static double stageX(int i) {
        return 170 + i * 82;
    }

    /** A group of the picture: an outline in its kind's colour and its title at its top left. */
    private void group(String name, String title, Kind kind, double x, double y, double width, double height) {
        Region outline = new Region();
        outline.getStyleClass().addAll("architecture-group", kindClass(kind));
        outline.resizeRelocate(x, y, width, height);
        outline.setManaged(false);
        SelectableText.Line words = SelectableText.line(title);
        words.setId("architecture-group-" + name);
        words.getStyleClass().addAll("architecture-group-title", kindClass(kind));
        words.relocate(x + 6, y + 2);
        getChildren().addAll(outline, words);
    }

    private void wire(String name, double... points) {
        Architecture.Wire wire = Architecture.WIRES.stream().filter(each -> each.name().equals(name)).findFirst()
                .orElseThrow();
        Polyline line = new Polyline(points);
        line.setId("architecture-wire-" + name);
        line.getStyleClass().add("architecture-wire");
        line.setManaged(false);
        wires.put(wire, line);
        getChildren().add(line);
    }

    /** A part: its box filled in its kind's colour, its title, the line saying what it is, and its words, beneath an outline. */
    private void part(Part part, double x, double y, double width, double height) {
        // a box as low as the ledger's draws its texts in a row, the others one beneath another
        boolean flat = height <= FLAT;
        Pane texts = flat ? new HBox() : new VBox();
        texts.getStyleClass().add("architecture-texts");
        if (texts instanceof HBox row) {
            row.setAlignment(Pos.CENTER);
            row.getStyleClass().add("architecture-row");
        } else if (texts instanceof VBox column) {
            column.setAlignment(Pos.CENTER);
            column.setFillWidth(false);
        }
        if (!part.number().isEmpty()) {
            SelectableText.Line number = SelectableText.line(part.number());
            number.getStyleClass().add("architecture-number");
            texts.getChildren().add(number);
        }
        for (String line : part.title().split("\n")) {
            SelectableText.Line title = SelectableText.line(line);
            title.getStyleClass().add("architecture-title");
            texts.getChildren().add(title);
        }
        for (String line : flat ? List.of(part.about()) : wrap(part.about(), width)) {
            SelectableText.Line about = SelectableText.line(line);
            about.getStyleClass().add("architecture-about");
            texts.getChildren().add(about);
        }
        SelectableText.Line percent = SelectableText.line("");
        percent.getStyleClass().add("architecture-words");
        SelectableText.Line words = SelectableText.line("");
        words.setId("architecture-words-" + part.id());
        words.getStyleClass().add("architecture-words");
        texts.getChildren().addAll(percent, words);
        Region outline = new Region();
        outline.getStyleClass().add("architecture-outline");
        outline.setMouseTransparent(true);
        StackPane box = new StackPane(texts, outline);
        box.setId("architecture-" + part.id());
        box.getStyleClass().addAll("architecture-part", kindClass(part.kind()));
        box.resizeRelocate(x, y, width, height);
        box.setManaged(false);
        parts.put(part, new Drawn(box, outline, words, percent));
        getChildren().add(box);
        show(words, "");
        show(percent, "");
    }

    /** {@code text} cut into lines that fit a box {@code width} wide, at its spaces. */
    private static List<String> wrap(String text, double width) {
        int perLine = (int) Math.max(8, width / 6.2);
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > perLine) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    /** The legend: the picture's colours by kind, and the states by outline and word (ADR-442 §4, §5). */
    private void legend(double x, double y) {
        Region frame = new Region();
        frame.getStyleClass().add("architecture-legend");
        frame.resizeRelocate(x, y, 270, 112);
        frame.setManaged(false);
        getChildren().add(frame);
        List<Object[]> kinds = List.of(new Object[] {Kind.STAGE, "Pipeline stage"},
                new Object[] {Kind.CONTAINER, "Local AI container"}, new Object[] {Kind.STORE, "Input, storage, output"},
                new Object[] {Kind.GATE, "Control, human gate"}, new Object[] {Kind.CONNECTOR, "Connector"});
        for (int i = 0; i < kinds.size(); i++) {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("architecture-swatch", kindClass((Kind) kinds.get(i)[0]));
            swatch.resizeRelocate(x + 10, y + 10 + i * 19, 14, 12);
            swatch.setManaged(false);
            SelectableText.Line words = SelectableText.line((String) kinds.get(i)[1]);
            words.getStyleClass().add("architecture-legend-text");
            words.relocate(x + 28, y + 5 + i * 19);
            getChildren().addAll(swatch, words);
        }
        List<String> states = List.of("▶ thick: running", "✓ finished", "✕ dashed: failed", "■ dotted: stopped",
                "⏸ double: at a gate", "○ down");
        for (int i = 0; i < states.size(); i++) {
            SelectableText.Line words = SelectableText.line(states.get(i));
            words.getStyleClass().add("architecture-legend-text");
            words.relocate(x + 152, y + 2 + i * 17);
            getChildren().add(words);
        }
    }

    private static String kindClass(Kind kind) {
        return "kind-" + kind.name().toLowerCase(Locale.ROOT);
    }

    private static void show(SelectableText.Line line, String text) {
        line.setText(text);
        line.setVisible(!text.isEmpty());
        line.setManaged(!text.isEmpty());
    }

    /** Draws {@code lighting}: each part's state class, words and percent, and each wire flowing or not. */
    void draw(Architecture.Lighting lighting) {
        drawn = lighting;
        parts.forEach((part, at) -> {
            Lit lit = lighting.of(part);
            at.box().getStyleClass().removeIf(each -> each.startsWith("lit-"));
            at.box().getStyleClass().add(lit.styleClass());
            show(at.words(), lit.words());
            show(at.percent(), lit == Lit.RUNNING && Architecture.STAGES.contains(part) ? lighting.percent() : "");
        });
        wires.forEach((wire, line) -> {
            line.getStyleClass().remove("flowing");
            if (lighting.flows(wire)) {
                line.getStyleClass().add("flowing");
            }
        });
        move(phase.get());
    }

    /** Sets the parts that pulse and the dashes that move where {@code at}, of a pulse's cycle from 0 to 1. */
    private void move(double at) {
        if (drawn == null) {
            return;
        }
        boolean moving = moving();
        double faded = moving ? (1 - FAINTEST) * (0.5 - 0.5 * Math.cos(2 * Math.PI * at)) : 0;
        parts.forEach((part, shown) -> shown.outline().setOpacity(drawn.of(part) == Lit.RUNNING ? 1 - faded : 1));
        wires.forEach((wire, line) -> line.setStrokeDashOffset(moving && drawn.flows(wire) ? -DASHES_MOVED * at : 0));
    }

    /** Starts or stops the one timeline that moves the drawing. */
    void moves(boolean move) {
        if (move && motion.getStatus() != Animation.Status.RUNNING) {
            motion.play();
        } else if (!move && motion.getStatus() == Animation.Status.RUNNING) {
            motion.stop();
            phase.set(0);
        }
        move(phase.get());
    }

    /** Draws the drawing {@code scale} times its own size, from its top left corner. */
    void scaleTo(double scale) {
        this.scale.setX(scale);
        this.scale.setY(scale);
    }

    /** How many times its own size the drawing is drawn. */
    double scale() {
        return scale.getX();
    }

    /** Whether the drawing moves now. */
    boolean moving() {
        return motion.getStatus() == Animation.Status.RUNNING;
    }

    /** The node of {@code part}, which a test reads. */
    Node nodeOf(Part part) {
        return parts.get(part).box();
    }
}
