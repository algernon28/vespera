package io.algernon.controlpanel.prototype;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * PROTOTYPE variant A, "Sidebar pages": the browser panel's seven pages, one at a time,
 * behind a left navigation rail — the closest port, as the baseline the others argue with.
 */
final class VariantA {

    private VariantA() {}

    static Parent build(Stub stub) {
        Map<String, Supplier<Node>> pages = new LinkedHashMap<>();
        pages.put("Launch", () -> launch(stub));
        pages.put("Invocation", () -> invocation(stub));
        pages.put("Progress", () -> progress(stub));
        pages.put("Next", () -> next(stub));
        pages.put("Relevance", () -> relevance(stub));
        pages.put("Code sets", () -> codeSets());
        pages.put("Profile", () -> profile(stub));

        BorderPane frame = new BorderPane();
        Label title = Ui.label("", "page-title");
        StackPane body = new StackPane();
        VBox nav = new VBox(2, Ui.label("VESPERA", "brand"));
        nav.getStyleClass().add("nav");
        ToggleGroup group = new ToggleGroup();
        pages.forEach((name, page) -> {
            ToggleButton button = new ToggleButton(name);
            button.setToggleGroup(group);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("nav-item");
            button.setOnAction(e -> {
                button.setSelected(true);
                title.setText(name);
                ScrollPane scroll = new ScrollPane(page.get());
                scroll.setFitToWidth(true);
                body.getChildren().setAll(scroll);
            });
            nav.getChildren().add(button);
        });
        VBox main = new VBox(10, title, Ui.outcome(stub), body);
        VBox.setVgrow(body, Priority.ALWAYS);
        main.setPadding(new Insets(16, 20, 70, 20));
        frame.setLeft(nav);
        frame.setCenter(main);
        ((ToggleButton) nav.getChildren().get(1)).fire();
        return frame;
    }

    private static HBox tiles(Stub stub) {
        Label invocation = Ui.label("", "tile-value");
        invocation.textProperty().bind(stub.childState.map(s -> s.equals("none") ? "none yet" : stub.childLine()));
        return new HBox(10,
                tile("Docling", Ui.label("runs the image named", "tile-value")),
                tile("Ollama", Ui.label("serves every model named", "tile-value")),
                tile("Free space", Ui.label("812 GiB", "tile-value")),
                tile("Invocation", invocation));
    }

