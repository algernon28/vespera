package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code next-invocation.properties}: what the closing line asks for, written beside it for the control panel to read
 * (ADR-294 §1, §3).
 *
 * <p>The file is {@code java.util.Properties} in UTF-8 and holds no path and nothing read from a document. {@link
 * NextAction} gives the {@link Contents} from the branch that words the line, so the two cannot disagree; this class
 * only writes them and deletes the file an earlier invocation left.
 */
final class NextInvocationFile {

    private static final Logger LOG = LoggerFactory.getLogger(NextInvocationFile.class);

    /** The file's name, in the working directory beside the database file. */
    static final String FILE_NAME = "next-invocation.properties";

    /** The profile key whose wanting comes with the arrangement to approve. */
    private static final String APPROVAL_KEY = "arrangementApproved";

    private NextInvocationFile() {}

    /** The command the next act ends on, or {@link #NONE} where nothing is left to set. */
    enum Invocation {
        RUN,
        LABEL,
        NONE;

        String written() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The file's four keys, written as their lowercase names. An enum rather than literals because {@code arrangement}
     * is also the name of a persisted step, which only {@code StepNames} may write as a literal (ADR-157 §7).
     */
    private enum Key {
        INVOCATION,
        WANTS,
        ANSWER,
        ARRANGEMENT;

        String written() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What the file holds.
     *
     * @param invocation the command the next act ends on
     * @param wants the profile keys the closing line asks to be written, in the order it names them
     * @param answer whether the next act is answering the questions and recording them with {@code vespera label}
     * @param arrangement the short name of the arrangement to approve, present exactly where {@code wants} holds
     *     {@code arrangementApproved}
     */
    record Contents(Invocation invocation, List<String> wants, boolean answer, Optional<String> arrangement) {

        Contents {
            wants = List.copyOf(wants);
            if (arrangement.isPresent() != wants.contains(APPROVAL_KEY)) {
                throw new IllegalArgumentException(
                        "an arrangement is given exactly where " + APPROVAL_KEY + " is wanted: " + this);
            }
            if (answer && invocation != Invocation.LABEL) {
                throw new IllegalArgumentException("answers are wanted only of label: " + this);
            }
            if (invocation == Invocation.NONE && !wants.isEmpty()) {
                throw new IllegalArgumentException("nothing is wanted where nothing is left to set: " + this);
            }
        }
    }

    /**
     * Writes {@code contents} over any file there, whole. A file that cannot be written is logged at WARN and left
     * as it is, whole or cut short, so the closing line that follows stays the last line of output (ADR-294 §3).
     */
    static void write(Path workingDirectory, Contents contents) {
        Properties properties = new Properties();
        properties.setProperty(Key.INVOCATION.written(), contents.invocation().written());
        properties.setProperty(Key.WANTS.written(), String.join(" ", contents.wants()));
        properties.setProperty(Key.ANSWER.written(), Boolean.toString(contents.answer()));
        contents.arrangement().ifPresent(name -> properties.setProperty(Key.ARRANGEMENT.written(), name));
        Path file = workingDirectory.resolve(FILE_NAME);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        } catch (IOException cannotBeWritten) {
            LOG.warn("Could not write {}, so the control panel finds no next invocation to offer: {}", file,
                    cannotBeWritten.toString());
        }
    }

    /** Deletes the file an earlier invocation left, if there is one; a file that cannot be deleted fails the start. */
    static void deleteIn(Path workingDirectory) {
        Path file = workingDirectory.resolve(FILE_NAME);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("could not delete " + file, e);
        }
    }
}
