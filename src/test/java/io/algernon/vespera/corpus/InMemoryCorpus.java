package io.algernon.vespera.corpus;

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

/**
 * A corpus built in memory, for the tests that ask about the walk's own logic (ADR-065).
 *
 * <p>Two things it buys that a real directory cannot. A denied directory is a {@code chmod}, so the
 * unprocessable anomaly is a test everywhere rather than a skip on any volume, account or container
 * that will not do ACLs. And a tree costs no disk, so the checkpoint ordinals can be exercised at a
 * scale where the arithmetic can actually break rather than at the three or four entries a
 * {@code @TempDir} fixture affords.
 *
 * <p>It models Linux, and that is not an oversight. This tier is evidence about the walk and never
 * about a platform (ADR-065), so the least Windows-looking model is the honest choice: nothing here
 * can be mistaken for a measurement of NTFS. Where a decision does rest on how NTFS behaves — path
 * identity, unencodable names, junctions, traversal order — the test belongs in {@link WalkTest},
 * against a real filesystem.
 */
final class InMemoryCorpus implements AutoCloseable {

    private final FileSystem fileSystem;
    private final Path root;

    private InMemoryCorpus(FileSystem fileSystem, Path root) {
        this.fileSystem = fileSystem;
        this.root = root;
    }

    /** An empty corpus. {@code name} only has to be unique among the filesystems a test opens. */
    static InMemoryCorpus open(String name) throws IOException {
        FileSystem fileSystem = MemoryFileSystemBuilder.newLinux().build(name);
        Path root = Files.createDirectories(fileSystem.getPath("/corpus"));
        return new InMemoryCorpus(fileSystem, root);
    }

    Path root() {
        return root;
    }

    /** Writes a file, creating whatever directories its path implies. */
    InMemoryCorpus file(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this;
    }

    InMemoryCorpus directory(String relative) throws IOException {
        Files.createDirectories(root.resolve(relative));
        return this;
    }

    /**
     * Denies all access to a directory, so the walk meets it and cannot list it.
     *
     * <p>The whole of what {@code AclFileAttributeView} needed sixty lines and three skip conditions
     * to attempt on Windows, and it holds regardless of who is running the build.
     */
    Path denyAccessTo(String relative) throws IOException {
        Path denied = root.resolve(relative);
        Files.setPosixFilePermissions(denied, EnumSet.noneOf(PosixFilePermission.class));
        return denied;
    }

    @Override
    public void close() throws IOException {
        fileSystem.close();
    }
}
