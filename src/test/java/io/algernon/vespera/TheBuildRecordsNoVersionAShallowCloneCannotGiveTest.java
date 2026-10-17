package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the build's stamping script writes for each module in a shallow clone (ADR-272, amending ADR-058).
 *
 * <p>The oldest commit a shallow clone holds is held without its parents, and git reports it as adding every file
 * in its tree, so {@code git log -1 -- <module path>} answers with it for every module whose last change lies
 * before the clone's window. ADR-272 has the script write {@code unknown} for such a module, which {@code
 * ImplementationVersions} refuses at mint time, and keep the SHA of a module answered by a commit that has its
 * parent in the clone.
 *
 * <p>Runs the real {@code .mvn/scripts/implementation-versions.groovy} through {@link GroovyShell}, binding {@code
 * project} and {@code log} as gmavenplus does, against clones of a repository of three linear commits built once
 * for the class with git: the first adds a file to each of {@code corpus}, {@code ledger} and {@code pipeline},
 * the second changes {@code corpus}, the third changes {@code pipeline}. Each test clones it to the depth it is
 * about, so what the script writes is a fact of the clone and not of this checkout's history.
 *
 * <p>Written before the change it pins: against the script as ADR-058 left it, the two shallow tests fail, the
 * clone one commit deep writing the latest commit for all three modules and the clone two commits deep writing
 * the second commit for {@code corpus} and {@code ledger}, with no warning in either. The full clone passes before
 * and after, and fails a script that writes {@code unknown} for a first commit outside a shallow clone.
 *
 * <p>Needs git on the path, as the build does to stamp the modules; without it every test aborts.
 */
@Epic("Ledger")
@Feature("Implementation version")
@Issue("543")
@Link(name = "ADR-272", url = Adr.A_SHALLOW_CLONES_BOUNDARY_IS_WRITTEN_AS_UNKNOWN, type = "adr")
class TheBuildRecordsNoVersionAShallowCloneCannotGiveTest {

    /** The script the build runs when it generates its resources, read from the checkout the tests run in. */
    private static final Path THE_SCRIPT = Path.of(".mvn", "scripts", "implementation-versions.groovy");

    /** Where the script looks for modules, under the base folder it is given: one folder each. */
    private static final String MODULES = "src/main/java/io/algernon/vespera";

    /** Where the script writes, under the build folder it is given. */
    private static final String WRITTEN =
            "generated-resources/implementation-versions/implementation-versions.properties";

    /** What the script writes for a module it has no version for, and {@code ImplementationVersions} refuses. */
    private static final String UNKNOWN = "unknown";

    /** A clone holding only the latest of the three commits. */
    private static final int ONLY_THE_LATEST_COMMIT = 1;

    /** A clone holding the latest two of the three commits. */
    private static final int THE_LATEST_TWO_COMMITS = 2;

    @TempDir
    static Path folder;

    private static Path origin;

    /** Adds a file to each of the three modules. */
    private static String first;

    /** Changes {@code corpus}. */
    private static String second;

    /** Changes {@code pipeline}. */
    private static String third;

    @BeforeAll
    static void threeCommits() throws IOException {
        assumeTrue(gitRuns(), "git is not on the path, and the build asks it for every module's version");
        origin = Files.createDirectories(folder.resolve("origin"));
        git(origin, "init", "--quiet");
        for (String module : List.of("corpus", "ledger", "pipeline")) {
            write(origin, module, "first");
        }
        first = commit("adds the three modules");
        write(origin, "corpus", "second");
        second = commit("changes corpus");
        write(origin, "pipeline", "third");
        third = commit("changes pipeline");
    }

    @Test
    @Story("A build records no version it cannot trace to the commit that last changed the module")
    @DisplayName("A clone of the whole history records each module with the commit that last changed it")
    void aFullCloneRecordsEachModulesLastCommit() throws IOException {
        Path clone = clone("full", null);
        Written written = stamp(clone);

        claim(
                "in a clone holding every commit, each of the three modules is recorded with the commit that last"
                        + " changed it: corpus with the second, ledger with the first, which has no parent because it"
                        + " is the repository's first commit, and pipeline with the third",
                () -> assertThat(written.versions())
                        .containsExactly(
                                Map.entry("corpus", second), Map.entry("ledger", first), Map.entry("pipeline", third)));
        claim("and the build warns of nothing", () -> assertThat(written.warnings()).isEmpty());
    }

