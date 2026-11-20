package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Optional;

/**
 * Keeps WSL from shutting its distribution down while idle (ADR-421): on Windows, writes {@code .wslconfig} in the
 * profile folder, holding {@code [general]} and {@code instanceIdleTimeout=-1}, where none is there, and reads one that
 * is there for that setting, never changing it. It runs no command and takes no lock; no line it gives refuses a start.
 */
final class WslIdleTimeout {

    /**
     * WSL's configuration file, in the Windows profile folder and not in the working directory, so it is not one of the
     * files README's "Where things live" names; its name is not held in a {@code FILE_NAME} constant, which
     * {@code docs/check-claims.mjs} reads as a file of the working directory.
     */
    static final String NAME_IN_THE_PROFILE_FOLDER = ".wslconfig";

    /** The file Vespera writes: two lines ended by CR LF, in ASCII, 35 bytes (ADR-421 §2). */
    private static final String WRITTEN = "[general]\r\ninstanceIdleTimeout=-1\r\n";

    private static final String THE_COST = "so WSL may shut its distribution down while idle, and Docker and the"
            + " containers with it, in the middle of a run.";

    private static final String THE_RESTART =
            "then run wsl --shutdown, which stops every WSL distribution and the containers in them.";

    private WslIdleTimeout() {
    }

    /**
     * Sees to {@code .wslconfig} in {@code profileFolder} (ADR-421 §2, §3).
     *
     * @param profileFolder the Windows profile folder, null where none is known
     * @return the line to log (ADR-421 §5); empty where the file sets the setting
     */
    static Optional<String> keepTheDistributionUp(Path profileFolder) {
        if (profileFolder == null) {
            return Optional.of("Vespera cannot tell where the Windows profile folder is, so it cannot see whether"
                    + " %UserProfile%\\.wslconfig sets instanceIdleTimeout=-1 under [general]; without it WSL may"
                    + " shut its distribution down while idle, and Docker and the containers with it, in the middle of"
                    + " a run. See that it does, " + THE_RESTART);
        }
        Path file = profileFolder.resolve(NAME_IN_THE_PROFILE_FOLDER);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.writeString(file, WRITTEN, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                return Optional.of("Vespera wrote " + file + ", setting instanceIdleTimeout=-1 under [general], so"
                        + " that WSL does not shut its distribution down while idle, and Docker and the containers with"
                        + " it. It applies from WSL's next start; if WSL is running now, wsl --shutdown starts it"
                        + " afresh, and stops every WSL distribution and the containers in them.");
            } catch (FileAlreadyExistsException appeared) {
                // Written meanwhile by another: one that is there, and read as such.
            } catch (IOException | RuntimeException cannotWrite) {
                return Optional.of("Vespera could not write " + file + " (" + reason(cannotWrite) + "), " + THE_COST
                        + " Create it with the two lines [general] and instanceIdleTimeout=-1, " + THE_RESTART);
            }
        }
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException cannotRead) {
            return Optional.of("Vespera could not read " + file + " (" + reason(cannotRead) + ") to see whether it"
                    + " sets instanceIdleTimeout=-1 under [general]; without it WSL may shut its distribution down"
                    + " while idle, and Docker and the containers with it, in the middle of a run. See that it does, "
                    + THE_RESTART);
        }
        if (sets(text)) {
            return Optional.empty();
        }
        return Optional.of(file + " does not set instanceIdleTimeout=-1 under [general], " + THE_COST
                + " Vespera changes no file that is there: add it yourself, " + THE_RESTART);
    }

    /** Whether the last value given to {@code instanceIdleTimeout} under {@code [general]} is {@code -1} (ADR-421 §3). */
    private static boolean sets(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String section = "";
        String value = null;
        for (String raw : text.split("\r\n|\n|\r", -1)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
                continue;
            }
            int equals = line.indexOf('=');
            if (equals >= 0 && section.equals("general")
                    && line.substring(0, equals).strip().equalsIgnoreCase("instanceIdleTimeout")) {
                value = line.substring(equals + 1).strip();
            }
        }
        return "-1".equals(value);
    }

    private static String reason(Exception refused) {
        String message = refused.getMessage();
        return message == null || message.isBlank() ? refused.getClass().getSimpleName() : message;
    }
}
