package io.algernon.controlpanel.prototype;

import java.util.List;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * PROTOTYPE variant C, "Guided wizard": one question per screen, in the order an operator
 * meets them — where, with what, which seed, go, answer the stop — then back to go. No
 * navigation to learn and no settings visible that the current screen does not need;
 * everything else (profile, reports) is one "More" link away.
 */
final class VariantC {

    private static final List<String> STEPS = List.of("Where", "With what", "Seed", "Run", "Answer");

    private VariantC() {}

    static Parent build(Stub stub) {
        IntegerProperty at = new SimpleIntegerProperty(stub.currentStep.get() < 0 ? 0 : 3);
        BorderPane frame = new BorderPane();
        HBox crumbs = new HBox(6);
        crumbs.setAlignment(Pos.CENTER);
        crumbs.setPadding(new Insets(18));
        VBox page = new VBox(18);
        page.setMaxWidth(640);
        page.setPadding(new Insets(24, 32, 24, 32));
        page.getStyleClass().add("wizard-page");
        Button back = new Button("Back");
        Button forward = new Button("Continue");
        forward.getStyleClass().add("primary");
        HBox buttons = new HBox(10, back, new Region(), forward);
        HBox.setHgrow(buttons.getChildren().get(1), Priority.ALWAYS);
        buttons.setMaxWidth(640);

        Runnable render = () -> {
            int step = at.get();
            crumbs.getChildren().clear();
            for (int i = 0; i < STEPS.size(); i++) {
                Label crumb = new Label((i + 1) + "  " + STEPS.get(i));
                crumb.getStyleClass().addAll("crumb", i < step ? "crumb-done" : i == step ? "crumb-now" : "crumb-todo");
                crumbs.getChildren().add(crumb);
            }
            page.getChildren().setAll(Ui.outcome(stub));
            page.getChildren().addAll(screen(stub, step, at));
            back.setDisable(step == 0 || stub.running());
            forward.setVisible(step < 3);
        };
        at.addListener((o, a, b) -> render.run());
        stub.childState.addListener((o, a, b) -> {
            if (!stub.running() && !stub.wants.isEmpty()) {
                at.set(4);
            }
            render.run();
        });
        stub.wants.addListener((ListChangeListener<String>) c -> render.run());
        back.setOnAction(e -> at.set(at.get() - 1));
        forward.setOnAction(e -> at.set(at.get() + 1));
        render.run();

        VBox centre = new VBox(16, page, buttons);
        centre.setAlignment(Pos.TOP_CENTER);
        centre.setPadding(new Insets(0, 0, 80, 0));
        ScrollPane scroll = new ScrollPane(centre);
        scroll.setFitToWidth(true);
        frame.setTop(crumbs);
        frame.setCenter(scroll);
        return frame;
    }

    private static List<Node> screen(Stub stub, int step, IntegerProperty at) {
        return switch (step) {
            case 0 -> List.of(Ui.label("Which documents, and where may Vespera work?", "wizard-title"),
                    Ui.folderField("Corpus root", stub.corpusRoot, "The folder of documents Vespera reads. It is never changed."),
                    Ui.folderField("Working directory", stub.workingDirectory, "Where the database, the reports and profile.yaml live."),
                    more("Output folder", Ui.folderField("Output folder", stub.outputFolder, "Leave empty for a folder per run.")));
            case 1 -> List.of(Ui.label("Which machine does the reading?", "wizard-title"),
                    Ui.label("● Docling answers and runs the GPU image", "ok-text"),
                    Ui.label("● Ollama serves every model named", "ok-text"),
                    Ui.imageField(stub),
                    more("Addresses and label model", new VBox(10, Ui.textField("Docling address", stub.doclingAddress),
                            Ui.textField("Ollama address", stub.ollamaAddress), Ui.textField("Label model", stub.labelModel))));
            case 2 -> List.of(Ui.label("Which documents show what is relevant?", "wizard-title"),
                    Ui.keyEditor(stub, "seedFolder", () -> at.set(3)));
            case 3 -> run(stub);
            default -> answer(stub, at);
        };
    }

    private static List<Node> run(Stub stub) {
        int index = Math.max(stub.currentStep.get(), 0);
        ProgressBar bar = new ProgressBar((double) index / Stub.STEPS.size());
        bar.setMaxWidth(Double.MAX_VALUE);
        stub.currentStep.addListener((o, a, b) -> bar.setProgress(b.doubleValue() / Stub.STEPS.size()));
        Label now = Ui.label("", "hint");
        now.textProperty().bind(stub.currentStep.map(n -> n.intValue() < 0 ? "Nothing has run yet."
                : "Step " + (n.intValue() + 1) + " of " + Stub.STEPS.size() + ": " + Stub.STEPS.get(n.intValue()).name()));
        Button go = new Button(stub.running() ? "Stop" : stub.nextInvocation.get().equals("none") ? "Publish" : "Run");
        go.getStyleClass().addAll(stub.running() ? "danger" : "primary", "big");
        go.setMaxWidth(Double.MAX_VALUE);
        go.setOnAction(e -> {
            if (stub.running()) {
                stub.stop();
            } else {
                stub.start(stub.nextInvocation.get().equals("none") ? "publish" : "run");
            }
        });
        var console = Ui.console(stub);
        console.setPrefRowCount(14);
        TitledPane output = new TitledPane("Output", console);
        output.setExpanded(false);
        String title = stub.running() ? "Running…" : stub.nextInvocation.get().equals("none")
                ? "The deliverable is written." : "Ready.";
        return List.of(Ui.label(title, "wizard-title"), Ui.pill(stub.childState, stub), bar, now, go, output,
                more("Profile and reports", reports(stub)));
    }

    private static List<Node> answer(Stub stub, IntegerProperty at) {
        if (stub.wants.isEmpty()) {
            return List.of(Ui.label("Nothing is asked of you.", "wizard-title"));
        }
        String key = stub.wants.getFirst();
        return List.of(Ui.label("The run stopped: it needs one value from you.", "wizard-title"),
                Ui.label("Open the report, decide where the cut goes, and say why.", "hint"),
                Ui.keyEditor(stub, key, () -> {
                    at.set(3);
                    stub.start("run");
                }));
    }

    private static VBox reports(Stub stub) {
        VBox box = new VBox(2);
        Stub.REPORTS.forEach(r -> box.getChildren().add(Ui.reportLink(stub, r)));
        return box;
    }

    private static TitledPane more(String title, Node content) {
        TitledPane pane = new TitledPane("More: " + title, content);
        pane.setExpanded(false);
        return pane;
    }
}
