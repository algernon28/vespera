package io.algernon.controlpanel;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;

/**
 * The control panel's window as one cockpit (ADR-344 §1): Run and Stop, the job's steps as a bar each beside the
 * child's output, the progress page's three charts, the Profile and Inclusions tabs and a sidebar of seven sections in
 * their order. Every part is found by its id. The cockpit reads and writes only through the {@link ControlPanelState}
 * it is given, and reads the sources its fields start from from that state (ADR-344 §3), so a cockpit a test draws
 * starts from what the test gives it. Every colour, size and space is the stylesheet's; no node here sets a style.
 */
final class Cockpit {

    private static final String NEEDS_A_VALUE = "needs a value";

    private static final Pattern A_DURATION = Pattern.compile("(\\d+)\\s*([smh]?)");

    private static final List<String> FLAGS = List.of("vespera.sidecars.start", "vespera.models.pull");

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final BorderPane root = new BorderPane();

    private final Label where = new Label();
    private final Label child = new Label();
    private final Label outcome = new Label();
    private final Button run = new Button("Run");
    private final Button stop = new Button("Stop");
    private final Button publish = new Button("Publish");
    private final Button label = new Button("Record my answers");
    private final Button labelAuto = new Button("Let the local model answer");
    private final HBox actions = new HBox();
    private final VBox wanted = new VBox();
    private final Map<String, StepRow> stepRows = new LinkedHashMap<>();
    private final Map<String, LegView> legViews = new LinkedHashMap<>();
    /** The legs the operator has opened or folded, which stay as they left them while the window is open (ADR-361 §6). */
    private final Set<String> chosenByTheOperator = new HashSet<>();
    private boolean foldingItself;
    private final VBox steps = new StepColumns(stepRows, legViews);
    private final ListView<String> output = new ListView<>();
    private final Map<String, LineChart<Number, Number>> charts = new LinkedHashMap<>();
    private final TabPane tabs = new TabPane();
    private final Tab profileTab = new Tab("Profile");
    private final Tab inclusionsTab = new Tab("Inclusions");
    private final Map<String, Tab> reportTabs = new LinkedHashMap<>();
    private final Accordion sidebar = new Accordion();
    private final Map<String, TitledPane> sections = new LinkedHashMap<>();
    private final Map<String, LaunchField> fields = new LinkedHashMap<>();
    private final Map<String, Preset> presets = new LinkedHashMap<>();
    private final Map<ControlPanelState.Start, Label> commandLines = new LinkedHashMap<>();
    private final TextField labelFile = new TextField();
    private final VBox preflight = new VBox();
    private final VBox reportsList = new VBox();
    private final VBox profileBody = new VBox();
    private final Label profileOutcome = new Label();
    private final List<ProfileRow> profileRows = new ArrayList<>();
    private final List<ProfileRow> wantedRows = new ArrayList<>();
    private ProfileRow seedRow;
    private boolean seedStated;
    private final InclusionsTree inclusions = new InclusionsTree();
    private final Label inclusionsOutcome = new Label();
    private final ListView<String> notRelevantList = new ListView<>();
    private final TextField notRelevantFolder = new TextField();
    private final Label notRelevantOutcome = new Label();
    private final HBox publishTo = new HBox();
    private List<String> notWalkedListed = List.of();
    private final VBox codeSets = new VBox();
    private final List<CodeSet> codeSetEntries = new ArrayList<>();
    private final TextField codeSetNote = new TextField();
    private final Label codeSetOutcome = new Label();
    private final Label seedOutcome = new Label();

    private Ending reportedEnding;
    private int ticksSinceProgress;
    private boolean sawChild;
    private boolean wasShown;
    private Timeline timeline;
    private Optional<NextInvocation> next = Optional.empty();
    private Ending wantedFor;
    private List<String> lastOutput = List.of();

    /** One row of the steps chart: its stage, its name, one bar, its state and, on the running step, its own Stop. */
    private record StepRow(HBox row, Label stage, Label name, ProgressBar bar, Label state, Label cost,
            Button stopHere) {
    }

    /** One leg of the steps chart: its pane, and the name, the one bar and the state its summary holds (ADR-361 §3). */
    private record LegView(TitledPane pane, Label name, ProgressBar bar, Label state) {
    }

    /**
     * The steps chart: the four legs, each holding a row for each of its steps, each its own {@code step-<name>} (ADR-344 §1). Each row lays
     * its parts out on its own, so before the rows are laid out every stage and every step name is given at least the
     * width of the widest of its column, and the bars and the states after them begin at one place on every row. The
     * widths are read from the labels as the stylesheet draws them, so no width is named here.
     */
    private static final class StepColumns extends VBox {
        private final Map<String, StepRow> rows;
        private final Map<String, LegView> legs;

        StepColumns(Map<String, StepRow> rows, Map<String, LegView> legs) {
            this.rows = rows;
            this.legs = legs;
        }

        @Override
        protected void layoutChildren() {
            alignColumn(rows.values().stream().map(StepRow::stage).toList());
            alignColumn(rows.values().stream().map(StepRow::name).toList());
            alignColumn(legs.values().stream().map(LegView::name).toList());
            super.layoutChildren();
        }

        /** Gives every label of {@code column} at least the width the widest of them asks for. */
        private static void alignColumn(List<Label> column) {
            double widest = column.stream().mapToDouble(label -> label.prefWidth(-1)).max().orElse(0);
            column.forEach(label -> label.setMinWidth(widest));
        }
    }

    /** A launch value's field: its text, the line saying where it started, and, for a folder, its status. */
    private final class LaunchField {
        final String property;
        final TextField input = new TextField();
        final Label startingLine = new Label();
        final Label status = new Label();
        final StartingValues.Starting starting;
        private final boolean createdByTheJar;

        LaunchField(String property, boolean folder, boolean createdByTheJar) {
            this.property = property;
            this.createdByTheJar = createdByTheJar;
            this.starting = StartingValues.of(property, state.sources());
            input.setId(property);
            input.setText(starting.value());
            input.setPromptText(LaunchValues.exampleOf(property));
            startingLine.setId("starting-" + property);
            startingLine.setText(startingWords(starting));
            startingLine.getStyleClass().add("small");
            status.setId("status-" + property);
            status.getStyleClass().add("small");
            if (folder) {
                input.textProperty().addListener((observed, was, now) -> status.setText(statusWords(now)));
                status.setText(statusWords(input.getText()));
            }
            input.textProperty().addListener((observed, was, now) -> {
                showCommandLines();
                showBadges();
            });
            fields.put(property, this);
        }

        String text() {
            return input.getText().strip();
        }

        /** What the field says of its folder; an empty working directory says the folder it stands for (ADR-345 §1). */
        private String statusWords(String text) {
            if (property.equals("vespera.working-dir") && text.isBlank()) {
                String theDefault = state.workingDirectoryDefault().folder();
                return "In use: " + theDefault + " (" + FolderStatus.of(theDefault, true).words() + ")";
            }
            return FolderStatus.of(text, createdByTheJar).words();
        }
    }

    /** A preset passed as an option of the command line, its control and whether the operator changed it (ADR-344 §4). */
    private final class Preset {
        final String property;
        final StartingValues.Starting starting;
        final Node control;
        private final Supplier<String> read;
        private final Runnable back;
        private boolean touched;

        Preset(String property) {
            this.property = property;
            this.starting = StartingValues.of(property, state.sources());
            if (FLAGS.contains(property)) {
                CheckBox box = new CheckBox();
                box.setSelected(starting.value().isBlank() || Boolean.parseBoolean(starting.value()));
                box.selectedProperty().addListener((observed, was, now) -> changed());
                read = () -> Boolean.toString(box.isSelected());
                back = () -> box.setSelected(starting.value().isBlank() || Boolean.parseBoolean(starting.value()));
                control = box;
            } else {
                Spinner<Integer> spinner = new Spinner<>(1, 1_000_000, secondsOf(starting.value()));
                spinner.setEditable(true);
                spinner.valueProperty().addListener((observed, was, now) -> changed());
                read = () -> spinner.getValue() + "s";
                back = () -> spinner.getValueFactory().setValue(secondsOf(starting.value()));
                control = spinner;
            }
            control.setId(property);
            presets.put(property, this);
        }

        private void changed() {
            touched = true;
            showCommandLines();
        }

        /** The text the preset holds: what it started as until the operator changes it. */
        String value() {
            return touched ? read.get() : starting.value();
        }

        void toTheDefault() {
            back.run();
            touched = false;
            showCommandLines();
        }
    }

    /** A code set the operator listed: a folder under the corpus root with a name, or one outside it. */
    private record CodeSet(String folder, String name, boolean outside) {
    }

    Cockpit(ControlPanelState state, Dialogs dialogs) {
        this.state = state;
        this.dialogs = dialogs;
        root.getStyleClass().add("cockpit");
        root.setLeft(sidebarPane());
        root.setCenter(mainPane());
        loadFiles();
        showCommandLines();
        showBadges();
        refresh();
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), tick -> refresh()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    Parent root() {
        return root;
    }

