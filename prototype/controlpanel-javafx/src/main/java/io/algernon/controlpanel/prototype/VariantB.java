package io.algernon.controlpanel.prototype;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

import javafx.beans.Observable;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * PROTOTYPE variant B, "Single-screen cockpit", third pass after the operator's review: every
 * profile value has a control that constrains it (bounded number pickers, pick-lists, lists
 * built with "+", folder dialogs), a descriptive name with the key below it and a plain
 * description; no "why" is asked; Stop sits beside Run and on the running step; a section still
 * missing a value says so in its title; the converter image says GPU or CPU; and the command-line
 * options can be set, with the command line they make shown as it changes.
 */
final class VariantB {

    private VariantB() {}

    static Parent build(Stub stub) {
        BorderPane frame = new BorderPane();
        frame.getStyleClass().add("cockpit");
        frame.setTop(header(stub));
        Map<String, TitledPane> sections = new LinkedHashMap<>();
        frame.setLeft(sidebar(stub, sections));

        SplitPane runView = new SplitPane(stepsChart(stub), console(stub));
        runView.setDividerPositions(0.5);
        Tab profile = new Tab("Profile", profileForm(stub, sections));
        Tab inclusions = new Tab("Inclusions", inclusions(stub));
        TabPane drawer = new TabPane(profile, inclusions);
        drawer.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        flag(profile, () -> !stub.wants.isEmpty(), stub.wants, stub.profileVersion);
        SplitPane vertical = new SplitPane(runView, charts(stub), drawer);
        vertical.setOrientation(Orientation.VERTICAL);
        vertical.setDividerPositions(0.4, 0.68);
        VBox.setVgrow(vertical, Priority.ALWAYS);

        VBox centre = new VBox(10, actionCard(stub), vertical);
        centre.setPadding(new Insets(10, 10, 64, 10));
        frame.setCenter(centre);
        return frame;
    }

    // ---------------------------------------------------------------- missing-value hints

    private static Label badge() {
        Label badge = new Label("● needs a value");
        badge.getStyleClass().add("missing-badge");
        return badge;
    }

    /** Shows the badge on a section while {@code missing} holds, re-asked whenever one of {@code on} changes. */
    private static void flag(TitledPane pane, BooleanSupplier missing, Observable... on) {
        pane.setContentDisplay(ContentDisplay.RIGHT);
        pane.setGraphicTextGap(10);
        Runnable update = () -> pane.setGraphic(missing.getAsBoolean() ? badge() : null);
        for (Observable o : on) {
            o.addListener(x -> update.run());
        }
        update.run();
    }

    private static void flag(Tab tab, BooleanSupplier missing, Observable... on) {
        Runnable update = () -> tab.setGraphic(missing.getAsBoolean() ? badge() : null);
        for (Observable o : on) {
            o.addListener(x -> update.run());
        }
        update.run();
    }

    private static boolean blank(StringProperty p) {
        return p.get() == null || p.get().isBlank();
    }

    // ---------------------------------------------------------------- header

    private static VBox header(Stub stub) {
        Label brand = Ui.label("VESPERA", "brand-dark");
        Label where = Ui.label("", "hint");
        where.textProperty().bind(stub.corpusRoot.concat("  →  ").concat(stub.workingDirectory));
        HBox top = new HBox(16, brand, where, Ui.pill(stub.childState, stub));
        top.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(8, top, Ui.outcome(stub));
        header.getStyleClass().add("cockpit-header");
        return header;
    }

    // ---------------------------------------------------------------- sidebar

