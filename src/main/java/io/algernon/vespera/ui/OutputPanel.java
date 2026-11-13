package io.algernon.vespera.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;

/**
 * What the output panel shows (ADR-364 sections 1 and 7): the watched child's held lines, each once, until a report the
 * window accepts names an output file that is there; then that file, its text replacing the panel's once, and from
 * then on the whole lines it gains. The panel is a {@link TextArea}, not editable and wrapping no line, whose view
 * follows new lines only while it is at the bottom and nothing in it is selected.
 */
final class OutputPanel {

    /**
     * The form of an invocation id, ADR-362 section 3's, read as the jar's {@code InvocationOutput} reads it; written
     * here because the control panel links no class of the jar (ADR-285). An id of any other form names no file.
     */
    private static final Pattern ID = Pattern.compile("\\d{8}T\\d{6}Z(?:-[1-9]\\d*)?");

    private final TextArea area;
    private long shown;
    private boolean childsLines;
    private Path file;
    private long read;
    private final ByteArrayOutputStream partial = new ByteArrayOutputStream();

    OutputPanel(TextArea area) {
        this.area = area;
        area.setEditable(false);
        area.setWrapText(false);
        if (!area.getStyleClass().contains("output")) {
            area.getStyleClass().add("output");
        }
    }

    /** A new child is watched: the panel empties and shows its lines from the first. */
    void showsTheChild() {
        reset(true);
    }

    /** An invocation the window did not start is watched: the panel empties and shows nothing until its file is found. */
    void showsNoChild() {
        reset(false);
    }

    private void reset(boolean child) {
        area.clear();
        shown = 0;
        childsLines = child;
        file = null;
        read = 0;
        partial.reset();
    }

    /** Whether the panel shows an output file. */
    boolean showsAFile() {
        return file != null;
    }

    /** One tick of the window's clock: the file's new whole lines, or the child's lines not shown yet. */
    void tick(Supplier<Child.Held> held) {
        if (file != null) {
            String gained = gained();
            append(gained);
            return;
        }
        if (!childsLines) {
            return;
        }
        Child.Held now = held.get();
        if (now.count() <= shown) {
            return;
        }
        long firstHeld = now.count() - now.lines().size() + 1;
        StringBuilder text = new StringBuilder();
        int from;
        if (shown + 1 < firstHeld) {
            text.append(firstHeld - 1 - shown)
                    .append(" lines are not shown here; the output file and logs/vespera.log hold them\n");
            from = 0;
        } else {
            from = (int) (shown + 1 - firstHeld);
        }
        for (String line : now.lines().subList(from, now.lines().size())) {
            text.append(line).append('\n');
        }
        shown = now.count();
        append(text.toString());
    }

    /**
     * The accepted report names {@code id}: where it is of the id's form and its file is there, the panel's text is
     * replaced by the file's, once, and {@code letGo} lets the child's held lines go.
     */
    void fileNamedBy(Path workingDirectory, String id, Runnable letGo) {
        if (file != null || id == null || !ID.matcher(id).matches()) {
            return;
        }
        Path named = workingDirectory.resolve("logs").resolve("output-" + id + ".log");
        if (!Files.isRegularFile(named)) {
            return;
        }
        file = named;
        read = 0;
        partial.reset();
        area.setText(gained());
        area.positionCaret(area.getLength());
        area.setScrollTop(Double.MAX_VALUE);
        letGo.run();
    }

    /** The whole lines the file has gained since the last read, decoded as UTF-8; a line not yet ended waits. */
    private String gained() {
        try (InputStream in = Files.newInputStream(file)) {
            in.skipNBytes(read);
            byte[] more = in.readAllBytes();
            read += more.length;
            partial.write(more);
        } catch (IOException | RuntimeException unreadable) {
            return "";
        }
        byte[] all = partial.toByteArray();
        int end = all.length;
        while (end > 0 && all[end - 1] != '\n') {
            end--;
        }
        String whole = new String(all, 0, end, StandardCharsets.UTF_8).replace("\r\n", "\n");
        partial.reset();
        partial.write(all, end, all.length - end);
        return whole;
    }

    /** Appends {@code text}, following the bottom only where the view is there and nothing is selected (ADR-364 section 1). */
    private void append(String text) {
        if (text.isEmpty()) {
            return;
        }
        boolean follow = area.getLength() == 0 || (area.getSelection().getLength() == 0 && atTheBottom());
        if (follow) {
            area.appendText(text);
            area.positionCaret(area.getLength());
            // laid out first, so that the skin knows the new text's height and the greatest scroll is the new end
            area.applyCss();
            area.layout();
            area.setScrollTop(Double.MAX_VALUE);
            return;
        }
        int anchor = area.getAnchor();
        int caret = area.getCaretPosition();
        double top = area.getScrollTop();
        area.appendText(text);
        area.selectRange(anchor, caret);
        area.applyCss();
        area.layout();
        area.setScrollTop(top);
    }

    /** Whether the view stands within one line's height of its greatest scroll. */
    private boolean atTheBottom() {
        if (!(area.lookup(".scroll-pane") instanceof ScrollPane scroll) || !(scroll.getContent() instanceof Region content)) {
            return true;
        }
        double greatest = content.getHeight() - scroll.getViewportBounds().getHeight();
        double oneLine = area.getFont().getSize() * 1.5;
        return greatest <= 0 || area.getScrollTop() >= greatest - oneLine;
    }
}
