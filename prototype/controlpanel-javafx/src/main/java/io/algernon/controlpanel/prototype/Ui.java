package io.algernon.controlpanel.prototype;

import java.io.File;

import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

/** PROTOTYPE. Small widgets every variant may use; layout stays each variant's own. */
final class Ui {

    private Ui() {}

    static Label label(String text, String... styles) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().addAll(styles);
        return label;
    }

    static VBox card(String title, Node... children) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        if (title != null) {
            card.getChildren().add(label(title, "card-title"));
        }
        card.getChildren().addAll(children);
        return card;
    }

    static Label pill(StringProperty childState, Stub stub) {
        Label pill = new Label();
        Runnable refresh = () -> {
            pill.setText(stub.childLine());
            pill.getStyleClass().setAll("label", "pill", Stub.kindOf(childState.get()));
        };
        childState.addListener((o, a, b) -> refresh.run());
        refresh.run();
        return pill;
    }

    /** A folder field with the native chooser — the one thing the browser panel cannot have. */
    static VBox folderField(String name, StringProperty value, String hint) {
        TextField field = new TextField();
        field.textProperty().bindBidirectional(value);
        field.setPromptText("e.g. D:\\archive\\corpus");
        Button browse = new Button("Browse…");
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(name);
            File dir = chooser.showDialog(browse.getScene().getWindow());
            if (dir != null) {
                value.set(dir.getAbsolutePath());
            }
        });
        HBox.setHgrow(field, Priority.ALWAYS);
        Label status = label("", "hint");
        Runnable check = () -> {
            String path = value.get();
            if (path == null || path.isBlank()) {
                status.setText("Not chosen");
            } else if (new File(path).isDirectory()) {
                status.setText("Found, " + new File(path).getUsableSpace() / (1L << 30) + " GiB free");
            } else {
                status.setText("Will be created");
            }
        };
        value.addListener((o, a, b) -> check.run());
        check.run();
        VBox box = new VBox(4, label(name, "field-name"), new HBox(6, field, browse), status);
        if (hint != null) {
            box.getChildren().add(label(hint, "hint"));
        }
        return box;
    }

    static VBox textField(String name, StringProperty value) {
        TextField field = new TextField();
        field.textProperty().bindBidirectional(value);
        return new VBox(4, label(name, "field-name"), field);
    }

    static VBox imageField(Stub stub) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll(Stub.GPU_IMAGE, Stub.CPU_IMAGE);
        box.setEditable(true);
        box.setMaxWidth(Double.MAX_VALUE);
        box.valueProperty().bindBidirectional(stub.doclingImage);
        return new VBox(4, label("Docling image", "field-name"), box,
                label("In use: chosen by the control panel; pick the other if this machine has no graphics card.", "hint"));
    }

    /** Value, why, cost and report links for one profile key, with its own Save. */
    static VBox keyEditor(Stub stub, String keyName, Runnable afterSave) {
        Stub.Key key = Stub.key(keyName);
        TextField value = new TextField(stub.profile.getOrDefault(keyName, ""));
        value.setPromptText("e.g. " + key.example());
        TextField why = new TextField(stub.why.getOrDefault(keyName, ""));
        why.setPromptText("e.g. read off the report beside it: the cut I can live with");
        HBox reports = new HBox(4);
        for (String report : Stub.REPORTS_OF_KEY.getOrDefault(keyName, java.util.List.of())) {
            reports.getChildren().add(reportLink(stub, report));
        }
        Button save = new Button("Save");
        save.setOnAction(e -> {
            stub.save(keyName, value.getText(), why.getText());
            if (afterSave != null && stub.profile.containsKey(keyName)) {
                afterSave.run();
            }
        });
        save.setDefaultButton(true);
        VBox box = new VBox(6,
                label(keyName, "key-name"),
                label(key.about(), "hint"),
                label("Value", "field-name"), value,
                label("Why this value?", "field-name"), why,
                label(Stub.costOf(keyName), "cost"));
        if (!reports.getChildren().isEmpty()) {
            box.getChildren().add(new HBox(6, label("Read it off:", "hint"), reports));
        }
        box.getChildren().add(save);
        return box;
    }

    static Hyperlink reportLink(Stub stub, String report) {
        Hyperlink link = new Hyperlink(report);
        link.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Would open " + stub.workingDirectory.get() + "\\" + report + ".html in the browser"
                            + " (or in an embedded WebView, if javafx-web is added).");
            alert.setHeaderText(report);
            alert.showAndWait();
        });
        return link;
    }

    static TextArea console(Stub stub) {
        TextArea area = new TextArea(String.join("\n", stub.log));
        area.setEditable(false);
        area.getStyleClass().add("console");
        stub.log.addListener((ListChangeListener<String>) c -> {
            area.setText(String.join("\n", stub.log));
            area.positionCaret(area.getLength());
            area.setScrollTop(Double.MAX_VALUE);
        });
        return area;
    }

    static Label outcome(Stub stub) {
        Label label = label("", "outcome");
        label.textProperty().bind(stub.outcome);
        label.visibleProperty().bind(stub.outcome.isNotEmpty());
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }

    static Insets pad(double v) {
        return new Insets(v);
    }
}
