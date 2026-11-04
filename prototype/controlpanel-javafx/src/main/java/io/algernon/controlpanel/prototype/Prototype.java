package io.algernon.controlpanel.prototype;

import java.util.List;
import java.util.function.Function;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * PROTOTYPE, throwaway, branch {@code javafx} only.
 *
 * <p>Question: what should a desktop (JavaFX) control panel look like, in place of the
 * loopback-browser one in {@code io.algernon.controlpanel}? Three structurally different
 * variants over the same in-memory {@link Stub}, switchable from the floating bar at the
 * bottom or with ← / →. The first argument picks the starting variant (A, B or C), so
 * {@code -Dvariant=B} on the Maven command line is the equivalent of {@code ?variant=B}.
 */
public final class Prototype extends Application {

    record Variant(String key, String name, Function<Stub, Parent> build) {}

    private static final List<Variant> VARIANTS = List.of(
            new Variant("A", "Sidebar pages", VariantA::build),
            new Variant("B", "Single-screen cockpit", VariantB::build),
            new Variant("C", "Guided wizard", VariantC::build));

    private final Stub stub = new Stub();
    private final StackPane root = new StackPane();
    private final Label current = new Label();
    private int index;
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        List<String> args = getParameters().getRaw();
        String wanted = args.isEmpty() ? "A" : args.getFirst().trim().toUpperCase();
        for (int i = 0; i < VARIANTS.size(); i++) {
            if (VARIANTS.get(i).key().equals(wanted)) {
                index = i;
            }
        }
        Scene scene = new Scene(root, 1500, 960);
        scene.getStylesheets().add(Prototype.class.getResource("prototype.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (scene.getFocusOwner() instanceof TextInputControl) {
                return;
            }
            if (e.getCode() == KeyCode.LEFT) {
                show(index - 1);
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT) {
                show(index + 1);
                e.consume();
            }
        });
        stage.setScene(scene);
        show(index);
        stage.show();
        if (args.contains("smoke")) {
            // Builds every variant once, runs the stub child to its stop, then exits.
            stub.start("run");
            javafx.animation.Timeline smoke = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> show(index + 1)));
            smoke.setCycleCount(VARIANTS.size() * 2);
            smoke.setOnFinished(e -> javafx.application.Platform.exit());
            smoke.play();
        }
    }

    private void show(int i) {
        index = Math.floorMod(i, VARIANTS.size());
        Variant variant = VARIANTS.get(index);
        root.getChildren().setAll(variant.build().apply(stub), switcher());
        current.setText(variant.key() + " (" + variant.name() + ")");
        stage.setTitle("Vespera — PROTOTYPE variant " + variant.key() + " (" + variant.name() + ")");
        System.out.println("variant " + variant.key() + " (" + variant.name() + ")   relaunch: "
                + "./mvnw -f prototype/controlpanel-javafx/pom.xml javafx:run -Dvariant=" + variant.key());
        System.out.println("  " + stub.dump());
    }

    private HBox switcher() {
        Button previous = new Button("◀");
        previous.setOnAction(e -> show(index - 1));
        Button next = new Button("▶");
        next.setOnAction(e -> show(index + 1));
        previous.setFocusTraversable(false);
        next.setFocusTraversable(false);
        HBox bar = new HBox(10, previous, current, next);
        bar.getStyleClass().add("switcher");
        bar.setAlignment(Pos.CENTER);
        bar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(bar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(bar, new javafx.geometry.Insets(0, 0, 18, 0));
        return bar;
    }
}
