package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * One value of the profile in the window (ADR-344 §1, ADR-343): its title, its key beneath it in small type, its short
 * description, the value in use, what a change redoes and its warnings, as ADR-377 §4 words them; a control that
 * constrains the value by its kind; the reports that inform it; and the provenance saved with it, filled in. The row
 * holds what the file stated as it was loaded, and a save compares the form with it by {@link
 * ProfileEdit#provenanceToSave} and writes by {@link ProfileEdit#rewrite}, never a key left as it was shown.
 */
final class ProfileRow {

    /** What a save of some rows came to: the one line to show, and whether the file was written. */
    record Saved(String line, boolean written) {
    }

    private static final double WHOLE_STEP = 1;
    private static final double SHARE_STEP = 0.05;
    private static final double MOST = 1e15;

    private final ProfileKey key;
    private final String idPrefix;
    private final VBox node = new VBox();
    private final TextField provenance = new TextField();
    private final SelectableText.Wrapping inUse = SelectableText.wrapping("");
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
     */
    ProfileRow(ProfileKey key, String idPrefix, List<String> models, Optional<EffectiveValues.Value> beneath,
            Dialogs dialogs, Consumer<ProfileKey> opensSection, Consumer<String> opensReport,
            Map<String, Boolean> reportsWritten) {
        this.key = key;
        this.idPrefix = idPrefix;
        node.getStyleClass().add("profile-row");
        node.setId(idPrefix + "row-" + key.key());
        SelectableText.Line title = SelectableText.line(key.title());
        title.getStyleClass().add("key-name");
        SelectableText.Line small = SelectableText.line(key.key());
        small.getStyleClass().add("small");
        SelectableText.Wrapping about = SelectableText.wrapping(key.shortDescription());
        about.getStyleClass().add("dim");
        node.getChildren().addAll(title, small, about);
        if (key == ProfileKey.CODE_SET_FOLDERS
                || ((key == ProfileKey.SEED_FOLDER || key == ProfileKey.FOLDERS_NOT_WALKED) && opensSection != null)) {
            SelectableText.Line shown = SelectableText.line("");
            shown.setId(idPrefix + key.key());
            Hyperlink opens = new Hyperlink(key == ProfileKey.FOLDERS_NOT_WALKED ? "Edit it in the Inclusions tab"
                    : "Edit it in its section");
            opens.setOnAction(event -> opensSection.accept(key));
            value = shown::getText;
            setValue = shown::setText;
            node.getChildren().addAll(spaced(shown, opens));
        } else {
            node.getChildren().add(controlFor(models, dialogs));
        }
        beneath.ifPresent(said -> inUse.setText(inUseOf(said)));
        inUse.getStyleClass().add("small");
        node.getChildren().add(inUse);
        SelectableText.Wrapping cost = SelectableText.wrapping(costOf(key));
        cost.getStyleClass().add("small");
        node.getChildren().add(cost);
        for (String warning : warningsOf(key)) {
            SelectableText.Wrapping line = SelectableText.wrapping(warning);
            line.getStyleClass().add("small");
            node.getChildren().add(line);
        }
        this.opensReport = opensReport;
        if (!Reports.OF_A_KEY.getOrDefault(key.key(), List.of()).isEmpty()) {
            links.getStyleClass().addAll("spaced-row", "reports-of-a-key");
            node.getChildren().add(links);
            showReports(reportsWritten);
        }
        SelectableText.Line provenanceLabel = SelectableText.line("Note saved with it");
        provenance.setId(idPrefix + key.key() + ".provenance");
        provenance.setPromptText(ProfileEdit.FILLED_IN_PROVENANCE);
        HBox.setHgrow(provenance, Priority.ALWAYS);
        SelectableText.Wrapping hint = SelectableText.wrapping("Saved beside the value in profile.yaml so that whoever reads it later knows"
                + " where the value came from. You may write how you chose the value instead.");
        hint.getStyleClass().add("small");
        HBox provenanceLine = spaced(provenanceLabel, provenance);
        node.getChildren().addAll(provenanceLine, hint);
    }

    /** Draws the key's reports, each a link where the working directory holds it and grey where it does not (ADR-344 §6). */
    void showReports(Map<String, Boolean> written) {
        links.getChildren().setAll(SelectableText.line("Reports:"));
        for (String report : Reports.OF_A_KEY.getOrDefault(key.key(), List.of())) {
            if (written.getOrDefault(report, false)) {
                Hyperlink link = new Hyperlink(report);
                link.setOnAction(event -> opensReport.accept(report));
                links.getChildren().add(link);
            } else {
                SelectableText.Line grey = SelectableText.line(report + " (not written yet)");
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

    /** Fills the control and the note from what the file states for the key now. */
    void load(Optional<ProfileEdit.Stated> inFile) {
        loaded = inFile;
        setValue.accept(inFile.map(ProfileEdit.Stated::value).orElse(""));
        provenance.setText(ProfileEdit.provenanceShown(inFile));
    }

    /** Puts the form back to the file as last loaded. */
    void reset() {
        load(loaded);
    }

    /** The provenance to write for this row by ADR-343 §2's table; empty where the key is not written. */
    Optional<String> provenanceToSave() {
        if (key == ProfileKey.MODELS_RUN_ON) {
            // A value is changed in profile.yaml alone, so that a key held commented out stays so (ADR-384 §7).
            return Optional.empty();
        }
        return ProfileEdit.provenanceToSave(key, value(), provenance.getText(), loaded);
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
     * Saves every row ADR-343 §2 says to write, under ADR-295's lock and the checks of ADR-330 §5: all refused before any
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

    private Node listControl() {
        TextField list = new TextField();
        list.setId(idPrefix + key.key());
        list.setPromptText(key.example());
        TextField add = new TextField();
        add.setId(idPrefix + key.key() + ".add");
        add.setPromptText(key.kind() == ProfileKey.Kind.LANGUAGES ? "it" : ".java");
        Button plus = new Button("+");
        plus.setId(idPrefix + key.key() + ".plus");
        plus.setOnAction(event -> {
            String piece = add.getText().strip();
            if (!piece.isEmpty()) {
                list.setText((list.getText().strip() + " " + piece).strip());
                add.clear();
            }
        });
        value = list::getText;
        setValue = list::setText;
        HBox.setHgrow(list, Priority.ALWAYS);
        return spaced(list, add, plus);
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

    // ---- words (ADR-377 §4)

    /** The line of a key whose change redoes no work: the five Confluence keys', and so every key entering none. */
    private static final String NO_WORK = "Changing it redoes no work; it takes effect the next time you press Publish.";

    /** The line of where the models run, which no Save writes and so no change in the window costs (ADR-384 §7). */
    private static final String WHERE_THE_MODELS_RUN = "It cannot be changed here; Vespera checks it each time it starts, and it redoes no work.";

    /** The cost line of each earliest work a change redoes, the work said in plain words. */
    private static final Map<ProfileKey.Run, String> COST_BY_EARLIEST = Map.of(
            ProfileKey.Run.BYTE_LEVEL_REDUCTION, "Changing it redoes everything from sorting your files to writing the"
                    + " pages; text already read out of your files is kept.",
            ProfileKey.Run.EXTRACTION, "Changing it redoes everything after reading the text out of your files; the"
                    + " text already read is kept.",
            ProfileKey.Run.CONTENT_REDUNDANCY, "Changing it redoes the search for documents that say the same thing,"
                    + " the groups and the pages; your files are not read again.",
            ProfileKey.Run.SEED_MEASUREMENT, "Changing it redoes everything from reading your seed documents to writing"
                    + " the pages; your other files are not read again.",
            ProfileKey.Run.EMBEDDING_SCORING, "Changing it redoes the measuring of closeness, the grouping and the"
                    + " writing of the pages; your files are not read again.",
            ProfileKey.Run.GENERATION, "Changing it redoes only the writing of the pages; everything before it is kept.");

    /** The cost line of the largest group page, which enters no work but draws the group pages again. */
    private static final String THE_GROUP_PAGES = "Changing it rewrites only the group pages (a split group's pictures"
            + " are read again); everything else is kept.";

    /** Each source {@code effective-values.properties} names, in plain words. */
    private static final Map<String, String> SOURCES = Map.of(
            "shipped", "Vespera's settings",
            "local-file", "your local settings file",
            "command-line", "the command that started Vespera",
            "environment", "this computer's environment",
            "code", "Vespera's built-in value");

    /**
     * The one sentence of what a change redoes, chosen by its earliest work. A key whose earliest work the table gives
     * no line, the content census or the arrangement, fails here rather than show none.
     */
    static String costOf(ProfileKey key) {
        if (key == ProfileKey.CLUSTER_PAGE_BYTE_CEILING) {
            return THE_GROUP_PAGES;
        }
        if (key == ProfileKey.MODELS_RUN_ON) {
            return WHERE_THE_MODELS_RUN;
        }
        return key.earliestRun().map(run -> {
            String line = COST_BY_EARLIEST.get(run);
            if (line == null) {
                throw new IllegalStateException(key.key() + " first enters " + run.stageName()
                        + ", for which no cost line is written");
            }
            return line;
        }).orElse(NO_WORK);
    }

    /** The warnings a change of the key carries, one line each, in order, each only where its cost applies it. */
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
        return List.copyOf(warnings);
    }

    /** The line of the value in use where the profile names none, its source in plain words where it is a known one. */
    static String inUseOf(EffectiveValues.Value said) {
        String source = SOURCES.get(said.source());
        return "Left empty, Vespera uses " + said.value() + (source == null ? "" : " (" + source + ")") + ".";
    }
}
