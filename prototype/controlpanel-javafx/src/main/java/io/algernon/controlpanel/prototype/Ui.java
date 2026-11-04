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

    /** GPU or CPU, said in words; the image names stay visible but small. */
    static VBox imageField(Stub stub) {
        String current = stub.doclingImage.get();
        boolean currentIsGpu = current.contains("-cu1") || current.contains("gpu");
        boolean currentIsCpu = current.contains("-cpu");
        String gpuImage = currentIsGpu ? current : Stub.GPU_IMAGE;
        String cpuImage = currentIsCpu ? current : Stub.CPU_IMAGE;
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton gpu = new javafx.scene.control.RadioButton("GPU: needs an NVIDIA graphics card. Much faster.");
        javafx.scene.control.RadioButton cpu = new javafx.scene.control.RadioButton("CPU: runs on any machine. Slower.");
        gpu.setUserData(gpuImage);
        cpu.setUserData(cpuImage);
        gpu.setToggleGroup(group);
        cpu.setToggleGroup(group);
        VBox box = new VBox(4, label("Document converter (Docling)", "field-name"),
                gpu, label(gpuImage, "source", "indent"), cpu, label(cpuImage, "source", "indent"));
        if (!current.isBlank() && !currentIsGpu && !currentIsCpu) {
            javafx.scene.control.RadioButton other = new javafx.scene.control.RadioButton("Other image");
            other.setUserData(current);
            other.setToggleGroup(group);
            box.getChildren().addAll(other, label(current, "source", "indent"));
        }
        group.getToggles().stream().filter(t -> t.getUserData().equals(current)).findFirst()
                .ifPresentOrElse(group::selectToggle, () -> group.selectToggle(gpu));
        group.selectedToggleProperty().addListener((o, a, b) -> {
            if (b != null) {
                stub.doclingImage.set((String) b.getUserData());
            }
        });
        return box;
    }

    /**
     * A list of folders, with the folder being added above it: Browse picks it, "+" adds it.
     * {@code refusal} says why a folder cannot be added, or null; {@code shown} turns it into
     * the text kept in the list.
     */
    static VBox folderList(String name, javafx.collections.ObservableList<String> items,
            java.util.function.Supplier<String> start, java.util.function.Function<File, String> refusal,
            java.util.function.Function<File, String> shown, String hint) {
        TextField picked = new TextField();
        picked.setEditable(false);
        picked.setPromptText("Browse to pick a folder");
        HBox.setHgrow(picked, Priority.ALWAYS);
        Label problem = label("", "bad-text");
        Button browse = new Button("Browse…");
        Button add = new Button("+");
        add.setTooltip(new javafx.scene.control.Tooltip("Add the folder to the list"));
        add.disableProperty().bind(picked.textProperty().isEmpty());
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(name);
            String from = start.get();
            if (from != null && new File(from).isDirectory()) {
                chooser.setInitialDirectory(new File(from));
            }
            File dir = chooser.showDialog(browse.getScene().getWindow());
            if (dir != null) {
                String why = refusal.apply(dir);
                problem.setText(why == null ? "" : "Not added: " + why);
                picked.setText(why == null ? shown.apply(dir) : "");
            }
        });
        add.setOnAction(e -> {
            if (items.stream().anyMatch(picked.getText()::equalsIgnoreCase)) {
                problem.setText("Not added: it is listed already");
            } else {
                items.add(picked.getText());
            }
            picked.clear();
        });
        VBox rows = new VBox(4);
        Runnable draw = () -> {
            rows.getChildren().clear();
            for (String item : items) {
                Label text = label(item, "code");
                text.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(text, Priority.ALWAYS);
                Button remove = new Button("✕");
                remove.getStyleClass().add("chip-remove");
                remove.setTooltip(new javafx.scene.control.Tooltip("Remove from the list"));
                remove.setOnAction(e -> items.remove(item));
                HBox row = new HBox(6, text, remove);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.getStyleClass().add("list-row");
                rows.getChildren().add(row);
            }
            if (items.isEmpty()) {
                rows.getChildren().add(label("None yet.", "hint"));
            }
        };
        items.addListener((ListChangeListener<String>) c -> draw.run());
        draw.run();
        VBox box = new VBox(6, label(name, "field-name"));
        if (hint != null) {
            box.getChildren().add(label(hint, "hint"));
        }
        box.getChildren().addAll(new HBox(6, picked, browse, add), problem, rows);
        return box;
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
            File file = new File(stub.workingDirectory.get(), report + ".html");
            if (file.isFile()) {
                // AWT's own thread, so the JavaFX thread never waits on the browser starting.
                Thread.ofVirtual().start(() -> {
                    try {
                        java.awt.Desktop.getDesktop().open(file);
                    } catch (Exception ignored) {
                        // a prototype: the link simply does nothing
                    }
                });
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "The working directory holds no " + report + ".html yet: a run writes it.");
                alert.setHeaderText(report);
                alert.showAndWait();
            }
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