    // ---- the main pane

    private Node mainPane() {
        VBox header = new VBox(where, child);
        header.getStyleClass().add("header");
        where.setId("where");
        child.setId("child");
        outcome.setId("outcome");
        outcome.setWrapText(true);
        outcome.getStyleClass().add("outcome");

        run.setId("run");
        stop.setId("stop");
        publish.setId("publish");
        label.setId("label");
        labelAuto.setId("label-auto");
        run.setOnAction(event -> begin(ControlPanelState.Start.RUN, "Run"));
        publish.setOnAction(event -> begin(ControlPanelState.Start.PUBLISH, "Publish"));
        label.setOnAction(event -> begin(ControlPanelState.Start.LABEL, "Record my answers"));
        labelAuto.setOnAction(event -> begin(ControlPanelState.Start.LABEL_AUTO, "Let the local model answer"));
        stop.setOnAction(event -> stopAfterConfirming());
        publishTo.setId("publish-to");
        publishTo.getStyleClass().add("spaced-row");
        actions.getChildren().addAll(run, stop, publishTo);
        actions.getStyleClass().add("actions");
        wanted.setId("wanted");
        wanted.getStyleClass().add("wanted");
        Label recordWords = new Label("You answered in relevance-labels.yaml, and this saves those answers for the next"
                + " Run.");
        Label autoWords = new Label();
        autoWords.setId("label-auto-words");
        autoWords.setWrapText(true);
        recordWords.setWrapText(true);
        fields.get("vespera.label.model").input.textProperty().addListener(
                (observed, was, now) -> autoWords.setText(autoSentence(now)));
        autoWords.setText(autoSentence(fields.get("vespera.label.model").text()));
        VBox actionCard = new VBox(actions, wanted, row(label, recordWords), row(labelAuto, autoWords));
        actionCard.getStyleClass().add("card");

        steps.setId("steps");
        steps.getStyleClass().add("steps");
        for (Legs.Leg leg : Legs.ALL) {
            steps.getChildren().add(legView(leg).pane());
        }
        output.setId("output");
        output.getStyleClass().add("output");
        SplitPane split = new SplitPane(new ScrollPane(steps), output);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.5);
        ((ScrollPane) split.getItems().getFirst()).setFitToWidth(true);
        split.getStyleClass().add("steps-and-output");

        HBox chartRow = new HBox(chart("database-chart", "Database size over time", "Database size"),
                chart("counts-chart", "Counts of the running step", "Count"),
                chart("verdicts-chart", "Verdicts by kind", "Verdicts"));
        chartRow.getStyleClass().add("charts");

        profileTab.setId("profile");
        inclusionsTab.setId("inclusions");
        profileTab.setClosable(false);
        inclusionsTab.setClosable(false);
        profileTab.setContent(profileTabContent());
        inclusionsTab.setContent(inclusionsTabContent());
        tabs.getTabs().addAll(profileTab, inclusionsTab);
        tabs.getStyleClass().add("form-tabs");

        VBox main = new VBox(header, outcome, actionCard, split, chartRow, tabs);
        main.getStyleClass().add("main");
        VBox.setVgrow(tabs, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private String autoSentence(String model) {
        return "The local model" + (model.isBlank() ? "" : ", " + model + ",")
                + " reads the documents, answers for you and sets the cut-off, and your own answers are never replaced.";
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
        Label stateWords = new Label("waiting");
        stateWords.setId("leg-state-" + leg.name());
        HBox summary = new HBox(name, bar, stateWords);
        summary.getStyleClass().add("leg-summary");
        TitledPane pane = new TitledPane();
        pane.setId("leg-" + leg.name());
        pane.getStyleClass().add("leg");
        pane.setText("");
        pane.setGraphic(summary);
        pane.setContent(rows);
        pane.setAnimated(false);
        pane.setExpanded(false);
        bar.visibleProperty().bind(pane.expandedProperty().not());
        stateWords.visibleProperty().bind(pane.expandedProperty().not());
        pane.expandedProperty().addListener((observed, was, now) -> {
            if (!foldingItself) {
                chosenByTheOperator.add(leg.name());
            }
        });
        LegView made = new LegView(pane, name, bar, stateWords);
        legViews.put(leg.name(), made);
        return made;
    }

    private StepRow stepRow(StopCosts.StopCost cost) {
        Label stage = new Label(StopCosts.stageOf(cost.step()));
        stage.getStyleClass().add("small");
        Label name = new Label(cost.step());
        ProgressBar bar = new ProgressBar(0);
        Label stateWords = new Label("waiting");
        Label costWords = new Label("A stop here costs " + cost.cost() + ".");
        costWords.getStyleClass().add("small");
        costWords.setVisible(false);
        costWords.setManaged(false);
        Button stopHere = new Button("Stop");
        stopHere.setId("stop-step");
        stopHere.setVisible(false);
        stopHere.setManaged(false);
        stopHere.setOnAction(event -> stopAfterConfirming());
        HBox row = new HBox(stage, name, bar, stateWords, stopHere, costWords);
        row.setId("step-" + cost.step());
        row.getStyleClass().add("step");
        StepRow made = new StepRow(row, stage, name, bar, stateWords, costWords, stopHere);
        stepRows.put(cost.step(), made);
        return made;
    }

    private LineChart<Number, Number> chart(String id, String title, String axis) {
        NumberAxis time = new NumberAxis();
        time.setLabel("Elapsed (s)");
        time.setForceZeroInRange(false);
        NumberAxis value = new NumberAxis();
        value.setLabel(axis);
        value.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(time, value);
        chart.setId(id);
        chart.setTitle(title);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.getStyleClass().add("progress-chart");
        HBox.setHgrow(chart, Priority.ALWAYS);
        charts.put(id, chart);
        return chart;
    }

    // ---- the starting, the plan and the command lines

    private static String startingWords(StartingValues.Starting starting) {
        return switch (starting.source()) {
            case LAUNCH_VALUE -> "Starting value from the launch values";
            case ENVIRONMENT -> "Starting value from the environment";
            case DOT_ENV -> "Starting value from .env";
            case GPU_PRESELECTION -> "Starting value from the GPU image, preselected";
            case APPLICATION_YAML -> "Starting value from application.yaml";
            case NONE -> "No source names a starting value";
        };
    }

    private ControlPanelState.Plan plan() {
        StartingValues.Sources sources = state.sources();
        Map<String, String> passed = new HashMap<>();
        Map<String, String> kept = new HashMap<>();
        for (LaunchField field : fields.values()) {
            String text = field.text();
            if (StartingValues.remembered(text, field.starting)) {
                kept.put(field.property, text);
            }
            boolean always = field.property.equals("vespera.corpus-root") || field.property.equals("vespera.working-dir");
            if (!text.isBlank() && (always || StartingValues.passed(field.property, text, field.starting, sources))) {
                passed.put(field.property, text);
            }
        }
        String seed = seedRow == null || seedRow.value().isBlank() ? state.values().lastSeedFolder()
                : seedRow.value().stripTrailing();
        Map<String, String> options = new LinkedHashMap<>();
        for (ChildCommand.Option option : ChildCommand.OPTIONS) {
            Preset preset = presets.get(option.property());
            if (preset != null && StartingValues.passed(option.property(), preset.value(), preset.starting, sources)) {
                options.put(option.property(), preset.value());
            }
        }
        Path file = labelFile.getText().isBlank() ? null : InclusionsTree.pathOf(labelFile.getText().strip()).orElse(null);
        if (!passed.containsKey("vespera.working-dir")) {
            // the field left empty stands for the folder the jar would use on this machine (ADR-345 §1)
            passed.put("vespera.working-dir", state.workingDirectoryDefault().folder());
        }
        List<String> ticked = publishTo.getChildren().stream().filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast).filter(CheckBox::isSelected)
                .map(box -> box.getId().substring("publish-to-".length())).toList();
        return new ControlPanelState.Plan(valuesOf(passed, seed), valuesOf(kept, seed), options, file, ticked);
    }

    /** Draws a box for each destination whose rendering is drawn, Confluence's ticked and the others not, as each is added (ADR-345 §4). */
    private void drawDestinations(List<String> drawn) {
        List<String> shown = publishTo.getChildren().stream().map(Node::getId)
                .map(id -> id.substring("publish-to-".length())).toList();
        if (shown.equals(drawn)) {
            return;
        }
        List<Node> boxes = new ArrayList<>();
        for (String destination : drawn) {
            Node existing = publishTo.getChildren().stream()
                    .filter(each -> ("publish-to-" + destination).equals(each.getId())).findFirst().orElse(null);
            if (existing != null) {
                boxes.add(existing);
                continue;
            }
            CheckBox box = new CheckBox(destination);
            box.setId("publish-to-" + destination);
            box.setSelected(destination.equals("confluence"));
            box.selectedProperty().addListener((observed, was, now) -> showCommandLines());
            boxes.add(box);
        }
        publishTo.getChildren().setAll(boxes);
        showCommandLines();
    }

