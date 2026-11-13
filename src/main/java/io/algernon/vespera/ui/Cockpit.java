package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;
import static io.algernon.vespera.ui.Layout.tight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The control panel's window as one cockpit (ADR-344 §1): Run and Stop, the job's steps as a bar each beside the
 * child's output, a chart for each stage in two rows (ADR-382), the Profile and Inclusions tabs and a sidebar of seven
 * sections in their order. Every part is found by its id. The cockpit reads and writes only through the {@link ControlPanelState}
 * it is given, and reads the sources its fields start from from that state (ADR-344 §3), so a cockpit a test draws
 * starts from what the test gives it. Every colour, size and space is the stylesheet's; no node here sets a style.
 * It composes the parts, each its own class, and keeps what they share: the launch fields, the plan a start is given,
 * the clock's tick and the reading of the working directory's files.
 */
final class Cockpit {

    private static final String NEEDS_A_VALUE = "needs a value";

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final BorderPane root = new BorderPane();

    private final SelectableText.Line where = SelectableText.line("");
    private final SelectableText.Line child = SelectableText.line("");
    private final SelectableText.Line databaseSize = SelectableText.line("");
    private final SelectableText.Wrapping outcome = SelectableText.wrapping("");
    private final Button run = new Button("Run");
    private final Button stop = new Button("Stop");
    private final Button publish = new Button("Publish");
    private final Button label = new Button("Record my answers");
    private final Button labelAuto = new Button("Let the local model answer");
    private final HBox actions = new HBox();
    private final TextArea output = new TextArea();
    private final OutputPanel outputPanel = new OutputPanel(output);
    private final TabPane tabs = new TabPane();
    private final Tab profileTab = new Tab("Profile");
    private final Tab inclusionsTab = new Tab("Inclusions");
    private final Accordion sidebar = new Accordion();
    private final Map<String, TitledPane> sections = new LinkedHashMap<>();
    private final Map<String, LaunchField> fields = new LinkedHashMap<>();
    private final HBox publishTo = new HBox();
    private final SelectableText.Wrapping seedOutcome = SelectableText.wrapping("");
    private final WorkingFolders folders = new WorkingFolders(this::workingDirectory, this::corpusRoot);
    private final StepsChart steps = new StepsChart(this::stopAfterConfirming);
    private final ProgressCharts charts = new ProgressCharts();
    private final OptionsPane options;
    private final ReportsPane reports;
    private final PreflightPane preflight;
    private final CodeSetsPane codeSets;
    private final InclusionsTab inclusions;
    private final ProfileForm profile;
    private final WantedValues wanted;
    /** The keys whose rows are open on the Profile tab, in Seed folder and in what the next invocation wants (ADR-377 §3). */
    private final ProfileRow.OpenKeys profileOpen = new ProfileRow.OpenKeys(false);
    private final ProfileRow.OpenKeys seedOpen = new ProfileRow.OpenKeys(false);
    private final ProfileRow.OpenKeys wantedOpen = new ProfileRow.OpenKeys(true);
    private ProfileRow seedRow;
    private boolean seedStated;

    private final LiveReport live;
    private Ending reportedEnding;

    /** The ending the closing line below was read for, and the {@code closing} of the report its child wrote (ADR-362 section 8). */
    private Ending closedAs;
    private String closingOfItsReport;
    private int ticksSinceProgress;
    /** Who the report is read for: the child's pid and start, or a foreign holder's; null before any is watched. */
    private ControlPanelState.Watched watching;
    /** Whether a report of the watched invocation's own has been drawn since it was first watched. */
    private boolean watchedReportDrawn;
    private InvocationReportFile.Invocation lastDrawn;
    private boolean sawChild;
    private boolean wasShown;
    private Timeline timeline;
    private Ending wantedFor;

