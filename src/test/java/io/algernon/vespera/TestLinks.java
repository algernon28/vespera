package io.algernon.vespera;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Makes a link on the machine the tests run on, so that a test about links builds the case instead of
 * describing it.
 *
 * <p>A symbolic link is tried first. Creating one on Windows needs a privilege that not every account
 * holds, and a junction needs none, so where the symbolic link is refused and the system is Windows a
 * junction is made instead. Where neither can be made the answer is {@code false}, and the test is to
 * abort by assumption with that answer as its reason, never to pass without having looked.
 *
 * <p>Lives in the root package for the reason {@link TestSteps} does: a test-support class in a package
 * of its own would read as a further module to {@code ApplicationModules}.
 */
public final class TestLinks {

    private TestLinks() {
    }

    /**
     * Makes {@code link} lead to {@code target}.
     *
     * @param target an absolute path; it need not exist, but a junction can only be made to one that does
     * @return whether the link was made
     */
    public static boolean make(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                return false;
            }
        }
        try {
            Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                    .redirectErrorStream(true)
                    .start();
            mklink.getInputStream().transferTo(OutputStream.nullOutputStream());
            return mklink.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
