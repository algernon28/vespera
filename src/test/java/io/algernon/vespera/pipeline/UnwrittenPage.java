package io.algernon.vespera.pipeline;

import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.UnwrittenSentencesOnRecord;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * What the page of a cluster nothing was written over says in place of writing (ADR-174), and where a test finds
 * that page in the HTML tree an invocation wrote (ADR-274 §1).
 *
 * <p><b>The sentences are the record's</b>, held in {@link UnwrittenSentencesOnRecord} beside the code that writes
 * them, and named again here for the invocation tests of this package.
 */
final class UnwrittenPage {

    static final String DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM =
            UnwrittenSentencesOnRecord.DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM;

    static final String NO_DOCUMENT_COULD_BE_SENT = UnwrittenSentencesOnRecord.NO_DOCUMENT_COULD_BE_SENT;

    static final String NO_DOCUMENT_FITS_THE_ROOM = UnwrittenSentencesOnRecord.NO_DOCUMENT_FITS_THE_ROOM;

    static final String WRITING_STOPPED_BEFORE_IT = UnwrittenSentencesOnRecord.WRITING_STOPPED_BEFORE_IT;

    static final List<String> EVERY_SENTENCE = UnwrittenSentencesOnRecord.EVERY_SENTENCE;

    private UnwrittenPage() {}

    /** What the page of a cluster carrying a fault row says, by kind (ADR-174 §2). */
    static String forKind(ClusterFaultKind kind) {
        return UnwrittenSentencesOnRecord.forKind(kind);
    }

    /** The tree the most recent invocation wrote under {@code generationRun} (ADR-103). */
    static Path treeOf(Path workingDirectory, String generationRun) {
        return workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(generationRun);
    }

    /**
     * The one cluster page in {@code tree} headed by {@code label}. A cluster nothing was written over is headed by
     * its 6a label (ADR-106), so this finds exactly the pages these tests are about.
     */
    static Path pageHeadedBy(Path tree, String label) {
        String heading = "<h1>" + escaped(label) + "</h1>";
        try (Stream<Path> files = Files.walk(tree)) {
            List<Path> pages = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".html"))
                    .filter(file -> !file.equals(tree.resolve(Deliverable.INDEX_FILE_NAME)))
                    .filter(file -> linesOf(file).contains(heading))
                    .toList();
            if (pages.size() != 1) {
                throw new IllegalStateException("expected exactly one page headed '" + heading + "' under "
                        + tree + ", found " + pages);
            }
            return pages.getFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * What stands on the line beneath the heading of {@code page}, as a reader sees it: its tags taken off and its
     * entities read back. On a page nothing was written over, that is the sentence saying why.
     */
    static String lineUnderTheHeadingOf(Path page) {
        List<String> lines = linesOf(page);
        for (int at = 0; at + 1 < lines.size(); at++) {
            if (lines.get(at).startsWith("<h1>")) {
                return asRead(lines.get(at + 1));
            }
        }
        return "";
    }

    /** The row of {@code tree}'s index naming the cluster labelled {@code label}, link or no link, as written. */
    static String indexRowFor(Path tree, String label) {
        String cell = escaped(label);
        return linesOf(tree.resolve(Deliverable.INDEX_FILE_NAME)).stream()
                .filter(line -> line.startsWith("<tr><td>" + cell + "</td>")
                        || (line.startsWith("<tr><td><a ") && line.contains("\">" + cell + "</a></td>")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no index row names '" + label + "'"));
    }

    /** The whole of {@code file}, read as the tree writes it. */
    static String textOf(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code text} as the tree escapes every value it did not write: the five characters as entities. */
    static String escaped(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** One line of a page with its tags taken off and the five entities read back. */
    private static String asRead(String line) {
        return line.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .strip();
    }

    private static List<String> linesOf(Path file) {
        return textOf(file).lines().toList();
    }
}
