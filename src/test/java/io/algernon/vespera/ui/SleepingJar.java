package io.algernon.vespera.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * A jar whose main class sleeps until it is killed, started with the java running the tests, so that the control
 * panel's state starts a child that runs as long as a test needs and is never Vespera. Written once for the test JVM,
 * in a folder of its own outside every test's folder: on Windows a jar a child has just run can stay in use for a
 * moment after the child ends, and a test's folder holding it could not then be deleted.
 */
final class SleepingJar {

    private static Path jar;

    private SleepingJar() {
    }

    /** The java running the tests, and the sleeping jar. */
    static ChildRuntime runtime() throws IOException {
        return new ChildRuntime(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"), theJar());
    }

    private static synchronized Path theJar() throws IOException {
        if (jar == null) {
            Path folder = Files.createTempDirectory("vespera-sleeping-jar");
            folder.toFile().deleteOnExit();
            jar = write(folder.resolve("sleeping.jar"), ASleepingChildForTests.class);
            jar.toFile().deleteOnExit();
        }
        return jar;
    }

    /** Writes a jar holding {@code main} alone, run by its manifest; {@link EndingJar} writes its own by it too. */
    static Path write(Path file, Class<?> main) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, main.getName());
        String entry = main.getName().replace('.', '/') + ".class";
        try (OutputStream out = Files.newOutputStream(file);
                JarOutputStream jarred = new JarOutputStream(out, manifest);
                InputStream bytes = main.getClassLoader().getResourceAsStream(entry)) {
            if (bytes == null) {
                throw new IOException("no compiled " + entry + " on the test class path");
            }
            jarred.putNextEntry(new JarEntry(entry));
            bytes.transferTo(jarred);
            jarred.closeEntry();
        }
        return file;
    }
}
