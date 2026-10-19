package io.algernon.vespera;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs git for a test's fixture repository, in the folder it is given and nowhere else (ADR-290).
 *
 * <p>Git reads {@code GIT_DIR}, {@code GIT_WORK_TREE}, {@code GIT_INDEX_FILE} and the rest of the {@code GIT_*}
 * variables before it looks at the folder it runs in, so a fixture started from a process that carries them, as a
 * git hook's children do, would commit into the repository they name. Every command here starts from an
 * environment without any variable that begins {@code GIT_}.
 */
final class FixtureGit {

    private FixtureGit() {
    }

    /** Whether git starts at all on this machine. */
    static boolean runs() {
        try {
            git(Path.of("."), "--version");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Runs git in {@code directory} with an identity and no signing of its own, so a machine's configuration cannot
     * stop a commit, and returns its output with the surrounding whitespace trimmed.
     */
    static String git(Path directory, String... arguments) {
        return git(directory, System.getenv(), arguments);
    }

    /** As {@link #git(Path, String...)}, the process starting from {@code inherited} where it would from this one's. */
    static String git(Path directory, Map<String, String> inherited, String... arguments) {
        List<String> command = new ArrayList<>(List.of(
                "git",
                "-c", "user.name=Vespera test",
                "-c", "user.email=test@vespera.invalid",
                "-c", "commit.gpgsign=false",
                "-c", "init.defaultBranch=main"));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(inherited);
        builder.environment().keySet().removeIf(name -> name.startsWith("GIT_"));
        try {
            Process process = builder.start();
            String output;
            try (InputStream out = process.getInputStream()) {
                output = new String(out.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (process.waitFor() != 0) {
                throw new IllegalStateException(String.join(" ", command) + " failed: " + output);
            }
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
