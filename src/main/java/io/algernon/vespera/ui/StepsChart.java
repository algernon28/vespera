package io.algernon.vespera.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The steps chart: the four legs, each holding a row for each of its steps, each its own {@code step-<name>} (ADR-344 §1,
 * ADR-361). Each row lays its parts out on its own, so before the rows are laid out every stage and every step name is
 * given at least the width of the widest of its column, and the bars and the states after them begin at one place on every
 * row. The widths are read from the labels as the stylesheet draws them, so no width is named here.
 */
final class StepsChart extends VBox {

    /** One row of the steps chart: its stage, its name, one bar, its state and, on the running step, its own Stop. */
    private record StepRow(HBox row, Label stage, Label name, ProgressBar bar, Label percent, Label state,
            Label cost, Button stopHere) {
    }

    /** One leg of the steps chart: its pane, and the name, the one bar with its percent and the state its summary holds (ADR-361 §3). */
    private record LegView(TitledPane pane, Label name, StackPane barWithPercent, ProgressBar bar, Label percent,
            Label state) {
    }

    private final Map<String, StepRow> stepRows = new LinkedHashMap<>();
    private final Map<String, LegView> legViews = new LinkedHashMap<>();
    /** The legs the operator has opened or folded, which stay as they left them while the window is open (ADR-361 §6). */
    private final Set<String> chosenByTheOperator = new HashSet<>();
    private boolean foldingItself;
    private final Runnable stopAsked;

    /** The chart with every leg folded and waiting; {@code stopAsked} runs when the Stop of the running step is pressed. */
    StepsChart(Runnable stopAsked) {
        this.stopAsked = stopAsked;
        setId("steps");
        getStyleClass().add("steps");
        for (Legs.Leg leg : Legs.ALL) {
            getChildren().add(legView(leg).pane());
        }
    }

    @Override
    protected void layoutChildren() {
        alignColumn(stepRows.values().stream().map(StepRow::stage).toList());
        alignColumn(stepRows.values().stream().map(StepRow::name).toList());
        alignColumn(legViews.values().stream().map(LegView::name).toList());
        super.layoutChildren();
    }

    /** Gives every label of {@code column} at least the width the widest of them asks for. */
    private static void alignColumn(List<Label> column) {
        double widest = column.stream().mapToDouble(label -> label.prefWidth(-1)).max().orElse(0);
        column.forEach(label -> label.setMinWidth(widest));
    }

    /** A leg folded and waiting: its summary the pane's graphic, its rows the pane's content (ADR-361 §3). */
    private LegView legView(Legs.Leg leg) {
        VBox rows = new VBox();
        rows.getStyleClass().add("leg-rows");
        for (String step : leg.steps()) {
            StopCosts.StopCost cost = StopCosts.ROWS.stream().filter(each -> each.step().equals(step)).findFirst()
                    .orElseThrow();
            rows.getChildren().add(stepRow(cost).row());
        }
        Label name = new Label(leg.name());
        name.setId("leg-name-" + leg.name());
        ProgressBar bar = new ProgressBar(0);
        bar.setId("leg-bar-" + leg.name());
        Label percent = percentLabel("leg-percent-" + leg.name());
        StackPane barWithPercent = new StackPane(bar, percent);
        barWithPercent.getStyleClass().add("bar-with-percent");
        Label stateWords = new Label("waiting");
        stateWords.setId("leg-state-" + leg.name());
        HBox summary = new HBox(name, barWithPercent, stateWords);
        summary.getStyleClass().add("leg-summary");
        TitledPane pane = new TitledPane();
        pane.setId("leg-" + leg.name());
        pane.getStyleClass().add("leg");
        pane.setText("");
        pane.setGraphic(summary);
        pane.setContent(rows);
        pane.setAnimated(false);
        pane.setExpanded(false);
        barWithPercent.visibleProperty().bind(pane.expandedProperty().not());
        stateWords.visibleProperty().bind(pane.expandedProperty().not());
        pane.expandedProperty().addListener((observed, was, now) -> {
            if (!foldingItself) {
                chosenByTheOperator.add(leg.name());
            }
        });
        LegView made = new LegView(pane, name, barWithPercent, bar, percent, stateWords);
        legViews.put(leg.name(), made);
        return made;
    }

