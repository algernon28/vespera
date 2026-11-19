package io.algernon.vespera.ui;

import static io.algernon.vespera.ui.Layout.row;
import static io.algernon.vespera.ui.Layout.tight;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.collections.FXCollections;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The control panel's window as one cockpit (ADR-344 §1): Run and Stop, the job's steps as a bar each beside the
 * child's output, a chart for each stage in two rows (ADR-382), the Report and Inclusions tabs and a sidebar of nine
 * sections in their order, Profile first, which opens the Profile window (ADR-389). Its tabs pane holds the Report tab and
 * Inclusions alone: an analysis report opens, by its name, in a tab of the Reports window, {@link AnalysisReportsWindow},
 * which a change of the working directory and the cockpit's closing close (ADR-414). Every part is found by its id. The cockpit reads and writes only through the {@link ControlPanelState}
 * it is given, and reads the sources its fields start from from that state (ADR-344 §3), so a cockpit a test draws
 * starts from what the test gives it. Every colour, size and space is the stylesheet's; no node here sets a style.
 * It composes the parts, each its own class, and keeps what they share: the launch fields, the plan a start is given,
 * the clock's tick and the reading of the working directory's files.
 */
final class Cockpit {

    private static final System.Logger log = System.getLogger(Cockpit.class.getName());

    private static final String NEEDS_A_VALUE = "needs a value";

    private final ControlPanelState state;
    private final Dialogs dialogs;
    private final BorderPane root = new BorderPane();

    private final SelectableText.Line where = SelectableText.line("");
    private final SelectableText.Line child = SelectableText.line("");
    private final SelectableText.Line databaseSize = SelectableText.line("");
    /** The model that names the topics, as the jar said it would use it: shown, never set (ADR-407 section 10). */
    private final SelectableText.Line namingModel = SelectableText.line("");
    /** The model the jar would use while the label model field stands blank (ADR-334 section 2). */
    private final SelectableText.Line labelModel = SelectableText.line("");
    private final SelectableText.Wrapping outcome = SelectableText.wrapping("");
    private final Button run = new Button("Run");
    private final Button stop = new Button("Stop");
    private final Button shutDownDocker = new Button("Shut down Docker");
    private volatile boolean shuttingDown;
    private final Button publish = new Button("Publish");
    /** What this window's own child was last started as, and the ending the parent's title warning was last opened for. */
    private ControlPanelState.Start startedKind;
    private Ending warnedFor;
    private final Button label = new Button("Save my labels");
    private final Button labelAuto = new Button("Auto Label");
    private final HBox actions = new HBox();
    private final TextArea output = new TextArea();
    private final OutputPanel outputPanel = new OutputPanel(output);
    private final TabPane tabs = new TabPane();
    /** The output's side of the steps and the output: the output above, the ending panel beneath it where it has one (ADR-417 §1). */
    private final SplitPane outputAndEnding = new SplitPane(output);
    private final EndingPanel endingPanel = new EndingPanel(this::openTheProfileWindow,
            this::selectTheReportTab);
    /** Where the divider above the ending panel stood when the panel was last taken away, as a fraction of the split. */
    private double endingDivider = 0.7;
    /** The working directory this window's own child was last started on, and whether its ending is still the last of it. */
    private Path childFolder;
    private boolean childIsStillTheLast;
    private long childSeenPid = -1;
    /** The ending the window last noticed, and when; the panel's last reading's folder, ending of its own child, and whether it was cleared. */
    private Ending noticedEnding;
    private Instant noticedAt = Instant.now();
    private String endingReadFor;
    private Ending endingReadOf;
    private boolean endingCleared;
    /** Whether the Report tab last drew a report for the ending panel, not for a watched invocation. */
    private boolean tabDrawnForTheEndingPanel;
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
    private final AnalysisReportsWindow analysisReportsWindow;
    private final PreflightPane preflight;
    private final CodeSetsPane codeSets;
    private final InclusionsTab inclusions;
    private final ProfileForm profile;
    private final ProfileWindow profileWindow;
    private final Button openProfile = new Button("Open profile");
    private final Button outcomeOpenProfile = new Button("Open profile");
    private final WantedValues wanted;
    /** The keys whose rows are open in the Profile window, in Seed folder and in what the next invocation wants (ADR-377 §3). */
    private final ProfileRow.OpenKeys profileOpen = new ProfileRow.OpenKeys(false);
    private final ProfileRow.OpenKeys seedOpen = new ProfileRow.OpenKeys(false);
    private final ProfileRow.OpenKeys wantedOpen = new ProfileRow.OpenKeys(true);
    private ProfileRow seedRow;
    private boolean seedStated;

    /** The list of working directories that takes the place of the working directory's field (ADR-393 §4). */
    private final ListView<Path> workingDirectoryList = new ListView<>();
    private final Button workingDirectoryNew = new Button("New\u2026");
    private final Button workingDirectoryBrowse = new Button("Browse");
    private final Button workingDirectoryRemove = new Button("Remove from list");
    /** What each listed folder's row said when it was last read: as the window opens, as it is added, as the child ends. */
    private final Map<Path, WorkingDirectoryRow> rowsRead = new HashMap<>();
    /** Set while the list moves the launch value, so that the value's change does not move the list in turn. */
    private boolean choosing;

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
    private Optional<Path> foreignHoldSeenOn = Optional.empty();

