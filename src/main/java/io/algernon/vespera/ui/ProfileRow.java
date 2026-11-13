package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One value of the profile in the window (ADR-377 §3): one line of a toggle, the key's title over the key in brackets,
 * and a control that constrains the value by its kind; behind the toggle the short description, the value in use, what
 * a change redoes and the reports that inform it. The title's tooltip holds the key's explanation and the provenance the
 * file states (§1); no field holds a provenance. The row holds what the file stated as it was loaded, and a save
 * compares the form with it by {@link ProfileEdit#provenanceToSave} and writes by {@link ProfileEdit#rewrite}, never a
 * key left as the file states it (§2).
 */
final class ProfileRow {

    /** What a save of some rows came to: the one line to show, and whether the file was written. */
    record Saved(String line, boolean written) {
    }

    /**
     * The keys whose rows are open in one list of rows (ADR-377 §3): a row drawn or loaded again is open where its key
     * is among them. In a list whose rows start open, a key met for the first time is put among them.
     */
    static final class OpenKeys {

        private final boolean openAtFirst;
        private final Set<ProfileKey> open = EnumSet.noneOf(ProfileKey.class);
        private final Set<ProfileKey> met = EnumSet.noneOf(ProfileKey.class);

        OpenKeys(boolean openAtFirst) {
            this.openAtFirst = openAtFirst;
        }

        boolean isOpen(ProfileKey key) {
            if (met.add(key) && openAtFirst) {
                open.add(key);
            }
            return open.contains(key);
        }

        void set(ProfileKey key, boolean opened) {
            met.add(key);
            if (opened) {
                open.add(key);
            } else {
                open.remove(key);
            }
        }

        /** Forgets every key, as the working directory changes. */
        void forget() {
            open.clear();
            met.clear();
        }
    }

    /**
     * The column the titles of one list of rows take (ADR-377 §3): as wide as the widest title and key among them, and
     * at most {@link #MOST_OF_A_ROW} of a row's width, so that every control of the list starts at one edge.
     */
    static final class TitleColumn {

        private static final double MOST_OF_A_ROW = 0.45;

        private final List<Titles> members = new ArrayList<>();

        /** Forgets the rows, as the list is drawn again. */
        void clear() {
            members.clear();
        }

        private void add(Titles titles) {
            members.forEach(Region::requestLayout);
            members.add(titles);
        }

        private double width(Region row) {
            double widest = members.stream().mapToDouble(Titles::natural).max().orElse(0);
            return row.getWidth() > 0 ? Math.min(widest, row.getWidth() * MOST_OF_A_ROW) : widest;
        }
    }

    /** A row's title over its key, as wide as its list's column. */
    private static final class Titles extends VBox {

        private final TitleColumn column;
        private final Region row;

        Titles(TitleColumn column, Region row, Node... children) {
            super(children);
            this.column = column;
            this.row = row;
            row.widthProperty().addListener((observed, was, now) -> requestLayout());
            column.add(this);
        }

        private double natural() {
            return super.computePrefWidth(-1);
        }

        @Override
        protected double computePrefWidth(double height) {
            return column.width(row);
        }

        @Override
        protected double computeMinWidth(double height) {
            return computePrefWidth(height);
        }

        @Override
        protected double computeMaxWidth(double height) {
            return computePrefWidth(height);
        }
    }

    /** The toggle's tooltip while its row is closed, and while it is open. */
    private static final String SHOW = "Show what this does, what a change redoes and its reports";
    private static final String HIDE = "Hide them";

    private static final double WHOLE_STEP = 1;
    private static final double SHARE_STEP = 0.05;
    private static final double MOST = 1e15;

    private final ProfileKey key;
    private final String idPrefix;
    private final OpenKeys openKeys;
    private final VBox node = new VBox();
    private final ToggleButton more = new ToggleButton();
    private final SelectableText.Line title;
    private final VBox details = new VBox();
    private final HBox links = new HBox();
    private Consumer<String> opensReport;
    private Supplier<String> value = () -> "";
    private Consumer<String> setValue = text -> {
    };
    private Optional<ProfileEdit.Stated> loaded = Optional.empty();

    /**
     * @param idPrefix put before the key in the ids of the row's controls, so that a key shown twice has distinct ones
     * @param models the models Ollama last reported, offered for a model
     * @param beneath what lies beneath the profile for this key, where the jar says so (ADR-330 §1)
     * @param dialogs how a folder is asked for
     * @param opensSection what a key edited in a section of the sidebar links to, or null where it is edited here
     * @param openKeys the keys whose rows are open in the row's list
     * @param column the column the titles of the row's list take
     */
    ProfileRow(ProfileKey key, String idPrefix, List<String> models, Optional<EffectiveValues.Value> beneath,
            Dialogs dialogs, Consumer<ProfileKey> opensSection, Consumer<String> opensReport,
            Map<String, Boolean> reportsWritten, OpenKeys openKeys, TitleColumn column) {
        this.key = key;
        this.idPrefix = idPrefix;
        this.openKeys = openKeys;
        String id = idPrefix + key.key();
        node.getStyleClass().add("profile-row");
        node.setId(idPrefix + "row-" + key.key());
        more.setId(id + ".more");
        more.getStyleClass().add("more");
        more.selectedProperty().addListener((observed, was, now) -> {
            showOpen(now);
            openKeys.set(key, now);
        });
        title = SelectableText.line(key.title());
        title.setId(id + ".title");
        title.getStyleClass().add("key-name");
        title.setTooltip(new Tooltip(key.tooltip()));
        SelectableText.Line small = SelectableText.line("(" + key.key() + ")");
        small.setId(id + ".key");
        small.getStyleClass().add("small");
        Titles titles = new Titles(column, node, title, small);
        Node control;
        if (key == ProfileKey.CODE_SET_FOLDERS
                || ((key == ProfileKey.SEED_FOLDER || key == ProfileKey.FOLDERS_NOT_WALKED) && opensSection != null)) {
            SelectableText.Line shown = SelectableText.line("");
            shown.setId(id);
            Hyperlink opens = new Hyperlink(key == ProfileKey.FOLDERS_NOT_WALKED ? "Edit it in the Inclusions tab"
                    : "Edit it in its section");
            opens.setOnAction(event -> opensSection.accept(key));
            value = shown::getText;
            setValue = shown::setText;
            control = spaced(shown, opens);
        } else {
            control = controlFor(models, dialogs);
        }
        HBox.setHgrow(control, Priority.ALWAYS);
        if (control instanceof Control growing) {
            growing.setMaxWidth(Double.MAX_VALUE);
        }
        node.getChildren().add(spaced(more, titles, control));

        details.setId(id + ".details");
        details.getStyleClass().add("details");
        SelectableText.Wrapping about = SelectableText.wrapping(key.shortDescription());
        about.setId(id + ".short");
        about.getStyleClass().add("dim");
        details.getChildren().add(about);
        beneath.ifPresent(said -> {
            SelectableText.Line inUse = SelectableText.line(inUseOf(said));
            inUse.setId(id + ".in-use");
            inUse.getStyleClass().add("small");
            details.getChildren().add(inUse);
        });
        SelectableText.Wrapping cost = SelectableText.wrapping(costOf(key));
        cost.setId(id + ".cost");
        cost.getStyleClass().add("small");
        details.getChildren().add(cost);
        for (String warning : warningsOf(key)) {
            SelectableText.Wrapping line = SelectableText.wrapping(warning);
            line.setId(id + "." + warningIdOf(warning));
            line.getStyleClass().add("small");
            details.getChildren().add(line);
        }
        this.opensReport = opensReport;
        if (!Reports.OF_A_KEY.getOrDefault(key.key(), List.of()).isEmpty()) {
            links.setId(id + ".reports");
            links.getStyleClass().addAll("spaced-row", "reports-of-a-key");
            details.getChildren().add(links);
            showReports(reportsWritten);
        }
        node.getChildren().add(details);
        showOpen(false);
    }

    /** The last part of a warning's id, by the words it opens with (ADR-377 §3). */
    private static String warningIdOf(String warning) {
        return warning.startsWith("Approval:") ? "approval" : warning.startsWith("Answers:") ? "answers" : "new-sample";
    }

    private void showOpen(boolean open) {
        more.setText(open ? "▾" : "▸");
        more.setTooltip(new Tooltip(open ? HIDE : SHOW));
        details.setVisible(open);
        details.setManaged(open);
    }

    /**
     * Draws the key's reports, {@code <prefix><key>.report-<name>}, each a link reading its title that opens its native
     * view where the working directory holds its JSON, and its title in grey, not written yet, where it does not
     * (ADR-377 §5, ADR-362 §10).
     */
    void showReports(Map<String, Boolean> written) {
        links.getChildren().setAll(SelectableText.line("Reports:"));
        for (String report : Reports.OF_A_KEY.getOrDefault(key.key(), List.of())) {
            String id = idPrefix + key.key() + ".report-" + report;
            String title = Reports.TITLES.get(report);
            if (written.getOrDefault(report, false)) {
                Hyperlink link = new Hyperlink(title);
                link.setId(id);
                link.setOnAction(event -> opensReport.accept(report));
                links.getChildren().add(link);
            } else {
                SelectableText.Line grey = SelectableText.line(title + " (not written yet)");
                grey.setId(id);
                grey.getStyleClass().add("grey");
                links.getChildren().add(grey);
            }
        }
    }

    /** Offers {@code text} in the control for the operator to confirm, leaving what the file stated as it was loaded. */
    void offer(String text) {
        setValue.accept(text);
    }

    /** A row of nodes spaced by the stylesheet. */
    private static HBox spaced(Node... children) {
        HBox box = new HBox(children);
        box.getStyleClass().add("spaced-row");
        return box;
    }

    Node node() {
        return node;
    }

    ProfileKey key() {
        return key;
    }

    /** The value the control holds now, as text. */
    String value() {
        return value.get();
    }

    /**
     * Fills the control from what the file states for the key now, puts the provenance it states in the title's
     * tooltip, and opens or closes the row as its list holds its key.
     */
    void load(Optional<ProfileEdit.Stated> inFile) {
        loaded = inFile;
        setValue.accept(inFile.map(ProfileEdit.Stated::value).orElse(""));
        title.getTooltip().setText(tooltipOf(key, inFile));
        more.setSelected(openKeys.isOpen(key));
        showOpen(more.isSelected());
    }

    /**
     * A key's tooltip text, then, where the file states a provenance that is not blank, an empty line and where the
     * value came from (ADR-377 §1).
     */
    static String tooltipOf(ProfileKey key, Optional<ProfileEdit.Stated> inFile) {
        return key.tooltip() + ProfileEdit.provenanceStated(inFile)
                .map(stated -> "\n\nWhere this value came from: " + stated).orElse("");
    }

    /** Puts the form back to the file as last loaded. */
    void reset() {
        load(loaded);
    }

    /** The provenance to write for this row by ADR-377 §2's table; empty where the key is not written. */
    Optional<String> provenanceToSave() {
        return ProfileEdit.provenanceToSave(key, value(), loaded);
    }

    /** The digest of what the form was loaded from, which a save checks the file against (ADR-330 §5). */
    String drawn() {
        return ProfileEdit.drawnOf(key, loaded.map(ProfileEdit.Stated::value).orElse(null),
                loaded.map(ProfileEdit.Stated::provenance).orElse(null));
    }

    // ---- the save of some rows

    /** What the profile file of {@code folder} states for each key, empty where it is not there. */
    static Map<ProfileKey, ProfileEdit.Stated> statedIn(Path folder) throws IOException {
        Path file = folder.resolve(ProfileEdit.FILE_NAME);
        return Files.isRegularFile(file)
                ? ProfileEdit.read(Files.readString(file, StandardCharsets.UTF_8)) : Map.of();
    }

    /**
     * Saves every row ADR-377 §2 says to write, under ADR-295's lock and the checks of ADR-330 §5: all refused before any
     * is written, the first refusal said.
     */
    static Saved save(ControlPanelState state, Optional<Path> folder, List<ProfileRow> rows) {
        if (folder.isEmpty()) {
            return new Saved("name the working directory first: profile.yaml lies in it", false);
        }
        Map<ProfileKey, ProfileEdit.Stated> fresh;
        try {
            fresh = statedIn(folder.get());
        } catch (IOException | IllegalArgumentException unreadable) {
            return new Saved(unreadable.getMessage() == null ? ProfileEdit.FILE_NAME + " cannot be read"
                    : unreadable.getMessage(), false);
        }
        List<ProfileRow> written = new ArrayList<>();
        List<String> provenances = new ArrayList<>();
        for (ProfileRow row : rows) {
            Optional<String> provenance = row.provenanceToSave();
            if (provenance.isEmpty()) {
                continue;
            }
            Optional<String> refusal = ProfileEdit.refusalOfSave(row.key, row.value(), provenance.get(),
                    Optional.ofNullable(fresh.get(row.key)), Optional.of(row.drawn()));
            if (refusal.isPresent()) {
                return new Saved(refusal.get(), false);
            }
            written.add(row);
            provenances.add(provenance.get());
        }
        if (written.isEmpty()) {
            return new Saved("nothing was written: every value is as profile.yaml states it", false);
        }
        Optional<String> refusal = state.write(folder.get(), ProfileEdit.FILE_NAME, current -> {
            Optional<String> text = current;
            for (int i = 0; i < written.size(); i++) {
                text = Optional.of(ProfileEdit.rewrite(text, written.get(i).key, written.get(i).value(), provenances.get(i)));
            }
            return text.orElseThrow();
        });
        if (refusal.isPresent()) {
            return new Saved(refusal.get(), false);
        }
        return new Saved(written.stream().map(row -> row.key.key()).collect(Collectors.joining(", ")) + " saved in "
                + folder.get().resolve(ProfileEdit.FILE_NAME), true);
    }

    // ---- the controls

    private Node controlFor(List<String> models, Dialogs dialogs) {
        return switch (key.kind()) {
            case NUMBER -> numberControl();
            case NAME_ENDINGS, LANGUAGES -> listControl();
            case FOLDERS, CORPUS_FOLDERS -> SelectableText.line("");
            case TEXT -> switch (key) {
                case EMBEDDING_MODEL, GENERATION_MODEL -> choice(models);
                case ARRANGEMENT_APPROVED -> choice(List.of("auto"));
                case DELIVERABLE_LANGUAGE -> languageControl();
                case MODELS_RUN_ON -> whereTheModelsRunControl();
                case SEED_FOLDER -> folderControl(dialogs);
                default -> textControl();
            };
        };
    }

    private Node textControl() {
        TextField field = new TextField();
        field.setId(idPrefix + key.key());
        field.setPromptText(key.example());
        value = field::getText;
        setValue = field::setText;
        return field;
    }

    private Node folderControl(Dialogs dialogs) {
        TextField field = new TextField();
        field.setId(idPrefix + key.key());
        field.setPromptText(key.example());
        value = field::getText;
        setValue = field::setText;
        Button browse = new Button("Browse");
        browse.setId(idPrefix + key.key() + ".browse");
        browse.setOnAction(event -> dialogs.folder("Choose the folder of documents you know are relevant",
                field.getText()).ifPresent(chosen -> field.setText(chosen.toString())));
        HBox.setHgrow(field, Priority.ALWAYS);
        return spaced(field, browse);
    }

    private Node choice(List<String> offered) {
        ComboBox<String> box = new ComboBox<>();
        box.setId(idPrefix + key.key());
        box.setEditable(true);
        box.getItems().setAll(offered);
        box.getEditor().setPromptText(key.example());
        value = () -> box.getEditor().getText();
        setValue = text -> box.getEditor().setText(text);
        return box;
    }

    /**
     * Where the models run, shown and not edited (ADR-384 §7): a disabled choice of the two values, in a holder that is
     * not disabled and carries the tooltip, since JavaFX shows none over a disabled control. It shows the value the file
     * states, as written, and {@code local} where it states none.
     */
    private Node whereTheModelsRunControl() {
        ComboBox<String> box = new ComboBox<>();
        box.setId(idPrefix + key.key());
        box.getItems().setAll("local", "cloud");
        box.setDisable(true);
        value = () -> box.getValue() == null ? "" : box.getValue();
        setValue = text -> {
            String shown = text.isBlank() ? "local" : text.strip();
            if (!box.getItems().contains(shown)) {
                box.getItems().add(shown);
            }
            box.setValue(shown);
        };
        HBox holder = new HBox(box);
        holder.setId(idPrefix + key.key() + ".holder");
        Tooltip.install(holder, new Tooltip("Cloud is not supported yet."));
        return holder;
    }

    private Node languageControl() {
        ComboBox<String> box = new ComboBox<>();
        box.setId(idPrefix + key.key());
        box.getItems().setAll("", "en", "it");
        value = () -> box.getValue() == null ? "" : box.getValue();
        setValue = text -> {
            if (!box.getItems().contains(text)) {
                box.getItems().add(text);
            }
            box.setValue(text);
        };
        return box;
    }

    /**
     * A list value as tags (ADR-377 §7): a tag for each item, in order, each with its remove button, then one box where
     * Enter adds each piece typed not already a tag, and Backspace, while it is empty, removes the last tag. The value is
     * the tags' items joined by one space, then the pieces still typed in the box, so that a Save loses nothing typed.
     */
    private Node listControl() {
        String id = idPrefix + key.key();
        FlowPane tags = new FlowPane();
        tags.setId(id);
        tags.getStyleClass().add("tags");
        TextField box = new TextField();
        box.setId(id + ".add");
        box.setPromptText("e.g. " + ProfileEdit.piecesOf(key.example()).getFirst() + ", then Enter");
        List<String> items = new ArrayList<>();
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            List<Node> children = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                int at = i;
                String item = items.get(at);
                SelectableText.Line text = SelectableText.line(item);
                Button remove = new Button("×");
                remove.setId(id + ".remove-" + (at + 1));
                remove.getStyleClass().add("remove");
                remove.setTooltip(new Tooltip("Remove " + item));
                remove.setOnAction(event -> {
                    items.remove(at);
                    draw[0].run();
                    box.requestFocus();
                });
                remove.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ENTER) {
                        remove.fire();
                        event.consume();
                    }
                });
                HBox tag = new HBox(text, remove);
                tag.setId(id + ".tag-" + (i + 1));
                tag.getStyleClass().add("tag");
                children.add(tag);
            }
            children.add(box);
            tags.getChildren().setAll(children);
        };
        box.setOnAction(event -> {
            for (String piece : ProfileEdit.piecesOf(box.getText())) {
                if (!items.contains(piece)) {
                    items.add(piece);
                }
            }
            box.clear();
            draw[0].run();
        });
        box.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.BACK_SPACE && box.getText().isEmpty() && !items.isEmpty()) {
                items.removeLast();
                draw[0].run();
                box.requestFocus();
                event.consume();
            }
        });
        value = () -> {
            List<String> all = new ArrayList<>(items);
            all.addAll(ProfileEdit.piecesOf(box.getText()));
            return String.join(" ", all);
        };
        setValue = text -> {
            items.clear();
            items.addAll(ProfileEdit.piecesOf(text == null ? "" : text));
            box.clear();
            draw[0].run();
        };
        draw[0].run();
        return tags;
    }

    private Node numberControl() {
        boolean whole = switch (key) {
            case DEGENERATE_OUTPUT_WORD_FLOOR, EXTRACTION_ATTEMPT, GENERATION_CONTEXT_WINDOW, CLUSTER_PAGE_BYTE_CEILING ->
                true;
            default -> false;
        };
        double smallest = switch (key) {
            case DEGENERATE_OUTPUT_WORD_FLOOR -> 0;
            case EXTRACTION_ATTEMPT, CLUSTER_PAGE_BYTE_CEILING -> 1;
            case GENERATION_CONTEXT_WINDOW -> 1282;
            case CLUSTER_RESOLUTION -> 0.01;
            default -> -MOST;
        };
        double largest = whole && key != ProfileKey.CLUSTER_PAGE_BYTE_CEILING ? Integer.MAX_VALUE : MOST;
        double example = parse(key.example()).orElse(Math.max(smallest, 0));
        SpinnerValueFactory.DoubleSpinnerValueFactory factory = new SpinnerValueFactory.DoubleSpinnerValueFactory(
                smallest, largest, Math.max(smallest, Math.min(largest, example)), whole ? WHOLE_STEP : SHARE_STEP);
        Spinner<Double> spinner = new Spinner<>(factory);
        spinner.setEditable(true);
        spinner.setId(idPrefix + key.key());
        CheckBox set = new CheckBox("set");
        set.setId(idPrefix + key.key() + ".set");
        spinner.disableProperty().bind(set.selectedProperty().not());
        value = () -> set.isSelected() ? spinner.getEditor().getText().strip() : "";
        setValue = text -> {
            boolean named = !text.isBlank();
            set.setSelected(named);
            parse(text).ifPresent(number -> factory.setValue(Math.max(smallest, Math.min(largest, number))));
            spinner.getEditor().setText(named ? text.strip() : formatted(factory.getValue(), whole));
        };
        return spaced(set, spinner);
    }

    private static Optional<Double> parse(String text) {
        try {
            double number = Double.parseDouble(text.strip());
            return Double.isFinite(number) ? Optional.of(number) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static String formatted(double number, boolean whole) {
        return whole || number == Math.rint(number) && Math.abs(number) < MOST
                ? Long.toString((long) number) : Double.toString(number);
    }

    // ---- words

    /** The words §4 of ADR-377 gives each source of a value in use. */
    private static final Map<String, String> SOURCES_IN_WORDS = Map.of(
            "shipped", "Vespera's settings",
            "local-file", "your local settings file",
            "command-line", "the command that started Vespera",
            "environment", "this computer's environment",
            "code", "Vespera's built-in value");

    /** What a change of the key redoes and what it keeps, in one sentence, by the earliest work it redoes (ADR-377 §4). */
    static String costOf(ProfileKey key) {
        if (key == ProfileKey.CLUSTER_PAGE_BYTE_CEILING) {
            return "Changing it rewrites only the group pages (a split group's pictures are read again); everything"
                    + " else is kept.";
        }
        return key.earliestRun().map(run -> switch (run) {
            case BYTE_LEVEL_REDUCTION -> "Changing it redoes everything from sorting your files to writing the pages;"
                    + " text already read out of your files is kept.";
            case EXTRACTION -> "Changing it redoes everything after reading the text out of your files; the text"
                    + " already read is kept.";
            case CONTENT_REDUNDANCY -> "Changing it redoes the search for documents that say the same thing, the"
                    + " groups and the pages; your files are not read again.";
            case SEED_MEASUREMENT -> "Changing it redoes everything from reading your seed documents to writing the"
                    + " pages; your other files are not read again.";
            case EMBEDDING_SCORING -> "Changing it redoes the measuring of closeness, the grouping and the writing of"
                    + " the pages; your files are not read again.";
            case GENERATION -> "Changing it redoes only the writing of the pages; everything before it is kept.";
            case CONTENT_CENSUS, ARRANGEMENT -> throw new IllegalStateException(
                    key.key() + " enters first a run the cost lines give no line: " + run.stageName());
        }).orElse("Changing it redoes no work; it takes effect the next time you press Publish.");
    }

    /** The one-line warnings a change of the key brings, in ADR-377 §4's order, none where its cost brings none. */
    static List<String> warningsOf(ProfileKey key) {
        ProfileKey.Cost cost = key.cost();
        List<String> warnings = new ArrayList<>();
        if (cost.approvalCloses()) {
            warnings.add("Approval: a grouping you approved must be approved again in the Approved grouping row.");
        }
        if (cost.labelsRefused()) {
            warnings.add("Answers: press Record my answers first; after the change the old answers file is refused.");
        } else if (cost.sampleDrawnAgain()) {
            warnings.add("Answers: press Record my answers first, or unsaved answers in relevance-labels.yaml are lost.");
        }
        if (cost.sampleDrawnAgain()) {
            warnings.add("New sample: Vespera picks new documents for you to judge in relevance-labels.yaml.");
        }
        return warnings;
    }

    /** The line of the value in use where the profile names none, its source in plain words (ADR-377 §4). */
    static String inUseOf(EffectiveValues.Value said) {
        String source = SOURCES_IN_WORDS.get(said.source());
        return "Left empty, Vespera uses " + said.value() + (source == null ? "" : " (" + source + ")") + ".";
    }
}
