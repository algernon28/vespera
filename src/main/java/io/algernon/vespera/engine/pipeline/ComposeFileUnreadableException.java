package io.algernon.vespera.engine.pipeline;

import java.nio.file.Path;

/**
 * A compose file beside the jar cannot be read for the image its {@code docling-serve} service builds, so the start of
 * the containers is refused in one line (ADR-397 §12). The files are the operator's to edit once they lie beside the
 * jar: one may not load, name no image, or be gone.
 */
final class ComposeFileUnreadableException extends ContainerStartRefusedException {

    private static final long serialVersionUID = 1L;

    /** @param reason what is wrong with {@code file}, as ADR-397 §12 words it; made one line here */
    ComposeFileUnreadableException(Path file, String reason, Throwable cause) {
        super("the containers are not answering and Vespera cannot start them: " + file
                + " cannot be read for the image its docling-serve service builds ("
                + reason.replaceAll("\\s*[\\r\\n]+\\s*", " ").strip()
                + "). Put that file right, or delete it so that Vespera writes its own there, and run the same"
                + " command again.");
        if (cause != null) {
            initCause(cause);
        }
    }
}