    Cockpit(ControlPanelState state, Dialogs dialogs) {
        this.state = state;
        this.dialogs = dialogs;
        this.options = new OptionsPane(state, this::showCommandLines);
        this.reports = new ReportsPane(tabs, dialogs, folders, this::say);
        this.live = new LiveReport(this::openReport, dialogs::handToTheSystem);
        this.preflight = new PreflightPane(state, folders, property -> fields.get(property).text());
        this.codeSets = new CodeSetsPane(state, dialogs, folders, this::foldersToKeepApart, this::loadFiles);
        this.inclusions = new InclusionsTab(state, dialogs, folders, this::loadFiles);
        this.profile = new ProfileForm(state, dialogs, folders, this::pointTo, this::openReport, this::loadFiles,
                profileOpen);
        this.wanted = new WantedValues(state, dialogs, folders, this::say, this::openReport, this::loadFiles,
                this::showBadges, wantedOpen);
        root.getStyleClass().add("cockpit");
        if (state.darkChosen()) {
            root.getStyleClass().add("dark");
        }
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
        VBox titles = new VBox(where, child, databaseSize);
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox header = new HBox(titles, ThemeSwitch.of(root, state));
        header.setAlignment(Pos.TOP_RIGHT);
        header.getStyleClass().add("header");
        where.setId("where");
        child.setId("child");
        databaseSize.setId("database-size");
        outcome.setId("outcome");
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
        SelectableText.Wrapping recordWords = SelectableText.wrapping("You answered in relevance-labels.yaml, and this saves those answers"
                + " for the next Run.");
        SelectableText.Wrapping autoWords = SelectableText.wrapping("");
        autoWords.setId("label-auto-words");
        fields.get("vespera.label.model").input.textProperty().addListener(
                (observed, was, now) -> autoWords.setText(autoSentence(now)));
        autoWords.setText(autoSentence(fields.get("vespera.label.model").text()));
        VBox actionCard = new VBox(actions, wanted.node(), row(label, recordWords), row(labelAuto, autoWords));
        actionCard.getStyleClass().add("card");

        output.setId("output");
        output.getStyleClass().add("output");
        SplitPane split = new SplitPane(new ScrollPane(steps), output);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.5);
        ((ScrollPane) split.getItems().getFirst()).setFitToWidth(true);
        split.getStyleClass().add("steps-and-output");

        profileTab.setId("profile");
        inclusionsTab.setId("inclusions");
        profileTab.setClosable(false);
        inclusionsTab.setClosable(false);
        profileTab.setContent(profile.content());
        inclusionsTab.setContent(inclusions.content());
        tabs.getTabs().addAll(live.tab(), profileTab, inclusionsTab);
        tabs.getStyleClass().add("form-tabs");

        VBox main = new VBox(header, outcome, actionCard, split, charts, tabs);
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

