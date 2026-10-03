package io.algernon.vespera.pipeline;

import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.Deliverable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * What the page of a cluster nothing was written over says in place of writing (ADR-174), and where
 * a test finds that page in the tree an invocation wrote.
 *
 * <p><b>The sentences are copied out of the record, not out of the code</b>, so the code is held to
 * the record and not the other way round. Each is rendered prose for a reader of the tree, so it says
 * <em>group</em> where every name here says cluster (ADR-122), and names no kind of
 * {@link ClusterFaultKind}.
 *
 * <p><b>{@link #forKind} is an exhaustive {@code switch} with no {@code default}</b>, so a fifth kind
 * stops this test tree compiling until the record gives it a sentence, the same guard ADR-174 asks of
 * the code's own mapping.
 */
final class UnwrittenPage {

    /** A question longer than the room, counted by the serving engine or refused by it as too long. */
    static final String DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM = "*Nothing was written over this group: its"
            + " documents came to more than the writing model was given room to read at once.*";

    /** An answer that stopped at the length it was allowed. */
    static final String THE_ANSWER_REACHED_ITS_LENGTH_LIMIT = "*Nothing was written over this group: the"
            + " writing model's answer reached its length limit before it was finished, so it was not used.*";

    /** An answer that could not be read into the shape the call imposed. */
    static final String THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE = "*Nothing was written over this group: the"
            + " writing model's answer did not come back in the shape it was asked for, so it was not used.*";

    /** Writing that cited outside the documents sent, or cited nothing. */
    static final String THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN = "*Nothing was written over this group:"
            + " the writing model's answer cited documents it had not been given, or cited none, so it was"
            + " not used.*";

    /** A cluster none of whose members this invocation could send (ADR-121's first route). */
    static final String NO_DOCUMENT_COULD_BE_SENT = "*Nothing was written over this group: none of its"
            + " documents could be read and sent to the writing model.*";

    /** A cluster every sendable member of which the estimate found too long (ADR-121's second route). */
    static final String NO_DOCUMENT_FITS_THE_ROOM = "*Nothing was written over this group: each of its"
            + " documents that could be read was judged longer than the writing model was given room to"
            + " read at once, so none was sent.*";

    /** A cluster after the five turned-down answers that stopped the step (ADR-111). */
    static final String WRITING_STOPPED_BEFORE_IT = "*Nothing has been written over this group yet: writing"
            + " stopped before it reached this group. Running the same command again carries the writing"
            + " on.*";

    /** Every sentence ADR-174 fixes, in the order its table states them. */
    static final List<String> EVERY_SENTENCE = List.of(
            DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM,
            THE_ANSWER_REACHED_ITS_LENGTH_LIMIT,
            THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE,
            THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN,
            NO_DOCUMENT_COULD_BE_SENT,
            NO_DOCUMENT_FITS_THE_ROOM,
            WRITING_STOPPED_BEFORE_IT);

    /**
     * What the page of a cluster carrying a fault row says, by kind (ADR-174 §2). The {@code detail}
     * plays no part: it is never shown.
     */
    static String forKind(ClusterFaultKind kind) {
        return switch (kind) {
            case PROMPT_EVALUATION_CEILING -> DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM;
            case ANSWER_RAN_OUT_OF_ROOM -> THE_ANSWER_REACHED_ITS_LENGTH_LIMIT;
            case SCHEMA_VIOLATION -> THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE;
            case CITATION_NOT_IN_RANGE -> THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN;
        };
    }

    /**
     * Where the line saying why sits on a page nothing was written over: the third line, after the
     * heading and the blank line beneath it, which is where the writing itself sits on any other page.
     */
    private static final int THE_LINE_UNDER_THE_HEADING = 2;

    private UnwrittenPage() {}

    /** The tree the most recent invocation wrote under {@code generationRun} (ADR-103). */
    static Path treeOf(Path workingDirectory, String generationRun) {
        return workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(generationRun);
    }

    /**
     * The one cluster file in {@code tree} headed by {@code label}. A cluster nothing was written
     * over is headed by its 6a label (ADR-106), so this finds exactly the pages these tests are about.
     */
    static Path pageHeadedBy(Path tree, String label) {
        String heading = "# " + label;
        try (Stream<Path> files = Files.walk(tree)) {
            List<Path> pages = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".md"))
                    .filter(file -> !file.getFileName().toString().equals(Deliverable.INDEX_FILE_NAME))
                    .filter(file -> heading.equals(firstLineOf(file)))
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

    /** The line beneath the heading of {@code page}: the writing, or what stands in its place. */
    static String lineUnderTheHeadingOf(Path page) {
        List<String> lines = linesOf(page);
        return lines.size() > THE_LINE_UNDER_THE_HEADING ? lines.get(THE_LINE_UNDER_THE_HEADING) : "";
    }

    /** The row of {@code tree}'s index naming the cluster labelled {@code label}, link or no link. */
    static String indexRowFor(Path tree, String label) {
        return linesOf(tree.resolve(Deliverable.INDEX_FILE_NAME)).stream()
                .filter(line -> line.startsWith("| " + label + " |") || line.startsWith("| [" + label + "]("))
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

    private static String firstLineOf(Path file) {
        List<String> lines = linesOf(file);
        return lines.isEmpty() ? "" : lines.getFirst();
    }

    private static List<String> linesOf(Path file) {
        return textOf(file).lines().toList();
    }
}
