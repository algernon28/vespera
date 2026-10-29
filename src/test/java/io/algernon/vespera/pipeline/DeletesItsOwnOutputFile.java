package io.algernon.vespera.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * A program that deletes every file in the folder its first argument names, which is where the runner created the file
 * its own output goes to, then writes the file its second argument names, and exits 0, for {@link
 * SystemProcessRunnerTest} (ADR-326). Run where a file another process holds open can be deleted, which is not Windows.
 */
final class DeletesItsOwnOutputFile {

    private DeletesItsOwnOutputFile() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> files;
        try (Stream<Path> listed = Files.list(Path.of(args[0]))) {
            files = listed.filter(Files::isRegularFile).toList();
        }
        for (Path file : files) {
            Files.delete(file);
        }
        Files.writeString(Path.of(args[1]), "ran");
        System.exit(0);
    }
}
