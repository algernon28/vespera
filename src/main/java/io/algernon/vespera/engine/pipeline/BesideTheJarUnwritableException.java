package io.algernon.vespera.engine.pipeline;

import java.io.Serial;
import java.nio.file.Path;

/**
 * A file of the configuration cannot be written in the folder beside the jar, so the start is refused in one line
 * (ADR-397 §3).
 */
public class BesideTheJarUnwritableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BesideTheJarUnwritableException(String name, Path folder, Throwable reason) {
        super("Vespera cannot write " + name + " in the folder beside the jar, " + folder + " ("
                + oneLine(SidecarLockUnavailableException.reasonOf(reason))
                + "); make that folder one this user can write, and start Vespera again.", reason);
    }

    private static String oneLine(String reason) {
        return reason.replaceAll("\\s*[\\r\\n]+\\s*", " ");
    }
}
