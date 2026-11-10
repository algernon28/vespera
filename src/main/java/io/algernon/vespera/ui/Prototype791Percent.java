package io.algernon.vespera.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * PROTOTYPE #791, throwaway, never on main. Three variants of the steps chart's percentages on the real StepsChart and
 * the real stylesheet, fed a fake run that walks every step. ← / → or the bottom bar switch variant; "Dark" switches
 * theme; "Pause" freezes the run. Start: {@code java -cp ... io.algernon.vespera.ui.Prototype791Percent$Main}.
 */
public final class Prototype791Percent extends Application {

    /** The launcher, outside the Application subclass so JavaFX starts from the class path. */
    public static final class Main {
        public static void main(String[] args) {
            Application.launch(Prototype791Percent.class, args);
        }
    }

    private static final List<String> VARIANTS = List.of("A", "B", "C", "");
    private static final List<String> NAMES = List.of("on the bar", "own column after the bar",
            "big number leading a thin bar", "today (percent only in 'running N%')");
    private static final int TICKS_PER_STEP = 25;

    private int current;
    private int tick;
    private boolean paused;
    private StepsChart chart;
    private final BorderPane root = new BorderPane();
    private final Label variantWords = new Label();

    @Override
    public void start(Stage stage) {
        root.getStylesheets().add(ControlPanelWindow.class.getResource("control-panel.css").toExternalForm());
        ToggleButton dark = new ToggleButton("Dark");
        dark.selectedProperty().addListener((o, was, now) -> {
            root.getStyleClass().remove("dark");
            if (now) {
                root.getStyleClass().add("dark");
            }
        });
        ToggleButton pause = new ToggleButton("Pause");
        pause.selectedProperty().addListener((o, was, now) -> paused = now);
        Label header = new Label("PROTOTYPE #791: where the progress bars' percentage sits (fake run)");
        HBox top = new HBox(12, header, dark, pause);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(12));
        root.setTop(top);

        Button back = new Button("←");
        back.setOnAction(e -> switchTo(current - 1));
        Button forward = new Button("→");
        forward.setOnAction(e -> switchTo(current + 1));
        variantWords.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        HBox bar = new HBox(12, back, variantWords, forward);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(8));
        bar.setStyle("-fx-background-color: #222; -fx-background-radius: 20;");
        bar.setMaxWidth(560);
        BorderPane.setMargin(bar, new Insets(8));
        BorderPane.setAlignment(bar, Pos.CENTER);
        root.setBottom(bar);

        switchTo(0);
        Scene scene = new Scene(root, 1100, 720);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT) {
                switchTo(current - 1);
            } else if (e.getCode() == KeyCode.RIGHT) {
                switchTo(current + 1);
            }
        });
        stage.setTitle("PROTOTYPE #791 progress percentage");
        stage.setScene(scene);
        stage.show();

        Timeline run = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            if (!paused) {
                tick++;
                chart.show(fake(tick));
            }
        }));
        run.setCycleCount(Timeline.INDEFINITE);
        run.play();
    }

    private void switchTo(int index) {
        current = Math.floorMod(index, VARIANTS.size());
        StepsChart.prototypeVariant = VARIANTS.get(current);
        chart = new StepsChart(() -> { });
        chart.setPadding(new Insets(12));
        chart.show(fake(tick));
        ScrollPane scroll = new ScrollPane(chart);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
        String key = VARIANTS.get(current).isEmpty() ? "today" : VARIANTS.get(current);
        variantWords.setText(key + " (" + NAMES.get(current) + ")   " + (current + 1) + "/" + VARIANTS.size());
        root.requestFocus();
    }

    /**
     * A fake run at {@code t}: steps before the running one finished, the running one counting to 100. content-census runs
     * without a total for its first ten ticks (an indeterminate bar); seed-extraction fails, so a failed state shows too.
     */
    private static ProgressFile.Progress fake(int t) {
        List<String> all = Legs.ALL.stream().flatMap(leg -> leg.steps().stream()).toList();
        int running = (t / TICKS_PER_STEP) % all.size();
        long k = t % TICKS_PER_STEP;
        List<ProgressFile.Step> steps = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            String name = all.get(i);
            if (i < running) {
                steps.add(name.equals("seed-extraction") ? new ProgressFile.Step(name, "failed", 37L, 120L)
                        : new ProgressFile.Step(name, "finished", 100L, 100L));
            } else if (i == running) {
                boolean noTotal = name.equals("content-census") && k < 10;
                steps.add(noTotal ? new ProgressFile.Step(name, "running", null, null)
                        : new ProgressFile.Step(name, "running", k * 4 + 3, 100L));
            } else {
                steps.add(new ProgressFile.Step(name, "waiting", null, null));
            }
        }
        return new ProgressFile.Progress(false, steps, List.of());
    }
}
