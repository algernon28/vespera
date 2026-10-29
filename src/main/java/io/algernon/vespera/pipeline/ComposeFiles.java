package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The compose files Vespera starts the sidecars from, and the Docling image each builds (ADR-323 §2, §4).
 *
 * <p>The jar carries {@code compose.yaml}, {@code compose.gpu.yaml} and everything under {@code docker/} as
 * resources under {@code sidecars/}, byte for byte; {@link #fromTheClasspath} writes them out to a new temporary
 * folder, so that the build context {@code compose.yaml} names relative to itself resolves, and {@link #close}
 * deletes that folder. {@link #in} reads files where they already lie and {@link #close} deletes none of them: only
 * a folder this class wrote out is ever deleted.
 */
final class ComposeFiles implements AutoCloseable {

    static final String COMPOSE_FILE = "compose.yaml";

    static final String GPU_FILE = "compose.gpu.yaml";

    /** Where the jar's resources hold the files. */
    private static final String RESOURCE_FOLDER = "sidecars/";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private final Path folder;
    private final boolean writtenByThis;

    private ComposeFiles(Path folder, boolean writtenByThis) {
        this.folder = folder;
        this.writtenByThis = writtenByThis;
    }

    /** Files read where they lie, in {@code folder}; none is deleted when this is closed. */
    static ComposeFiles in(Path folder) {
        return new ComposeFiles(folder, false);
    }

    /** Writes the jar's own copy of the files to a new temporary folder, which {@link #close} deletes. */
    static ComposeFiles fromTheClasspath() throws IOException {
        Path folder = Files.createTempDirectory("vespera-sidecars-");
        try {
            var root = ComposeFiles.class.getClassLoader().getResource(RESOURCE_FOLDER);
            if (root == null) {
                throw new IOException("the build carries no " + RESOURCE_FOLDER + " resources");
            }
            String rootUrl = root.toString().endsWith("/") ? root.toString() : root + "/";
            for (Resource resource : new PathMatchingResourcePatternResolver(ComposeFiles.class.getClassLoader())
                    .getResources("classpath*:" + RESOURCE_FOLDER + "**")) {
                String url = resource.getURL().toString();
                if (!url.startsWith(rootUrl) || url.endsWith("/") || !resource.isReadable()) {
                    continue;
                }
                String relative = URLDecoder.decode(url.substring(rootUrl.length()).replace("+", "%2B"),
                        StandardCharsets.UTF_8);
                Path target = folder.resolve(relative);
                Files.createDirectories(target.getParent());
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, target);
                }
            }
            if (!Files.isRegularFile(folder.resolve(COMPOSE_FILE)) || !Files.isRegularFile(folder.resolve(GPU_FILE))) {
                throw new IOException("the build carries no " + COMPOSE_FILE + " or " + GPU_FILE + " under "
                        + RESOURCE_FOLDER);
            }
        } catch (IOException | RuntimeException unwritten) {
            deleteTree(folder);
            throw unwritten;
        }
        return new ComposeFiles(folder, true);
    }

    Path folder() {
        return folder;
    }

    Path composeFile() {
        return folder.resolve(COMPOSE_FILE);
    }

    Path gpuFile() {
        return folder.resolve(GPU_FILE);
    }

    /** The image {@code compose.yaml}'s {@code docling-serve} service builds. */
    String cpuImage() {
        return doclingImageIn(composeFile());
    }

    /** The image {@code compose.gpu.yaml}'s {@code docling-serve} service builds. */
    String gpuImage() {
        return doclingImageIn(gpuFile());
    }

    @Override
    public void close() {
        if (writtenByThis) {
            deleteTree(folder);
        }
    }

    private static String doclingImageIn(Path composeFile) {
        try {
            JsonNode image = YAML.readTree(Files.readString(composeFile)).path("services").path("docling-serve")
                    .path("image");
            if (!image.isString()) {
                throw new IllegalStateException(composeFile + " names no image for its docling-serve service");
            }
            return image.asString();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void deleteTree(Path folder) {
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | UncheckedIOException leftBehind) {
            // A temporary folder that will not go is the operating system's to clear; nothing reads it again.
        }
    }
}
