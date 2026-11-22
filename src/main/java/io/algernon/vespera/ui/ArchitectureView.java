package io.algernon.vespera.ui;

import io.algernon.vespera.ui.Architecture.Kind;
import io.algernon.vespera.ui.Architecture.Lit;
import io.algernon.vespera.ui.Architecture.Part;
import java.util.ArrayList;
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
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polygon;
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

    /**
     * How long a pulse of a running outline takes, and how far a flowing wire's runners move in it: the gap between two
     * of them, so that each takes the place of the one ahead as the pulse ends.
     */
    private static final Duration PULSE = Duration.millis(1600);

    private static final double RUNNER_GAP = 24;

    /** How long an arrowhead is from its base to its tip, and how far its base reaches to either side of the wire. */
    private static final double HEAD_LENGTH = 9;

    private static final double HEAD_HALF_WIDTH = 5;

    /** How far a lane of runners lies to the right of a wire data goes along both ways, each way in a lane of its own. */
    private static final double LANE = 6;

    /** How far from either end of its lane a runner fades in or out. */
    private static final double FADE = 6;

    /** The height at or below which a part's texts stand in a row: the ledger's. */
    private static final double FLAT = 40;

    /** The faintest a running outline gets as it pulses. */
    private static final double FAINTEST = 0.35;

    /** One part as drawn: its box, the outline over it that pulses, its words, and how far its step has got. */
    private record Drawn(StackPane box, Region outline, SelectableText.Line words, SelectableText.Line percent) {
    }

    private final Map<Part, Drawn> parts = new EnumMap<>(Part.class);
    /** A wire as drawn: its line, its arrowheads at each end data arrives at, and its lanes of runners. */
    private record Laid(Polyline line, List<Polygon> heads, List<Lane> lanes) {
    }

    /** A lane of runners: the route they follow, from where data leaves to where it arrives, and the runners on it. */
    private record Lane(Route route, List<Polygon> runners) {
    }

    private final Map<Architecture.Wire, Laid> wires = new LinkedHashMap<>();
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
        wire("docling-gpu", 390, 440, 390, 425, 755, 425, 755, 440);
        wire("ollama-gpu", 665, 473, 685, 473);
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
        // the application's wires to its containers pass over the ledger, between the stages' wires and clear of its
        // words: data goes from a stage to the container and back, never through the ledger
        wire("docling", 249, 308, 249, 440);
        wire("ollama", 741, 308, 741, 385, 600, 385, 600, 440);
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

    /**
     * A wire through {@code points}, laid from where data leaves to where it arrives: its line, an arrowhead at its last
     * point, and one at its first where data goes both ways; and a lane of runners each way data goes, to the right of
     * the line where it goes both ways and along it otherwise (ADR-442 §6).
     */
    private void wire(String name, double... points) {
        Architecture.Wire wire = Architecture.WIRES.stream().filter(each -> each.name().equals(name)).findFirst()
                .orElseThrow();
        Route route = Route.through(points);
        // the line stops short of each arrowhead's tip, so that its width shows nowhere beside the narrowing head
        Route drawnRoute = route.trimmed(wire.bothWays() ? HEAD_LENGTH - 1 : 0, HEAD_LENGTH - 1);
        Polyline line = new Polyline(drawnRoute.points());
        line.setId("architecture-wire-" + name);
        line.getStyleClass().add("architecture-wire");
        line.setManaged(false);
        getChildren().add(line);
        List<Polygon> heads = new ArrayList<>(List.of(head("architecture-arrow-" + name, route)));
        List<Lane> lanes = new ArrayList<>();
        if (route.length() - (wire.bothWays() ? 2 : 1) * HEAD_LENGTH < RUNNER_GAP / 2) {
            // a wire too short to hold a runner says its flow by its accent and its arrowheads alone
            lanes = List.of();
        } else if (wire.bothWays()) {
            heads.add(head("architecture-arrow-" + name + "-back", route.reversed()));
            lanes.add(lane("architecture-runners-" + name, route.shifted(LANE)));
            lanes.add(lane("architecture-runners-" + name + "-back", route.reversed().shifted(LANE)));
        } else {
            lanes.add(lane("architecture-runners-" + name, route));
        }
        wires.put(wire, new Laid(line, List.copyOf(heads), List.copyOf(lanes)));
    }

    /** An arrowhead whose tip is the last point of {@code route}, pointing the way its last run goes. */
    private Polygon head(String id, Route route) {
        double[] tip = route.pointAt(route.length());
        double[] base = route.pointAt(route.length() - HEAD_LENGTH);
        double dx = (tip[0] - base[0]) / HEAD_LENGTH;
        double dy = (tip[1] - base[1]) / HEAD_LENGTH;
        Polygon head = new Polygon(tip[0], tip[1], base[0] - dy * HEAD_HALF_WIDTH, base[1] + dx * HEAD_HALF_WIDTH,
                base[0] + dy * HEAD_HALF_WIDTH, base[1] - dx * HEAD_HALF_WIDTH);
        head.setId(id);
        head.getStyleClass().add("architecture-arrow");
        head.setManaged(false);
        head.setMouseTransparent(true);
        getChildren().add(head);
        return head;
    }

    /** A lane along {@code route}: as many runners as fit it, a gap apart, hidden until its wire carries data. */
    private Lane lane(String id, Route route) {
        Group group = new Group();
        group.setId(id);
        group.setManaged(false);
        group.setMouseTransparent(true);
        List<Polygon> runners = new ArrayList<>();
        for (int i = 0; i <= (int) Math.ceil(route.length() / RUNNER_GAP); i++) {
            // a chevron about its own middle, pointing right until it is turned the way its run goes
            Polygon runner = new Polygon(-5, -5, -1, -5, 5, 0, -1, 5, -5, 5, 1, 0);
            runner.getStyleClass().add("architecture-runner");
            runner.setVisible(false);
            runners.add(runner);
        }
        group.getChildren().addAll(runners);
        getChildren().add(group);
        return new Lane(route, List.copyOf(runners));
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
        wires.forEach((wire, laid) -> {
            List<Node> marked = new ArrayList<>(laid.heads());
            marked.add(laid.line());
            for (Node node : marked) {
                node.getStyleClass().remove("flowing");
                if (lighting.flows(wire)) {
                    node.getStyleClass().add("flowing");
                }
            }
        });
        move(phase.get());
    }

    /**
     * Sets the parts that pulse and the runners that move where {@code at}, of a pulse's cycle from 0 to 1: each runner
     * of a wire carrying data stands {@code at} of a gap past its place, so that it runs the way data goes; with
     * motion off they stand still at their places, still saying which way it goes.
     */
    private void move(double at) {
        if (drawn == null) {
            return;
        }
        boolean moving = moving();
        double faded = moving ? (1 - FAINTEST) * (0.5 - 0.5 * Math.cos(2 * Math.PI * at)) : 0;
        parts.forEach((part, shown) -> shown.outline().setOpacity(drawn.of(part) == Lit.RUNNING ? 1 - faded : 1));
        wires.forEach((wire, laid) -> {
            boolean flows = drawn.flows(wire);
            for (Lane lane : laid.lanes()) {
                // runners stop short of the arrowhead at the lane's end, and of the one at its start where there is one
                double from = wire.bothWays() ? HEAD_LENGTH : 0;
                double to = lane.route().length() - HEAD_LENGTH;
                for (int i = 0; i < lane.runners().size(); i++) {
                    Polygon runner = lane.runners().get(i);
                    double along = (i + at) * RUNNER_GAP;
                    boolean shown = flows && along >= from && along <= to;
                    runner.setVisible(shown);
                    if (shown) {
                        double[] point = lane.route().pointAt(along);
                        runner.setLayoutX(point[0]);
                        runner.setLayoutY(point[1]);
                        runner.setRotate(point[2]);
                        runner.setOpacity(Math.min(1, Math.min(along - from, to - along) / FADE));
                    }
                }
            }
        });
    }

    /** Sets the drawing as it stands {@code at} of a pulse's cycle, which a test calls with the motion stopped. */
    void pose(double at) {
        move(at);
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

    /**
     * A route of straight runs through points: where each point lies, and how far along the route from its first one;
     * which a lane's runners follow and an arrowhead points along.
     */
    private record Route(double[] xs, double[] ys, double[] along) {

        static Route through(double... points) {
            int count = points.length / 2;
            double[] xs = new double[count];
            double[] ys = new double[count];
            double[] along = new double[count];
            for (int i = 0; i < count; i++) {
                xs[i] = points[2 * i];
                ys[i] = points[2 * i + 1];
                along[i] = i == 0 ? 0 : along[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            }
            return new Route(xs, ys, along);
        }

        double length() {
            return along[along.length - 1];
        }

        double[] points() {
            double[] points = new double[2 * xs.length];
            for (int i = 0; i < xs.length; i++) {
                points[2 * i] = xs[i];
                points[2 * i + 1] = ys[i];
            }
            return points;
        }

        /** The point {@code distance} along the route, and the angle in degrees of the run it lies on, clockwise from right. */
        double[] pointAt(double distance) {
            double clamped = Math.clamp(distance, 0, length());
            int run = 1;
            while (run < xs.length - 1 && along[run] < clamped) {
                run++;
            }
            double span = along[run] - along[run - 1];
            double share = span == 0 ? 0 : (clamped - along[run - 1]) / span;
            return new double[] {xs[run - 1] + share * (xs[run] - xs[run - 1]),
                    ys[run - 1] + share * (ys[run] - ys[run - 1]),
                    Math.toDegrees(Math.atan2(ys[run] - ys[run - 1], xs[run] - xs[run - 1]))};
        }

        /** The same route the other way, from its last point to its first. */
        Route reversed() {
            double[] points = new double[2 * xs.length];
            for (int i = 0; i < xs.length; i++) {
                points[2 * i] = xs[xs.length - 1 - i];
                points[2 * i + 1] = ys[ys.length - 1 - i];
            }
            return through(points);
        }

        /** The route moved {@code by} to its own right, each point along the runs it joins. */
        Route shifted(double by) {
            double[] points = new double[2 * xs.length];
            for (int i = 0; i < xs.length; i++) {
                int a = Math.max(0, i - 1);
                int b = Math.min(xs.length - 1, i + 1);
                double dx = xs[b] - xs[a];
                double dy = ys[b] - ys[a];
                double norm = Math.hypot(dx, dy);
                // to the right of the way the route goes, as the screen's y grows downwards
                points[2 * i] = xs[i] - dy / norm * by;
                points[2 * i + 1] = ys[i] + dx / norm * by;
            }
            return through(points);
        }

        /** The route with {@code fromStart} cut off its first run and {@code fromEnd} off its last. */
        Route trimmed(double fromStart, double fromEnd) {
            double[] points = points();
            double[] start = pointAt(fromStart);
            double[] end = pointAt(length() - fromEnd);
            points[0] = start[0];
            points[1] = start[1];
            points[points.length - 2] = end[0];
            points[points.length - 1] = end[1];
            return through(points);
        }
    }

    /** The node of {@code part}, which a test reads. */
    Node nodeOf(Part part) {
        return parts.get(part).box();
    }
}
