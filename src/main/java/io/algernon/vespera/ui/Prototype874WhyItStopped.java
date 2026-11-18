package io.algernon.vespera.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * PROTOTYPE #874, throwaway, never on main. Where and how the cockpit says why an invocation stopped. A mock of the
 * cockpit's main column (header, child line, actions, the real StepsChart beside the output) on the real stylesheet,
 * fed five fake endings. Variants: today (the child line alone), A a centred card over a dimmed cockpit, B a panel in
 * the middle of the main column above the chart, C a third column between the chart and the output. ← / → or the
 * bottom bar switch variant; the top bar picks the ending and the theme.
 */
public final class Prototype874WhyItStopped extends Application {

    /** The launcher, outside the Application subclass so JavaFX starts from the class path. */
    public static final class Main {
        public static void main(String[] args) {
            Application.launch(Prototype874WhyItStopped.class, args);
        }
    }

    /** One fake ending: what the panel would be handed. {@code step} null where it stopped before any step ran. */
    private record Scenario(String button, HowItEnded how, int exitCode, String step, String headline, String cause,
            String toDo, String action, List<String> output, int causeLine, String kind) {
    }

    private static final List<String> SPRING = List.of(
            "2026-11-18T09:14:02.118 INFO  io.algernon.vespera.EntryPoint : Starting EntryPoint v0.9.0 using Java 26",
            "2026-11-18T09:14:02.120 INFO  io.algernon.vespera.EntryPoint : The following 1 profile is active: \"local\"",
            "2026-11-18T09:14:03.402 INFO  com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Starting...",
            "2026-11-18T09:14:03.511 INFO  com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Start completed.");

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("Profile refused", HowItEnded.FAILED_OR_REFUSED, 1, null,
                    "Refused: profile.yaml names a seed folder that does not exist",
                    "seedFolder is <corpus root>/File sicuri, and no folder of that name is under the corpus root."
                            + " Nothing ran; the database is as it was.",
                    "Correct seedFolder in the Profile window, then Run again.",
                    "Open profile",
                    concat(SPRING, List.of(
                            "2026-11-18T09:14:03.640 ERROR i.a.v.engine.pipeline.VesperaCommand : refused: seedFolder"
                                    + " <corpus root>/File sicuri does not exist. Correct it in the control panel's"
                                    + " Profile window, and run again.",
                            "Report: E:/vespera_working_dir/kt-perin/invocation-report.html",
                            "2026-11-18T09:14:03.655 INFO  com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Shutdown"
                                    + " initiated...",
                            "2026-11-18T09:14:03.661 INFO  com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Shutdown"
                                    + " completed.")),
                    4, "refused"),
            new Scenario("Sidecar down", HowItEnded.FAILED_OR_REFUSED, 1, "extraction",
                    "Failed at extraction: the Docling sidecar stopped answering",
                    "http://localhost:5001 did not answer after 3 tries. 412 of 1,930 files were converted and are"
                            + " kept.",
                    "Start Docker Desktop (or Restart the sidecars), then Run again: extraction resumes at file 413.",
                    "Restart sidecars",
                    concat(SPRING, List.of(
                            "2026-11-18T09:15:10.004 INFO  i.a.v.engine.pipeline.SidecarStarter : the sidecars answer",
                            "2026-11-18T09:15:11.220 INFO  o.s.batch.core.job.SimpleStepHandler : Executing step:"
                                    + " [extraction]",
                            "2026-11-18T10:02:47.981 WARN  i.a.v.engine.extract.DoclingClient : try 1 of 3: Connection"
                                    + " refused",
                            "2026-11-18T10:02:52.990 WARN  i.a.v.engine.extract.DoclingClient : try 2 of 3: Connection"
                                    + " refused",
                            "2026-11-18T10:02:58.001 ERROR i.a.v.engine.extract.DoclingClient : http://localhost:5001"
                                    + " did not answer after 3 tries: Connection refused",
                            "2026-11-18T10:02:58.010 ERROR o.s.batch.core.step.AbstractStep : Encountered an error"
                                    + " executing step extraction in job vespera",
                            "Report: E:/vespera_working_dir/kt-perin/invocation-report.html",
                            "2026-11-18T10:02:58.044 INFO  o.s.batch.core.launch.support.SimpleJobLauncher : Job:"
                                    + " [SimpleJob: [name=vespera]] completed with the following parameters: [{}] and"
                                    + " the following status: [FAILED]")),
                    8, "failed"),
            new Scenario("Stage threw", HowItEnded.FAILED_OR_REFUSED, 1, "clustering",
                    "Failed at clustering: an error inside Vespera",
                    "IllegalStateException: cluster 17 has no member above the relevance floor. This is a defect of"
                            + " Vespera, not of the corpus or the profile.",
                    "Copy the details into a ticket. Running again stops at the same place; everything before"
                            + " clustering is kept.",
                    "Copy details",
                    concat(SPRING, List.of(
                            "2026-11-18T11:40:01.300 INFO  o.s.batch.core.job.SimpleStepHandler : Executing step:"
                                    + " [clustering]",
                            "2026-11-18T11:41:19.871 ERROR o.s.batch.core.step.AbstractStep : Encountered an error"
                                    + " executing step clustering in job vespera",
                            "java.lang.IllegalStateException: cluster 17 has no member above the relevance floor",
                            "\tat io.algernon.vespera.engine.cluster.Clustering.labelOf(Clustering.java:212)",
                            "\tat io.algernon.vespera.engine.cluster.ClusteringTasklet.execute(ClusteringTasklet.java:88)",
                            "\tat org.springframework.batch.core.step.tasklet.TaskletStep$ChunkTransactionCallback"
                                    + ".doInTransaction(TaskletStep.java:388)",
                            "\tat java.base/java.lang.Thread.run(Thread.java:1474)")),
                    6, "failed"),
            new Scenario("Your Stop", HowItEnded.STOPPED, 143, "embedding-scoring",
                    "Stopped by you at embedding-scoring",
                    "You pressed Stop. Every step before embedding-scoring finished and is kept; 3,108 of 5,240"
                            + " embeddings were written.",
                    "Run again to resume at embedding-scoring.",
                    null,
                    concat(SPRING, List.of(
                            "2026-11-18T12:05:40.001 INFO  o.s.batch.core.job.SimpleStepHandler : Executing step:"
                                    + " [embedding-scoring]",
                            "2026-11-18T12:31:12.448 INFO  i.a.v.engine.pipeline.VesperaCommand : stop asked: ending"
                                    + " after the current chunk")),
                    5, "stopped"),
            new Scenario("Panel defect", HowItEnded.DEFECT_OF_THE_CONTROL_PANEL, 2, null,
                    "The control panel started the jar wrongly",
                    "The jar does not take the option --label-autoo the control panel passed. Nothing ran.",
                    "This is a defect of the control panel: copy the details into a ticket.",
                    "Copy details",
                    List.of("Unknown option: '--label-autoo'",
                            "Usage: vespera [-hV] [COMMAND]",
                            "Commands:",
                            "  run      Runs the stages over the corpus root",
                            "  publish  Delivers the last drawing"),
                    0, "defect"));

    private static final List<String> VARIANTS = List.of("", "A", "B", "C");
    private static final List<String> NAMES = List.of("today: child line only", "centred card over a dimmed cockpit",
            "panel in the middle of the column", "third column between chart and output");

    private int variant;
    private int scenario;
    private boolean dismissed;
    private final BorderPane root = new BorderPane();
    private final Label variantWords = new Label();

    @Override
    public void start(Stage stage) {
        root.getStylesheets().add(ControlPanelWindow.class.getResource("control-panel.css").toExternalForm());
        root.getStyleClass().add("cockpit");

        ToggleButton dark = new ToggleButton("Dark");
        dark.selectedProperty().addListener((o, was, now) -> {
            root.getStyleClass().remove("dark");
            if (now) {
                root.getStyleClass().add("dark");
            }
        });
        HBox top = new HBox(8, new Label("PROTOTYPE #874 — ending:"));
        ToggleGroup endings = new ToggleGroup();
        for (int i = 0; i < SCENARIOS.size(); i++) {
            int index = i;
            ToggleButton each = new ToggleButton(SCENARIOS.get(i).button());
            each.setToggleGroup(endings);
            each.setSelected(i == 0);
            each.setOnAction(e -> {
                each.setSelected(true);
                scenario = index;
                dismissed = false;
                redraw();
            });
            top.getChildren().add(each);
        }
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        top.getChildren().addAll(gap, dark);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8, 12, 8, 12));
        top.setStyle("-fx-background-color: -vespera-rule;");
        root.setTop(top);

        Button back = new Button("←");
        back.setOnAction(e -> switchTo(variant - 1));
        Button forward = new Button("→");
        forward.setOnAction(e -> switchTo(variant + 1));
        variantWords.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        HBox bar = new HBox(12, back, variantWords, forward);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(8));
        bar.setStyle("-fx-background-color: #222; -fx-background-radius: 20;");
        bar.setMaxWidth(560);
        BorderPane.setMargin(bar, new Insets(8));
        BorderPane.setAlignment(bar, Pos.CENTER);
        root.setBottom(bar);

        Scene scene = new Scene(root, 1280, 800);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getTarget() instanceof TextArea) {
                return;
            }
            if (e.getCode() == KeyCode.LEFT) {
                switchTo(variant - 1);
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT) {
                switchTo(variant + 1);
                e.consume();
            }
        });
        switchTo(0);
        stage.setTitle("PROTOTYPE #874 why the invocation stopped");
        stage.setScene(scene);
        stage.show();
    }

    private void switchTo(int index) {
        variant = Math.floorMod(index, VARIANTS.size());
        dismissed = false;
        String key = VARIANTS.get(variant).isEmpty() ? "today" : VARIANTS.get(variant);
        variantWords.setText(key + " (" + NAMES.get(variant) + ")   " + (variant + 1) + "/" + VARIANTS.size());
        redraw();
    }

    // ---- the mock cockpit

    private void redraw() {
        Scenario s = SCENARIOS.get(scenario);
        String v = VARIANTS.get(variant);

        Label where = new Label("E:/vespera_working_dir/kt-perin");
        where.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label childLine = new Label(todaysChildLine(s));
        childLine.setWrapText(true);
        HBox childRow = new HBox(8, childLine);
        childRow.setAlignment(Pos.CENTER_LEFT);
        if (!v.isEmpty()) {
            childLine.setText("run: " + shortHow(s) + (s.step() == null ? "" : " at " + s.step()));
            childLine.setStyle("-fx-text-fill: " + colourOf(s) + "; -fx-font-weight: bold;");
            if (v.equals("A") && dismissed) {
                Hyperlink reopen = new Hyperlink("Why it stopped…");
                reopen.setOnAction(e -> {
                    dismissed = false;
                    redraw();
                });
                childRow.getChildren().add(reopen);
            }
        }
        VBox header = new VBox(2, where, childRow);
        header.getStyleClass().add("header");

        HBox actions = new HBox(8, new Button("Run"), disabled(new Button("Stop")), new Button("Shut down Docker"),
                new Button("Publish"));
        actions.getStyleClass().add("actions");
        VBox actionCard = new VBox(actions);
        actionCard.getStyleClass().add("card");

        StepsChart chart = new StepsChart(() -> { });
        chart.setPadding(new Insets(12));
        chart.show(fakeReport(s), s.how() == HowItEnded.STOPPED ? StepsChart.STOPPED : StepsChart.FAILED);
        ScrollPane chartScroll = new ScrollPane(chart);
        chartScroll.setFitToWidth(true);

        TextArea output = new TextArea(String.join("\n", s.output()));
        output.setEditable(false);
        output.getStyleClass().add("output");
        output.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");

        SplitPane split;
        if (v.equals("C")) {
            split = new SplitPane(chartScroll, columnPanel(s, output), output);
            split.setDividerPositions(0.33, 0.62);
            selectCauseLine(output, s);
        } else {
            split = new SplitPane(chartScroll, output);
            split.setDividerPositions(0.5);
        }
        split.setOrientation(Orientation.HORIZONTAL);
        split.setPrefHeight(470);
        split.getStyleClass().add("steps-and-output");

        VBox main = new VBox(12, header, actionCard);
        if (v.equals("B")) {
            main.getChildren().add(middlePanel(s));
        }
        main.getChildren().add(split);
        VBox.setVgrow(split, Priority.ALWAYS);
        main.getStyleClass().add("main");
        main.setPadding(new Insets(16));

        Node centre = main;
        if (v.equals("A") && !dismissed) {
            Region scrim = new Region();
            scrim.setStyle("-fx-background-color: rgba(10,8,20,0.45);");
            StackPane stack = new StackPane(main, scrim, centredCard(s));
            centre = stack;
        }
        root.setCenter(centre);
        root.requestFocus();
    }

    private static String todaysChildLine(Scenario s) {
        String how = switch (s.how()) {
            case ENDED -> "Ended";
            case FAILED_OR_REFUSED -> "Failed or refused, exit " + s.exitCode();
            case DEFECT_OF_THE_CONTROL_PANEL -> "Defect of the control panel, exit " + s.exitCode();
            case STOPPED -> "Stopped";
        };
        List<String> lines = s.output().reversed().stream()
                .filter(line -> !line.isBlank() && !line.startsWith("Report: ")).toList();
        return "run: " + how + (lines.isEmpty() ? "" : " (" + lines.getFirst() + ")");
    }

    // ---- variant A: a centred card over a dimmed cockpit, dismissable, reopened from the child line

    private Node centredCard(Scenario s) {
        VBox card = panelBody(s, true);
        card.setMaxWidth(620);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setStyle("-fx-background-color: -vespera-surface; -fx-background-radius: 10;"
                + " -fx-border-color: " + colourOf(s) + " transparent transparent transparent;"
                + " -fx-border-width: 6 0 0 0; -fx-border-radius: 10 10 0 0;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 24, 0, 0, 6);");
        StackPane.setAlignment(card, Pos.CENTER);
        return card;
    }

    // ---- variant B: a panel in the middle of the column, above the chart and output, stays until the next Run

    private Node middlePanel(Scenario s) {
        VBox body = panelBody(s, false);
        HBox.setHgrow(body, Priority.ALWAYS);
        Region stripe = new Region();
        stripe.setMinWidth(6);
        stripe.setStyle("-fx-background-color: " + colourOf(s) + ";");
        HBox panel = new HBox(16, stripe, body);
        panel.setPadding(new Insets(0, 16, 0, 0));
        panel.setStyle("-fx-background-color: -vespera-surface; -fx-border-color: -vespera-rule;"
                + " -fx-border-radius: 6; -fx-background-radius: 6;");
        body.setPadding(new Insets(14, 0, 14, 0));
        return panel;
    }

    // ---- variant C: a third column between chart and output, tying the failed step to the output line

    private Node columnPanel(Scenario s, TextArea output) {
        VBox body = panelBody(s, false);
        body.setPadding(new Insets(14));
        if (s.causeLine() >= 0) {
            Hyperlink show = new Hyperlink("→ the line in the output");
            show.setOnAction(e -> selectCauseLine(output, s));
            body.getChildren().add(2, show);
        }
        body.setStyle("-fx-background-color: -vespera-surface; -fx-border-color: " + colourOf(s)
                + "; -fx-border-width: 0 0 0 4;");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setMinWidth(260);
        return scroll;
    }

    private static void selectCauseLine(TextArea output, Scenario s) {
        int from = 0;
        for (int i = 0; i < s.causeLine() && i < s.output().size(); i++) {
            from += s.output().get(i).length() + 1;
        }
        int to = from + s.output().get(Math.min(s.causeLine(), s.output().size() - 1)).length();
        output.selectRange(from, to);
    }

    // ---- the panel's content, the same in every variant; only its place and frame differ

    private VBox panelBody(Scenario s, boolean withDismiss) {
        Label kind = new Label(kindWords(s).toUpperCase());
        kind.setStyle("-fx-text-fill: " + colourOf(s) + "; -fx-font-weight: bold; -fx-font-size: 11px;");
        Label headline = new Label(s.headline());
        headline.setWrapText(true);
        headline.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        Label cause = new Label(s.cause());
        cause.setWrapText(true);

        GridPane facts = new GridPane();
        facts.setHgap(12);
        facts.setVgap(2);
        facts.addRow(0, dim("Stopped at"), new Label(s.step() == null ? "before any step" : stepWords(s.step())));
        facts.addRow(1, dim("Exit code"), new Label(String.valueOf(s.exitCode())));
        facts.addRow(2, dim("Ended"), new Label("2026-11-18 " + endedAt(s)));

        Label toDoTitle = dim("What to do");
        Label toDo = new Label(s.toDo());
        toDo.setWrapText(true);
        toDo.setStyle("-fx-font-weight: bold;");
        VBox toDoBox = new VBox(2, toDoTitle, toDo);
        toDoBox.setPadding(new Insets(8, 10, 8, 10));
        toDoBox.setStyle("-fx-background-color: -vespera-ground; -fx-background-radius: 6;");

        HBox buttons = new HBox(8);
        if (s.action() != null) {
            Button primary = new Button(s.action());
            primary.setDefaultButton(true);
            primary.setOnAction(e -> copy(s));
            buttons.getChildren().add(primary);
        }
        if (s.action() == null || !s.action().equals("Copy details")) {
            Button copy = new Button("Copy details");
            copy.setOnAction(e -> copy(s));
            buttons.getChildren().add(copy);
        }
        if (s.how() == HowItEnded.STOPPED || s.kind().equals("failed")) {
            buttons.getChildren().add(new Button("Run again"));
        }
        if (withDismiss) {
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            Button dismiss = new Button("Dismiss");
            dismiss.setCancelButton(true);
            dismiss.setOnAction(e -> {
                dismissed = true;
                redraw();
            });
            buttons.getChildren().addAll(gap, dismiss);
        }

        VBox body = new VBox(10, kind, headline, cause, facts, toDoBox, buttons);
        if (withDismiss) {
            List<String> last = s.output().subList(Math.max(0, s.output().size() - 8), s.output().size());
            TextArea tail = new TextArea(String.join("\n", last));
            tail.setEditable(false);
            tail.setPrefRowCount(6);
            tail.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
            TitledPane lines = new TitledPane("Last lines of the output", tail);
            lines.setExpanded(false);
            body.getChildren().add(lines);
        }
        return body;
    }

    private static Label dim(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: -vespera-dim;");
        return label;
    }

    private static String kindWords(Scenario s) {
        return switch (s.kind()) {
            case "refused" -> "Refused — nothing ran";
            case "failed" -> "Failed";
            case "stopped" -> "Stopped by you";
            default -> "Defect of the control panel";
        };
    }

    private static String shortHow(Scenario s) {
        return switch (s.kind()) {
            case "refused" -> "Refused";
            case "failed" -> "Failed";
            case "stopped" -> "Stopped";
            default -> "Defect of the control panel";
        };
    }

    private static String colourOf(Scenario s) {
        return s.how() == HowItEnded.STOPPED ? "-vespera-accent" : "-vespera-bad";
    }

    private static String stepWords(String step) {
        for (Legs.Leg leg : Legs.ALL) {
            int at = leg.steps().indexOf(step);
            if (at >= 0) {
                return step + " (" + leg.name() + ", step " + (at + 1) + " of " + leg.steps().size() + ")";
            }
        }
        return step;
    }

    private static String endedAt(Scenario s) {
        String line = s.output().isEmpty() ? "" : s.output().getLast();
        return line.length() > 23 && line.startsWith("2026-") ? line.substring(11, 19) : "09:14:03";
    }

    private static void copy(Scenario s) {
        ClipboardContent content = new ClipboardContent();
        content.putString(s.headline() + "\n" + s.cause() + "\n" + s.toDo() + "\nexit " + s.exitCode() + "\n\n"
                + String.join("\n", s.output()));
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static Button disabled(Button button) {
        button.setDisable(true);
        return button;
    }

    /** Steps before {@code s.step()} finished, that one still "running" (drawn as failed or stopped), the rest waiting. */
    private static InvocationReportFile.Report fakeReport(Scenario s) {
        List<InvocationReportFile.Step> steps = new ArrayList<>();
        boolean reached = s.step() == null;
        for (Legs.Leg leg : Legs.ALL) {
            for (String name : leg.steps()) {
                if (reached) {
                    steps.add(new InvocationReportFile.Step(name, leg.name(), "waiting", null, null));
                } else if (name.equals(s.step())) {
                    reached = true;
                    steps.add(new InvocationReportFile.Step(name, leg.name(), "running", 412L, 1930L));
                } else {
                    steps.add(new InvocationReportFile.Step(name, leg.name(), "finished", 100L, 100L));
                }
            }
        }
        InvocationReportFile.Invocation invocation = new InvocationReportFile.Invocation("prototype", 1L, "run", null,
                null, "running", null, null, null, 1L);
        InvocationReportFile.Report none = InvocationReportFile.Report.none();
        return new InvocationReportFile.Report(none.format(), invocation, null, null, null, steps, List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, List.of(), List.of());
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