    Cockpit(ControlPanelState state, Dialogs dialogs) {
        this.state = state;
        this.dialogs = dialogs;
        this.options = new OptionsPane(state, this::showCommandLines);
        this.reports = new ReportsPane(folders, this::openReport);
        this.analysisReportsWindow = new AnalysisReportsWindow(root, dialogs, folders, this::say);
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
        this.profileWindow = new ProfileWindow(profile, root, dialogs);
        root.setLeft(sidebarPane());
        root.setCenter(mainPane());
        readNext();
        loadFiles();
        showModelsInUse();
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
        shutDownDocker.setId("shut-down-docker");
        publish.setId("publish");
        label.setId("label");
        labelAuto.setId("label-auto");
        run.setOnAction(event -> begin(ControlPanelState.Start.RUN, "Run"));
        publish.setOnAction(event -> begin(ControlPanelState.Start.PUBLISH, "Publish"));
        label.setOnAction(event -> begin(ControlPanelState.Start.LABEL, "Save my labels"));
        labelAuto.setOnAction(event -> begin(ControlPanelState.Start.LABEL_AUTO, "Auto Label"));
        stop.setOnAction(event -> stopAfterConfirming());
        shutDownDocker.setOnAction(event -> shutDownDockerAfterConfirming());
        publishTo.setId("publish-to");
        publishTo.getStyleClass().add("spaced-row");
        actions.getChildren().addAll(run, stop, shutDownDocker, publishTo);
        actions.getStyleClass().add("actions");
        VBox actionCard = new VBox(actions, wanted.node());
        actionCard.getStyleClass().add("card");

        output.setId("output");
        output.getStyleClass().add("output");
        outputAndEnding.setId("output-and-ending");
        outputAndEnding.setOrientation(Orientation.VERTICAL);
        outputAndEnding.getStyleClass().add("output-and-ending");
        SplitPane split = new SplitPane(new ScrollPane(steps), outputAndEnding);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.5);
        ((ScrollPane) split.getItems().getFirst()).setFitToWidth(true);
        split.getStyleClass().add("steps-and-output");
        split.setPrefHeight(STEPS_AND_OUTPUT_HEIGHT);
        Region stepsAndOutputGrip = grip("steps-and-output-grip", split, false, LEAST_STEPS_AND_OUTPUT_HEIGHT);

        inclusionsTab.setId("inclusions");
        inclusionsTab.setClosable(false);
        inclusionsTab.setContent(inclusions.content());
        tabs.getTabs().addAll(live.tab(), inclusionsTab);
        tabs.getStyleClass().add("form-tabs");

        outcomeOpenProfile.setId("outcome-open-profile");
        outcomeOpenProfile.setOnAction(event -> profileWindow.open());
        HBox.setHgrow(outcome, Priority.ALWAYS);
        HBox outcomeLine = new HBox(outcome, outcomeOpenProfile);
        outcomeLine.setAlignment(Pos.CENTER_LEFT);
        showTheButtonBesideTheOutcome();
        child.textProperty().addListener((observed, was, now) -> showTheButtonBesideTheOutcome());
        outcome.textProperty().addListener((observed, was, now) -> showTheButtonBesideTheOutcome());
        VBox main = new VBox(header, outcomeLine, actionCard, split, stepsAndOutputGrip, charts, tabs);
        main.getStyleClass().add("main");
        VBox.setVgrow(tabs, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        return scroll;
    }

    /** The least width of the sidebar and the least height of the steps beside the output, as the operator drags their grips (ADR-417 §1). */
    private static final double LEAST_SIDEBAR_WIDTH = 220;
    private static final double LEAST_STEPS_AND_OUTPUT_HEIGHT = 150;
    /**
     * The sizes each opens at. They are set here and not by the stylesheet, whose rules a size the operator drags to would
     * not outrank.
     */
    private static final double SIDEBAR_WIDTH = 420;
    private static final double STEPS_AND_OUTPUT_HEIGHT = 300;

    /**
     * A bar the operator drags to size {@code target}: its width where {@code wide}, else its height, by as much as the
     * pointer moves, and no less than {@code least} (ADR-417 §1). The size stays as it is left while the window is open.
     */
    private Region grip(String id, Region target, boolean wide, double least) {
        Region bar = new Region();
        bar.setId(id);
        bar.getStyleClass().addAll("grip", wide ? "grip-wide" : "grip-high");
        double[] from = new double[2];
        bar.setOnMousePressed(event -> {
            from[0] = wide ? event.getScreenX() : event.getScreenY();
            from[1] = wide ? target.getWidth() : target.getHeight();
        });
        bar.setOnMouseDragged(event -> {
            double moved = (wide ? event.getScreenX() : event.getScreenY()) - from[0];
            double size = Math.max(least, from[1] + moved);
            if (wide) {
                target.setPrefWidth(size);
            } else {
                target.setPrefHeight(size);
            }
            root.requestLayout();
            root.layout();
        });
        return bar;
    }

    /** The words by which a line says to set a value in the Profile window; the jar's refusals reach the window as text (ADR-389 §6). */
    private static final String THE_PROFILE_WINDOW = "Profile window";

    /** Shows Open profile beside the outcome while the child's line or the outcome holds the words, and lays it out only then. */
    private void showTheButtonBesideTheOutcome() {
        boolean names = child.getText().contains(THE_PROFILE_WINDOW) || outcome.getText().contains(THE_PROFILE_WINDOW);
        outcomeOpenProfile.setVisible(names);
        outcomeOpenProfile.setManaged(names);
    }