    private static ScrollPane sidebar(Stub stub, Map<String, TitledPane> sections) {
        TitledPane preflight = new TitledPane("Preflight", new VBox(4,
                check("Corpus root", new File(stub.corpusRoot.get()).isDirectory()),
                check("Working directory", new File(stub.workingDirectory.get()).isDirectory()),
                Ui.label("○ Docling: not checked by the prototype", "hint"),
                Ui.label("○ Ollama: not checked by the prototype", "hint"),
                Ui.label("○ Locks: not checked by the prototype", "hint")));
        TitledPane folders = new TitledPane("Folders", new VBox(10,
                withSource(Ui.folderField("Corpus root", stub.corpusRoot, "The documents Vespera reads. It never changes them."), stub, "corpusRoot"),
                withSource(Ui.folderField("Working directory", stub.workingDirectory, "Where the database, the reports and the profile live."), stub, "workingDirectory"),
                withSource(Ui.folderField("Output folder", stub.outputFolder,
                        "Empty: each run's pages go in a folder of their own under the working directory."), stub, "deliverableRoot")));
        flag(folders, () -> blank(stub.corpusRoot) || blank(stub.workingDirectory), stub.corpusRoot, stub.workingDirectory);
        TitledPane services = new TitledPane("Services", new VBox(12,
                withSource(Ui.imageField(stub), stub, "doclingImage"),
                withSource(Ui.textField("Converter address", stub.doclingAddress), stub, "doclingAddress"),
                withSource(Ui.textField("Model server address (Ollama)", stub.ollamaAddress), stub, "ollamaAddress"),
                withSource(Ui.textField("Model that answers relevance questions", stub.labelModel), stub, "labelModel")));
        flag(services, () -> blank(stub.doclingAddress) || blank(stub.ollamaAddress) || blank(stub.labelModel),
                stub.doclingAddress, stub.ollamaAddress, stub.labelModel);
        TitledPane seed = new TitledPane("Seed folder", seedFolder(stub));
        flag(seed, () -> stub.profile.getOrDefault("seedFolder", "").isBlank(), stub.profileVersion);
        TitledPane codeSets = new TitledPane("Code sets", codeSets(stub));
        TitledPane options = new TitledPane("Command options", commandOptions(stub));
        TitledPane reports = new TitledPane("Reports", reports(stub));
        sections.put("Folders", folders);
        sections.put("Seed folder", seed);
        sections.put("Code sets", codeSets);
        Accordion accordion = new Accordion(preflight, folders, services, seed, codeSets, options, reports);
        accordion.setExpandedPane(folders);
        ScrollPane scroll = new ScrollPane(accordion);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(380);
        return scroll;
    }

    private static Label check(String what, boolean found) {
        return found ? Ui.label("● " + what + ": found", "ok-text") : Ui.label("● " + what + ": not found", "bad-text");
    }

    private static VBox withSource(VBox field, Stub stub, String name) {
        field.getChildren().add(Ui.label("Starting value from " + stub.sourceOf(name) + ".", "source"));
        return field;
    }

    private static VBox seedFolder(Stub stub) {
        StringProperty folder = new SimpleStringProperty(stub.profile.getOrDefault("seedFolder", ""));
        stub.profileVersion.addListener((o, a, b) -> folder.set(stub.profile.getOrDefault("seedFolder", "")));
        Button save = new Button("Save");
        save.setOnAction(e -> stub.save("seedFolder", folder.get(), null));
        Stub.Key key = Stub.key("seedFolder");
        return new VBox(6, Ui.folderField("Exemplars folder", folder, key.about()),
                Ui.label(Stub.rerunOf(key), "rerun"), save);
    }

    private static VBox reports(Stub stub) {
        VBox box = new VBox(2);
        for (String report : Stub.REPORTS) {
            var link = Ui.reportLink(stub, report);
            if (!new File(stub.workingDirectory.get(), report + ".html").isFile()) {
                link.getStyleClass().add("absent");
                link.setTooltip(new Tooltip("Not written yet"));
            }
            box.getChildren().add(link);
        }
        box.getChildren().add(Ui.label("Grey: not written yet in this working directory.", "hint"));
        return box;
    }

    // ---------------------------------------------------------------- code sets

