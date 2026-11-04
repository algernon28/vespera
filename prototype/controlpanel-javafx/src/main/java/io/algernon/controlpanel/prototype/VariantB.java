package io.algernon.controlpanel.prototype;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * PROTOTYPE variant B, "Single-screen cockpit": no pages. The sixteen steps run across the
 * top as a strip, the one thing the operator must do next sits in the middle as the primary
 * affordance, the live output is always on the right, and settings are tucked into a
 * collapsible rail on the left and tabs along the bottom.
 */
final class VariantB {

    private VariantB() {}

    static Parent build(Stub stub) {
        BorderPane frame = new BorderPane();
        frame.setTop(header(stub));
        frame.setLeft(settings(stub));

        SplitPane middle = new SplitPane(nextCard(stub), console(stub));
        middle.setDividerPositions(0.45);
        SplitPane vertical = new SplitPane(middle, drawer(stub));
        vertical.setOrientation(Orientation.VERTICAL);
        vertical.setDividerPositions(0.62);
        frame.setCenter(vertical);
        BorderPane.setMargin(vertical, new Insets(0, 0, 64, 0));
        return frame;
    }

    private static VBox header(Stub stub) {
        FlowPane strip = new FlowPane(4, 4);
        Runnable paint = () -> {
            strip.getChildren().clear();
            int at = stub.currentStep.get();
            for (int i = 0; i < Stub.STEPS.size(); i++) {
                Stub.Step step = Stub.STEPS.get(i);
                Label chip = new Label(step.stage() + " " + step.name());
                String state = i < at ? "chip-done" : i == at ? (stub.running() ? "chip-running" : "chip-reached") : "chip-todo";
                chip.getStyleClass().addAll("chip", state);
                chip.setTooltip(new Tooltip("Stopping here loses: " + step.stopCost()));
                strip.getChildren().add(chip);
            }
        };
        stub.currentStep.addListener((o, a, b) -> paint.run());
        stub.childState.addListener((o, a, b) -> paint.run());
        paint.run();
        Label brand = Ui.label("VESPERA", "brand-dark");
        Label where = Ui.label("", "hint");
        where.textProperty().bind(stub.corpusRoot.concat("  →  ").concat(stub.workingDirectory));
        HBox top = new HBox(16, brand, where, Ui.pill(stub.childState, stub));
        top.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(8, top, strip, Ui.outcome(stub));
        header.getStyleClass().add("cockpit-header");
        return header;
    }

    private static ScrollPane settings(Stub stub) {
        TitledPane folders = new TitledPane("Folders", new VBox(10,
                Ui.folderField("Corpus root", stub.corpusRoot, null),
                Ui.folderField("Working directory", stub.workingDirectory, null),
                Ui.folderField("Output folder", stub.outputFolder, null)));
        TitledPane services = new TitledPane("Services", new VBox(10, Ui.imageField(stub),
                Ui.textField("Docling address", stub.doclingAddress), Ui.textField("Ollama address", stub.ollamaAddress),
                Ui.textField("Label model", stub.labelModel)));
        TitledPane seed = new TitledPane("Seed folder", Ui.keyEditor(stub, "seedFolder", null));
        TitledPane preflight = new TitledPane("Preflight  ● 5 of 5 green", new VBox(4,
                Ui.label("● Corpus root: found", "ok-text"), Ui.label("● Working directory: found", "ok-text"),
                Ui.label("● Docling: runs the image named", "ok-text"), Ui.label("● Ollama: serves every model named", "ok-text"),
                Ui.label("● Locks: nothing holds a folder", "ok-text")));
        Accordion accordion = new Accordion(preflight, folders, services, seed);
        accordion.setExpandedPane(preflight);
        ScrollPane scroll = new ScrollPane(accordion);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(340);
        return scroll;
    }