    /** The Labels section: Save my labels and Auto Label, each with its caption (ADR-413 section 1). */
    private Node labelsPane() {
        SelectableText.Wrapping labelWords = SelectableText.wrapping("Reads relevance-labels.yaml in the working directory,"
                + " where each document's relevant is set to true or false, and saves those labels for the next Run."
                + " It reads no document and sets no label itself.");
        labelWords.setId("label-words");
        SelectableText.Wrapping autoWords = SelectableText.wrapping("");
        autoWords.setId("label-auto-words");
        fields.get("vespera.label.model").input.textProperty().addListener(
                (observed, was, now) -> autoWords.setText(autoSentence(now)));
        autoWords.setText(autoSentence(fields.get("vespera.label.model").text()));
        VBox pane = new VBox(label, labelWords, labelAuto, autoWords);
        pane.getStyleClass().add("section");
        return pane;
    }

    private String autoSentence(String model) {
        return "The local model" + (model.isBlank() ? "" : ", " + model + ",")
                + " reads the documents, sets their labels and the cut-off, and never replaces a label you set.";
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
        String started = words + " started" + (stored.isEmpty() ? "" : "; " + String.join("; ", stored));
        if (refusal.isEmpty() && kind == ControlPanelState.Start.RUN) {
            // Run warns, and starts, where its working directory is named for another corpus root (ADR-393 section 3)
            Optional<Path> folder = workingDirectory();
            Optional<Path> root = corpusRoot();
            if (folder.isPresent() && root.isPresent()) {
                started += WorkingDirectoryName.warningToRun(folder.get(), root.get()).map(line -> "\n" + line).orElse("");
            }
        }
        say(refusal.orElse(started));
        sawChild = refusal.isEmpty() || sawChild;
        if (refusal.isEmpty()) {
            startedKind = kind;
        }
        refresh();
    }

    // ---- the warning of a parent's title naming no page or several (ADR-383)

    /** The exit codes of a publish the control panel started whose parent's title names no page or folder, and several. */
    private static final int NO_PAGE_SO_TITLED = 3;
    private static final int SEVERAL_SO_TITLED = 4;

    private static final String TITLE_LEFT_AS_IT_WAS = "the publish to Confluence sent nothing: confluenceFolderTitle is"
            + " left as profile.yaml states it; name the parent by confluenceFolderTitle or confluenceFolderId in the"
            + " Profile window, and publish again";

    /** Opens the warning for an ending of this window's own Publish child with 3 or 4, once for that ending. */
    private void warnOfTheParentsTitle(Ending ending) {
        if (ending == warnedFor || startedKind != ControlPanelState.Start.PUBLISH
                || ending.howItEnded() != HowItEnded.FAILED_OR_REFUSED
                || (ending.exitCode() != NO_PAGE_SO_TITLED && ending.exitCode() != SEVERAL_SO_TITLED)) {
            return;
        }
        warnedFor = ending;
        Platform.runLater(() -> askForAnotherTitle(ending.exitCode()));
    }

