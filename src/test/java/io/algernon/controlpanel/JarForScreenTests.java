package io.algernon.controlpanel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The main class of the jar the screen tests have the control panel start in place of Vespera's (ADR-302): started
 * as the control panel starts the jar, {@code run <corpus root> --db-dir=<working directory>} or {@code label [--auto]
 * --db-dir=<working directory>}, in the working directory, it does what {@link #WHAT_TO_DO} in that directory says.
 *
 * <p>{@code sleep}, or no such file: prints {@link #STARTED} and sleeps until it is killed. {@code exit <code> <line>}:
 * prints {@link #STARTED}, then the line, then a blank line, and exits with the code. It names nothing but
 * {@code java.base}, so the jar holds this one class.
 */
public final class JarForScreenTests {

    /** The file in the working directory that says what the child does. */
    public static final String WHAT_TO_DO = "jar-for-screen-tests.txt";

    /** Said before what the child does: it first copies {@code profile.yaml} as it finds it, to {@link #THE_PROFILE_IT_FOUND}. */
    public static final String COPY_THE_PROFILE = "copy-the-profile ";

    /** The file in the working directory the child copies {@code profile.yaml} to, where it is told to. */
    public static final String THE_PROFILE_IT_FOUND = "the-profile-the-child-found.yaml";

    /** The first line the child prints, whatever it then does. */
    public static final String STARTED = "the jar for the screen tests started";

    /** Longer than any test waits; the test stops or kills this process long before. */
    private static final long SLEEP_MILLIS = 600_000L;

    private JarForScreenTests() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println(STARTED);
        System.out.flush();
        Path told = Path.of(WHAT_TO_DO);
        String what = Files.isRegularFile(told) ? Files.readString(told, StandardCharsets.UTF_8).strip() : "sleep";
        if (what.startsWith(COPY_THE_PROFILE)) {
            Files.copy(Path.of("profile.yaml"), Path.of(THE_PROFILE_IT_FOUND));
            what = what.substring(COPY_THE_PROFILE.length());
        }
        if (what.startsWith("exit ")) {
            String[] parts = what.split(" ", 3);
            System.out.println(parts.length > 2 ? parts[2] : "");
            System.out.println();
            System.out.flush();
            System.exit(Integer.parseInt(parts[1]));
        }
        Thread.sleep(SLEEP_MILLIS);
    }
}
