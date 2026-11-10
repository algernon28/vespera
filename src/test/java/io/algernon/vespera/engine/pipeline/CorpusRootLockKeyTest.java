package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
}
