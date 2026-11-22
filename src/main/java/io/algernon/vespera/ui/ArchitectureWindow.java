package io.algernon.vespera.ui;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * The one Architecture window of a cockpit (ADR-442): a stage of its own, with no owner and no modality, whose scene's
 * root holds the line saying what runs, {@code architecture-now}, the motion switch and the 100% toggle, and beneath them
 * the drawing, {@link ArchitectureView}, fitted to the window or at its own size with scrolling. It is handed the report
 * and the word the steps chart is handed, and reads the cockpit's child, the header's last check and whether the
 * containers run on the GPU through the suppliers it is given, as it draws and once a second while it is shown. It keeps
 * its size and place for the cockpit's next opening of it and nowhere else, follows the cockpit's stylesheet and
 * {@code dark} style class, and moves nothing while it is hidden or minimized.
 */
final class ArchitectureWindow {

    private static final String TITLE = "Architecture";

    /** Its first size: the drawing at its own size, with the line and the switches above it. */
    private static final double FIRST_WIDTH = 1180;
    private static final double FIRST_HEIGHT = 680;

    /** The motion switch's choices, the system's setting first (ADR-442 §6). */
    static final String AS_THE_SYSTEM = "Motion: as the system";
    static final String MOTION_ON = "Motion: on";
    static final String MOTION_OFF = "Motion: off";

    /** How often the window reads the cockpit's child, check and image while it is shown. */
    private static final Duration READING = Duration.seconds(1);

    private record Placed(double x, double y, double width, double height) {
    }

    private final BorderPane cockpitRoot;
    private final Supplier<Architecture.Child> child;
    private final Supplier<DockerCheck> check;
    private final BooleanSupplier gpu;
    private final BorderPane root = new BorderPane();
    private final SelectableText.Wrapping now = SelectableText.wrapping("Now: nothing is running.");
    private final ToggleButton fullSize = new ToggleButton("100%");
    private final ChoiceBox<String> motion = new ChoiceBox<>();
    private final ArchitectureView view = new ArchitectureView();
    /** The drawing's holder, as large as the drawing scaled, so that the scroll lays it out and scrolls over it. */
    private final Pane holder = new Pane(view);
    private final ScrollPane scroll = new ScrollPane(holder);
    private final Timeline reading = new Timeline(new KeyFrame(READING, event -> draw()));
    private InvocationReportFile.Report report = InvocationReportFile.Report.none();
    private String endedAs;
    /** What the window does as the system's reduced-motion setting changes, held here so that the setting holds it weakly. */
    private final ChangeListener<Boolean> reducedMotion = (observed, was, reduced) -> move();
    private Stage stage;
    private Placed kept;

    /**
     * The window of the cockpit whose root is {@code cockpitRoot}, reading its child, the header's last check (null
     * before any) and whether the containers run with {@code compose.gpu.yaml} from the three suppliers.
     */
    ArchitectureWindow(BorderPane cockpitRoot, Supplier<Architecture.Child> child, Supplier<DockerCheck> check,
            BooleanSupplier gpu) {
        this.cockpitRoot = cockpitRoot;
        this.child = child;
        this.check = check;
        this.gpu = gpu;
        now.setId("architecture-now");
        now.getStyleClass().add("architecture-now");
        fullSize.setId("architecture-full-size");
        motion.setId("architecture-motion");
        motion.getItems().setAll(List.of(AS_THE_SYSTEM, MOTION_ON, MOTION_OFF));
        motion.getSelectionModel().select(AS_THE_SYSTEM);
        motion.getSelectionModel().selectedItemProperty().addListener((observed, was, chosen) -> move());
        fullSize.selectedProperty().addListener((observed, was, chosen) -> fit());
        HBox switches = new HBox(motion, fullSize);
        switches.getStyleClass().add("architecture-switches");
        VBox top = new VBox(now, switches);
        top.getStyleClass().add("architecture-top");
        scroll.setId("architecture-scroll");
        scroll.getStyleClass().add("architecture-scroll");
        scroll.viewportBoundsProperty().addListener((observed, was, bounds) -> fit());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setTop(top);
        root.setCenter(scroll);
        root.getStyleClass().addAll("cockpit", "architecture-window");
        reading.setCycleCount(Animation.INDEFINITE);
        // the system's setting outlives every window, so it holds this one's listener weakly, and the window holds it
        Platform.getPreferences().reducedMotionProperty().addListener(new WeakChangeListener<>(reducedMotion));
        follow();
        cockpitRoot.getStyleClass().addListener((ListChangeListener<String>) change -> follow());
    }

