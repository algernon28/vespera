package io.algernon.vespera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.core.io.ClassPathResource;

/**
 * Where the jar carries its shipped copies, the files it writes into the folder beside the jar where they are missing
 * (ADR-397 §2): under {@value #FOLDER} on the class path, where Spring's configuration loading never reads, and so the
 * one place a test reads the configuration the jar ships; and where the repository keeps the source of each.
 *
 * <p>Named once here, so that every test reading what ships reads the same place, and a move of the folder is one
 * edit. Lives in the root package for the reason {@link TestSteps} does.
 */
public final class TheShippedCopy {

    /** The resource folder the jar carries its shipped copies in. */
    public static final String FOLDER = "beside-the-jar/";

    /** The shipped copy of Vespera's own {@code application.yaml}, as a class path resource's name. */
    public static final String APPLICATION_YAML = FOLDER + "application.yaml";

    /** The YAML files beside the jar that have a shipped copy, by their names there, in the order they are written. */
    public static final List<String> YAML_FILES = List.of("application.yaml", "compose.yaml", "compose.gpu.yaml");

    /** The build context {@code compose.yaml} names relative to itself, every file of which has a shipped copy. */
    public static final String BUILD_CONTEXT = "docker";

    /** The repository's root, where Maven runs the tests from, and which holds the sources of the shipped copies. */
    public static final Path REPOSITORY = Path.of("").toAbsolutePath();

    /**
     * Where the repository keeps the source of {@code application.yaml}'s shipped copy (ADR-397 §2): below
     * {@code src/main}, in no resource folder Spring reads, and not at the root, which is the current directory of every
     * test and a location Spring reads by default, above {@code application-test.yaml}.
     */
    public static final Path APPLICATION_YAML_SOURCE = REPOSITORY.resolve("src/main/beside-the-jar/application.yaml");

    private TheShippedCopy() {
    }

    /** The shipped copy of the file {@code name} beside the jar, {@code /} separating its folders. */
    public static ClassPathResource of(String name) {
        return new ClassPathResource(FOLDER + name);
    }

    /**
     * The file of the repository the shipped copy of {@code name} is built from: {@link #APPLICATION_YAML_SOURCE} for
     * {@code application.yaml}, and the file of that name below the root for the compose files and the build context,
     * where they lie today.
     */
    public static Path sourceOf(String name) {
        return name.equals("application.yaml") ? APPLICATION_YAML_SOURCE : REPOSITORY.resolve(name);
    }

    /**
     * The name, below the folder beside the jar, of every file the jar keeps there, as the repository holds them: the
     * three YAML files, then each file of the build context in the order of its names.
     */
    public static List<String> namesInTheRepository() throws IOException {
        List<String> names = new ArrayList<>(YAML_FILES);
        for (String name : filesUnder(REPOSITORY.resolve(BUILD_CONTEXT))) {
            names.add(BUILD_CONTEXT + "/" + name);
        }
        return List.copyOf(names);
    }

    /** Every regular file below {@code folder}, by its name below it with {@code /} between folders; none where it is not there. */
    public static List<String> filesUnder(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> folder.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }
}
