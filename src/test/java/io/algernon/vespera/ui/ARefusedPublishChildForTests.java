package io.algernon.vespera.ui;

/**
 * The main class of {@link PrintingJars#refusedPublish()}: it prints, whole, the line a publish the control panel
 * started is refused with where the profile names the parent both by its id and by its title (ADR-372 §1, as ADR-388
 * §6 words it), and exits with code 1, as the jar exits a refused publish, having written no report.
 */
public final class ARefusedPublishChildForTests {

    /** The line it prints, the last of its output. */
    public static final String ITS_LAST_LINE = "vespera publish sent nothing: the parent is named both by"
            + " confluenceFolderId and by confluenceFolderTitle. Clear one in the control panel's Profile window, and"
            + " publish again.";

    private ARefusedPublishChildForTests() {
    }

    public static void main(String[] args) {
        System.out.println(ITS_LAST_LINE);
        System.out.flush();
        System.exit(1);
    }
}
