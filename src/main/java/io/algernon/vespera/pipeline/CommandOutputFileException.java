package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The temporary file a command's output goes to cannot be created or cannot be read (ADR-326). Deliberately not an
 * {@link IOException}: one is read as a program that cannot be started, and this is not that.
 */
final class CommandOutputFileException extends Exception {

    private final transient List<String> command;
    private final transient Path folder;

    CommandOutputFileException(List<String> command, Path folder, IOException cause) {
        super("the output file of " + String.join(" ", command) + " in " + folder + " failed", cause);
        this.command = List.copyOf(command);
        this.folder = folder;
    }

    /** The command as it was given. */
    List<String> command() {
        return command;
    }

    /** The folder the file was to be created in. */
    Path folder() {
        return folder;
    }
}
