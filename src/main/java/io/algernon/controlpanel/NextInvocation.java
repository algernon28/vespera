package io.algernon.controlpanel;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * What the jar's last successful invocation says the next one wants, read from {@code next-invocation.properties}
 * (ADR-294 §1, §4).
 *
 * <p>Parsed here with {@code java.base} alone, as a holder line is, because the control panel links no class of the
 * jar (ADR-285 §3). A file not in the shape ADR-294 §1 gives is refused with {@link IllegalArgumentException}, and the
 * control panel offers nothing from it.
 *
 * @param invocation the command the next act ends on
 * @param wants the profile keys to write, in the order the closing line names them
 * @param answer whether the questions are to be answered and recorded with {@code vespera label}
 * @param arrangement the short name of the arrangement to approve, where {@code wants} holds {@code
 *     arrangementApproved}
 */
public record NextInvocation(Invocation invocation, List<String> wants, boolean answer, Optional<String> arrangement) {

    /** The file's name, in the working directory beside the database file. */
    public static final String FILE_NAME = "next-invocation.properties";

    private static final String APPROVAL_KEY = "arrangementApproved";

    /** The command the next act ends on, or {@link #NONE} where there is no next act to offer. */
    public enum Invocation {
        RUN,
        LABEL,
        NONE
    }

    /**
     * The file in {@code workingDirectory}, or empty where there is none.
     *
     * @throws IllegalArgumentException where the file is not in ADR-294 §1's shape, naming the file
     * @throws IOException where the file exists and cannot be read
     */
    public static Optional<NextInvocation> read(Path workingDirectory) throws IOException {
        Path file = workingDirectory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException malformed) {
            throw unreadable("it is not a properties file", malformed);
        }
        Invocation invocation = switch (required(properties, "invocation")) {
            case "run" -> Invocation.RUN;
            case "label" -> Invocation.LABEL;
            case "none" -> Invocation.NONE;
            default -> throw unreadable("invocation is not run, label or none", null);
        };
        String wantsText = required(properties, "wants").strip();
        List<String> wants = wantsText.isEmpty() ? List.of() : List.of(wantsText.split("\\s+"));
        boolean answer = switch (required(properties, "answer")) {
            case "true" -> true;
            case "false" -> false;
            default -> throw unreadable("answer is not true or false", null);
        };
        Optional<String> arrangement = Optional.ofNullable(properties.getProperty("arrangement"));
        if (arrangement.isPresent() != wants.contains(APPROVAL_KEY)) {
            throw unreadable("arrangement is given exactly where " + APPROVAL_KEY + " is wanted", null);
        }
        if (answer && invocation != Invocation.LABEL) {
            throw unreadable("answer is true without invocation=label", null);
        }
        if (invocation == Invocation.NONE && !wants.isEmpty()) {
            throw unreadable("invocation is none with keys wanted", null);
        }
        return Optional.of(new NextInvocation(invocation, wants, answer, arrangement));
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw unreadable(key + " is missing", null);
        }
        return value.strip();
    }

    private static IllegalArgumentException unreadable(String why, Throwable cause) {
        return new IllegalArgumentException(FILE_NAME + " is not readable: " + why, cause);
    }

    /** Held as an immutable copy, so a record read is never changed from outside. */
    public NextInvocation {
        wants = List.copyOf(wants);
    }
}
