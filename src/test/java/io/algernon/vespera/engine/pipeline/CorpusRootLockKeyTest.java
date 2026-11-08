package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestLinks;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The corpus-root lock is keyed by the root's real path, so one directory however it is spelled or reached is
 * one root, and its file is named by sixteen hexadecimal digits of the key's SHA-256 (ADR-279 §2, §3).
 *
 * <p>Keys are compared as text, never as {@link Path}s: on Windows {@code Path.equals} folds case, so two keys
 * that differ only in case would compare equal there while naming two files, since the file name hashes the
 * text.
 */
@Epic("Architecture")
@Feature("One invocation per corpus root")
@Issue("559")
@Link(name = "ADR-279", url = Adr.ONE_VESPERA_RUN_PER_CORPUS_ROOT, type = "adr")
class CorpusRootLockKeyTest {

    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    /** How many hexadecimal digits of the SHA-256 name a file. */
    private static final int DIGITS = 16;

    @Test
    @Story("One corpus root is one directory, however it is named")
    @DisplayName("A trailing separator, a dot and a sub-folder followed back up spell one directory, keyed by its real path")
    void spellingsOfOneDirectoryGiveOneKey(@TempDir Path temporary) throws IOException {
        Path root = Files.createDirectories(temporary.resolve("corpus").resolve("sub")).getParent();
        String realPath = root.toRealPath().toString();

        for (Path spelling : List.of(
                root,
                Path.of(root + File.separator),
                root.resolve("."),
                root.resolve("sub").resolve(".."))) {
            claim("the root spelled " + spelling + " keys as its real path " + realPath,
                    () -> assertThat(CorpusRootLock.key(spelling).toString()).isEqualTo(realPath));
        }
    }

    @Test
    @Story("One corpus root is one directory, however it is named")
    @DisplayName("On a file system that folds case, the root spelled in capitals keys as its real path")
    void caseSpellingsOfOneDirectoryGiveOneKey(@TempDir Path temporary) throws IOException {
        Path root = Files.createDirectories(temporary.resolve("Corpus"));
        Path inCapitals = Path.of(root.toString().toUpperCase(Locale.ROOT));
        assumeTrue(!inCapitals.toString().equals(root.toString()) && Files.isDirectory(inCapitals),
                "this file system tells names apart by case, so the capitals name another directory");

        claim("the root in capitals keys as the real path, in the case the file system holds it",
                () -> assertThat(CorpusRootLock.key(inCapitals).toString()).isEqualTo(root.toRealPath().toString()));
        claim("and so names the same file as the root spelled as created",
                () -> assertThat(CorpusRootLock.fileName(CorpusRootLock.key(inCapitals)))
                        .isEqualTo(CorpusRootLock.fileName(CorpusRootLock.key(root))));
    }

    @Test
    @Story("One corpus root is one directory, however it is named")
    @DisplayName("Two directories give two keys, and a root inside another is a root of its own")
    void twoDirectoriesGiveTwoKeys(@TempDir Path temporary) throws IOException {
        Path one = Files.createDirectories(temporary.resolve("one"));
        Path another = Files.createDirectories(temporary.resolve("another"));
        Path inside = Files.createDirectories(one.resolve("inside"));

        claim("two sibling directories give two keys and two files",
                () -> assertThat(CorpusRootLock.fileName(CorpusRootLock.key(one)))
                        .isNotEqualTo(CorpusRootLock.fileName(CorpusRootLock.key(another))));
        claim("and a root inside another gives a key and a file of its own",
                () -> assertThat(CorpusRootLock.fileName(CorpusRootLock.key(inside)))
                        .isNotEqualTo(CorpusRootLock.fileName(CorpusRootLock.key(one))));
    }

    @Test
    @Story("One corpus root is one directory, however it is named")
    @DisplayName("On Windows, a junction to the root keys as the root")
    void aJunctionToTheRootGivesTheRootsKey(@TempDir Path temporary) throws Exception {
        assumeTrue(WINDOWS, "a junction is a Windows link");
        Path root = Files.createDirectories(temporary.resolve("corpus"));
        Path junction = temporary.resolve("through-a-junction");
        assumeTrue(junction(junction, root), "could not create a junction in this environment");

        claim("the root reached through the junction keys as the root's real path",
                () -> assertThat(CorpusRootLock.key(junction).toString()).isEqualTo(root.toRealPath().toString()));
    }

    @Test
    @Story("One corpus root is one directory, however it is named")
    @DisplayName("A root reached through a link on the way keys as the root")
    void aLinkOnTheWayGivesTheRootsKey(@TempDir Path temporary) throws IOException {
        Path parent = Files.createDirectories(temporary.resolve("archive"));
        Path root = Files.createDirectories(parent.resolve("corpus"));
        Path link = temporary.resolve("linked-archive");
        assumeTrue(TestLinks.make(link, parent), "could not create a soft link in this environment");

        claim("the root named through a link to its parent keys as the root's real path",
                () -> assertThat(CorpusRootLock.key(link.resolve("corpus")).toString())
                        .isEqualTo(root.toRealPath().toString()));
    }

    @Test
    @Story("One corpus root is one directory, however it is named")
    @DisplayName("A root that is not there keys by its absolute, normalised path")
    void aRootThatIsNotThereKeysByItsAbsoluteNormalisedPath(@TempDir Path temporary) {
        Path missing = temporary.resolve("gone");
        Path spelledWithDots = temporary.resolve("elsewhere").resolve("..").resolve("gone");
        Path relative = Path.of("no-such-corpus-root-of-adr-279");

        claim("a missing root spelled through a folder and back keys as the missing root, made absolute and"
                        + " normalised",
                () -> assertThat(CorpusRootLock.key(spelledWithDots).toString())
                        .isEqualTo(missing.toAbsolutePath().normalize().toString()));
        claim("and a missing relative root keys against the current directory",
                () -> assertThat(CorpusRootLock.key(relative).toString())
                        .isEqualTo(relative.toAbsolutePath().normalize().toString()));
    }

    @Test
    @Story("The lock's file lies outside every working directory")
    @DisplayName("A root's file is named by sixteen digits of its key's SHA-256, so never vespera.lock or vespera.db")
    void theFileIsNamedBySixteenDigitsOfTheKeysSha256() {
        Map<String, String> fileNameOfEachKey = new LinkedHashMap<>();
        for (String key : List.of("corpus", WorkingDirectoryLock.FILE_NAME, "vespera.db")) {
            fileNameOfEachKey.put(key, CorpusRootLock.fileName(Path.of(key)));
        }

        claim("the file for each key is the first " + DIGITS + " lowercase hexadecimal digits of the SHA-256 of"
                        + " the key's text in UTF-8, then .lock: the digests as sha256sum prints them for the"
                        + " texts corpus, vespera.lock and vespera.db, so a key named like either file the agent"
                        + " guard knows a working directory by is named like neither",
                () -> assertThat(fileNameOfEachKey)
                        .containsExactly(
                                Map.entry("corpus", "6b17b8fcc411a533.lock"),
                                Map.entry(WorkingDirectoryLock.FILE_NAME, "f49ba92c65cb3412.lock"),
                                Map.entry("vespera.db", "318b2c61fe4d4597.lock")));
    }

    /** Makes a junction with {@code mklink /J}, which needs no privilege, as {@link TestLinks} does on Windows. */
    private static boolean junction(Path link, Path target) throws Exception {
        Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true)
                .start();
        mklink.getInputStream().transferTo(OutputStream.nullOutputStream());
        return mklink.waitFor() == 0;
    }
}
