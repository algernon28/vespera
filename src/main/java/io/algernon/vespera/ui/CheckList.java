package io.algernon.vespera.ui;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

/**
 * The check as a list in the header (ADR-430 §2, §8): five rows in two columns, the Docker daemon, Docling and Ollama in
 * the left column and the embedding and generative models in the right, each a name and a mark, {@code OK}, {@code KO} or
 * a dash. A model's mark carries, while its download runs with the panel hidden, <i>↓ n%</i> beside it. Each name carries
 * as its caption what the row is and where it was found. A name, a mark and a percent are buttons that stand for text,
 * since the walk of every text of the window finds a label with words in none (ADR-364 §8): the name and the mark are
 * controls' texts. Every colour, size and width is the stylesheet's, so that a row whose mark grows moves nothing.
 */
final class CheckList {

    static final String DASH = "–";

    private static final String DOWN_ARROW = "↓";

    /** The five rows' ids, in the order the columns hold them. */
    static final List<String> ROWS = List.of("docker-daemon", "docling", "ollama", "embedding-model",
            "generative-model");

    private record Row(String id, String name, Button nameButton, Button mark, Button progress, Tooltip caption) {
    }

    private final GridPane grid = new GridPane();
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final Consumer<String> markClicked;
    private final Consumer<String> progressClicked;

    /** {@code markClicked} gets a row's id when its mark is clicked, {@code progressClicked} when its percent is. */
    CheckList(Consumer<String> markClicked, Consumer<String> progressClicked) {
        this.markClicked = markClicked;
        this.progressClicked = progressClicked;
        grid.setId("docker-check");
        grid.getStyleClass().add("docker-check");
        draw("docker-daemon", "Docker daemon", 0, 0);
        draw("docling", "Docling", 0, 1);
        draw("ollama", "Ollama", 0, 2);
        draw("embedding-model", "Embedding model", 2, 0);
        draw("generative-model", "Generative model", 2, 1);
        show(null, new DockerControl.Models("", ""));
    }

    Node node() {
        return grid;
    }

    private void draw(String id, String name, int column, int line) {
        Button nameButton = new Button(name);
        nameButton.setId("check-" + id);
        nameButton.getStyleClass().add("check-name");
        nameButton.setFocusTraversable(false);
        Tooltip caption = Captions.on(nameButton, name);
        Button mark = new Button(DASH);
        mark.setId("check-" + id + "-mark");
        mark.getStyleClass().addAll("check-mark", "not-asked");
        mark.setFocusTraversable(false);
        mark.setOnMouseClicked(event -> markClicked.accept(id));
        Button progress = new Button();
        progress.setId("check-" + id + "-progress");
        progress.getStyleClass().add("check-progress");
        progress.setFocusTraversable(false);
        progress.setVisible(false);
        progress.setManaged(false);
        progress.setOnMouseClicked(event -> progressClicked.accept(id));
        Captions.on(progress, "Downloading. Click to show the download.");
        HBox cell = new HBox(mark, progress);
        cell.getStyleClass().add("check-cell");
        grid.add(nameButton, column, line);
        grid.add(cell, column + 1, line);
        rows.put(id, new Row(id, name, nameButton, mark, progress, caption));
    }

    /** Draws {@code check}, null before the first one has come back, and the captions for the models in force. */
    void show(DockerCheck check, DockerControl.Models models) {
        markOf("docker-daemon", check == null ? null : check.daemon());
        markOf("docling", check == null ? null : check.docling().mark());
        markOf("ollama", check == null ? null : check.ollama().mark());
        markOf("embedding-model", check == null ? null : check.embeddingModel());
        markOf("generative-model", check == null ? null : check.generativeModel());
        caption("docker-daemon", check != null && check.daemon() == DockerCheck.Mark.NOT_ASKED
                ? "Docker daemon: not asked, Docling and Ollama are both on another machine"
                : "Docker daemon: the Docker engine the containers on this machine run in");
        caption("docling", serviceWords("Docling", check == null ? null : check.docling()));
        caption("ollama", serviceWords("Ollama", check == null ? null : check.ollama()));
        caption("embedding-model", modelWords("Embedding model", models.embedding(),
                check == null ? null : check.embeddingModel()));
        caption("generative-model", modelWords("Generative model", models.generative(),
                check == null ? null : check.generativeModel()));
    }

    /** The mark {@code row} reads now: OK, KO or a dash, in the class that draws it. */
    private void markOf(String id, DockerCheck.Mark mark) {
        Button shown = rows.get(id).mark();
        shown.getStyleClass().removeAll("ok", "ko", "not-asked");
        DockerCheck.Mark read = mark == null ? DockerCheck.Mark.NOT_ASKED : mark;
        switch (read) {
            case OK -> {
                shown.setText("OK");
                shown.getStyleClass().add("ok");
            }
            case KO -> {
                shown.setText("KO");
                shown.getStyleClass().add("ko");
            }
            case NOT_ASKED -> {
                shown.setText(DASH);
                shown.getStyleClass().add("not-asked");
            }
        }
    }

    private void caption(String id, String words) {
        rows.get(id).caption().setText(words);
    }

    /** What the row of Docling or Ollama says it is and where it was found (ADR-430 §8, §10). */
    private static String serviceWords(String name, DockerCheck.Service service) {
        if (service == null) {
            return name + ": not checked yet";
        }
        String host = hostOf(service.address());
        return switch (service.where()) {
            case THIS_MACHINE -> name + ": this machine, " + service.address();
            case ANOTHER_MACHINE -> name + ": " + host + ", another machine; Vespera does not start it";
            case NOT_RESOLVED -> name + ": " + host + " cannot be resolved; Vespera does not ask it or start it";
        };
    }

    private static String hostOf(String address) {
        try {
            String host = URI.create(address).getHost();
            return host == null ? address : host;
        } catch (IllegalArgumentException unreadable) {
            return address;
        }
    }

    /** What a model's row says: its name, and what clicking its mark does or when it is asked. */
    private static String modelWords(String kind, String name, DockerCheck.Mark mark) {
        if (name == null || name.isBlank()) {
            return kind + ": none is named";
        }
        String words = kind + ": " + name.strip();
        if (mark == DockerCheck.Mark.KO) {
            return words + "; click KO to download it into Ollama";
        }
        if (mark == DockerCheck.Mark.NOT_ASKED) {
            return words + "; asked once Ollama shows OK";
        }
        return words;
    }

    /** Shows {@code percent} beside {@code row}'s mark, or takes it away where it is negative. */
    void progress(String id, int percent) {
        Button shown = rows.get(id).progress();
        boolean downloading = percent >= 0;
        shown.setText(downloading ? DOWN_ARROW + " " + percent + "%" : "");
        shown.setVisible(downloading);
        shown.setManaged(downloading);
    }
}