    /** The primary affordance: what the run needs from the operator, and the one button. */
    private static ScrollPane nextCard(Stub stub) {
        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        Runnable refresh = () -> {
            box.getChildren().clear();
            Button go = new Button();
            go.getStyleClass().addAll("primary", "big");
            go.setMaxWidth(Double.MAX_VALUE);
            if (stub.running()) {
                box.getChildren().add(Ui.label("Running " + stub.command.get(), "headline"));
                box.getChildren().add(Ui.label("Now at " + Stub.STEPS.get(Math.max(stub.currentStep.get(), 0)).name()
                        + ". Stopping now loses " + Stub.STEPS.get(Math.max(stub.currentStep.get(), 0)).stopCost() + ".", "hint"));
                go.setText("Stop");
                go.getStyleClass().setAll("button", "danger", "big");
                go.setOnAction(e -> stub.stop());
            } else if (stub.nextInvocation.get().isEmpty()) {
                box.getChildren().add(Ui.label("Ready to run", "headline"));
                box.getChildren().add(Ui.label("Preflight is green. The run goes as far as the next missing value.", "hint"));
                go.setText("Run");
                go.setOnAction(e -> stub.start("run"));
            } else if (!stub.wants.isEmpty()) {
                box.getChildren().add(Ui.label("The run stopped: it needs a value", "headline"));
                for (String key : stub.wants) {
                    box.getChildren().add(Ui.card(null, Ui.keyEditor(stub, key, null)));
                }
                go.setText("Save the value above, then Run");
                go.setDisable(true);
            } else if (stub.nextInvocation.get().equals("none")) {
                box.getChildren().add(Ui.label("The deliverable is written", "headline"));
                go.setText("Publish");
                go.setOnAction(e -> stub.start("publish"));
            } else {
                box.getChildren().add(Ui.label("The next invocation is " + stub.nextInvocation.get(), "headline"));
                go.setText("Run");
                go.setOnAction(e -> stub.start("run"));
            }
            Button label = new Button("Label");
            label.setOnAction(e -> stub.start("label"));
            Button auto = new Button("Have the local model answer");
            auto.setOnAction(e -> stub.start("label --auto"));
            HBox secondary = new HBox(8, label, auto);
            secondary.setDisable(stub.running());
            box.getChildren().addAll(go, secondary);
        };
        stub.childState.addListener((o, a, b) -> refresh.run());
        stub.wants.addListener((ListChangeListener<String>) c -> refresh.run());
        stub.currentStep.addListener((o, a, b) -> refresh.run());
        refresh.run();
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static VBox console(Stub stub) {
        var area = Ui.console(stub);
        VBox.setVgrow(area, Priority.ALWAYS);
        VBox box = new VBox(6, Ui.label("Output", "card-title"), area);
        box.setPadding(new Insets(10));
        return box;
    }

    private static TabPane drawer(Stub stub) {
        TableView<Stub.Key> table = new TableView<>();
        TableColumn<Stub.Key, String> name = new TableColumn<>("Key");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        name.setPrefWidth(260);
        TableColumn<Stub.Key, String> value = new TableColumn<>("Value");
        value.setCellValueFactory(c -> new SimpleStringProperty(stub.profile.getOrDefault(c.getValue().name(), "not set")));
        TableColumn<Stub.Key, String> why = new TableColumn<>("Why");
        why.setCellValueFactory(c -> new SimpleStringProperty(stub.why.getOrDefault(c.getValue().name(), "")));
        why.setPrefWidth(320);
        TableColumn<Stub.Key, String> cost = new TableColumn<>("Saving it mints again from");
        cost.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().earliestRun()));
        cost.setPrefWidth(200);
        table.getColumns().setAll(java.util.List.of(name, value, why, cost));
        table.getItems().setAll(Stub.KEYS);
        stub.profile.addListener((javafx.collections.MapChangeListener<String, String>) c -> table.refresh());

        FlowPane reports = new FlowPane(10, 6);
        reports.setPadding(new Insets(10));
        Stub.REPORTS.forEach(r -> reports.getChildren().add(Ui.reportLink(stub, r)));

        VBox relevance = new VBox(6, Ui.label("Folders not relevant", "field-name"));
        relevance.setPadding(new Insets(10));
        stub.notRelevantFolders.forEach(f -> relevance.getChildren().add(new Label("  " + f)));

        TabPane tabs = new TabPane(new Tab("Profile (20 keys)", table), new Tab("Reports", reports),
                new Tab("Relevance", relevance), new Tab("Code sets", Ui.label("  src/billing — Billing engine", "hint")));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }
}