    private static VBox tile(String name, Label value) {
        VBox tile = new VBox(4, Ui.label(name, "tile-name"), value);
        tile.getStyleClass().add("tile");
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    private static Node launch(Stub stub) {
        Button run = new Button("Run");
        run.getStyleClass().add("primary");
        run.setOnAction(e -> stub.start("run"));
        Button label = new Button("Label");
        label.setOnAction(e -> stub.start("label"));
        Button auto = new Button("Have the local model answer");
        auto.setOnAction(e -> stub.start("label --auto"));
        Button check = new Button("Check");
        for (Button b : new Button[] {run, label, auto}) {
            b.disableProperty().bind(stub.childState.isEqualTo("running"));
        }
        GridPane columns = new GridPane(16, 16);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        columns.getColumnConstraints().addAll(half, half);
        VBox left = new VBox(16,
                Ui.card("Folders",
                        Ui.folderField("Corpus root", stub.corpusRoot, "The folder of documents Vespera reads. It is never changed."),
                        Ui.folderField("Working directory", stub.workingDirectory, "Where the database, the reports and profile.yaml live."),
                        Ui.folderField("Output folder", stub.outputFolder, "Empty: each run's pages go in a folder of their own under the working directory.")),
                Ui.card("Services", Ui.imageField(stub), Ui.textField("Docling address", stub.doclingAddress),
                        Ui.textField("Ollama address", stub.ollamaAddress), Ui.textField("Label model", stub.labelModel)),
                Ui.card("Seed folder", Ui.keyEditor(stub, "seedFolder", null)));
        VBox reports = new VBox(2);
        Stub.REPORTS.forEach(r -> reports.getChildren().add(Ui.reportLink(stub, r)));
        VBox right = new VBox(16,
                Ui.card("Preflight",
                        Ui.label("● Corpus root: found", "ok-text"),
                        Ui.label("● Working directory: found", "ok-text"),
                        Ui.label("● Docling: runs the image named", "ok-text"),
                        Ui.label("● Ollama: serves every model named", "ok-text"),
                        Ui.label("● Locks: nothing holds a folder", "ok-text")),
                Ui.card("Reports", reports));
        columns.add(left, 0, 0);
        columns.add(right, 1, 0);
        return new VBox(14, new HBox(8, run, label, auto, check), tiles(stub), columns);
    }

    private static Node invocation(Stub stub) {
        Button stop = new Button("Stop");
        stop.getStyleClass().add("danger");
        stop.visibleProperty().bind(stub.childState.isEqualTo("running"));
        stop.setOnAction(e -> stub.stop());
        Button publish = new Button("Publish");
        publish.visibleProperty().bind(stub.childState.isNotEqualTo("running").and(stub.nextInvocation.isEqualTo("none")));
        publish.setOnAction(e -> stub.start("publish"));
        var console = Ui.console(stub);
        console.setPrefRowCount(24);
        return new VBox(12, tiles(stub), new HBox(10, Ui.pill(stub.childState, stub), stop, publish),
                Ui.label("Output", "card-title"), console);
    }

    private static Node progress(Stub stub) {
        TableView<Stub.Step> table = new TableView<>();
        TableColumn<Stub.Step, String> stage = new TableColumn<>("Stage");
        stage.setCellValueFactory(new PropertyValueFactory<>("stage"));
        TableColumn<Stub.Step, String> step = new TableColumn<>("Step");
        step.setCellValueFactory(new PropertyValueFactory<>("name"));
        step.setPrefWidth(220);
        TableColumn<Stub.Step, String> state = new TableColumn<>("State");
        state.setCellValueFactory(c -> stub.currentStep.map(n -> {
            int i = Stub.STEPS.indexOf(c.getValue());
            int at = n.intValue();
            return i < at ? "done" : i == at ? (stub.running() ? "running" : "reached") : "";
        }));
        TableColumn<Stub.Step, String> cost = new TableColumn<>("What a stop costs");
        cost.setCellValueFactory(new PropertyValueFactory<>("stopCost"));
        cost.setPrefWidth(420);
        table.getColumns().setAll(java.util.List.of(stage, step, state, cost));
        table.getItems().setAll(Stub.STEPS);
        table.setPrefHeight(560);
        return new VBox(8, Ui.label("In the browser panel this page frames the jar's progress.html; here it is a native table.", "hint"), table);
    }

    private static Node next(Stub stub) {
        VBox box = new VBox(12);
        Runnable refresh = () -> {
            box.getChildren().clear();
            if (stub.nextInvocation.get().isEmpty()) {
                box.getChildren().add(Ui.label("No run has finished yet, so there is no next step to suggest. "
                        + "Start a run from the Launch page; once it finishes, this page shows what it needs next.", "hint"));
                return;
            }
            box.getChildren().add(Ui.label("The next invocation is " + stub.nextInvocation.get(), "headline"));
            for (String key : stub.wants) {
                box.getChildren().add(Ui.card(null, Ui.keyEditor(stub, key, null)));
            }
            Button run = new Button("Run");
            run.getStyleClass().add("primary");
            run.disableProperty().bind(stub.childState.isEqualTo("running"));
            run.setOnAction(e -> stub.start("run"));
            if (!stub.nextInvocation.get().equals("none")) {
                box.getChildren().add(run);
            }
        };
        stub.nextInvocation.addListener((o, a, b) -> refresh.run());
        stub.wants.addListener((ListChangeListener<String>) c -> refresh.run());
        refresh.run();
        return box;
    }

    private static Node relevance(Stub stub) {
        ListView<String> folders = new ListView<>(stub.notRelevantFolders);
        folders.setPrefHeight(160);
        TextField add = new TextField();
        add.setPromptText("e.g. archive/old-flows");
        Button save = new Button("Save");
        save.setOnAction(e -> {
            if (!add.getText().isBlank() && !stub.notRelevantFolders.contains(add.getText())) {
                stub.notRelevantFolders.add(add.getText());
                stub.outcome.set("The folders not relevant are saved, and the 40 questions answered.");
                add.clear();
            }
        });
        Button saveLabel = new Button("Save, then Label");
        saveLabel.setOnAction(e -> {
            save.fire();
            stub.start("label");
        });
        return new VBox(10, Ui.label("40 questions, 7 of them in a folder not relevant.", "headline"),
                folders, Ui.label("Folder, from the corpus root", "field-name"), add,
                Ui.label("A folder of what you are not keeping: every question about a file below it is answered not relevant.", "hint"),
                new HBox(8, save, saveLabel));
    }

    private static Node codeSets() {
        GridPane grid = new GridPane(10, 8);
        grid.addRow(0, Ui.label("Folder", "field-name"), Ui.label("Name", "field-name"));
        grid.addRow(1, new Label("src/billing"), new TextField("Billing engine"));
        grid.addRow(2, new Label("(the root)"), new TextField());
        grid.addRow(3, new TextField(), new TextField());
        return new VBox(12, Ui.card("Code sets", Ui.label("The name the deliverable gives the set. Clear it to remove the name.", "hint"),
                grid, new Button("Save")));
    }

    private static Node profile(Stub stub) {
        ListView<String> keys = new ListView<>();
        Stub.KEYS.forEach(k -> keys.getItems().add(k.name()));
        keys.setPrefWidth(300);
        keys.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item + "\n   " + stub.profile.getOrDefault(item, "not set"));
            }
        });
        StackPane editor = new StackPane();
        editor.setAlignment(Pos.TOP_LEFT);
        keys.getSelectionModel().selectedItemProperty().addListener((o, a, key) -> {
            if (key != null) {
                editor.getChildren().setAll(Ui.card(null, Ui.keyEditor(stub, key, keys::refresh)));
            }
        });
        keys.getSelectionModel().selectFirst();
        keys.setPrefHeight(600);
        HBox.setHgrow(editor, Priority.ALWAYS);
        editor.setMinWidth(Region.USE_PREF_SIZE);
        return new VBox(8, Ui.label("profile.yaml is edited where it stands, at " + stub.workingDirectory.get() + "\\profile.yaml", "hint"),
                new HBox(16, keys, editor));
    }
}
