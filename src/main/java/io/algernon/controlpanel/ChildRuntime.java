package io.algernon.controlpanel;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * What the control panel starts its child with (ADR-288 §3, ADR-342 §2): the {@code java} of the runtime running it,
 * and the jar it runs from, the one file the class path names.
 */
public record ChildRuntime(Path java, Path jar) {

    /**
     * @param classPath the class path of the running JVM, which under {@code java -jar} is the jar's path alone
     * @param javaHome the Java home of the running JVM
     * @param osName the system's name, whose {@code Windows} prefix gives {@code java.exe}
     * @throws FileNotFoundException with ADR-342 §2's one line where the class path is not one regular file
     */
    public static ChildRuntime find(String classPath, Path javaHome, String osName) throws FileNotFoundException {
        Path jar = null;
        if (classPath != null && !classPath.isBlank() && !classPath.contains(File.pathSeparator)) {
            try {
                Path named = Path.of(classPath).toAbsolutePath();
                jar = Files.isRegularFile(named) ? named : null;
            } catch (InvalidPathException cannotBeHeld) {
                jar = null;
            }
        }
        if (jar == null) {
            throw new FileNotFoundException(
                    "the control panel starts the Vespera jar it runs from, and runs from no jar: " + classPath);
        }
        String executable = osName.startsWith("Windows") ? "java.exe" : "java";
        return new ChildRuntime(javaHome.resolve("bin").resolve(executable), jar);
    }
}
