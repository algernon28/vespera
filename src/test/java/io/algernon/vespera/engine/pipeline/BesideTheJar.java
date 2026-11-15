package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.SpringApplication;

/**
 * STAND-IN, to be deleted by the change that builds ADR-397. It exists so that the tests of ADR-397 compile before
 * {@code src/main} holds the class, and fail at their claims rather than at the compiler: every method answers as if
 * nothing were built, writing nothing, pointing Spring nowhere and finding the folder nowhere. The change that writes
 * {@code src/main/java/io/algernon/vespera/engine/pipeline/BesideTheJar.java} deletes this file and
 * {@link BesideTheJarUnwritableException}'s stand-in beside it, in the same commit.
 */
public final class BesideTheJar {

    /** Where the jar carries its shipped copies (ADR-397 §2); empty in the stand-in. */
    static final String RESOURCE_FOLDER = "";

    /** What the name of a shipped copy written beside a file that differs ends in (ADR-397 §5). */
    static final String NEW_SUFFIX = ".new";

    /** A file beside the jar that differs from its shipped copy, the keys that differ, and the line that says so. */
    record Drift(String file, List<String> keys, String line) {
    }

    private final Path folder;

    BesideTheJar(Path folder) {
        this.folder = folder;
    }

    /** The folder beside the jar for a code source (ADR-397 §1); the current directory in the stand-in. */
    static Path folderOf(Path codeSource, Path currentDirectory) {
        return currentDirectory;
    }

    Path folder() {
        return folder;
    }

    /** Writes each missing file from its shipped copy (ADR-397 §3); writes nothing in the stand-in. */
    List<String> writeWhatIsMissing() {
        return List.of();
    }

    /** Writes {@code <name>.new} beside each file that differs, and names them (ADR-397 §5); none in the stand-in. */
    List<Drift> writeNewWhereDrifted() {
        return List.of();
    }

    /** Points Spring at the two files beside the jar (ADR-397 §4); does nothing in the stand-in. */
    void pointSpringAt(SpringApplication application) {
        // the stand-in points Spring nowhere
    }

    /** The compose files beside the jar (ADR-397 §6); the jar's class path copy in the stand-in. */
    ComposeFiles composeFiles() throws IOException {
        return ComposeFiles.fromTheClasspath();
    }
}