    @Test
    @Story("A build records no version it cannot trace to the commit that last changed the module")
    @DisplayName("A clone holding only the latest commit records no version for any module, and warns once for each")
    void aCloneOfTheLatestCommitRecordsEveryModuleAsUnknown() throws IOException {
        Path clone = clone("latest", ONLY_THE_LATEST_COMMIT);
        Written written = stamp(clone);

        claim(
                "in a clone holding only the latest of the three commits, that commit is held without its parent"
                        + " and git reports it as adding every file, so no module can be told from it: all three are"
                        + " recorded as unknown, the one that commit really changed included",
                () -> assertThat(written.versions())
                        .containsExactly(
                                Map.entry("corpus", UNKNOWN), Map.entry("ledger", UNKNOWN), Map.entry("pipeline", UNKNOWN)));
        claim(
                "and the build warns once for each of the three, in the order the modules are recorded",
                () -> assertThat(written.warnings())
                        .containsExactly(warningFor("corpus"), warningFor("ledger"), warningFor("pipeline")));
    }

    @Test
    @Story("A build records no version it cannot trace to the commit that last changed the module")
    @DisplayName("A clone holding the latest two commits keeps the version of the module the latest one changed, and records none for the other two")
    void aCloneTwoCommitsDeepKeepsTheModuleChangedInsideIt() throws IOException {
        Path clone = clone("latest-two", THE_LATEST_TWO_COMMITS);
        Written written = stamp(clone);

        claim(
                "in a clone holding the latest two of the three commits, pipeline keeps the latest commit, whose"
                        + " parent is in the clone, while corpus and ledger are answered by the older commit, held"
                        + " without its parent and so reported as adding every file, and are recorded as unknown",
                () -> assertThat(written.versions())
                        .containsExactly(
                                Map.entry("corpus", UNKNOWN), Map.entry("ledger", UNKNOWN), Map.entry("pipeline", third)));
        claim(
                "and the build warns for corpus and ledger, in that order, and not for pipeline",
                () -> assertThat(written.warnings()).containsExactly(warningFor("corpus"), warningFor("ledger")));
    }

    /** The line the script logs for a module it records as unknown, whatever the reason. */
    private static String warningFor(String module) {
        return "No commit found for module " + module + "; recording it as unknown";
    }

    /** What one evaluation of the script wrote, in the file's order, and what it warned of. */
    private record Written(Map<String, String> versions, List<String> warnings) {
    }

    /** Stands in for the Maven log gmavenplus binds as {@code log}: warnings kept, the rest dropped. */
    static final class RecordingLog {

        private final List<String> warnings = new ArrayList<>();

        public void warn(CharSequence line) {
            warnings.add(line.toString());
        }

        public void info(CharSequence line) {
            // the summary line the script ends with is not what these tests are about
        }
    }

    /** Evaluates the real script with {@code clone} as the project's base folder and a build folder inside it. */
    private static Written stamp(Path clone) throws IOException {
        Path build = clone.resolve("target");
        RecordingLog log = new RecordingLog();
        Binding binding = new Binding();
        binding.setVariable(
                "project", Map.of("basedir", clone.toFile(), "build", Map.of("directory", build.toString())));
        binding.setVariable("log", log);
        new GroovyShell(binding).evaluate(THE_SCRIPT.toFile());

        Map<String, String> versions = new LinkedHashMap<>();
        for (String line : Files.readAllLines(build.resolve(WRITTEN), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                String[] moduleAndVersion = line.split("=", 2);
                versions.put(moduleAndVersion[0], moduleAndVersion[1]);
            }
        }
        return new Written(versions, List.copyOf(log.warnings));
    }

    /** Clones the origin through {@code file://}, which honours a depth where a plain path does not. */
    private static Path clone(String name, Integer depth) {
        Path clone = folder.resolve(name);
        List<String> arguments = new ArrayList<>(List.of("clone", "--quiet"));
        if (depth != null) {
            arguments.addAll(List.of("--depth", Integer.toString(depth)));
        }
        arguments.addAll(List.of(origin.toUri().toString(), clone.toString()));
        git(folder, arguments.toArray(String[]::new));
        return clone;
    }

    private static void write(Path repository, String module, String content) throws IOException {
        Path file = repository.resolve(MODULES).resolve(module).resolve("Changed.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String commit(String message) {
        git(origin, "add", "--all");
        git(origin, "commit", "--quiet", "--message", message);
        return git(origin, "rev-parse", "HEAD");
    }

    private static boolean gitRuns() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Runs git with an identity and no signing of its own, so a machine's configuration cannot stop a commit. */
    private static String git(Path directory, String... arguments) {
        List<String> command = new ArrayList<>(List.of(
                "git",
                "-c", "user.name=Vespera test",
                "-c", "user.email=test@vespera.invalid",
                "-c", "commit.gpgsign=false",
                "-c", "init.defaultBranch=main"));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
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
