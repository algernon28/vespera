package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The main class of the jar {@code TheChildIsGivenTheLockFolderAbsoluteTest} starts as the control panel's child: it
 * writes the value of {@value #VARIABLE} in its environment, or {@value #NONE} where it has none, into {@value
 * #FILE_WRITTEN} in its current directory, which is the working directory the control panel passed, and exits 0.
 */
public final class ALockFolderWritingChildForTests {

    /** The variable the child is asked about. */
    public static final String VARIABLE = "VESPERA_CORPUS_ROOT_LOCK_FOLDER";

    /** The file the child writes, in its current directory. */
    public static final String FILE_WRITTEN = "lock-folder-given.txt";

    /** What the file holds where the child's environment has no such variable. */
    public static final String NONE = "(none)";

    private ALockFolderWritingChildForTests() {
    }

    public static void main(String[] args) throws IOException {
        String given = System.getenv(VARIABLE);
        Files.writeString(Path.of("").toAbsolutePath().resolve(FILE_WRITTEN), given == null ? NONE : given,
                StandardCharsets.UTF_8);
    }
}