    /** The percent drawn on a bar, centred on it, reading 0% until a reading says otherwise (ADR-369 §1). */
    private static Label percentLabel(String id) {
        Label percent = new Label("0%");
        percent.setId(id);
        percent.getStyleClass().add("bar-percent");
        return percent;
    }

    private StepRow stepRow(StopCosts.StopCost cost) {
        Label stage = new Label(StopCosts.stageOf(cost.step()));
        stage.getStyleClass().add("small");
        Label name = new Label(cost.step());
        ProgressBar bar = new ProgressBar(0);
        Label percent = percentLabel("step-percent-" + cost.step());
        StackPane barWithPercent = new StackPane(bar, percent);
        barWithPercent.getStyleClass().add("bar-with-percent");
        Label stateWords = new Label("waiting");
        Label costWords = new Label("A stop here costs " + cost.cost() + ".");
        costWords.getStyleClass().add("small");
        costWords.setVisible(false);
        costWords.setManaged(false);
        Button stopHere = new Button("Stop");
        stopHere.setId("stop-step");
        stopHere.setVisible(false);
        stopHere.setManaged(false);
        stopHere.setOnAction(event -> stopAsked.run());
        HBox row = new HBox(stage, name, barWithPercent, stateWords, stopHere, costWords);
        row.setId("step-" + cost.step());
        row.getStyleClass().add("step");
        StepRow made = new StepRow(row, stage, name, bar, percent, stateWords, costWords, stopHere);
        stepRows.put(cost.step(), made);
        return made;
    }

    /** Draws each step's bar and state and each leg's summary from what the progress page holds. */
    void show(InvocationReportFile.Report progress) {
        Map<String, InvocationReportFile.Step> byName = new HashMap<>();
        progress.steps().forEach(step -> byName.put(step.name(), step));
        Map<String, Double> fractions = new HashMap<>();
        stepRows.forEach((name, row) -> {
            InvocationReportFile.Step step = byName.get(name);
            String words = step == null ? "waiting" : step.state();
            boolean running = words.equals("running");
            row.state().setText(running && step.done() != null && step.total() != null && step.total() > 0
                    ? "running " + Math.min(99L, 100L * step.done() / step.total()) + "%" : words);
            row.stopHere().setVisible(running);
            row.stopHere().setManaged(running);
            row.cost().setVisible(running);
            row.cost().setManaged(running);
            if (step != null && step.done() != null && step.total() != null && step.total() > 0) {
                row.bar().setProgress(Math.min(1.0, (double) step.done() / step.total()));
                fractions.put(name, row.bar().getProgress());
                // worked out on the whole numbers the file gives, 99 at most while the step runs (ADR-369 §2)
                row.percent().setText(Math.min(running ? 99L : 100L, 100L * step.done() / step.total()) + "%");
            } else if (words.equals("finished") || words.equals("stopped at a gate")) {
                row.bar().setProgress(1.0);
                fractions.put(name, 1.0);
                row.percent().setText("100%");
            } else if (running) {
                row.bar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                row.percent().setText("…");
            } else {
                row.bar().setProgress(0);
                row.percent().setText(words.equals("failed") ? "—" : "0%");
            }
        });
        Map<String, String> legStates = Legs.statesOf(progress);
        List<String> legsOpen = Legs.openOf(progress);
        for (Legs.Leg leg : Legs.ALL) {
            LegView view = legViews.get(leg.name());
            // a bar moving without a fraction counts as nothing (ADR-361 §4)
            double legBar = leg.steps().stream().mapToDouble(step -> fractions.getOrDefault(step, 0.0)).sum()
                    / leg.steps().size();
            String legWord = legStates.get(leg.name());
            // the tolerance keeps the binary error of the mean below a whole percent from being counted (ADR-365 §5)
            view.state().setText("running".equals(legWord)
                    ? "running " + Math.min(99L, (long) Math.floor(100 * legBar + 1e-6)) + "%" : legWord);
            view.bar().setProgress(legBar);
            long legWhole = (long) Math.floor(100 * legBar + 1e-6);
            view.percent().setText(Math.min("running".equals(legWord) ? 99L : 100L, legWhole) + "%");
            if (!chosenByTheOperator.contains(leg.name())) {
                foldingItself = true;
                try {
                    view.pane().setExpanded(legsOpen.contains(leg.name()));
                } finally {
                    foldingItself = false;
                }
            }
        }
    }
}
