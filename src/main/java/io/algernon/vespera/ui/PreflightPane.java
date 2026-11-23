package io.algernon.vespera.ui;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The sidebar's Preflight section (ADR-440): the two folders and their holders, a row each of a name, a mark and words in
 * the three columns of the grid {@code preflight-list}, and Docling's image where the header's check found it running
 * another than the one Run would use. A holder is said in words, its line whole the caption (§3); one process holding
 * both folders is said once (§4); free space is said in each folder's words alone (§5). The section holds no Check and no
 * line of Docling's or Ollama's answers, which the header's list says.
 */
final class PreflightPane {

    private static final String IMAGE_CAPTION =
            "Extraction refuses a Docling that runs another image than the one Run would use.";

    private static final String NO_RUNTIME_CAPTION = "The GPU image starts only where Docker has the nvidia runtime,"
            + " which the NVIDIA Container Toolkit gives it.";

    /** One row: its id, its name, whether its mark is OK, its words and the caption of its words (empty for none). */
    private record Row(String id, String name, boolean ok, String words, String caption) {
    }

    private final ControlPanelState state;
    private final WorkingFolders folders;
    private final Function<String, String> fieldText;
    private final GridPane preflight = new GridPane();

    /** The rows last drawn. */
    private List<Row> drawn = List.of();

    /** What the window keeps of the card's last check, null where it keeps none (ADR-445 §5). */
    private final Supplier<DockerControl.CardCheck> cardKept;

    /** {@code fieldText} gives the text of the launch field of a property, {@code cardKept} the answer kept of the card. */
    PreflightPane(ControlPanelState state, WorkingFolders folders, Function<String, String> fieldText,
            Supplier<DockerControl.CardCheck> cardKept) {
        this.state = state;
        this.folders = folders;
        this.fieldText = fieldText;
        this.cardKept = cardKept;
    }

    Node pane() {
        preflight.setId("preflight-list");
        preflight.getStyleClass().addAll("section", "preflight-list");
        ColumnConstraints name = new ColumnConstraints();
        name.setHgrow(Priority.NEVER);
        ColumnConstraints mark = new ColumnConstraints();
        mark.setHgrow(Priority.NEVER);
        ColumnConstraints words = new ColumnConstraints();
        words.setHgrow(Priority.ALWAYS);
        words.setFillWidth(true);
        preflight.getColumnConstraints().setAll(name, mark, words);
        VBox pane = new VBox(preflight);
        pane.getStyleClass().add("section");
        return pane;
    }

    /** Draws §1's rows, the clock drawing them each second. */
    void draw() {
        ControlPanelState.Holders holders = state.holders(folders.workingDirectory(), folders.corpusRoot());
        String corpus = fieldText.apply("vespera.corpus-root");
        String working = fieldText.apply("vespera.working-dir");
        ZonedDateTime now = ZonedDateTime.now();
        FolderStatus corpusStatus = FolderStatus.of(corpus, false);
        FolderStatus workingStatus = FolderStatus.of(working, false);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("corpus-root", "Corpus root", found(corpusStatus), corpusStatus.words(), ""));
        Optional<String> rootLine = holders.corpusRoot();
        Optional<String> workingLine = holders.workingDirectory();
        boolean one = rootLine.isPresent() && workingLine.isPresent() && samePid(rootLine.get(), workingLine.get());
        if (rootLine.isPresent() && !one) {
            rows.add(holder("corpus-root-holder", "Held by", rootLine.get(), rootLine.get(), now));
        }
        rows.add(new Row("working-directory", "Working directory", found(workingStatus), workingStatus.words(), ""));
        if (one) {
            rows.add(holder("holder", "Both held by", workingLine.get(), rootLine.get() + "\n" + workingLine.get(), now));
        } else if (workingLine.isPresent()) {
            rows.add(holder("working-directory-holder", "Held by", workingLine.get(), workingLine.get(), now));
        }
        String kept = state.lastCheck().map(DockerCheck::doclingImage).orElse("");
        String field = fieldText.apply("vespera.docling.image").strip();
        if (!kept.isEmpty() && !field.isEmpty() && !kept.equals(field)) {
            rows.add(new Row("docling-image", "Docling's image", false, "runs " + kept + "; Run would use " + field,
                    IMAGE_CAPTION));
        }
        DockerControl.CardCheck card = cardKept.get();
        if (card != null && card.runtimeListed().isPresent()
                && fieldText.apply("vespera.docling.image").strip().equals(StartingValues.DOCLING_IMAGE_GPU)) {
            boolean listed = card.runtimeListed().get();
            rows.add(listed
                    ? new Row("graphics-card", "Graphics card", true, "Docker has the nvidia runtime", "")
                    : new Row("graphics-card", "Graphics card", false,
                            "Docker has no nvidia runtime; Start Docker offers to install the NVIDIA Container Toolkit",
                            NO_RUNTIME_CAPTION));
            for (int at = 0; at < card.warnings().size(); at++) {
                String warning = card.warnings().get(at);
                rows.add(new Row("graphics-card-warning-" + (at + 1), "Warning", false, warning, warning));
            }
        }
        if (rows.equals(drawn)) {
            // the clock draws this each second; rows that are the same are left as they are, selection and all
            return;
        }
        drawn = rows;
        List<Node> children = new ArrayList<>();
        for (int at = 0; at < rows.size(); at++) {
            Row row = rows.get(at);
            SelectableText.Line name = SelectableText.line(row.name());
            name.setId("preflight-" + row.id());
            SelectableText.Line mark = SelectableText.line(row.ok() ? "OK" : "KO");
            mark.setId("preflight-" + row.id() + "-mark");
            mark.getStyleClass().addAll("preflight-mark", row.ok() ? "ok" : "ko");
            SelectableText.Line words = SelectableText.line(row.words());
            words.setId("preflight-" + row.id() + "-words");
            if (!row.caption().isEmpty()) {
                Captions.on(words, row.caption());
            }
            GridPane.setConstraints(name, 0, at);
            GridPane.setConstraints(mark, 1, at);
            GridPane.setConstraints(words, 2, at);
            GridPane.setHgrow(words, Priority.ALWAYS);
            children.addAll(List.of(name, mark, words));
        }
        preflight.getChildren().setAll(children);
    }

    private static boolean found(FolderStatus status) {
        return status.state().equals("found");
    }

    private static Row holder(String id, String name, String line, String caption, ZonedDateTime now) {
        return new Row(id, name, false, HolderWords.of(line, now), caption);
    }

    /** Whether both lines have the form and name the same pid (ADR-440 §4). */
    private static boolean samePid(String one, String other) {
        Optional<HolderLine> first = HolderLine.parse(one);
        Optional<HolderLine> second = HolderLine.parse(other);
        return first.isPresent() && second.isPresent() && first.get().pid() == second.get().pid();
    }
}