    /**
     * One list of code folders, built with Browse and "+". A folder inside the corpus root is a code
     * set the walk finds anyway, and gets an optional name for the deliverable; a folder outside it is
     * added to codeSetFolders, so the walk reads it too.
     */
    private static VBox codeSets(Stub stub) {
        TextField picked = new TextField();
        picked.setEditable(false);
        picked.setPromptText("Browse to pick a folder");
        picked.setMinWidth(0);
        HBox.setHgrow(picked, Priority.ALWAYS);
        Label problem = Ui.label("", "bad-text");
        Button browse = new Button("Browse…");
        browse.setMinWidth(Button.USE_PREF_SIZE);
        browse.setOnAction(e -> {
            File dir = choose(browse, "A folder of code", stub.corpusRoot.get());
            if (dir != null) {
                picked.setText(dir.getAbsolutePath());
                problem.setText("");
            }
        });
        Button add = new Button("+");
        add.setMinWidth(Button.USE_PREF_SIZE);
        add.setTooltip(new Tooltip("Add the folder to the list"));
        add.disableProperty().bind(picked.textProperty().isEmpty());
        add.setOnAction(e -> {
            File dir = new File(picked.getText());
            String relative = relativeToRoot(stub, dir);
            if (relative != null) {
                if (stub.codeSetNames.containsKey(relative)) {
                    problem.setText("Not added: it is listed already.");
                } else {
                    stub.codeSetNames.put(relative, "");
                }
            } else if (stub.codeSetFolders.stream().anyMatch(dir.getAbsolutePath()::equalsIgnoreCase)) {
                problem.setText("Not added: it is listed already.");
            } else {
                stub.codeSetFolders.add(dir.getAbsolutePath());
            }
            picked.clear();
        });

        VBox rows = new VBox(4);
        Runnable draw = () -> {
            rows.getChildren().clear();
            stub.codeSetNames.forEach((folder, label) -> {
                TextField name = new TextField(label);
                name.setPromptText("Name in the deliverable (optional)");
                name.textProperty().addListener((o, a, b) -> stub.codeSetNames.put(folder, b));
                Button remove = new Button("✕");
                remove.getStyleClass().add("chip-remove");
                remove.setOnAction(x -> stub.codeSetNames.remove(folder));
                rows.getChildren().add(codeRow(folder.isEmpty() ? "(the corpus root)" : folder,
                        "inside the corpus root", name, remove));
            });
            for (String folder : stub.codeSetFolders) {
                Button remove = new Button("✕");
                remove.getStyleClass().add("chip-remove");
                remove.setOnAction(x -> stub.codeSetFolders.remove(folder));
                rows.getChildren().add(codeRow(folder, "outside the corpus root: read with it", null, remove));
            }
            if (rows.getChildren().isEmpty()) {
                rows.getChildren().add(Ui.label("None yet.", "hint"));
            }
        };
        // a name typed into a row replaces its value: redraw only when a row comes or goes, or focus is lost
        stub.codeSetNames.addListener((MapChangeListener<String, String>) c -> {
            if (c.wasAdded() != c.wasRemoved()) {
                draw.run();
            }
        });
        stub.codeSetFolders.addListener((ListChangeListener<String>) c -> draw.run());
        draw.run();

        Button save = new Button("Save");
        save.setOnAction(e -> stub.save("codeSetFolders", String.join(";", stub.codeSetFolders), null));
        HBox top = new HBox(6, picked, browse, add);
        top.setAlignment(Pos.CENTER_LEFT);
        return new VBox(8,
                Ui.label("Code folders", "field-name"),
                Ui.label("Folders of program code to keep together and copy whole into the deliverable. "
                        + "Pick one inside the corpus root to give it a name, or outside it to have it read too.", "hint"),
                top, problem, rows,
                Ui.label(Stub.rerunOf(Stub.key("codeSetFolders")) + " (for folders outside the corpus root)", "rerun"),
                save);
    }

    private static VBox codeRow(String folder, String where, TextField name, Button remove) {
        Label text = Ui.label(folder, "code");
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox head = new HBox(6, text, remove);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox row = new VBox(3, head, Ui.label(where, "source"));
        if (name != null) {
            row.getChildren().add(name);
        }
        row.getStyleClass().add("list-row");
        return row;
    }

