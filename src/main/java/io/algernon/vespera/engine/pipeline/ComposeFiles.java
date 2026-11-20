package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The compose files Vespera starts the containers from, and the Docling image each builds (ADR-323 §2, §4).
 *
 * <p>The files lie beside the jar (ADR-397 §6), where the build context {@code compose.yaml} names relative to itself
 * resolves. They are read where they lie, and {@link #close} deletes none of them.
 */
final class ComposeFiles implements AutoCloseable {

    static final String COMPOSE_FILE = "compose.yaml";

    static final String GPU_FILE = "compose.gpu.yaml";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private final Path folder;

    private ComposeFiles(Path folder) {
        this.folder = folder;
    }

    /** Files read where they lie, in {@code folder}; none is written out or deleted. */
    static ComposeFiles in(Path folder) {
        return new ComposeFiles(folder);
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
        // Nothing to release: the files lie beside the jar and are the operator's (ADR-397 §6).
    }

    private static String doclingImageIn(Path composeFile) {
        String text;
        try {
            text = Files.readString(composeFile);
        } catch (NoSuchFileException notThere) {
            throw new ComposeFileUnreadableException(composeFile, "it is not there", notThere);
        } catch (IOException | RuntimeException unreadable) {
            throw new ComposeFileUnreadableException(composeFile, ContainerLockUnavailableException.reasonOf(unreadable),
                    unreadable);
        }
        JsonNode tree;
        try {
            tree = YAML.readTree(text);
        } catch (RuntimeException doesNotLoad) {
            throw new ComposeFileUnreadableException(composeFile,
                    "it does not load as YAML: " + ContainerLockUnavailableException.reasonOf(doesNotLoad), doesNotLoad);
        }
        JsonNode image = tree.path("services").path("docling-serve").path("image");
        if (!image.isString()) {
            throw new ComposeFileUnreadableException(composeFile, "it names no image for its docling-serve service",
                    null);
        }
        return image.asString();
    }
}