    private void askForAnotherTitle(int exitCode) {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty()) {
            return;
        }
        Map<ProfileKey, ProfileEdit.Stated> stated;
        try {
            stated = ProfileRow.statedIn(folder.get());
        } catch (IOException | RuntimeException unreadable) {
            return;
        }
        String title = Optional.ofNullable(stated.get(ProfileKey.CONFLUENCE_FOLDER_TITLE))
                .map(ProfileEdit.Stated::value).orElse("");
        if (title.isBlank()) {
            return;
        }
        String space = Optional.ofNullable(stated.get(ProfileKey.CONFLUENCE_SPACE_KEY))
                .map(ProfileEdit.Stated::value).orElse("");
        String sentence = (exitCode == NO_PAGE_SO_TITLED ? "No page or folder" : "Several pages or folders")
                + " of the space " + space + (exitCode == NO_PAGE_SO_TITLED ? " is" : " are") + " titled " + title
                + ", so the publish to Confluence sent nothing. Enter a different title for the parent the pages are"
                + " published under: it is saved as confluenceFolderTitle in the Profile window, and the publish starts"
                + " again.";
        Optional<String> entered = dialogs.warnAndAsk("The parent's title", sentence, title)
                .map(String::strip).filter(typed -> !typed.isEmpty());
        if (entered.isEmpty()) {
            say(TITLE_LEFT_AS_IT_WAS);
            return;
        }
        Optional<String> refusal = saveTheTitle(folder.get(), entered.get());
        loadFiles();
        if (refusal.isPresent()) {
            say(refusal.get());
            return;
        }
        begin(ControlPanelState.Start.PUBLISH, "Publish");
    }

    /** Saves {@code title} as confluenceFolderTitle as the Profile window saves a key; a title as the file states it is not written. */
    private Optional<String> saveTheTitle(Path folder, String title) {
        Optional<ProfileEdit.Stated> inFile;
        try {
            inFile = Optional.ofNullable(ProfileRow.statedIn(folder).get(ProfileKey.CONFLUENCE_FOLDER_TITLE));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.of(unreadable.getMessage() == null ? ProfileEdit.FILE_NAME + " cannot be read"
                    : unreadable.getMessage());
        }
        Optional<String> provenance = ProfileEdit.provenanceToSave(ProfileKey.CONFLUENCE_FOLDER_TITLE, title, inFile);
        if (provenance.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> refused = ProfileEdit.refusalOfSave(ProfileKey.CONFLUENCE_FOLDER_TITLE, title,
                provenance.get(), inFile, Optional.empty());
        if (refused.isPresent()) {
            return refused;
        }
        return state.write(folder, ProfileEdit.FILE_NAME, current -> ProfileEdit.rewrite(current,
                ProfileKey.CONFLUENCE_FOLDER_TITLE, title, provenance.get()));
    }

    /** Stop is confirmed with the one sentence a stop is confirmed by (ADR-278 §6); the child is stopped only on the answer that goes on. */
    private void stopAfterConfirming() {
        if (dialogs.confirm("Stop the invocation", Child.STOP_CONFIRMATION, "Stop", "Keep it running")) {
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

    /** The warning of the button, and with a child running the sentence that says it is stopped first (ADR-390 §5). */
    private static final String SHUT_DOWN_WARNING = "Shutting down Docker stops Vespera's sidecars and then the Docker"
            + " engine, even if Vespera did not start it, and with the engine every other container running on it.";

    private void shutDownDockerAfterConfirming() {
        String sentence = SHUT_DOWN_WARNING + (state.childRuns()
                ? " The invocation running now is stopped first. " + Child.STOP_CONFIRMATION : "");
        if (!dialogs.confirm("Shut down Docker", sentence, "Shut down Docker", "Keep Docker running")) {
            return;
        }
        shuttingDown = true;
        shutDownDocker.setDisable(true);
        Thread.ofPlatform().daemon().name("the control panel shuts Docker down").start(() -> {
            String line;
            try {
                state.stop();
                line = state.shutDownDocker().line();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                line = "Docker was not shut down: interrupted.";
            } catch (RuntimeException failed) {
                line = "Docker was not shut down: " + failed;
            }
            String said = line;
            Platform.runLater(() -> {
                say(said);
                shuttingDown = false;
                shutDownDocker.setDisable(false);
                refresh();
            });
        });
    }

    /** Starts the Docker engine once, on a thread of its own, as the window opens; a refusal's line is shown (ADR-390 §5). */
    void startDocker() {
        Thread.ofPlatform().daemon().name("the control panel starts Docker").start(() -> {
            String refusal;
            try {
                DockerControl.Said started = state.startDocker();
                refusal = started.line();
            } catch (RuntimeException failed) {
                refusal = "Docker was not started: " + failed;
            }
            if (!refusal.isEmpty()) {
                // A refusal's line, or the line of a start that went through with one to say (ADR-421 §6).
                String line = refusal;
                Platform.runLater(() -> say(line));
            }
        });
    }

    /**
     * Where the operator closes the window and it goes on: on a thread of its own the child, where one runs, is stopped
     * and waited for, then the sidecars are stopped, and then {@code close} runs on the toolkit's thread. The engine is
     * not stopped; a line the stop gives is logged, the window being gone through the JDK's own logger, which writes to this process's console, as ADR-364 lets no class but the entry point name a stream (ADR-390 §5).
     */
    void stopTheChildAndTheSidecarsThen(Runnable close) {
        Thread.ofPlatform().daemon().name("the control panel closes").start(() -> {
            try {
                state.stop();
                DockerControl.Said stopped = state.stopTheSidecars();
                if (!stopped.done() && !stopped.line().isEmpty()) {
                    log.log(System.Logger.Level.WARNING, stopped.line());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException failed) {
                log.log(System.Logger.Level.WARNING, "the sidecars were not stopped: " + failed);
            } finally {
                Platform.runLater(close);
            }
        });
    }

    private void say(String line) {
        outcome.setText(line);
    }

    // ---- the sidebar

    private Node sidebarPane() {
        sidebar.setId("sidebar");
        openProfile.setId("open-profile");
        openProfile.setOnAction(event -> profileWindow.open());
        VBox profilePane = new VBox(openProfile);
        profilePane.getStyleClass().add("section");
        section("profile", "Profile", profilePane);
        section("preflight", "Preflight", preflight.pane());
        section("folders", "Folders", foldersPane());
        section("services", "Services", servicesPane());
        section("seed-folder", "Seed folder", seedPane());
        section("labels", "Labels", labelsPane());
        section("code-sets", "Code sets", codeSets.pane());
        section("command-options", "Command options", options.pane());
        section("reports", "Reports", reports.pane());
        sidebar.setExpandedPane(sections.get("folders"));
        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sidebar");
        scroll.setPrefWidth(SIDEBAR_WIDTH);
        HBox sidebarAndGrip = new HBox(scroll, grip("sidebar-grip", scroll, true, LEAST_SIDEBAR_WIDTH));
        return sidebarAndGrip;
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
            showModelsInUse();
            analysisReportsWindow.close();
            // the rows drawn for another working directory start as they first do (ADR-377 section 3)
            List.of(profileOpen, seedOpen, wantedOpen).forEach(ProfileRow.OpenKeys::forget);
            settleTheEditBeforeLoading();
            // the rows are the new directory's own, drawn before loadFiles() loads them (ADR-404 section 2)
            readNext();
            loadFiles();
            if (!sameFolder(Optional.ofNullable(childFolder), workingDirectory())) {
                // the child's ending is shown only until another working directory is chosen (ADR-417 §2)
                childIsStillTheLast = false;
            }
            showTheEnding();
        });
        VBox pane = new VBox(fieldBox(corpus, "Corpus root", true), workingDirectoryBox(working),
                fieldBox(deliverable, "Output folder", true));
        pane.getStyleClass().add("section");
        return pane;
    }

    // ---- the list of working directories (ADR-393 §4 to §6)

    /**
     * The list in the place of the working directory's field: the field stays, hidden, as the launch value
     * {@code vespera.working-dir} the row chosen is; the machine's folder, the first row, chosen is the value left empty.
     */
    private Node workingDirectoryBox(LaunchField working) {
        SelectableText.Line heading = SelectableText.line("Working directory");
        heading.getStyleClass().add("field-name");
        SelectableText.Wrapping hint = SelectableText.wrapping(LaunchValues.hintOf(working.property));
        hint.setId("hint-" + working.property);
        hint.getStyleClass().add("small");
        working.input.setVisible(false);
        working.input.setManaged(false);
        workingDirectoryList.setId("working-directories");
        workingDirectoryList.setPrefHeight(160);
        workingDirectoryList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Path folder, boolean empty) {
                super.updateItem(folder, empty);
                setText(empty || folder == null ? null : wordsOfRow(folder));
            }
        });
        workingDirectoryNew.setId("working-directory-new");
        workingDirectoryBrowse.setId("working-directory-browse");
        workingDirectoryRemove.setId("working-directory-remove");
        List<Path> rows = state.workingDirectories().rows();
        state.besideTheJarLines().forEach(line -> log.log(System.Logger.Level.WARNING, line));
        state.workingDirectoryLines().forEach(line -> log.log(System.Logger.Level.WARNING, line));
        state.sendWorkingDirectoryLinesTo(line -> log.log(System.Logger.Level.WARNING, line));
        workingDirectoryList.setItems(FXCollections.observableArrayList(rows));
        rows.forEach(this::readRow);
        selectTheRowOf(working.text());
        workingDirectoryList.getSelectionModel().selectedItemProperty().addListener((observed, was, now) -> {
            if (now != null && !choosing) {
                choosing = true;
                try {
                    working.input.setText(state.workingDirectories().isTheDefault(now) ? "" : now.toString());
                } finally {
                    choosing = false;
                }
                showTheListsButtons();
            }
        });
        working.input.textProperty().addListener((observed, was, now) -> {
            if (!choosing) {
                selectTheRowOf(now.strip());
            }
        });
        workingDirectoryNew.setOnAction(event -> addANewWorkingDirectory());
        workingDirectoryBrowse.setOnAction(event -> dialogs
                .folder("Choose the working directory", working.input.getText()).ifPresent(chosen -> {
                    Path folder = chosen.toAbsolutePath().normalize();
                    state.workingDirectories().add(folder, false);
                    drawTheRows(Optional.of(folder));
                }));
        workingDirectoryRemove.setOnAction(event -> {
            Path chosen = workingDirectoryList.getSelectionModel().getSelectedItem();
            if (chosen != null && !state.childRuns()) {
                state.workingDirectories().remove(chosen);
                rowsRead.remove(chosen);
                drawTheRows(Optional.of(state.workingDirectories().rows().getFirst()));
            }
        });
        showTheListsButtons();
        HBox buttons = row(workingDirectoryNew, workingDirectoryBrowse, workingDirectoryRemove);
        VBox box = tight(heading, working.input, workingDirectoryList, buttons, working.status, working.startingLine,
                hint);
        box.getStyleClass().add("field");
        return box;
    }

    /** What a row says, on one line: its name and the parts of ADR-393 §5. */
    private String wordsOfRow(Path folder) {
        WorkingDirectoryRow row = rowsRead.computeIfAbsent(folder, each -> WorkingDirectoryRow.read(each, ZoneId.systemDefault()));
        return (state.workingDirectories().isTheDefault(folder) ? row.name() + " (this machine's folder)" : row.name())
                + " \u2014 corpus root: " + row.corpusRoot() + "; last invocation: " + row.lastInvocation()
                + "; reached: " + row.legReached() + "; next: " + row.next();
    }

    private void readRow(Path folder) {
        rowsRead.put(folder, WorkingDirectoryRow.read(folder, ZoneId.systemDefault()));
    }

    /** Reads every row again, as the window's child ends (ADR-393 §5). */
    private void readTheRowsAgain() {
        List<Path> shown = List.copyOf(workingDirectoryList.getItems());
        rowsRead.clear();
        shown.forEach(this::readRow);
        workingDirectoryList.refresh();
    }

    /** Draws the rows as the state lists them, and chooses {@code chosen} where given, else the row chosen already. */
    private void drawTheRows(Optional<Path> chosen) {
        Path was = workingDirectoryList.getSelectionModel().getSelectedItem();
        List<Path> rows = state.workingDirectories().rows();
        chosen.ifPresent(this::readRow);
        choosing = true;
        try {
            workingDirectoryList.getItems().setAll(rows);
        } finally {
            choosing = false;
        }
        Path toChoose = chosen.orElse(was);
        workingDirectoryList.getSelectionModel().clearSelection();
        workingDirectoryList.getSelectionModel().select(rows.stream()
                .filter(each -> toChoose != null && ControlPanelState.namesTheSameFolder(each.toString(), toChoose.toString()))
                .findFirst().orElse(rows.getFirst()));
    }

    /** Chooses the row {@code text}, the launch value, names, adding one (not written) where it names a folder not listed. */
    private void selectTheRowOf(String text) {
        Optional<Path> named = text.isBlank() ? Optional.empty() : ControlPanelState.pathOf(text);
        List<Path> rows = state.workingDirectories().rows();
        Path row = named.flatMap(folder -> rows.stream()
                .filter(each -> ControlPanelState.namesTheSameFolder(each.toString(), folder.toString())).findFirst())
                .orElse(named.isEmpty() ? rows.getFirst() : null);
        boolean was = choosing;
        choosing = true;
        try {
            if (row == null) {
                Path folder = named.get().toAbsolutePath().normalize();
                state.workingDirectories().add(folder, false);
                readRow(folder);
                workingDirectoryList.getItems().setAll(state.workingDirectories().rows());
                row = folder;
            }
            Path toSelect = row;
            workingDirectoryList.getSelectionModel().select(workingDirectoryList.getItems().stream()
                    .filter(each -> ControlPanelState.namesTheSameFolder(each.toString(), toSelect.toString()))
                    .findFirst().orElse(null));
        } finally {
            choosing = was;
        }
        showTheListsButtons();
    }

    /** New\u2026 proposes ADR-393 \u00a72's name and adds the folder as a row, chosen; nothing makes the folder but a Save of the profile. */
    private void addANewWorkingDirectory() {
        if (state.childRuns()) {
            return;
        }
        Path chosen = workingDirectory().orElse(Path.of("").toAbsolutePath());
        Path parent = chosen.toAbsolutePath().getParent() == null ? chosen.toAbsolutePath()
                : chosen.toAbsolutePath().getParent();
        Path proposed = WorkingDirectoryName.proposed(parent, corpusRoot(), Instant.now(), ZoneId.systemDefault());
        dialogs.newWorkingDirectory(proposed).ifPresent(folder -> {
            state.workingDirectories().add(folder, true);
            drawTheRows(Optional.of(folder));
            say("The working directory " + folder + " is to be created by the first Save of the profile");
        });
    }

    /** The list cannot be changed while the window's own child runs, as Run cannot be pressed (ADR-393 \u00a74). */
    private void showTheListsButtons() {
        boolean runs = state.childRuns();
        Path chosen = workingDirectoryList.getSelectionModel().getSelectedItem();
        workingDirectoryList.setDisable(runs);
        workingDirectoryNew.setDisable(runs);
        workingDirectoryBrowse.setDisable(runs);
        workingDirectoryRemove.setDisable(runs || chosen == null || state.workingDirectories().isTheDefault(chosen));
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
                labelModelBox(model), namingModelPart());
        pane.getStyleClass().add("section");
        return pane;
    }

    /** The label model's field box, with the line saying the model in use beneath it while the field stands blank. */
    private Node labelModelBox(LaunchField model) {
        labelModel.setId("in-use-vespera.label.model");
        showLabelModelLine(model.text());
        model.input.textProperty().addListener((observed, was, now) -> showLabelModelLine(now));
        VBox box = (VBox) fieldBox(model, "Label model", false);
        box.getChildren().add(labelModel);
        return box;
    }

    /**
     * The heading <i>Naming model</i> and one line saying the model the working directory's file names and where it
     * came from. It is no field: the naming model enters the arrangement run's identity, so it is no launch value
     * (ADR-407 section 10).
     */
    private Node namingModelPart() {
        namingModel.setId("in-use-vespera.naming.model");
        SelectableText.Line heading = SelectableText.line("Naming model");
        heading.getStyleClass().add("field-name");
        VBox part = tight(heading, namingModel);
        part.getStyleClass().add("field");
        return part;
    }

    /** Reads the file again, for the working directory chosen now, and says the naming model and the label model it names. */
    private void showModelsInUse() {
        namingModel.setText(modelInUse("vespera.naming.model"));
        labelModel.setText(modelInUse("vespera.label.model"));
    }

    /** What the working directory's file says of the model {@code key} names, or that the jar has not said yet. */
    private String modelInUse(String key) {
        return state.effectiveValues(folders.workingDirectory()).flatMap(values -> values.named(key))
                .map(ProfileRow::modelInUseOf)
                .orElse("The jar has not said yet what it would use; run an invocation to see it.");
    }

    /** The label model's line is shown only while its field stands blank (ADR-334 section 2). */
    private void showLabelModelLine(String typed) {
        labelModel.setVisible(typed.isBlank());
        labelModel.setManaged(typed.isBlank());
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

    /** Takes the operator to where {@code chosen}, a key the Profile window does not edit, is edited. */
    private void pointTo(ProfileKey chosen) {
        if (chosen == ProfileKey.FOLDERS_NOT_WALKED) {
            tabs.getSelectionModel().select(inclusionsTab);
        } else {
            sidebar.setExpandedPane(sections.get(chosen == ProfileKey.SEED_FOLDER ? "seed-folder" : "code-sets"));
        }
    }

    private void openReport(String report) {
        analysisReportsWindow.open(report);
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

    /** Whether both name a folder and it is the same one, compared absolute and normalized, ignoring case on Windows. */
    private static boolean sameFolder(Optional<Path> loaded, Optional<Path> named) {
        if (loaded.isEmpty() || named.isEmpty()) {
            return false;
        }
        try {
            String was = loaded.get().toAbsolutePath().normalize().toString();
            String now = named.get().toAbsolutePath().normalize().toString();
            boolean windows = System.getProperty("os.name", "").startsWith("Windows");
            return windows ? was.equalsIgnoreCase(now) : was.equals(now);
        } catch (java.io.IOError | RuntimeException cannotBeHeld) {
            return false;
        }
    }

    /** Set while an answer of ADR-389 section 4 is being carried out, so that the loading it ends in asks nothing more. */
    private boolean asked;

    /**
     * The working directory changed under an edit unsaved (ADR-389 section 4): asks Save or Discard alone, no answer
     * counting as Save. Save writes into the working directory the form was loaded from; one refused drops the edit and
     * says the refusal, whole, in the outcome. The new working directory's files are loaded after, whatever happened.
     */
    private void settleTheEditBeforeLoading() {
        if (profileWindow == null || asked || !profile.unsaved()) {
            return;
        }
        if (sameFolder(profile.loadedFrom(), workingDirectory())) {
            // the same folder named another way is no change of folder: the loading asks its own three answers
            return;
        }
        String answer = dialogs.choose("The profile", "The profile has changes not saved in profile.yaml.",
                List.of("Save", "Discard")).orElse("Save");
        if (answer.equals("Discard")) {
            profile.discardNow();
        } else {
            ProfileRow.Saved saved = profile.saveWhereLoaded();
            if (!saved.written()) {
                outcome.setText(saved.line());
            }
        }
        asked = true;
    }

    /** Reads the working directory's files into the profile form, the seed folder, the code sets and the tree. */
    private void loadFiles() {
        if (seedRow == null) {
            return;
        }
        boolean settled = asked;
        asked = false;
        boolean drawTheProfile = true;
        if (!settled && profileWindow != null && profileWindow.isOpen() && profile.unsaved()) {
            String answer = dialogs.choose("The profile", "The profile has changes not saved in profile.yaml.",
                    List.of("Save", "Discard", "Cancel")).orElse("Cancel");
            if (answer.equals("Save")) {
                asked = true;
                ProfileRow.Saved saved;
                try {
                    saved = profile.saveNow();
                } finally {
                    asked = false;
                }
                if (saved.written()) {
                    return;
                }
                drawTheProfile = false;
            } else if (answer.equals("Discard")) {
                profile.discardNow();
            } else {
                drawTheProfile = false;
            }
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
        if (drawTheProfile) {
            profile.draw(stated, problem, notInForm, text, written);
        }
        seedRow.load(Optional.ofNullable(stated.get(ProfileKey.SEED_FOLDER)));
        seedRow.showReports(written);
        wanted.load(stated);
        wanted.showReports(written);
        if (drawTheProfile) {
            profile.showReports(written);
        }
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
        badge("profile", !wanted.isEmpty());
        profileWindow.titled(sections.get("profile").getText());
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
        state.workingDirectories().keepWhatWasMade();
        showTheListsButtons();
        Optional<Ending> ending = state.ending();
        boolean runs = runs(ending);
        ending.ifPresent(this::warnOfTheParentsTitle);
        ControlPanelState.Holders holders = state.holders(workingDirectory(), corpusRoot());
        boolean busy = runs || holders.any();
        run.setDisable(busy);
        label.setDisable(busy);
        labelAuto.setDisable(busy);
        publish.setDisable(busy);
        stop.setDisable(!runs);
        shutDownDocker.setDisable(shuttingDown);
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
            readTheRowsAgain();
            showModelsInUse();
            Map<String, Boolean> written = folders.writtenReports();
            profile.showReports(written);
            wanted.showReports(written);
            seedRow.showReports(written);
            reports.draw();
            // however the child ended (ADR-404 section 5)
            readNext();
        }
        readNextWhereAForeignHoldEnded(holders);
        showTheEnding(runs, ending, holders);
        preflight.draw();
        wasShown = wasShown || showing;
    }

    /**
     * Whether the child runs, by {@code ending}, the one look at it the window draws from: a look of its own asking
     * whether it runs, taken a moment before, could find running a child the look at its ending then finds ended, and
     * the tick draw it running beside its own ending (ADR-278 section 5).
     */
    private boolean runs(Optional<Ending> ending) {
        return ending.isEmpty() && state.childWasStarted();
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

    /**
     * Reads the file on the tick where a foreign hold seen on the working directory shown is gone (ADR-404 section 5).
     */
    private void readNextWhereAForeignHoldEnded(ControlPanelState.Holders holders) {
        Optional<Path> shown = workingDirectory();
        // the window's own child writes its pid into the holder line: its hold is no foreign one (ADR-404 section 5)
        Optional<Long> ownChild = state.watchedChild().map(ControlPanelState.Watched::pid);
        boolean foreign = holders.workingDirectory().isPresent()
                && state.watchedHolder(shown).filter(held -> !ownChild.equals(Optional.of(held.pid()))).isPresent();
        if (foreign && shown.isPresent()) {
            foreignHoldSeenOn = shown;
            // another process held the working directory since: the child's ending is no longer its last (ADR-417 §2)
            childIsStillTheLast = false;
            return;
        }
        Optional<Path> seen = foreignHoldSeenOn;
        foreignHoldSeenOn = Optional.empty();
        if (seen.isPresent() && shown.isPresent()
                && ControlPanelState.namesTheSameFolder(seen.get().toString(), shown.get().toString())) {
            readNext();
        }
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

    // ---- the ending panel (ADR-417)

    /** Reads the ending again where the working directory chosen changed, outside the clock's tick. */
    private void showTheEnding() {
        Optional<Path> folder = workingDirectory();
        Optional<Ending> ending = state.ending();
        showTheEnding(runs(ending), ending, state.holders(folder, corpusRoot()));
    }

    /**
     * Shows the last ending of the working directory chosen beneath the output, and the report it is read from in the
     * Report tab (ADR-417 §2): the window's own child's once it has ended, else the ending {@code report.json} records.
     * Nothing shows while an invocation runs, the window's own or another process's. It reads again only where what it
     * is read for changed: the window opening, another working directory chosen, a child ended, or a hold gone.
     */
    private void showTheEnding(boolean runs, Optional<Ending> ending, ControlPanelState.Holders holders) {
        if (ending.isPresent() && ending.get() != noticedEnding) {
            noticedEnding = ending.get();
            noticedAt = Instant.now();
        }
        Optional<Path> folder = workingDirectory();
        Optional<ControlPanelState.Watched> watchedChild = state.watchedChild();
        if (watchedChild.isPresent() && watchedChild.get().pid() != childSeenPid) {
            // a child this window started, seen as it runs or as it has ended: its ending is the last of the working directory now chosen
            childSeenPid = watchedChild.get().pid();
            childFolder = folder.orElse(null);
            childIsStillTheLast = true;
        }
        boolean cleared = runs || holders.workingDirectory().isPresent() || folder.isEmpty();
        Ending own = !cleared && ending.isPresent() && childIsStillTheLast
                && sameFolder(Optional.ofNullable(childFolder), folder) ? ending.get() : null;
        String readFor = cleared ? null : folder.get().toAbsolutePath().normalize().toString();
        if (cleared == endingCleared && own == endingReadOf && Objects.equals(readFor, endingReadFor)) {
            return;
        }
        if (cleared) {
            endingCleared = true;
            endingReadOf = own;
            endingReadFor = readFor;
            takeTheEndingPanelAway();
            tabDrawnForTheEndingPanel = false;
            return;
        }
        Optional<EndingPanel.Said> said;
        if (own != null) {
            Optional<InvocationReportFile.Report> report = state.watchedChild()
                    .flatMap(child -> theChildsOwnReport(folder.get(), child));
            said = Optional.of(EndingPanel.ofTheChild(own, report, noticedAt, ZoneId.systemDefault()));
            tabDrawnForTheEndingPanel = false;
        } else {
            Optional<EndingPanel.Said> earlier;
            try {
                Optional<InvocationReportFile.Report> report = theReportAtTheTop(folder.get());
                earlier = report.flatMap(each -> EndingPanel.ofAnEarlier(each, ZoneId.systemDefault()));
                if (earlier.isPresent()) {
                    live.showsAnEarlier(report.get());
                }
            } catch (RuntimeException unreadable) {
                // a report that cannot be read or drawn is no panel, and must not leave the last folder's ending standing
                earlier = Optional.empty();
            }
            said = earlier;
            if (said.isPresent()) {
                tabDrawnForTheEndingPanel = true;
            } else if (tabDrawnForTheEndingPanel) {
                live.says(LiveReport.WAITING);
                tabDrawnForTheEndingPanel = false;
            }
        }
        // cached only once the reading is worked out, so a throw above cannot leave a stale reading standing
        endingCleared = false;
        endingReadOf = own;
        endingReadFor = readFor;
        if (said.isEmpty()) {
            takeTheEndingPanelAway();
            return;
        }
        endingPanel.shows(said.get());
        if (!outputAndEnding.getItems().contains(endingPanel.node())) {
            outputAndEnding.getItems().add(endingPanel.node());
            outputAndEnding.setDividerPositions(endingDivider);
        }
    }

    private void openTheProfileWindow() {
        profileWindow.open();
    }

    /** Open report selects the Report tab, which draws the report of the ending the panel shows, and opens nothing outside the window (ADR-417 §4). */
    private void selectTheReportTab() {
        tabs.getSelectionModel().select(live.tab());
    }

    private void takeTheEndingPanelAway() {
        if (outputAndEnding.getItems().contains(endingPanel.node())) {
            endingDivider = outputAndEnding.getDividerPositions()[0];
            outputAndEnding.getItems().remove(endingPanel.node());
        }
    }

    /** The working directory's {@code report.json}, empty where it is not there, cannot be read, is not that JSON or is of a format not read. */
    private static Optional<InvocationReportFile.Report> theReportAtTheTop(Path folder) {
        try {
            Path file = folder.resolve(InvocationReportFile.FILE_NAME);
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            InvocationReportFile.Report report = InvocationReportFile.read(Files.readString(file, StandardCharsets.UTF_8));
            return isThatJson(report) ? Optional.of(report) : Optional.empty();
        } catch (IOException | IllegalArgumentException | UncheckedIOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Whether {@code report}, read as of the format, is that JSON as far as the window draws it (ADR-362 §8): an
     * invocation with its start and its last write, every step named and every fault typed.
     */
    private static boolean isThatJson(InvocationReportFile.Report report) {
        return report.invocation() != null
                && report.invocation().started() != null
                && report.invocation().writtenAt() != null
                && (report.steps() == null || report.steps().stream().allMatch(each -> each != null && each.name() != null))
                && (report.faults() == null || report.faults().stream().allMatch(each -> each != null && each.type() != null));
    }

    /** The report at the top of {@code folder} where it is {@code child}'s own, as ADR-362 §8 accepts it. */
    private static Optional<InvocationReportFile.Report> theChildsOwnReport(Path folder, ControlPanelState.Watched child) {
        return theReportAtTheTop(folder).filter(report -> report.invocation() != null
                && report.invocation().pid() == child.pid()
                && !report.invocation().started().isBefore(child.notBefore().truncatedTo(ChronoUnit.MILLIS)));
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
            if (!isThatJson(report)) {
                return false;
            }
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
            // a child that ended before its last write leaves no step of its report running (ADR-387 section 1)
            steps.show(report, childEnded && endingOfItsChild.isPresent() && !report.ended()
                    ? endingOfItsChild.get().howItEnded() == HowItEnded.STOPPED ? StepsChart.STOPPED : StepsChart.FAILED
                    : null);
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

    /** Whether the window may close, as far as an unsaved edit of the profile is concerned: asked over the window (ADR-389 §4). */
    boolean profileAllowsClosing() {
        return profileWindow.mayClose(dialogs);
    }

    /** Where the window is closed: the clock stops with it. */
    void closed() {
        timeline.stop();
        profileWindow.close();
        analysisReportsWindow.close();
    }
}