    private static File choose(Node owner, String title, String start) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (start != null && new File(start).isDirectory()) {
            chooser.setInitialDirectory(new File(start));
        }
        return chooser.showDialog(owner.getScene().getWindow());
    }

    /** The folder relative to the corpus root with / between its elements, or null when it is not below it. */
    private static String relativeToRoot(Stub stub, File dir) {
        if (stub.corpusRoot.get().isBlank()) {
            return null;
        }
        Path root = Path.of(stub.corpusRoot.get()).toAbsolutePath().normalize();
        Path path = dir.toPath().toAbsolutePath().normalize();
        if (!path.toString().toLowerCase().startsWith(root.toString().toLowerCase())) {
            return null;
        }
        return root.relativize(path).toString().replace('\\', '/');
    }

    // ---------------------------------------------------------------- command options

    private static VBox commandOptions(Stub stub) {
        VBox box = new VBox(10, Ui.label("Options the commands are started with. Only what differs from the "
                + "default is added to the command line.", "hint"));
        for (Stub.Flag flag : stub.flags) {
            box.getChildren().add(flagControl(stub, flag));
        }
        Button reset = new Button("Back to the defaults");
        reset.setOnAction(e -> stub.flags.forEach(f -> stub.flagValues.put(f.name(), f.defaultValue())));
        VBox preview = new VBox(4);
        Runnable draw = () -> {
            preview.getChildren().clear();
            for (String cmd : List.of("run", "label", "label --auto", "publish")) {
                preview.getChildren().add(Ui.label(stub.commandLine(cmd), "command-line"));
            }
        };
        stub.flagValues.addListener((MapChangeListener<String, String>) c -> draw.run());
        stub.corpusRoot.addListener((o, a, b) -> draw.run());
        stub.workingDirectory.addListener((o, a, b) -> draw.run());
        draw.run();
        box.getChildren().addAll(reset, Ui.label("The command lines they make", "field-name"), preview);
        return box;
    }

    private static VBox flagControl(Stub stub, Stub.Flag flag) {
        String value = stub.flagValues.getOrDefault(flag.name(), flag.defaultValue());
        Node control;
        switch (flag.type()) {
            case "bool" -> {
                CheckBox box = new CheckBox(flag.label());
                box.setSelected(Boolean.parseBoolean(value));
                box.selectedProperty().addListener((o, a, b) -> stub.flagValues.put(flag.name(), String.valueOf(b)));
                stub.flagValues.addListener((MapChangeListener<String, String>) c -> {
                    if (c.getKey().equals(flag.name())) {
                        box.setSelected(Boolean.parseBoolean(c.getValueAdded()));
                    }
                });
                control = box;
            }
            case "seconds", "whole" -> {
                boolean seconds = flag.type().equals("seconds");
                int initial = Integer.parseInt(value.replaceAll("\\D", "").isEmpty() ? "0" : value.replaceAll("\\D", ""));
                Spinner<Integer> spinner = new Spinner<>(seconds ? 1 : 1, seconds ? 86_400 : 1000, initial);
                spinner.setEditable(true);
                spinner.setPrefWidth(120);
                spinner.focusedProperty().addListener((o, was, is) -> {
                    if (!is) {
                        spinner.increment(0);
                    }
                });
                spinner.valueProperty().addListener((o, a, b) -> stub.flagValues.put(flag.name(), b + (seconds ? "s" : "")));
                stub.flagValues.addListener((MapChangeListener<String, String>) c -> {
                    if (c.getKey().equals(flag.name()) && c.getValueAdded() != null) {
                        String digits = c.getValueAdded().replaceAll("\\D", "");
                        if (!digits.isEmpty() && Integer.parseInt(digits) != spinner.getValue()) {
                            spinner.getValueFactory().setValue(Integer.parseInt(digits));
                        }
                    }
                });
                HBox row = new HBox(8, spinner, Ui.label(flag.label(), "field-name"));
                row.setAlignment(Pos.CENTER_LEFT);
                control = row;
            }
            default -> {
                StringProperty path = new SimpleStringProperty(value);
                path.addListener((o, a, b) -> stub.flagValues.put(flag.name(), b == null ? "" : b));
                stub.flagValues.addListener((MapChangeListener<String, String>) c -> {
                    if (c.getKey().equals(flag.name()) && !java.util.Objects.equals(c.getValueAdded(), path.get())) {
                        path.set(c.getValueAdded());
                    }
                });
                if (flag.type().equals("file")) {
                    TextField field = new TextField();
                    field.setEditable(false);
                    field.textProperty().bind(path);
                    field.setPromptText("none: the working directory's own");
                    HBox.setHgrow(field, Priority.ALWAYS);
                    Button browse = new Button("Browse…");
                    browse.setOnAction(e -> {
                        FileChooser chooser = new FileChooser();
                        chooser.setTitle(flag.label());
                        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Label file", "*.yaml", "*.yml"));
                        File file = chooser.showOpenDialog(browse.getScene().getWindow());
                        if (file != null) {
                            path.set(file.getAbsolutePath());
                        }
                    });
                    Button clear = new Button("✕");
                    clear.getStyleClass().add("chip-remove");
                    clear.setOnAction(e -> path.set(""));
                    control = new VBox(4, Ui.label(flag.label(), "field-name"), new HBox(6, field, browse, clear));
                } else {
                    control = Ui.folderField(flag.label(), path, null);
                }
            }
        }
        return new VBox(3, control, Ui.label(flag.about() + "  For: " + flag.commands() + ".  --" + flag.name(), "source"));
    }

    // ---------------------------------------------------------------- action

    /** What the operator does next: Run and Stop side by side, a wanted value in place, and the two label commands. */
    private static VBox actionCard(Stub stub) {
        Label headline = Ui.label("", "headline");
        Label detail = Ui.label("", "hint");
        Button run = new Button("Run");
        run.setMinWidth(110);
        Button stop = new Button("Stop");
        stop.setMinWidth(110);
        stop.disableProperty().bind(stub.childState.isNotEqualTo("running"));
        stop.setOnAction(e -> stub.stop());
        run.disableProperty().bind(stub.childState.isEqualTo("running"));
        VBox wanted = new VBox(8);
        Runnable refresh = () -> {
            wanted.getChildren().clear();
            int at = Math.max(stub.currentStep.get(), 0);
            run.setText("Run");
            run.setOnAction(e -> stub.start("run"));
            if (stub.running()) {
                headline.setText("Running " + stub.command.get());
                detail.setText("Now at " + Stub.STEPS.get(at).name() + ". Stopping now loses " + Stub.STEPS.get(at).stopCost() + ".");
            } else if (!stub.wants.isEmpty()) {
                headline.setText("The run stopped: it needs a value from you");
                detail.setText("Set it below (it is also marked in the Profile tab), then Run again.");
                for (String name : stub.wants) {
                    wanted.getChildren().add(wantedEditor(stub, Stub.key(name)));
                }
            } else if (stub.nextInvocation.get().equals("none")) {
                headline.setText("The deliverable is written");
                detail.setText("Publish sends its Confluence rendering.");
                run.setText("Publish");
                run.setOnAction(e -> stub.start("publish"));
            } else {
                headline.setText(stub.nextInvocation.get().isEmpty() ? "Ready to run" : "The next invocation is " + stub.nextInvocation.get());
                detail.setText("The run goes as far as the next value only you can give, then stops and asks for it here.");
            }
        };
        stub.childState.addListener((o, a, b) -> refresh.run());
        stub.wants.addListener((ListChangeListener<String>) c -> refresh.run());
        stub.currentStep.addListener((o, a, b) -> refresh.run());
        refresh.run();

        Button record = new Button("Record my answers");
        record.setOnAction(e -> stub.start("label"));
        Button auto = new Button("Let the local model answer");
        auto.setOnAction(e -> stub.start("label --auto"));
        record.disableProperty().bind(stub.childState.isEqualTo("running"));
        auto.disableProperty().bind(stub.childState.isEqualTo("running"));
        record.setMinWidth(210);
        auto.setMinWidth(210);
        VBox labelling = new VBox(6,
                Ui.label("Relevance questions", "field-name"),
                Ui.label("After its first run, Vespera asks you about a sample of documents (60 as shipped): "
                        + "is each relevant or not? Your answers set where the relevance cut-off goes.", "hint"),
                explained(record, "You answered in relevance-labels.yaml (true or false beside each document). "
                        + "This saves those answers for the next Run."),
                explained(auto, "No time to answer? The local model (" + stub.labelModel.get() + ") reads the documents, "
                        + "answers for you and sets the cut-off. Your own answers are never replaced."));
        labelling.setMaxWidth(600);

        HBox buttons = new HBox(8, run, stop);
        VBox main = new VBox(6, headline, detail, buttons, wanted);
        HBox.setHgrow(main, Priority.ALWAYS);
        HBox row = new HBox(24, main, labelling);
        VBox card = new VBox(row);
        card.getStyleClass().add("card");
        return card;
    }

    /** The note kept beside a value in profile.yaml, filled in so the operator need not write one. */
    private static TextField noteField(Stub stub, String key) {
        TextField note = new TextField(stub.noteFor(key));
        note.setPromptText("e.g. read off relevance-labelling: the cut that keeps every manual");
        note.setTooltip(new Tooltip("Saved beside the value in profile.yaml, so whoever reads it later knows "
                + "where the value came from. Filled in for you; replace it with how you chose the value, if you like."));
        return note;
    }

    private static VBox noted(Node control, TextField note) {
        HBox caption = new HBox(6, Ui.label("Note saved with it:", "source"), note);
        caption.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(note, Priority.ALWAYS);
        return new VBox(4, control, caption);
    }

    private static VBox wantedEditor(Stub stub, Stub.Key key) {
        KeyControls.Editor editor = KeyControls.of(stub, key, stub.profile.getOrDefault(key.name(), ""));
        TextField note = noteField(stub, key.name());
        Button save = new Button("Save");
        save.setOnAction(e -> stub.save(key.name(), editor.value().get(), note.getText()));
        HBox reports = new HBox(6);
        Stub.REPORTS_OF_KEY.getOrDefault(key.name(), List.of()).forEach(r -> reports.getChildren().add(Ui.reportLink(stub, r)));
        HBox controls = new HBox(10, noted(editor.node(), note), save);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(4, Ui.label(key.label(), "field-name"), Ui.label(key.name(), "key-small"),
                Ui.label(key.about(), "hint"), controls, new HBox(6, Ui.label("Reports:", "hint"), reports));
        box.getStyleClass().add("wanted");
        return box;
    }

    private static HBox explained(Button button, String text) {
        Label explanation = Ui.label(text, "hint");
        HBox.setHgrow(explanation, Priority.ALWAYS);
        HBox box = new HBox(10, button, explanation);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // ---------------------------------------------------------------- steps and output

    private static ScrollPane stepsChart(Stub stub) {
        GridPane grid = new GridPane(10, 6);
        grid.setPadding(new Insets(10));
        ColumnConstraints stage = new ColumnConstraints(34);
        ColumnConstraints name = new ColumnConstraints(170);
        ColumnConstraints bar = new ColumnConstraints();
        bar.setHgrow(Priority.ALWAYS);
        ColumnConstraints state = new ColumnConstraints(70);
        ColumnConstraints stopColumn = new ColumnConstraints(70);
        grid.getColumnConstraints().addAll(stage, name, bar, state, stopColumn);
        grid.add(Ui.label("Steps", "card-title"), 0, 0, 5, 1);
        for (int i = 0; i < Stub.STEPS.size(); i++) {
            Stub.Step step = Stub.STEPS.get(i);
            int index = i;
            ProgressBar progress = new ProgressBar();
            progress.setMaxWidth(Double.MAX_VALUE);
            progress.progressProperty().bind(stub.stepProgress.get(i));
            Label stateLabel = Ui.label("", "hint");
            Button stop = new Button("Stop");
            stop.setOnAction(e -> stub.stop());
            stop.setTooltip(new Tooltip("Stopping here loses " + step.stopCost() + "."));
            Runnable paint = () -> {
                int at = stub.currentStep.get();
                double done = stub.stepProgress.get(index).get();
                boolean runningHere = index == at && stub.running();
                String text = index < at || done >= 1 ? "done"
                        : runningHere ? Math.round(done * 100) + "%"
                        : index == at && stub.childState.get().equals("stopped") ? "stopped"
                        : index == at ? "waiting" : "";
                stateLabel.setText(text);
                stop.setVisible(runningHere);
                progress.getStyleClass().removeAll("bar-done", "bar-running", "bar-stopped");
                progress.getStyleClass().add(text.equals("done") ? "bar-done"
                        : text.equals("stopped") || text.equals("waiting") ? "bar-stopped" : "bar-running");
            };
            stub.currentStep.addListener((o, a, b) -> paint.run());
            stub.childState.addListener((o, a, b) -> paint.run());
            stub.stepProgress.get(i).addListener((o, a, b) -> paint.run());
            paint.run();
            Label nameLabel = Ui.label(step.name(), "step-name");
            Tooltip.install(nameLabel, new Tooltip("Stopping here loses " + step.stopCost() + "."));
            grid.addRow(i + 1, Ui.label(step.stage(), "hint"), nameLabel, progress, stateLabel, stop);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        return scroll;
    }

    // ---------------------------------------------------------------- charts

    /**
     * The progress page's three line charts, drawn natively: the database's size, the running step's
     * count, and the verdicts recorded by the latest step that removes occurrences, each over time.
     */
    private static HBox charts(Stub stub) {
        LineChart<Number, Number> database = lineChart("Database size over time", "Size (MB)");
        LineChart<Number, Number> counts = lineChart("Running step over time", "Done (count)");
        LineChart<Number, Number> verdicts = lineChart("Verdicts over time", "Verdicts (count)");
        database.setLegendVisible(false);
        counts.setLegendVisible(false);
        Runnable draw = () -> {
            List<Stub.Sample> samples = List.copyOf(stub.samples);
            XYChart.Series<Number, Number> size = new XYChart.Series<>();
            samples.forEach(s -> size.getData().add(new XYChart.Data<>(s.seconds(), s.databaseBytes() / 1e6)));
            database.getData().setAll(List.of(size));

            XYChart.Series<Number, Number> done = new XYChart.Series<>();
            String step = samples.isEmpty() ? null : samples.getLast().step();
            if (step != null) {
                samples.stream().filter(s -> s.step().equals(step))
                        .forEach(s -> done.getData().add(new XYChart.Data<>(s.seconds(), s.done())));
                counts.setTitle(step + ": " + Math.round(samples.getLast().done()) + " of " + Math.round(samples.getLast().total()));
            } else {
                counts.setTitle("Running step over time");
            }
            counts.getData().setAll(List.of(done));

            String outcome = null;
            for (Stub.Sample s : samples) {
                if (Stub.OUTCOME_STEPS.containsKey(s.step())) {
                    outcome = s.step();
                }
            }
            List<XYChart.Series<Number, Number>> lines = new ArrayList<>();
            if (outcome != null) {
                for (String kind : Stub.OUTCOME_STEPS.get(outcome)) {
                    XYChart.Series<Number, Number> line = new XYChart.Series<>();
                    line.setName(kind);
                    samples.forEach(s -> line.getData().add(new XYChart.Data<>(s.seconds(), s.verdicts().getOrDefault(kind, 0.0))));
                    lines.add(line);
                }
                verdicts.setTitle("Verdicts recorded by " + outcome);
            } else {
                verdicts.setTitle("Verdicts over time");
            }
            verdicts.getData().setAll(lines);
        };
        stub.samples.addListener((ListChangeListener<Stub.Sample>) c -> draw.run());
        draw.run();
        HBox row = new HBox(8, database, counts, verdicts);
        row.setPadding(new Insets(4, 6, 4, 6));
        for (LineChart<Number, Number> chart : List.of(database, counts, verdicts)) {
            HBox.setHgrow(chart, Priority.ALWAYS);
            chart.setMinSize(0, 0);
            chart.setPrefSize(400, 220);
        }
        return row;
    }

    private static LineChart<Number, Number> lineChart(String title, String valueAxis) {
        NumberAxis time = new NumberAxis();
        time.setLabel("Time (s)");
        time.setForceZeroInRange(false);
        NumberAxis value = new NumberAxis();
        value.setLabel(valueAxis);
        LineChart<Number, Number> chart = new LineChart<>(time, value);
        chart.setTitle(title);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.getStyleClass().add("progress-chart");
        return chart;
    }

    private static VBox console(Stub stub) {
        var area = Ui.console(stub);
        VBox.setVgrow(area, Priority.ALWAYS);
        VBox box = new VBox(6, Ui.label("Output", "card-title"), area);
        box.setPadding(new Insets(10));
        return box;
    }

    // ---------------------------------------------------------------- profile

    private static BorderPane profileForm(Stub stub, Map<String, TitledPane> sections) {
        BorderPane pane = new BorderPane();
        Runnable draw = () -> {
            GridPane grid = new GridPane(16, 10);
            grid.setPadding(new Insets(10));
            ColumnConstraints nameColumn = new ColumnConstraints(250);
            ColumnConstraints controlColumn = new ColumnConstraints(300, 380, Double.MAX_VALUE);
            ColumnConstraints aboutColumn = new ColumnConstraints(240, 420, Double.MAX_VALUE);
            aboutColumn.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(nameColumn, controlColumn, aboutColumn);
            Map<String, KeyControls.Editor> editors = new LinkedHashMap<>();
            Map<String, TextField> notes = new LinkedHashMap<>();
            int row = 0;
            for (Stub.Key key : Stub.KEYS) {
                String saved = stub.profile.getOrDefault(key.name(), "");
                VBox name = new VBox(1, Ui.label(key.label(), "field-name"), Ui.label(key.name(), "key-small"));
                Node control;
                if (key.editedIn() != null) {
                    Label value = Ui.label(saved.isBlank() ? "not set" : saved.replace(";", "\n"), saved.isBlank() ? "hint" : "code");
                    Hyperlink edit = new Hyperlink("Edit in " + key.editedIn() + " (sidebar)");
                    edit.setOnAction(e -> {
                        TitledPane section = sections.get(key.editedIn());
                        if (section != null) {
                            section.setExpanded(true);
                        }
                    });
                    control = new VBox(2, value, edit);
                } else {
                    KeyControls.Editor editor = KeyControls.of(stub, key, saved);
                    editors.put(key.name(), editor);
                    TextField note = noteField(stub, key.name());
                    notes.put(key.name(), note);
                    control = noted(editor.node(), note);
                }
                VBox about = new VBox(2, Ui.label(key.about(), "hint"), Ui.label(Stub.rerunOf(key), "rerun"));
                if (stub.wants.contains(key.name())) {
                    name.getChildren().add(badge());
                    name.getStyleClass().add("wanted-name");
                }
                grid.addRow(row++, name, control, about);
                grid.add(new javafx.scene.control.Separator(), 0, row++, 3, 1);
            }
            Button save = new Button("Save");
            save.setOnAction(e -> {
                Map<String, String> values = new LinkedHashMap<>(stub.profile);
                Map<String, String> reasons = new LinkedHashMap<>(stub.why);
                editors.forEach((k, editor) -> {
                    String value = editor.value().get();
                    values.put(k, value);
                    reasons.put(k, stub.noteToSave(k, value, notes.get(k).getText()));
                });
                stub.saveProfile(values, reasons);
                stub.wants.removeIf(k -> !values.getOrDefault(k, "").isBlank());
            });
            Button reset = new Button("Reset");
            reset.setTooltip(new Tooltip("Back to the file as last saved"));
            reset.setOnAction(e -> stub.resetProfile());
            Label source = Ui.label("Editing " + stub.profileSource.get()
                    + ". Save writes the prototype's own copy, never profile.yaml.", "hint");
            HBox bar = new HBox(8, save, reset, source);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.setPadding(new Insets(8, 10, 8, 10));
            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true);
            pane.setCenter(scroll);
            pane.setTop(bar);
        };
        stub.profileVersion.addListener((o, a, b) -> draw.run());
        stub.wants.addListener((ListChangeListener<String>) c -> draw.run());
        draw.run();
        return pane;
    }

    // ---------------------------------------------------------------- inclusions

    /**
     * The corpus root's folders as a tree, every one included until unticked. The state is a
     * set of folders left out, the highest of each branch only; a folder's box is unticked when
     * it or a folder above it is left out, half-ticked when a folder below it is, ticked
     * otherwise. Folders are listed only when opened, so a large corpus root is never walked.
     */
    private static BorderPane inclusions(Stub stub) {
        BorderPane pane = new BorderPane();
        TreeSet<String> leftOut = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        leftOut.addAll(stub.notRelevantFolders);
        Label summary = Ui.label("", "hint");
        Runnable count = () -> summary.setText(leftOut.isEmpty() ? "Every folder is included."
                : leftOut.size() + " folders left out, with everything below them.");
        count.run();

        String rootPath = stub.corpusRoot.get();
        if (rootPath.isBlank() || !new File(rootPath).isDirectory()) {
            pane.setCenter(Ui.label("  Choose a corpus root that exists, in Folders, to see its folders here.", "hint"));
            return pane;
        }
        File root = new File(rootPath);
        TreeView<String> tree = new TreeView<>(new FolderItem(root, ""));
        tree.getRoot().setExpanded(true);
        tree.setCellFactory(view -> new TreeCell<>() {
            private final CheckBox box = new CheckBox();

            @Override
            protected void updateItem(String relative, boolean empty) {
                super.updateItem(relative, empty);
                if (empty || relative == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                if (relative.isEmpty()) {
                    setText("corpus root");
                    setGraphic(null);
                    return;
                }
                setText(relative.substring(relative.lastIndexOf('/') + 1));
                boolean excluded = isLeftOut(leftOut, relative);
                boolean partly = !excluded && hasLeftOutBelow(leftOut, relative);
                box.setIndeterminate(partly);
                box.setSelected(!excluded && !partly);
                box.setOnAction(e -> {
                    if (excluded) {
                        include(leftOut, root, relative);
                    } else {
                        leftOut.removeIf(f -> f.toLowerCase().startsWith(relative.toLowerCase() + "/"));
                        leftOut.add(relative);
                    }
                    count.run();
                    view.refresh();
                });
                setGraphic(box);
            }
        });

        Button save = new Button("Save");
        save.setOnAction(e -> stub.saveInclusions(new ArrayList<>(leftOut)));
        Button saveAndRecord = new Button("Save, then record answers");
        saveAndRecord.setOnAction(e -> {
            stub.saveInclusions(new ArrayList<>(leftOut));
            stub.start("label");
        });
        Button reset = new Button("Reset");
        reset.setOnAction(e -> {
            leftOut.clear();
            leftOut.addAll(stub.notRelevantFolders);
            count.run();
            tree.refresh();
        });
        HBox bar = new HBox(8, save, saveAndRecord, reset, summary);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 4, 10));
        VBox top = new VBox(2, bar, Ui.label("   Every folder is included. Untick one that holds nothing you want: every "
                + "relevance question about a file below it is answered not relevant.", "hint"));
        pane.setTop(top);
        pane.setCenter(tree);
        return pane;
    }

    private static boolean isLeftOut(TreeSet<String> leftOut, String relative) {
        String lower = relative.toLowerCase();
        return leftOut.stream().map(String::toLowerCase).anyMatch(f -> lower.equals(f) || lower.startsWith(f + "/"));
    }

    private static boolean hasLeftOutBelow(TreeSet<String> leftOut, String relative) {
        String prefix = relative.toLowerCase() + "/";
        return leftOut.stream().anyMatch(f -> f.toLowerCase().startsWith(prefix));
    }

    /**
     * Ticks a folder again. Where the folder left out is one above it, that one is replaced by
     * its other subfolders at each level down to this one, so only this branch comes back.
     */
    private static void include(TreeSet<String> leftOut, File root, String relative) {
        if (leftOut.remove(relative)) {
            return;
        }
        String ancestor = leftOut.stream()
                .filter(f -> relative.toLowerCase().startsWith(f.toLowerCase() + "/")).findFirst().orElse(null);
        if (ancestor == null) {
            return;
        }
        leftOut.remove(ancestor);
        String[] parts = relative.split("/");
        int depth = ancestor.split("/").length;
        String level = ancestor;
        for (int i = depth; i < parts.length; i++) {
            String keep = level + "/" + parts[i];
            for (String sibling : subfolders(new File(root, level))) {
                String path = level + "/" + sibling;
                if (!path.equalsIgnoreCase(keep)) {
                    leftOut.add(path);
                }
            }
            level = keep;
        }
    }

    private static List<String> subfolders(File dir) {
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return List.of();
        }
        return Arrays.stream(children).map(File::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** A folder whose subfolders are listed the first time it is opened, and never before. */
    private static final class FolderItem extends TreeItem<String> {
        private final File dir;
        private boolean listed;

        FolderItem(File dir, String relative) {
            super(relative);
            this.dir = dir;
        }

        @Override
        public ObservableList<TreeItem<String>> getChildren() {
            if (!listed) {
                listed = true;
                List<TreeItem<String>> items = new ArrayList<>();
                for (String name : subfolders(dir)) {
                    String relative = getValue().isEmpty() ? name : getValue() + "/" + name;
                    items.add(new FolderItem(new File(dir, name), relative));
                }
                super.getChildren().setAll(items);
            }
            return super.getChildren();
        }

        @Override
        public boolean isLeaf() {
            return listed && super.getChildren().isEmpty();
        }
    }
}