    private static LaunchValues valuesOf(Map<String, String> by, String lastSeedFolder) {
        return new LaunchValues(by.get("vespera.corpus-root"), by.get("vespera.working-dir"),
                by.get("vespera.deliverable-root"), by.get("vespera.docling.image"), by.get("vespera.docling.base-url"),
                by.get("spring.ai.ollama.base-url"), by.get("vespera.label.model"), lastSeedFolder);
    }

    private void showCommandLines() {
        if (commandLines.isEmpty()) {
            return;
        }
        ControlPanelState.Plan plan = plan();
        commandLines.forEach((kind, shown) -> {
            try {
                shown.setText(ControlPanelState.commandOf(state.runtime(), kind, plan).shown());
            } catch (RuntimeException refused) {
                shown.setText(refused.getMessage() == null ? refused.getClass().getSimpleName() : refused.getMessage());
            }
        });
    }

    private void begin(ControlPanelState.Start kind, String words) {
        List<String> stored = new ArrayList<>();
        Optional<String> refusal = state.start(kind, plan(), stored);
        say(refusal.orElse(words + " started" + (stored.isEmpty() ? "" : "; " + String.join("; ", stored))));
        sawChild = refusal.isEmpty() || sawChild;
        refresh();
    }

    /** Stop is confirmed with the one sentence a stop is confirmed by (ADR-278 §6); the child is stopped only on the answer that goes on. */
    private void stopAfterConfirming() {
        if (dialogs.confirm(Child.STOP_CONFIRMATION, "Stop", "Keep it running")) {
            stopTheChild();
        }
    }

    private static HBox row(Node... children) {
        HBox box = new HBox(children);
        box.getStyleClass().add("spaced-row");
        return box;
    }

    private static VBox tight(Node... children) {
        VBox box = new VBox(children);
        box.getStyleClass().add("tight-column");
        return box;
    }

    private void stopTheChild() {
        Thread.ofPlatform().daemon().name("the control panel stops its child").start(() -> {
            try {
                state.stop();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> {
                say("the invocation was stopped");
                refresh();
            });
        });
    }

    private void say(String line) {
        outcome.setText(line);
    }

    // ---- the sidebar

    private Node sidebarPane() {
        sidebar.setId("sidebar");
        section("preflight", "Preflight", preflightPane());
        section("folders", "Folders", foldersPane());
        section("services", "Services", servicesPane());
        section("seed-folder", "Seed folder", seedPane());
        section("code-sets", "Code sets", codeSetsPane());
        section("command-options", "Command options", optionsPane());
        section("reports", "Reports", reportsPane());
        sidebar.setExpandedPane(sections.get("folders"));
        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sidebar");
        return scroll;
    }

    private void section(String id, String title, Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setId(id);
        pane.setUserData(title);
        sections.put(id, pane);
        sidebar.getPanes().add(pane);
    }

    private Node fieldBox(LaunchField field, String name, boolean browse) {
        Label heading = new Label(name);
        heading.getStyleClass().add("field-name");
        Label hint = new Label(LaunchValues.hintOf(field.property));
        hint.setWrapText(true);
        hint.getStyleClass().add("small");
        HBox line = row(field.input);
        HBox.setHgrow(field.input, Priority.ALWAYS);
        if (browse) {
            Button button = new Button("Browse");
            button.setId(field.property + ".browse");
            button.setOnAction(event -> dialogs.folder("Choose " + name.toLowerCase(java.util.Locale.ROOT),
                    field.input.getText()).ifPresent(chosen -> field.input.setText(chosen.toString())));
            line.getChildren().add(button);
        }
        VBox box = tight(heading, line, field.status, field.startingLine, hint);
        box.getStyleClass().add("field");
        return box;
    }

    private Node foldersPane() {
        LaunchField corpus = new LaunchField("vespera.corpus-root", true, false);
        LaunchField working = new LaunchField("vespera.working-dir", true, true);
        LaunchField deliverable = new LaunchField("vespera.deliverable-root", true, true);
        corpus.input.textProperty().addListener((observed, was, now) -> {
            where();
            drawInclusions();
        });
        working.input.textProperty().addListener((observed, was, now) -> {
            where();
            closeReportTabs();
            loadFiles();
        });
        VBox pane = new VBox(fieldBox(corpus, "Corpus root", true), fieldBox(working, "Working directory", true),
                fieldBox(deliverable, "Output folder", true));
        pane.getStyleClass().add("section");
        return pane;
    }

    private void where() {
        where.setText("Corpus root: " + shown(fields.get("vespera.corpus-root")) + "    Working directory: "
                + shown(fields.get("vespera.working-dir")));
    }

    private static String shown(LaunchField field) {
        return field.text().isEmpty() ? "not chosen" : field.text();
    }

    private Node servicesPane() {
        LaunchField image = new LaunchField("vespera.docling.image", false, false);
        LaunchField converter = new LaunchField("vespera.docling.base-url", false, false);
        LaunchField ollama = new LaunchField("spring.ai.ollama.base-url", false, false);
        LaunchField model = new LaunchField("vespera.label.model", false, false);
        ToggleGroup group = new ToggleGroup();
        RadioButton gpu = new RadioButton("GPU: needs an NVIDIA graphics card. Much faster.");
        RadioButton cpu = new RadioButton("CPU: runs on any machine. Slower.");
        RadioButton other = new RadioButton("Another image");
        gpu.setId("image-gpu");
        cpu.setId("image-cpu");
        other.setId("image-other");
        for (RadioButton radio : List.of(gpu, cpu, other)) {
            radio.setToggleGroup(group);
        }
        Label gpuName = new Label(StartingValues.DOCLING_IMAGE_GPU);
        Label cpuName = new Label(StartingValues.DOCLING_IMAGE_CPU);
        gpuName.getStyleClass().add("small");
        cpuName.getStyleClass().add("small");
        other.setVisible(false);
        other.setManaged(false);
        Runnable follow = () -> {
            String text = image.text();
            boolean isGpu = text.equals(StartingValues.DOCLING_IMAGE_GPU);
            boolean isCpu = text.equals(StartingValues.DOCLING_IMAGE_CPU);
            other.setVisible(!isGpu && !isCpu && !text.isEmpty());
            other.setManaged(other.isVisible());
            group.selectToggle(isGpu ? gpu : isCpu ? cpu : other.isVisible() ? other : null);
        };
        image.input.textProperty().addListener((observed, was, now) -> follow.run());
        gpu.setOnAction(event -> image.input.setText(StartingValues.DOCLING_IMAGE_GPU));
        cpu.setOnAction(event -> image.input.setText(StartingValues.DOCLING_IMAGE_CPU));
        follow.run();
        VBox images = tight(gpu, gpuName, cpu, cpuName, other);
        images.setId("docling-images");
        Label hint = new Label(LaunchValues.hintOf(image.property));
        hint.setWrapText(true);
        hint.getStyleClass().add("small");
        VBox pane = new VBox(new Label("Converter's image"), images, image.input, image.startingLine, hint,
                fieldBox(converter, "Converter's address", false), fieldBox(ollama, "Ollama's address", false),
                fieldBox(model, "Label model", false));
        pane.getStyleClass().add("section");
        return pane;
    }

