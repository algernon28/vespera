package io.algernon.controlpanel;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the control panel starts its child with (ADR-288 §3, §4): the {@code java} of the runtime running it, and the
 * jar beside its own jar, by the file name its manifest gives.
 */
public record ChildRuntime(Path java, Path jar) {

    /**
     * @param controlPanelLocation the jar the control panel runs from, or the folder of classes where it is not a jar
     * @param jarName the manifest's name of the jar to start, or {@code null} where the control panel has no manifest
     */
    public static ChildRuntime find(Path controlPanelLocation, String jarName, Path javaHome, String osName)
            throws FileNotFoundException {
        Path jar = jarName == null ? null : controlPanelLocation.toAbsolutePath().getParent().resolve(jarName);
        if (jar == null || !Files.isRegularFile(jar)) {
            throw new FileNotFoundException("the control panel looks for the Vespera jar beside its own jar, and found"
                    + " none at " + (jar == null ? controlPanelLocation.toAbsolutePath() : jar));
        }
        String executable = osName.startsWith("Windows") ? "java.exe" : "java";
        return new ChildRuntime(javaHome.resolve("bin").resolve(executable), jar);
    }
}