    /**
     * The cockpit's child as the window lights it: none before {@code started} names a command; else alive until
     * {@code state} has its ending, and for a publish the destinations {@code destinations} names, read only then.
     */
    static Architecture.Child childOf(ControlPanelState.Start started, ControlPanelState state,
            Supplier<List<String>> destinations) {
        if (started == null) {
            return Architecture.Child.NONE;
        }
        Optional<Ending> ending = state.ending();
        return new Architecture.Child(started, ending.isEmpty(), ending.map(Ending::howItEnded),
                started == ControlPanelState.Start.PUBLISH ? destinations.get() : List.of());
    }

    /** Takes the cockpit's stylesheet and, as it has it or not, the {@code dark} style class. */
    private void follow() {
        root.getStylesheets().setAll(cockpitRoot.getStylesheets());
        boolean dark = cockpitRoot.getStyleClass().contains("dark");
        root.getStyleClass().remove("dark");
        if (dark) {
            root.getStyleClass().add("dark");
        }
    }

    /**
     * The report the steps chart was handed and the word a step still running in it reads, null where none (ADR-387
     * §1): drawn at once where the window is open, and kept for its next opening otherwise.
     */
    void show(InvocationReportFile.Report shown, String shownEndedAs) {
        report = shown == null ? InvocationReportFile.Report.none() : shown;
        endedAs = shownEndedAs;
        if (isOpen()) {
            draw();
        }
    }

    /** Opens the window, or shows the one open again where it is minimized, brings it to the front and gives it the focus. */
    void open() {
        if (stage == null) {
            stage = new Stage();
            stage.setTitle(TITLE);
            stage.setScene(new Scene(root, FIRST_WIDTH, FIRST_HEIGHT));
            stage.setResizable(true);
            stage.setOnHiding(event -> kept = new Placed(stage.getX(), stage.getY(), stage.getWidth(),
                    stage.getHeight()));
            stage.showingProperty().addListener((observed, was, showing) -> move());
            stage.iconifiedProperty().addListener((observed, was, iconified) -> move());
        }
        follow();
        if (!stage.isShowing() && kept != null) {
            stage.setX(kept.x());
            stage.setY(kept.y());
            stage.setWidth(kept.width());
            stage.setHeight(kept.height());
        }
        draw();
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
        move();
    }

    boolean isOpen() {
        return stage != null && stage.isShowing();
    }

    /** Closes the window asking nothing, as the cockpit's closing does. */
    void close() {
        if (stage != null) {
            stage.hide();
        }
    }

    /** Lights the drawing and the line from the report kept and what the suppliers read now. */
    private void draw() {
        Architecture.Lighting lighting = Architecture.light(new Architecture.Reading(report, endedAs, child.get(),
                check.get(), gpu.getAsBoolean()));
        now.setText(lighting.now());
        view.draw(lighting);
    }

    /**
     * Moves the drawing and reads once a second exactly while the window is shown and not minimized; the drawing moves
     * only where the switch says on, or says the system and the system asks for no reduced motion (ADR-442 §6).
     */
    private void move() {
        boolean shown = stage != null && stage.isShowing() && !stage.isIconified();
        String chosen = motion.getSelectionModel().getSelectedItem();
        boolean wanted = MOTION_ON.equals(chosen)
                || AS_THE_SYSTEM.equals(chosen) && !Platform.getPreferences().isReducedMotion();
        view.moves(shown && wanted);
        if (shown) {
            reading.play();
        } else {
            reading.stop();
        }
    }

    /** Scales the drawing to the scroll's viewport, or draws it at its own size where 100% is pressed (ADR-442 §7). */
    private void fit() {
        boolean full = fullSize.isSelected();
        double width = scroll.getViewportBounds().getWidth();
        double height = scroll.getViewportBounds().getHeight();
        double scale = full || width <= 0 || height <= 0 ? 1
                : Math.min(width / ArchitectureView.WIDTH, height / ArchitectureView.HEIGHT);
        view.scaleTo(scale);
        holder.setMinSize(ArchitectureView.WIDTH * scale, ArchitectureView.HEIGHT * scale);
        holder.setPrefSize(ArchitectureView.WIDTH * scale, ArchitectureView.HEIGHT * scale);
        holder.setMaxSize(ArchitectureView.WIDTH * scale, ArchitectureView.HEIGHT * scale);
        ScrollPane.ScrollBarPolicy bars = full ? ScrollPane.ScrollBarPolicy.AS_NEEDED : ScrollPane.ScrollBarPolicy.NEVER;
        scroll.setHbarPolicy(bars);
        scroll.setVbarPolicy(bars);
    }

    /**
     * Whether the containers run with {@code compose.gpu.yaml} beside {@code compose.yaml}: exactly where the converter's
     * image in force, {@code held} or where it is blank the one the sources start from, is the GPU image (ADR-442's
     * Context).
     */
    static boolean gpuInUse(String held, StartingValues.Sources sources) {
        String image = held == null || held.isBlank()
                ? StartingValues.of(StartingValues.DOCLING_IMAGE, sources).value() : held.strip();
        return StartingValues.DOCLING_IMAGE_GPU.equals(image);
    }
}