    // ---- the plan and the command lines

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
        Map<String, String> passedOptions = options.passed(sources);
        if (!passed.containsKey("vespera.working-dir")) {
            // the field left empty stands for the folder the jar would use on this machine (ADR-345 §1)
            passed.put("vespera.working-dir", state.workingDirectoryDefault().folder());
        }
        List<String> ticked = publishTo.getChildren().stream().filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast).filter(CheckBox::isSelected)
                .map(box -> box.getId().substring("publish-to-".length())).toList();
        return new ControlPanelState.Plan(valuesOf(passed, seed), valuesOf(kept, seed), passedOptions, ticked);
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
        if (!options.showsCommandLines()) {
            return;
        }
        options.showCommandLines(plan());
    }

    private void begin(ControlPanelState.Start kind, String words) {
        List<String> stored = new ArrayList<>();
        ControlPanelState.Plan plan = plan();
        Optional<String> token = Optional.empty();
        if (kind == ControlPanelState.Start.PUBLISH && plan.destinations().contains("confluence")) {
            token = dialogs.masked("Publish to Confluence", "The API token of the Confluence account, which is passed"
                    + " to this publish alone and kept nowhere:").filter(typed -> !typed.isBlank());
            if (token.isEmpty()) {
                say(words + " is not started: no API token was typed");
                return;
            }
        }
        Optional<String> refusal = state.start(kind, plan, token, stored);
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
        section("preflight", "Preflight", preflight.pane());
        section("folders", "Folders", foldersPane());
        section("services", "Services", servicesPane());
        section("seed-folder", "Seed folder", seedPane());
        section("code-sets", "Code sets", codeSets.pane());
        section("command-options", "Command options", options.pane());
        section("reports", "Reports", reports.pane());
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

    private LaunchField launchField(String property, boolean folder, boolean createdByTheJar) {
        LaunchField field = new LaunchField(state, property, folder, createdByTheJar, () -> {
            showCommandLines();
            showBadges();
        });
        fields.put(property, field);
        return field;
    }

    private Node fieldBox(LaunchField field, String name, boolean browse) {
        SelectableText.Line heading = SelectableText.line(name);
        heading.getStyleClass().add("field-name");
        SelectableText.Wrapping hint = SelectableText.wrapping(LaunchValues.hintOf(field.property));
        hint.setId("hint-" + field.property);
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
        LaunchField corpus = launchField("vespera.corpus-root", true, false);
        LaunchField working = launchField("vespera.working-dir", true, true);
        LaunchField deliverable = launchField("vespera.deliverable-root", true, true);
        corpus.input.textProperty().addListener((observed, was, now) -> {
            where();
            inclusions.draw();
        });
        working.input.textProperty().addListener((observed, was, now) -> {
            where();
            reports.closeTabs();
            // the rows drawn for another working directory start as they first do (ADR-377 section 3)
            List.of(profileOpen, seedOpen, wantedOpen).forEach(ProfileRow.OpenKeys::forget);
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
        LaunchField image = launchField("vespera.docling.image", false, false);
        LaunchField converter = launchField("vespera.docling.base-url", false, false);
        LaunchField ollama = launchField("spring.ai.ollama.base-url", false, false);
        LaunchField model = launchField("vespera.label.model", false, false);
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
        SelectableText.Line gpuName = SelectableText.line(StartingValues.DOCLING_IMAGE_GPU);
        SelectableText.Line cpuName = SelectableText.line(StartingValues.DOCLING_IMAGE_CPU);
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
        SelectableText.Wrapping hint = SelectableText.wrapping(LaunchValues.hintOf(image.property));
        hint.setId("hint-" + image.property);
        hint.getStyleClass().add("small");
        VBox pane = new VBox(SelectableText.line("Converter's image"), images, image.input, image.startingLine, hint,
                fieldBox(converter, "Converter's address", false), fieldBox(ollama, "Ollama's address", false),
                fieldBox(model, "Label model", false));
        pane.getStyleClass().add("section");
        return pane;
    }

    private Node seedPane() {
        seedRow = new ProfileRow(ProfileKey.SEED_FOLDER, "seed-", List.of(), Optional.empty(), dialogs, null,
                this::openReport, folders.writtenReports(), seedOpen, new ProfileRow.TitleColumn());
        Button save = new Button("Save");
        save.setId("seed-folder-save");
        seedOutcome.setId("seed-folder-outcome");
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

    /** The folders a code set outside the corpus root must stay apart from, as the window names them now. */
    private List<CodeSetFolderChecks.Apart> foldersToKeepApart() {
        return List.of(
                new CodeSetFolderChecks.Apart("corpus root", fields.get("vespera.corpus-root").text()),
                new CodeSetFolderChecks.Apart("working directory", fields.get("vespera.working-dir").text()),
                new CodeSetFolderChecks.Apart("seed folder", seedRow == null ? "" : seedRow.value()),
                new CodeSetFolderChecks.Apart("output folder", fields.get("vespera.deliverable-root").text()));
    }

    /** Takes the operator to where {@code chosen}, a key the Profile tab does not edit, is edited. */
    private void pointTo(ProfileKey chosen) {
        if (chosen == ProfileKey.FOLDERS_NOT_WALKED) {
            tabs.getSelectionModel().select(inclusionsTab);
        } else {
            sidebar.setExpandedPane(sections.get(chosen == ProfileKey.SEED_FOLDER ? "seed-folder" : "code-sets"));
        }
    }

    private void openReport(String report) {
        reports.open(report);
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

    /** Reads the working directory's files into the profile form, the seed folder, the code sets and the tree. */
    private void loadFiles() {
        if (seedRow == null) {
            return;
        }
        Map<ProfileKey, ProfileEdit.Stated> stated = Map.of();
        String problem = "";
        Optional<ProfileRepair.Analysis> notInForm = Optional.empty();
        String text = null;
        try {
            text = folders.readFile(ProfileEdit.FILE_NAME).orElse(null);
            if (text != null) {
                notInForm = ProfileRepair.analyse(text);
                if (notInForm.isEmpty()) {
                    stated = ProfileEdit.read(text);
                }
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            problem = String.valueOf(unreadable.getMessage());
        }
        Map<String, Boolean> written = folders.writtenReports();
        profile.draw(stated, problem, notInForm, text, written);
        seedRow.load(Optional.ofNullable(stated.get(ProfileKey.SEED_FOLDER)));
        seedRow.showReports(written);
        wanted.load(stated);
        wanted.showReports(written);
        seedStated = !seedRow.value().isBlank();
        if (seedRow.value().isBlank() && !state.values().lastSeedFolder().isEmpty()) {
            // offered for the operator to confirm, not stated by the file: a save writes it (ADR-292 §6)
            seedRow.offer(state.values().lastSeedFolder());
        }
        codeSets.load(stated);
        inclusions.draw();
        reports.draw();
        where();
        showBadges();
        readStageCharts();
    }

    /**
     * Reads the eight files the jar keeps the stages' charts in, at the top of the working directory, and draws each
     * chart from its file: as the window opens, whenever another working directory is named and at each reading of
     * the report (ADR-382 §4). A file not there, not readable, not that JSON or of another format leaves its chart
     * empty under its title, and nothing is said.
     */
    private void readStageCharts() {
        Map<String, StageChartFile.Chart> kept = new HashMap<>();
        Optional<Path> folder = workingDirectory();
        if (folder.isPresent()) {
            for (String stage : ProgressCharts.STAGES) {
                try {
                    Path file = folder.get().resolve(StageChartFile.nameOf(stage));
                    if (Files.isRegularFile(file)) {
                        StageChartFile.read(Files.readString(file, StandardCharsets.UTF_8))
                                .ifPresent(chart -> kept.put(stage, chart));
                    }
                } catch (IOException | RuntimeException unreadable) {
                    // drawn empty under its title
                }
            }
        }
        charts.show(kept);
    }

    /**
     * Writes the sum of the database file and its write-ahead log in the working directory, each read by a file system
     * call and never opened, as {@code AnalysisReportView.bytes} writes a size (ADR-382 §3); {@code none yet} where
     * neither is there.
     */
    private void showDatabaseSize() {
        long bytes = 0;
        boolean found = false;
        Optional<Path> folder = workingDirectory();
        if (folder.isPresent()) {
            for (String name : List.of("vespera.db", "vespera.db-wal")) {
                try {
                    bytes += Files.size(folder.get().resolve(name));
                    found = true;
                } catch (IOException | RuntimeException absent) {
                    // the log is there only while the database is open
                }
            }
        }
        databaseSize.setText("Database size: " + (found ? AnalysisReportView.bytes(java.math.BigDecimal.valueOf(bytes)) : "none yet"));
    }

    // ---- the ticks of the clock

    private void showBadges() {
        badge("folders", fields.get("vespera.corpus-root").text().isEmpty()
                || fields.get("vespera.working-dir").text().isEmpty());
        badge("services", fields.get("vespera.docling.base-url").text().isEmpty()
                || fields.get("spring.ai.ollama.base-url").text().isEmpty()
                || fields.get("vespera.label.model").text().isEmpty());
        badge("seed-folder", seedRow != null && !seedStated);
        profileTab.setText(wanted.isEmpty() ? "Profile" : "Profile (" + NEEDS_A_VALUE + ")");
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
        showDatabaseSize();
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
        readReport(runs, ending, holders);
        outputPanel.tick(state::held);
        child.setText(childWords(runs, ending));
        if (ending.isPresent() && ending.get() != wantedFor) {
            wantedFor = ending.get();
            Map<String, Boolean> written = folders.writtenReports();
            profile.showReports(written);
            wanted.showReports(written);
            seedRow.showReports(written);
            reports.draw();
            if (ending.get().howItEnded() == HowItEnded.ENDED) {
                readNext();
            }
        }
        preflight.draw();
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
        Optional<String> line = ended == closedAs && closingOfItsReport != null
                ? Optional.of(closingOfItsReport) : ended.lastLine();
        return state.childCommand() + ": " + how + line.map(each -> " (" + each + ")").orElse("");
    }

    private void readNext() {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        Optional<NextInvocation> next;
        try {
            next = NextInvocation.read(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            next = Optional.empty();
        }
        wanted.draw(next);
    }

    /**
     * Reads the working directory's {@code report.json} for the invocation the window watches (ADR-362 §8): its own
     * child's while that runs, once as it ends, and a foreign holder's while its holder line is held. A report is drawn
     * only where it is that invocation's; until then the rows, the legs and the charts stand as before any report.
     */
    private void readReport(boolean runs, Optional<Ending> ending, ControlPanelState.Holders holders) {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        Optional<ControlPanelState.Watched> child = state.watchedChild();
        boolean justEnded = ending.isPresent() && ending.get() != reportedEnding;
        ControlPanelState.Watched who;
        boolean ownChild = child.isPresent() && (runs || justEnded);
        if (ownChild) {
            who = child.get();
        } else {
            Optional<ControlPanelState.Watched> foreign = state.watchedHolder(folder);
            if (foreign.isEmpty()) {
                if (lastDrawn != null && !(child.isPresent() && ending.isPresent())) {
                    live.refreshStale(Instant.now());
                }
                return;
            }
            who = foreign.get();
        }
        boolean fresh = !who.equals(watching);
        if (fresh) {
            if (ownChild) {
                outputPanel.showsTheChild();
            } else {
                outputPanel.showsNoChild();
            }
            watching = who;
            watchedReportDrawn = false;
            lastDrawn = null;
            closedAs = null;
            closingOfItsReport = null;
            steps.show(InvocationReportFile.Report.none());
            readStageCharts();
            live.says(LiveReport.WAITING);
            ticksSinceProgress = options.intervalSeconds();
        }
        if (!ownChild) {
            justEnded = false;
        }
        ticksSinceProgress++;
        if (!fresh && !justEnded && ticksSinceProgress < options.intervalSeconds()) {
            live.refreshStale(Instant.now());
            return;
        }
        ticksSinceProgress = 0;
        String how = ownChild && ending.isPresent() ? childWords(false, ending)
                : ownChild ? "" : "held by " + holders.workingDirectory().orElse("another invocation");
        readStageCharts();
        boolean drawn = readOnce(folder.get(), who, justEnded, how, ownChild ? ending : Optional.empty());
        watchedReportDrawn = watchedReportDrawn || drawn;
        if (justEnded) {
            reportedEnding = ending.get();
            if (!watchedReportDrawn) {
                live.wroteNone(how);
            }
        }
    }

    /** Reads the file once; whether a report that is the watched invocation's was drawn. */
    private boolean readOnce(Path folder, ControlPanelState.Watched who, boolean childEnded, String howItEnded,
            Optional<Ending> endingOfItsChild) {
        try {
            Path file = folder.resolve(InvocationReportFile.FILE_NAME);
            if (!Files.isRegularFile(file)) {
                return false;
            }
            InvocationReportFile.Report report = InvocationReportFile.read(Files.readString(file, StandardCharsets.UTF_8));
            InvocationReportFile.Invocation invocation = report.invocation();
            if (invocation.pid() != who.pid()
                    || invocation.started().isBefore(who.notBefore().truncatedTo(ChronoUnit.MILLIS))) {
                return false;
            }
            if (endingOfItsChild.isPresent() && report.closing() != null) {
                // how its child ended is the closing line of the report it wrote, not the last line of its output (ADR-362 section 8)
                closedAs = endingOfItsChild.get();
                closingOfItsReport = report.closing();
                howItEnded = childWords(false, endingOfItsChild);
            }
            outputPanel.fileNamedBy(folder, invocation.id(), state::letGo);
            steps.show(report);
            live.shows(report, Instant.now(), childEnded, howItEnded);
            lastDrawn = invocation;
            return true;
        } catch (InvocationReportFile.FormatNotKnownException notKnown) {
            say(notKnown.getMessage());
        } catch (IOException | IllegalArgumentException unreadable) {
            // a file not there, not readable or not that JSON leaves the window as it was
        }
        return false;
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