    private Node seedPane() {
        seedRow = new ProfileRow(ProfileKey.SEED_FOLDER, "seed-", List.of(), Optional.empty(), dialogs, null,
                this::openReport, writtenReports());
        Button save = new Button("Save");
        save.setId("seed-folder-save");
        seedOutcome.setId("seed-folder-outcome");
        seedOutcome.setWrapText(true);
        save.setOnAction(event -> {
            ProfileRow.Saved saved = ProfileRow.save(state, workingDirectory(), List.of(seedRow));
            seedOutcome.setText(saved.line());
            loadFiles();
            showBadges();
        });
        VBox pane = new VBox(seedRow.node(), save, seedOutcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    private Node codeSetsPane() {
        codeSets.setId("code-set-list");
        TextField folder = new TextField();
        folder.setId("code-set-folder");
        folder.setPromptText("A folder, under the corpus root or outside it");
        TextField name = new TextField();
        name.setId("code-set-name");
        name.setPromptText("A name for the deliverable (optional)");
        Button browse = new Button("Browse");
        browse.setId("code-set-browse");
        browse.setOnAction(event -> dialogs.folder("Choose a folder of code", folder.getText())
                .ifPresent(chosen -> folder.setText(chosen.toString())));
        Button add = new Button("+");
        add.setId("code-set-add");
        add.setOnAction(event -> {
            Optional<String> refusal = addCodeSet(folder.getText().strip(), name.getText().strip());
            codeSetOutcome.setText(refusal.orElse(""));
            if (refusal.isEmpty()) {
                folder.clear();
                name.clear();
            }
        });
        codeSetNote.setId("code-set-note");
        codeSetNote.setPromptText(ProfileEdit.FILLED_IN_NOTE);
        Button save = new Button("Save");
        save.setId("code-sets-save");
        save.setOnAction(event -> saveCodeSets());
        codeSetOutcome.setId("code-set-outcome");
        codeSetOutcome.setWrapText(true);
        HBox.setHgrow(folder, Priority.ALWAYS);
        VBox pane = new VBox(codeSets, row(folder, browse, add), name,
                row(new Label("Note saved with the folders outside"), codeSetNote), save, codeSetOutcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** The corpus root as the field names it now, which the window uses wherever it reads or holds one (ADR-344 §3). */
    private Optional<Path> corpusRoot() {
        return folderOf(fields.get("vespera.corpus-root"));
    }

    /**
     * The working directory as the field names it now: it is what a Run is passed, whether or not it is ever remembered.
     * Where the field is empty it stands for the folder the jar would use on this machine (ADR-345 §1).
     */
    private Optional<Path> workingDirectory() {
        LaunchField field = fields.get("vespera.working-dir");
        if (field == null || !field.text().isEmpty()) {
            return folderOf(field);
        }
        return ControlPanelState.pathOf(state.workingDirectoryDefault().folder());
    }

    private static Optional<Path> folderOf(LaunchField field) {
        String named = field == null ? "" : field.text();
        return named.isEmpty() ? Optional.empty() : ControlPanelState.pathOf(named);
    }

    /** Which of the reports the working directory holds now. */
    private Map<String, Boolean> writtenReports() {
        Map<String, Boolean> written = new HashMap<>();
        Optional<Path> folder = workingDirectory();
        for (String report : Reports.ALL) {
            written.put(report, folder.isPresent() && Files.isRegularFile(folder.get().resolve(report)));
        }
        return written;
    }

    private Optional<String> addCodeSet(String typed, String name) {
        if (typed.isEmpty()) {
            return Optional.of("choose a folder first");
        }
        Optional<Path> chosen = InclusionsTree.pathOf(typed);
        if (chosen.isEmpty() || !chosen.get().isAbsolute()) {
            return Optional.of("the folder " + typed + " is not added: it is not the whole path of a folder");
        }
        Optional<Path> root = corpusRoot();
        Path real = realOf(chosen.get());
        if (root.isPresent() && Files.isDirectory(root.get())) {
            Path realRoot = realOf(root.get());
            if (real.startsWith(realRoot) && !real.equals(realRoot)) {
                String relative = realRoot.relativize(real).toString().replace('\\', '/');
                codeSetEntries.add(new CodeSet(relative, name, false));
                drawCodeSets();
                return Optional.empty();
            }
        }
        List<CodeSetFolderChecks.Apart> apart = new ArrayList<>();
        apart.add(new CodeSetFolderChecks.Apart("corpus root", fields.get("vespera.corpus-root").text()));
        apart.add(new CodeSetFolderChecks.Apart("working directory", fields.get("vespera.working-dir").text()));
        apart.add(new CodeSetFolderChecks.Apart("seed folder", seedRow == null ? "" : seedRow.value()));
        apart.add(new CodeSetFolderChecks.Apart("output folder", fields.get("vespera.deliverable-root").text()));
        for (CodeSet each : codeSetEntries) {
            if (each.outside()) {
                apart.add(new CodeSetFolderChecks.Apart("folder already stated", each.folder()));
            }
        }
        Optional<String> refusal = CodeSetFolderChecks.refusal(typed, apart);
        if (refusal.isPresent()) {
            return refusal;
        }
        codeSetEntries.add(new CodeSet(typed, "", true));
        drawCodeSets();
        return Optional.empty();
    }

    private static Path realOf(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException unreal) {
            return path.toAbsolutePath().normalize();
        }
    }

    private void drawCodeSets() {
        codeSets.getChildren().clear();
        for (CodeSet each : new ArrayList<>(codeSetEntries)) {
            Label words = new Label(each.outside() ? each.folder() + "  (outside the corpus root)"
                    : each.name().isEmpty() ? each.folder() + "  (under the corpus root, no name)"
                    : each.folder() + "  named " + each.name());
            Button remove = new Button("Remove");
            remove.setOnAction(event -> {
                codeSetEntries.remove(each);
                drawCodeSets();
            });
            codeSets.getChildren().add(row(words, remove));
        }
    }

    private void saveCodeSets() {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            codeSetOutcome.setText("name the working directory first");
            return;
        }
        List<CodeSetRenames.Rename> renames = codeSetEntries.stream()
                .filter(each -> !each.outside() && !each.name().isEmpty())
                .map(each -> new CodeSetRenames.Rename(each.folder(), each.name())).toList();
        String outside = String.join(java.io.File.pathSeparator,
                codeSetEntries.stream().filter(CodeSet::outside).map(CodeSet::folder).toList());
        Map<ProfileKey, ProfileEdit.Stated> stated;
        try {
            stated = ProfileRow.statedIn(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            codeSetOutcome.setText(String.valueOf(unreadable.getMessage()));
            return;
        }
        Optional<ProfileEdit.Stated> inFile = Optional.ofNullable(stated.get(ProfileKey.CODE_SET_FOLDERS));
        Optional<String> note = ProfileEdit.noteToSave(ProfileKey.CODE_SET_FOLDERS, outside,
                codeSetNote.getText(), inFile);
        Optional<String> refusal = note.flatMap(words -> ProfileEdit.refusalOfSave(ProfileKey.CODE_SET_FOLDERS, outside,
                words, inFile, Optional.empty()));
        if (refusal.isPresent()) {
            codeSetOutcome.setText(refusal.get());
            return;
        }
        List<WorkingDirectoryWrite.Change> changes = new ArrayList<>();
        changes.add(new WorkingDirectoryWrite.Change(CodeSetRenames.FILE_NAME,
                current -> CodeSetRenames.render(renames)));
        note.ifPresent(words -> changes.add(new WorkingDirectoryWrite.Change(ProfileEdit.FILE_NAME,
                current -> ProfileEdit.rewrite(current, ProfileKey.CODE_SET_FOLDERS, outside, words))));
        Optional<String> failed = state.write(folder.get(), changes);
        codeSetOutcome.setText(failed.orElse("code sets saved in " + folder.get()));
        loadFiles();
    }

    private Node optionsPane() {
        labelFile.setId("label-file");
        labelFile.setPromptText("The label file for Record my answers (optional)");
        labelFile.textProperty().addListener((observed, was, now) -> showCommandLines());
        Button browse = new Button("Browse");
        browse.setId("label-file.browse");
        browse.setOnAction(event -> dialogs.file("Choose the label file", "")
                .ifPresent(chosen -> labelFile.setText(chosen.toString())));
        HBox.setHgrow(labelFile, Priority.ALWAYS);
        VBox pane = new VBox(new Label("Label file"), row(labelFile, browse));
        for (ChildCommand.Option option : ChildCommand.OPTIONS) {
            Preset preset = new Preset(option.property());
            Label words = new Label(option.property() + "  (" + String.join(", ", option.commands()) + ")");
            words.getStyleClass().add("small");
            Label from = new Label(startingWords(preset.starting));
            from.getStyleClass().add("small");
            pane.getChildren().add(tight(words, preset.control, from));
        }
        Button defaults = new Button("Back to the defaults");
        defaults.setId("options-defaults");
        defaults.setOnAction(event -> {
            presets.values().forEach(Preset::toTheDefault);
            labelFile.clear();
        });
        pane.getChildren().add(defaults);
        for (ControlPanelState.Start kind : List.of(ControlPanelState.Start.RUN, ControlPanelState.Start.LABEL,
                ControlPanelState.Start.LABEL_AUTO, ControlPanelState.Start.PUBLISH)) {
            Label shown = new Label();
            shown.setWrapText(true);
            shown.setId("command-line-" + switch (kind) {
                case RUN -> "run";
                case LABEL -> "label";
                case LABEL_AUTO -> "label-auto";
                case PUBLISH -> "publish";
            });
            shown.getStyleClass().add("command-line");
            commandLines.put(kind, shown);
            pane.getChildren().add(shown);
        }
        pane.getStyleClass().add("section");
        return pane;
    }

    private Node reportsPane() {
        reportsList.setId("reports-list");
        reportsList.getStyleClass().add("section");
        drawReports();
        return reportsList;
    }

    private void drawReports() {
        reportsList.getChildren().clear();
        Map<String, Boolean> written = writtenReports();
        for (String report : Reports.ALL) {
            if (written.get(report)) {
                Hyperlink link = new Hyperlink(report);
                link.setOnAction(event -> openReport(report));
                reportsList.getChildren().add(link);
            } else {
                Label grey = new Label(report + " (not written yet)");
                grey.getStyleClass().add("grey");
                reportsList.getChildren().add(grey);
            }
        }
    }

    /** Opens {@code report}, a file of the working directory, in a tab of its own, or selects the tab it already has (ADR-344 §6). */
    private void openReport(String report) {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        Tab open = reportTabs.get(report);
        if (open != null) {
            tabs.getSelectionModel().select(open);
            return;
        }
        Path file = folder.get().toAbsolutePath().normalize().resolve(report);
        WebView view = new WebView();
        view.setId("viewer-" + report);
        WebEngine engine = view.getEngine();
        engine.setCreatePopupHandler(features -> null);
        engine.locationProperty().addListener((observed, was, now) -> followed(file, engine, now));
        Button reload = new Button("Reload");
        reload.setId("reload-" + report);
        reload.setOnAction(event -> engine.reload());
        VBox.setVgrow(view, Priority.ALWAYS);
        VBox content = new VBox(reload, view);
        content.getStyleClass().add("tab-content");
        Tab tab = new Tab(report, content);
        tab.setId("report-" + report);
        tab.setOnClosed(event -> reportTabs.remove(report));
        reportTabs.put(report, tab);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        engine.load(file.toUri().toString());
    }

    /**
     * Decides a location a viewer is about to show (ADR-344 §6). The same report, with or without a fragment, is
     * followed; another report of the working directory opens in its own tab; a report not written, or an address that
     * is not a file's, opens nothing and is said on the outcome line; any other file is handed to the system. Where it is
     * not followed the viewer goes back to its report.
     */
    private void followed(Path report, WebEngine engine, String location) {
        if (location == null || location.isBlank()) {
            return;
        }
        String bare = location.split("[#?]", 2)[0];
        Optional<Path> file = fileOf(bare);
        if (file.isPresent() && file.get().equals(report)) {
            return;
        }
        if (file.isEmpty()) {
            say("not opened: " + location + " is not a file of this computer");
        } else {
            Path parent = file.get().getParent();
            String name = file.get().getFileName().toString();
            boolean aReport = parent != null && Reports.ALL.contains(name)
                    && workingDirectory().map(folder -> folder.toAbsolutePath().normalize().equals(parent)).orElse(false);
            if (!aReport) {
                dialogs.handToTheSystem(URI.create(bare));
            } else if (Files.isRegularFile(file.get())) {
                openReport(name);
            } else {
                say(name + " is not written yet, so it is not opened");
            }
        }
        engine.getLoadWorker().cancel();
        Platform.runLater(() -> engine.load(report.toUri().toString()));
    }

    private static Optional<Path> fileOf(String address) {
        try {
            URI uri = URI.create(address);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(Path.of(uri).toAbsolutePath().normalize());
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException unusable) {
            return Optional.empty();
        }
    }

    /** The reports' tabs belong to the working directory: another one closes them. */
    private void closeReportTabs() {
        for (Tab tab : List.copyOf(reportTabs.values())) {
            tabs.getTabs().remove(tab);
        }
        reportTabs.clear();
    }

    private Node preflightPane() {
        preflight.setId("preflight-list");
        preflight.getStyleClass().add("section");
        Button check = new Button("Check");
        check.setId("check");
        check.setOnAction(event -> askTheSidecars());
        VBox pane = new VBox(preflight, check);
        pane.getStyleClass().add("section");
        return pane;
    }

    private void askTheSidecars() {
        String converter = fields.get("vespera.docling.base-url").text();
        String image = fields.get("vespera.docling.image").text();
        String ollama = fields.get("spring.ai.ollama.base-url").text();
        String model = fields.get("vespera.label.model").text();
        Thread.ofPlatform().daemon().name("the control panel asks the sidecars").start(() -> {
            SidecarChecks.Checks asked = SidecarChecks.ask(converter, image, ollama,
                    model.isBlank() ? List.of() : List.of(model));
            Platform.runLater(() -> {
                state.keepChecks(asked);
                drawPreflight();
            });
        });
    }

    private static String describe(SidecarChecks.Result result) {
        return switch (result) {
            case NOT_CHECKED -> "not checked";
            case MATCHES -> "answers, and runs the image named";
            case ANOTHER_IMAGE -> "answers, and runs another image";
            case NOT_ANSWERING -> "does not answer";
            case SERVES_ALL -> "answers, and serves every model named";
            case MISSING -> "answers, and is missing a model named";
        };
    }

    private void drawPreflight() {
        preflight.getChildren().clear();
        ControlPanelState.Holders holders = state.holders(workingDirectory(), corpusRoot());
        SidecarChecks.Checks checks = state.lastChecks();
        String working = fields.get("vespera.working-dir").text();
        List<String> lines = new ArrayList<>(List.of(
                "Corpus root: " + FolderStatus.of(fields.get("vespera.corpus-root").text(), false).words(),
                "Working directory: " + FolderStatus.of(working, false).words(),
                "Converter: " + describe(checks.docling().result()),
                "Ollama: " + describe(checks.ollama().result()),
                "Working directory held by: " + holders.workingDirectory().orElse("nobody"),
                "Corpus root held by: " + holders.corpusRoot().orElse("nobody"),
                "Free space: " + FolderStatus.usable(working).map(FolderStatus::gib).orElse("not known")));
        for (String line : lines) {
            preflight.getChildren().add(new Label(line));
        }
        checks.docling().warnings().forEach(warning -> preflight.getChildren().add(new Label(warning)));
    }

    // ---- the Profile tab

    private Node profileTabContent() {
        profileBody.setId("profile-form");
        Button save = new Button("Save");
        save.setId("profile-save");
        Button reset = new Button("Reset");
        reset.setId("profile-reset");
        save.setOnAction(event -> {
            ProfileRow.Saved saved = ProfileRow.save(state, workingDirectory(), profileRows);
            profileOutcome.setText(saved.line());
            if (saved.written()) {
                loadFiles();
            }
        });
        reset.setOnAction(event -> {
            profileRows.forEach(ProfileRow::reset);
            profileOutcome.setText("the form is back to profile.yaml as last saved");
        });
        profileOutcome.setId("profile-outcome");
        profileOutcome.setWrapText(true);
        ScrollPane scroll = new ScrollPane(profileBody);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox content = new VBox(profileOutcome, scroll, row(save, reset));
        content.getStyleClass().add("tab-content");
        return content;
    }

    private Node inclusionsTabContent() {
        Button save = new Button("Save");
        save.setId("inclusions-save");
        Button reset = new Button("Reset");
        reset.setId("inclusions-reset");
        inclusions.view().setId("inclusions-tree");
        save.setOnAction(event -> saveInclusions());
        reset.setOnAction(event -> {
            drawInclusions();
            inclusionsOutcome.setText("the tree is back to profile.yaml as last saved");
        });
        inclusionsOutcome.setId("inclusions-outcome");
        inclusionsOutcome.setWrapText(true);
        VBox.setVgrow(inclusions.view(), Priority.ALWAYS);
        Label words = new Label("Untick a folder holding nothing you want. An unticked folder is never read, and a"
                + " change makes the next walk another, so every run after it is new.");
        words.setWrapText(true);
        VBox content = new VBox(words, inclusions.view(), inclusionsOutcome, row(save, reset), notRelevantPane());
        content.getStyleClass().add("tab-content");
        return content;
    }

    /** The folders not relevant, a list beneath the tree (ADR-345 §2, ADR-334 §6). */
    private Node notRelevantPane() {
        notRelevantList.setId("not-relevant-list");
        notRelevantList.setPrefHeight(120);
        notRelevantFolder.setId("not-relevant-folder");
        notRelevantFolder.setPromptText("A folder, from the corpus root, / between its names");
        HBox.setHgrow(notRelevantFolder, Priority.ALWAYS);
        Button browse = new Button("Browse");
        browse.setId("not-relevant-browse");
        browse.setOnAction(event -> dialogs.folder("Choose a folder not relevant",
                corpusRoot().map(Path::toString).orElse("")).ifPresent(chosen -> notRelevantFolder.setText(
                        fromTheCorpusRoot(chosen))));
        Button add = new Button("Add");
        add.setId("not-relevant-add");
        add.setOnAction(event -> changeNotRelevant(true, null));
        Button remove = new Button("Remove");
        remove.setId("not-relevant-remove");
        remove.setOnAction(event -> changeNotRelevant(false, notRelevantList.getSelectionModel().getSelectedItem()));
        notRelevantOutcome.setId("not-relevant-outcome");
        notRelevantOutcome.setWrapText(true);
        Label words = new Label("Folders not relevant are still read: the relevance questions below them are answered"
                + " not relevant, and no run changes.");
        words.setWrapText(true);
        VBox pane = new VBox(new Label("Folders not relevant"), words, notRelevantList,
                row(notRelevantFolder, browse, add, remove), notRelevantOutcome);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** {@code chosen} as a folder not relevant is spelt: from the corpus root with {@code /} between its names where it lies below it. */
    private String fromTheCorpusRoot(Path chosen) {
        Optional<Path> root = corpusRoot();
        Path absolute = chosen.toAbsolutePath().normalize();
        if (root.isPresent() && absolute.startsWith(root.get().toAbsolutePath().normalize())
                && !absolute.equals(root.get().toAbsolutePath().normalize())) {
            return root.get().toAbsolutePath().normalize().relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString().replace('\\', '/');
    }

    private void drawNotRelevant() {
        try {
            notRelevantList.getItems().setAll(NotRelevantFolders.read(readFile(NotRelevantFolders.FILE_NAME)));
        } catch (IOException | IllegalArgumentException unreadable) {
            notRelevantList.getItems().clear();
            notRelevantOutcome.setText(String.valueOf(unreadable.getMessage()));
        }
    }

    /**
     * ADR-334 §6's one action, in one hold of the lock: the folder typed added to the list the file holds as read then, or
     * {@code removed} taken out of it, and every question of the label file answered from the list, all or none.
     */
    private void changeNotRelevant(boolean adding, String removed) {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            notRelevantOutcome.setText("name the working directory first");
            return;
        }
        if (!adding && removed == null) {
            notRelevantOutcome.setText("select a folder of the list to remove");
            return;
        }
        String typed = notRelevantFolder.getText().strip();
        if (adding && typed.isEmpty()) {
            notRelevantOutcome.setText("type a folder first, or select one of the list to remove");
            return;
        }
        if (adding) {
            Optional<String> refusal = refusalOfNotRelevantFolder(typed, List.of());
            if (refusal.isPresent()) {
                notRelevantOutcome.setText(refusal.get());
                return;
            }
        }
        List<String> kept = new ArrayList<>();
        int[] counts = new int[2];
        boolean[] answered = {false};
        List<WorkingDirectoryWrite.Change> changes = List.of(
                new WorkingDirectoryWrite.Change(NotRelevantFolders.FILE_NAME, current -> {
                    // rebuilt from the file as read under the lock, never from the list as drawn (ADR-334 §6)
                    List<String> list = new ArrayList<>(NotRelevantFolders.read(current));
                    if (!adding) {
                        list.removeIf(listed -> listed.equals(removed));
                    } else {
                        Optional<String> refusal = refusalOfNotRelevantFolder(typed, list);
                        if (refusal.isPresent()) {
                            throw new IllegalArgumentException(refusal.get());
                        }
                        list.add(typed);
                    }
                    kept.clear();
                    kept.addAll(list);
                    return NotRelevantFolders.render(list);
                }),
                new WorkingDirectoryWrite.Change(LabelAnswers.FILE_NAME, current -> {
                    if (current.isEmpty()) {
                        return null;
                    }
                    answered[0] = true;
                    return answersGivenBy(current.get(), kept, counts);
                }));
        Optional<String> refusal = state.write(folder.get(), changes);
        if (refusal.isPresent()) {
            notRelevantOutcome.setText(refusal.get());
            return;
        }
        if (adding) {
            notRelevantFolder.clear();
        }
        Path file = folder.get().resolve(NotRelevantFolders.FILE_NAME);
        drawNotRelevant();
        notRelevantOutcome.setText(answered[0]
                ? "the folders not relevant are saved in " + file + ", and the " + counts[0] + " questions answered: "
                        + counts[1] + " not relevant, " + (counts[0] - counts[1]) + " relevant"
                : "the folders not relevant are saved in " + file + "; there are no questions to answer yet");
    }

    /**
     * The label file's text with each question answered by the folders listed (ADR-334 §6): {@code false} where its
     * path lies at or below one, {@code true} otherwise. Only a {@code relevant} line whose answer changes is rewritten.
     *
     * @param counts set to the number of questions and the number answered not relevant
     */
    private static String answersGivenBy(String text, List<String> folders, int[] counts) {
        List<LabelAnswers.Question> questions = LabelAnswers.read(text);
        Map<String, Optional<Boolean>> changed = new LinkedHashMap<>();
        counts[0] = questions.size();
        counts[1] = 0;
        for (LabelAnswers.Question question : questions) {
            boolean relevant = folders.stream().noneMatch(folder -> NotRelevantFolders.lies(question.path(), folder));
            if (!relevant) {
                counts[1]++;
            }
            if (!question.relevant().equals(Optional.of(relevant))) {
                changed.put(question.path(), Optional.of(relevant));
            }
        }
        return LabelAnswers.rewrite(Optional.of(text), changed);
    }

    /** The line refusing {@code typed} as a folder not relevant, or none (ADR-334 §6). */
    private Optional<String> refusalOfNotRelevantFolder(String typed, List<String> listed) {
        String refused = "the folder " + typed + " is not added: ";
        if (typed.equals(".") || typed.equals("./") || typed.equals(".\\") || namesTheCorpusRoot(typed)) {
            return Optional.of(refused + "the corpus root holds every question; name a folder below it");
        }
        boolean belowACodeSetFolder = NotRelevantFolders.isAbsolute(typed)
                && codeSetFoldersStated().stream().map(CodeSetFolderChecks::spelt)
                        .anyMatch(stated -> NotRelevantFolders.lies(typed, stated));
        if (!NotRelevantFolders.isWritten(typed) || (NotRelevantFolders.isAbsolute(typed) && !belowACodeSetFolder)) {
            return Optional.of(refused
                    + "it is not a folder below the corpus root, written with / between its elements");
        }
        if (listed.stream().anyMatch(stated -> NotRelevantFolders.lies(typed, stated)
                && NotRelevantFolders.lies(stated, typed))) {
            return Optional.of(refused + "it is listed already");
        }
        return Optional.empty();
    }

    /** The code set folders the profile states, outside the corpus root; none where it states none or cannot be read. */
    private List<String> codeSetFoldersStated() {
        try {
            Optional<String> text = readFile(ProfileEdit.FILE_NAME);
            if (text.isEmpty()) {
                return List.of();
            }
            ProfileEdit.Stated stated = ProfileEdit.read(text.get()).get(ProfileKey.CODE_SET_FOLDERS);
            return stated == null || stated.value() == null ? List.of() : CodeSetFolderChecks.statedIn(stated.value());
        } catch (IOException | IllegalArgumentException unreadable) {
            return List.of();
        }
    }

    /** Whether {@code typed} spells the corpus root itself, with either separator, with or without a trailing one. */
    private boolean namesTheCorpusRoot(String typed) {
        Optional<Path> root = corpusRoot();
        if (root.isEmpty() || !NotRelevantFolders.isAbsolute(typed.replace('\\', '/'))) {
            return false;
        }
        String given = withoutTrailingSeparators(typed);
        String own = withoutTrailingSeparators(root.get().toString());
        if (NotRelevantFolders.lies(given, own) && NotRelevantFolders.lies(own, given)) {
            return true;
        }
        try {
            return Files.isSameFile(Path.of(typed), root.get());
        } catch (IOException | RuntimeException cannotBeCompared) {
            return false;
        }
    }

    private static String withoutTrailingSeparators(String path) {
        String slashed = path.strip().replace('\\', '/');
        while (slashed.length() > 1 && slashed.endsWith("/")) {
            slashed = slashed.substring(0, slashed.length() - 1);
        }
        return slashed;
    }

    private Optional<String> readFile(String name) throws IOException {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty() || !Files.isRegularFile(folder.get().resolve(name))) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(folder.get().resolve(name), StandardCharsets.UTF_8));
    }

    /**
     * Draws the tree of the corpus root the field names, with each folder the profile's {@code foldersNotWalked} lists
     * unticked (ADR-345 §2); a value the walk cannot read is drawn as the walk reads it, every folder ticked, and said.
     */
    private void drawInclusions() {
        List<String> listed = List.of();
        try {
            Optional<String> text = readFile(ProfileEdit.FILE_NAME);
            ProfileEdit.Stated stated = text.isEmpty() ? null
                    : ProfileEdit.read(text.get()).get(ProfileKey.FOLDERS_NOT_WALKED);
            if (stated != null && stated.value() != null && !stated.value().isBlank()) {
                if (ProfileEdit.isReadable(ProfileKey.FOLDERS_NOT_WALKED, stated.value())) {
                    listed = List.of(stated.value().split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                            .stream().map(String::strip).filter(piece -> !piece.isEmpty()).toList();
                } else {
                    inclusionsOutcome.setText(ProfileKey.FOLDERS_NOT_WALKED.key() + " in " + ProfileEdit.FILE_NAME
                            + " cannot be read, so the walk reads every folder and every folder is drawn ticked; a Save"
                            + " takes the value out");
                }
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            inclusionsOutcome.setText(String.valueOf(unreadable.getMessage()));
        }
        notWalkedListed = listed;
        inclusions.draw(corpusRoot(), listed);
        drawNotRelevant();
    }

    /**
     * Writes the profile's {@code foldersNotWalked} and nothing else (ADR-345 §2): the highest unticked folder of each
     * branch, the pieces already listed first; a value as the file states it is not written, a changed one is written
     * with the note filled in, and with no folder unticked and none kept the key is taken out.
     */
    private void saveInclusions() {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            inclusionsOutcome.setText("name the working directory first");
            return;
        }
        ProfileKey key = ProfileKey.FOLDERS_NOT_WALKED;
        Map<ProfileKey, ProfileEdit.Stated> stated;
        try {
            stated = ProfileRow.statedIn(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            inclusionsOutcome.setText(String.valueOf(unreadable.getMessage()));
            return;
        }
        Optional<ProfileEdit.Stated> inFile = Optional.ofNullable(stated.get(key));
        List<String> pieces = inclusions.toWrite(notWalkedListed);
        String saidSo = "; the next walk is another, so every run after it is new";
        if (pieces.isEmpty()) {
            if (inFile.map(ProfileEdit.Stated::value).filter(value -> value != null && !value.isBlank()).isEmpty()) {
                inclusionsOutcome.setText("nothing was written: every folder is ticked and the profile lists none");
                return;
            }
            Optional<String> failed = state.write(folder.get(), ProfileEdit.FILE_NAME,
                    current -> ProfileEdit.without(current.orElseThrow(), key));
            inclusionsOutcome.setText(failed.orElse(key.key() + " is taken out of " + folder.get().resolve(
                    ProfileEdit.FILE_NAME) + saidSo));
            loadFiles();
            return;
        }
        String value = String.join(java.io.File.pathSeparator, pieces);
        Optional<String> note = ProfileEdit.noteToSave(key, value, ProfileEdit.noteShown(inFile), inFile);
        if (note.isEmpty()) {
            inclusionsOutcome.setText("nothing was written: the folders not walked are as " + ProfileEdit.FILE_NAME
                    + " states them");
            return;
        }
        Optional<String> refusal = ProfileEdit.refusalOfSave(key, value, note.get(), inFile, Optional.empty());
        if (refusal.isPresent()) {
            inclusionsOutcome.setText(refusal.get());
            return;
        }
        Optional<String> failed = state.write(folder.get(), ProfileEdit.FILE_NAME,
                current -> ProfileEdit.rewrite(current, key, value, note.get()));
        inclusionsOutcome.setText(failed.orElse(key.key() + " saved in " + folder.get().resolve(ProfileEdit.FILE_NAME)
                + saidSo));
        if (failed.isEmpty()) {
            loadFiles();
        }
    }

    /** Reads the working directory's files into the profile form, the seed folder, the code sets and the tree. */
    private void loadFiles() {
        if (profileBody == null || seedRow == null) {
            return;
        }
        Map<ProfileKey, ProfileEdit.Stated> stated = Map.of();
        String problem = "";
        Optional<ProfileRepair.Analysis> notInForm = Optional.empty();
        String text = null;
        try {
            text = readFile(ProfileEdit.FILE_NAME).orElse(null);
            if (text != null) {
                notInForm = ProfileRepair.analyse(text);
                if (notInForm.isEmpty()) {
                    stated = ProfileEdit.read(text);
                }
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            problem = String.valueOf(unreadable.getMessage());
        }
        Optional<EffectiveValues> effective = state.effectiveValues(workingDirectory());
        Map<String, Boolean> written = writtenReports();
        List<String> models = state.lastChecks().ollama().models();
        profileRows.clear();
        profileBody.getChildren().clear();
        if (workingDirectory().isEmpty()) {
            profileBody.getChildren().add(new Label("Name the working directory: profile.yaml lies in it."));
        }
        if (notInForm.isPresent()) {
            profileBody.getChildren().add(formProblem(text, notInForm.get()));
        }
        if (!problem.isEmpty()) {
            profileBody.getChildren().add(new Label(problem));
        }
        for (ProfileKey key : ProfileKey.values()) {
            Optional<EffectiveValues.Value> beneath = key.hasAValueBeneath()
                    ? effective.flatMap(said -> said.named(key.key())) : Optional.empty();
            ProfileRow row = new ProfileRow(key, "profile-", models, beneath, dialogs,
                    chosen -> {
                        if (chosen == ProfileKey.FOLDERS_NOT_WALKED) {
                            tabs.getSelectionModel().select(inclusionsTab);
                        } else {
                            sidebar.setExpandedPane(sections.get(chosen == ProfileKey.SEED_FOLDER
                                    ? "seed-folder" : "code-sets"));
                        }
                    }, this::openReport, written);
            row.load(Optional.ofNullable(stated.get(key)));
            profileRows.add(row);
            profileBody.getChildren().add(row.node());
        }
        seedRow.load(Optional.ofNullable(stated.get(ProfileKey.SEED_FOLDER)));
        seedRow.showReports(written);
        wantedRows.forEach(each -> each.showReports(written));
        seedStated = !seedRow.value().isBlank();
        if (seedRow.value().isBlank() && !state.values().lastSeedFolder().isEmpty()) {
            // offered for the operator to confirm, not stated by the file: a save writes it (ADR-292 §6)
            seedRow.offer(state.values().lastSeedFolder());
        }
        codeSetEntries.clear();
        try {
            for (CodeSetRenames.Rename rename : CodeSetRenames.read(readFile(CodeSetRenames.FILE_NAME))) {
                codeSetEntries.add(new CodeSet(rename.folder(), rename.name(), false));
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            codeSetOutcome.setText(String.valueOf(unreadable.getMessage()));
        }
        ProfileEdit.Stated codeFolders = stated.get(ProfileKey.CODE_SET_FOLDERS);
        if (codeFolders != null && codeFolders.value() != null) {
            CodeSetFolderChecks.statedIn(codeFolders.value()).forEach(each -> codeSetEntries.add(new CodeSet(each, "", true)));
        }
        codeSetNote.setText(ProfileEdit.noteShown(Optional.ofNullable(codeFolders)));
        drawCodeSets();
        drawInclusions();
        drawReports();
        where();
        showBadges();
    }

    private Node formProblem(String text, ProfileRepair.Analysis analysis) {
        VBox box = new VBox();
        box.getStyleClass().add("repair");
        box.setId("profile-problem");
        box.getChildren().add(new Label(ProfileEdit.FILE_NAME + " is not in the form the jar writes it: the first line"
                + " that is not is line " + analysis.firstLine() + "."
                + (analysis.repairable() ? " The changes below put it in form and lose no value or provenance."
                        : " The repair cannot put it right; it is left to you.")));
        if (analysis.repairable()) {
            analysis.changes().forEach(change -> box.getChildren().add(
                    new Label("line " + change.line() + ": " + change.words())));
            Button repair = new Button("Repair");
            repair.setId("profile-repair");
            String digest = ProfileEdit.digestOf(text);
            repair.setOnAction(event -> repair(digest));
            box.getChildren().add(repair);
        }
        return box;
    }

    private void repair(String drawn) {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        String[] copy = {""};
        Optional<String> refusal = state.write(folder.get(), ProfileEdit.FILE_NAME, current -> {
            String text = current.orElseThrow(() -> new IllegalArgumentException(
                    ProfileEdit.FILE_NAME + " is not there, so there is nothing to repair"));
            if (!ProfileEdit.digestOf(text).equals(drawn)) {
                throw new IllegalArgumentException(
                        ProfileEdit.FILE_NAME + " has changed since it was shown, so it was not repaired");
            }
            ProfileRepair.Analysis analysis = ProfileRepair.analyse(text).filter(ProfileRepair.Analysis::repairable)
                    .orElseThrow(() -> new IllegalArgumentException(ProfileEdit.FILE_NAME
                            + " is in no form that the repair can put right, so it was not repaired"));
            copy[0] = WorkingDirectoryWrite.keepCopyBeforeRepair(folder.get(), ProfileEdit.FILE_NAME);
            return analysis.repaired();
        });
        profileOutcome.setText(refusal.orElse("profile.yaml is repaired; the file as it stood is kept as " + copy[0]));
        loadFiles();
    }

    // ---- what the next invocation wants

    private void drawWanted() {
        wanted.getChildren().clear();
        wantedRows.clear();
        if (next.isEmpty() || next.get().wants().isEmpty()) {
            return;
        }
        Map<ProfileKey, ProfileEdit.Stated> stated = Map.of();
        try {
            Optional<String> text = readFile(ProfileEdit.FILE_NAME);
            if (text.isPresent() && ProfileRepair.analyse(text.get()).isEmpty()) {
                stated = ProfileEdit.read(text.get());
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            // the Profile tab says so
        }
        Optional<EffectiveValues> effective = state.effectiveValues(workingDirectory());
        Map<String, Boolean> written = writtenReports();
        wanted.getChildren().add(new Label("The next invocation wants these values:"));
        for (String want : next.get().wants()) {
            Optional<ProfileKey> key = ProfileKey.named(want);
            if (key.isEmpty()) {
                continue;
            }
            Optional<EffectiveValues.Value> beneath = key.get().hasAValueBeneath()
                    ? effective.flatMap(said -> said.named(key.get().key())) : Optional.empty();
            ProfileRow row = new ProfileRow(key.get(), "wanted-", state.lastChecks().ollama().models(), beneath,
                    dialogs, null, this::openReport, written);
            row.load(Optional.ofNullable(stated.get(key.get())));
            if (key.get() == ProfileKey.ARRANGEMENT_APPROVED && next.get().arrangement().isPresent()) {
                // the arrangement the jar arrives at, offered for the operator to confirm (ADR-294 section 4)
                row.offer(next.get().arrangement().get());
            } else if (key.get() == ProfileKey.SEED_FOLDER && row.value().isBlank()
                    && !state.values().lastSeedFolder().isEmpty()) {
                // offered, not stated by the file: a save writes it (ADR-292 section 6)
                row.offer(state.values().lastSeedFolder());
            }
            wantedRows.add(row);
            wanted.getChildren().add(row.node());
        }
        Button save = new Button("Save");
        save.setId("wanted-save");
        save.setOnAction(event -> {
            ProfileRow.Saved saved = ProfileRow.save(state, workingDirectory(), wantedRows);
            say(saved.line());
            if (saved.written()) {
                loadFiles();
            }
        });
        wanted.getChildren().add(save);
        showBadges();
    }

    // ---- the ticks of the clock

    private void showBadges() {
        badge("folders", fields.get("vespera.corpus-root").text().isEmpty()
                || fields.get("vespera.working-dir").text().isEmpty());
        badge("services", fields.get("vespera.docling.base-url").text().isEmpty()
                || fields.get("spring.ai.ollama.base-url").text().isEmpty()
                || fields.get("vespera.label.model").text().isEmpty());
        badge("seed-folder", seedRow != null && !seedStated);
        profileTab.setText(wantedRows.isEmpty() ? "Profile" : "Profile (" + NEEDS_A_VALUE + ")");
    }

    private void badge(String id, boolean needs) {
        TitledPane pane = sections.get(id);
        if (pane != null) {
            pane.setText(pane.getUserData() + (needs ? " (" + NEEDS_A_VALUE + ")" : ""));
        }
    }

    private void refresh() {
        boolean showing = root.getScene() != null && root.getScene().getWindow() != null
                && root.getScene().getWindow().isShowing();
        if (showing) {
            wasShown = true;
        } else if (wasShown) {
            timeline.stop();
            return;
        }
        boolean runs = state.childRuns();
        Optional<Ending> ending = state.ending();
        ControlPanelState.Holders holders = state.holders(workingDirectory(), corpusRoot());
        boolean busy = runs || holders.any();
        run.setDisable(busy);
        label.setDisable(busy);
        labelAuto.setDisable(busy);
        publish.setDisable(busy);
        stop.setDisable(!runs);
        List<String> drawnDestinations = state.destinationsDrawn(workingDirectory(),
                fields.get("vespera.deliverable-root").text());
        boolean drawn = !drawnDestinations.isEmpty();
        if (drawn && !actions.getChildren().contains(publish)) {
            actions.getChildren().add(actions.getChildren().indexOf(publishTo), publish);
        } else if (!drawn) {
            actions.getChildren().remove(publish);
        }
        drawDestinations(drawnDestinations);
        child.setText(childWords(runs, ending));
        List<String> tail = state.tail();
        if (!tail.equals(lastOutput)) {
            lastOutput = tail;
            output.getItems().setAll(tail);
            if (!tail.isEmpty()) {
                output.scrollTo(tail.size() - 1);
            }
        }
        readProgress(runs, ending);
        if (ending.isPresent() && ending.get() != wantedFor) {
            wantedFor = ending.get();
            Map<String, Boolean> written = writtenReports();
            profileRows.forEach(each -> each.showReports(written));
            wantedRows.forEach(each -> each.showReports(written));
            seedRow.showReports(written);
            drawReports();
            if (ending.get().howItEnded() == HowItEnded.ENDED) {
                readNext();
            }
        }
        drawPreflight();
        wasShown = wasShown || showing;
    }

    private String childWords(boolean runs, Optional<Ending> ending) {
        if (runs) {
            return "Running: " + state.childCommand();
        }
        if (ending.isEmpty()) {
            return "No invocation started";
        }
        Ending ended = ending.get();
        String how = switch (ended.howItEnded()) {
            case ENDED -> "Ended";
            case FAILED_OR_REFUSED -> "Failed or refused, exit " + ended.exitCode();
            case DEFECT_OF_THE_CONTROL_PANEL -> "Defect of the control panel, exit " + ended.exitCode();
            case STOPPED -> "Stopped";
        };
        return state.childCommand() + ": " + how + ended.lastLine().map(line -> " (" + line + ")").orElse("");
    }

    private void readNext() {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        try {
            next = NextInvocation.read(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            next = Optional.empty();
        }
        drawWanted();
    }

    private void readProgress(boolean runs, Optional<Ending> ending) {
        Optional<Path> folder = workingDirectory();
        boolean justEnded = ending.isPresent() && ending.get() != reportedEnding;
        if (folder.isEmpty() || !(runs || justEnded)) {
            return;
        }
        ticksSinceProgress++;
        if (!justEnded && ticksSinceProgress < intervalSeconds()) {
            return;
        }
        ticksSinceProgress = 0;
        if (justEnded) {
            reportedEnding = ending.get();
        }
        try {
            Path file = folder.get().resolve(ProgressFile.FILE_NAME);
            if (Files.isRegularFile(file)) {
                show(ProgressFile.read(Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            // a file not there, not readable or not that JSON leaves the window as it was
        }
    }

    private int intervalSeconds() {
        Preset interval = presets.get("vespera.progress-interval");
        return interval == null ? 5 : secondsOf(interval.value());
    }

    private static int secondsOf(String duration) {
        Matcher read = A_DURATION.matcher(duration == null ? "" : duration.strip());
        if (!read.matches()) {
            return 5;
        }
        long number = Long.parseLong(read.group(1));
        long seconds = switch (read.group(2)) {
            case "m" -> number * 60;
            case "h" -> number * 3600;
            default -> number;
        };
        return (int) Math.max(1, Math.min(seconds, 1_000_000));
    }

    private void show(ProgressFile.Progress progress) {
        Map<String, ProgressFile.Step> byName = new HashMap<>();
        progress.steps().forEach(step -> byName.put(step.name(), step));
        Map<String, Double> fractions = new HashMap<>();
        stepRows.forEach((name, row) -> {
            ProgressFile.Step step = byName.get(name);
            String words = step == null ? "waiting" : step.state();
            row.state().setText(words);
            boolean running = words.equals("running");
            row.stopHere().setVisible(running);
            row.stopHere().setManaged(running);
            row.cost().setVisible(running);
            row.cost().setManaged(running);
            if (step != null && step.done() != null && step.total() != null && step.total() > 0) {
                row.bar().setProgress(Math.min(1.0, (double) step.done() / step.total()));
                fractions.put(name, row.bar().getProgress());
            } else if (words.equals("finished") || words.equals("stopped at a gate")) {
                row.bar().setProgress(1.0);
                fractions.put(name, 1.0);
            } else if (running) {
                row.bar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            } else {
                row.bar().setProgress(0);
            }
        });
        Map<String, String> legStates = Legs.statesOf(progress);
        List<String> legsOpen = Legs.openOf(progress);
        for (Legs.Leg leg : Legs.ALL) {
            LegView view = legViews.get(leg.name());
            view.state().setText(legStates.get(leg.name()));
            // a bar moving without a fraction counts as nothing (ADR-361 §4)
            view.bar().setProgress(leg.steps().stream().mapToDouble(step -> fractions.getOrDefault(step, 0.0)).sum()
                    / leg.steps().size());
            if (!chosenByTheOperator.contains(leg.name())) {
                foldingItself = true;
                try {
                    view.pane().setExpanded(legsOpen.contains(leg.name()));
                } finally {
                    foldingItself = false;
                }
            }
        }
        for (Map.Entry<String, LineChart<Number, Number>> entry : charts.entrySet()) {
            String id = entry.getKey().replace("-chart", "");
            LineChart<Number, Number> chart = entry.getValue();
            Optional<ProgressFile.Chart> drawn = progress.charts().stream().filter(each -> each.id().equals(id)).findFirst();
            chart.getData().clear();
            if (drawn.isEmpty()) {
                continue;
            }
            chart.setTitle(drawn.get().title());
            chart.getYAxis().setLabel(drawn.get().axis());
            for (ProgressFile.Line line : drawn.get().lines()) {
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName(line.name());
                for (int i = 0; i < line.values().size() && i < drawn.get().seconds().size(); i++) {
                    series.getData().add(new XYChart.Data<>(drawn.get().seconds().get(i), line.values().get(i)));
                }
                chart.getData().add(series);
            }
        }
    }

    /** Whether the child runs now, which closing the window asks about once (ADR-344 §7). */
    boolean childRuns() {
        return state.childRuns();
    }

    /** Where the window is closed: the clock stops with it. */
    void closed() {
        timeline.stop();
    }
}
