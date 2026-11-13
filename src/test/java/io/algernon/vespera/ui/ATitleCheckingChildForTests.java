package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The main class of the jar {@code PublishToConfluenceAsksForTheTokenTest} starts as a publish whose parent's title
 * names no page or folder of the space, or several (ADR-383 §1). It reads {@code confluenceFolderTitle} in the {@code
 * profile.yaml} of the folder its {@code --db-dir=} names, as the jar reads the parent there, prints nothing and exits:
 * {@link #NO_PAGE_SO_TITLED} where the title is {@link #TITLE_OF_NO_PAGE}, {@link #SEVERAL_SO_TITLED} where it is
 * {@link #TITLE_OF_SEVERAL}, {@link #AS_EXPECTED} where it is {@link #TITLE_ENTERED} and the environment holds {@link
 * ATokenCheckingChildForTests#TOKEN} as {@code VESPERA_CONFLUENCE_TOKEN} and {@code VESPERA_STARTED_BY=control-panel},
 * and {@link #NOT_AS_EXPECTED} otherwise. It is the jar's main class alone: the one name it takes of another class,
 * the token, is a constant the compiler writes into it.
 */
public final class ATitleCheckingChildForTests {

    /** The two exit codes of a publish the control panel started whose parent's title is not one page's (ADR-383 §1). */
    public static final int NO_PAGE_SO_TITLED = 3;

    public static final int SEVERAL_SO_TITLED = 4;

    public static final int AS_EXPECTED = 0;

    public static final int NOT_AS_EXPECTED = 1;

    /** A title no page or folder of the space holds, as the child takes it. */
    public static final String TITLE_OF_NO_PAGE = "Nowhere";

    /** A title several pages or folders of the space hold, as the child takes it. */
    public static final String TITLE_OF_SEVERAL = "Guides";

    /** The title the test enters in the warning: one page of the space holds it, as the child takes it. */
    public static final String TITLE_ENTERED = "Release notes";

    private ATitleCheckingChildForTests() {
    }

    public static void main(String[] args) throws IOException {
        String title = null;
        for (String argument : args) {
            if (argument.startsWith("--db-dir=")) {
                title = titleIn(Path.of(argument.substring("--db-dir=".length())).resolve("profile.yaml"));
            }
        }
        if (TITLE_OF_NO_PAGE.equals(title)) {
            System.exit(NO_PAGE_SO_TITLED);
        }
        if (TITLE_OF_SEVERAL.equals(title)) {
            System.exit(SEVERAL_SO_TITLED);
        }
        if (TITLE_ENTERED.equals(title)
                && ATokenCheckingChildForTests.TOKEN.equals(System.getenv("VESPERA_CONFLUENCE_TOKEN"))
                && "control-panel".equals(System.getenv("VESPERA_STARTED_BY"))) {
            System.exit(AS_EXPECTED);
        }
        System.exit(NOT_AS_EXPECTED);
    }

    /** The value of {@code confluenceFolderTitle} the file states, read off its double-quoted scalar; null where none. */
    private static String titleIn(Path profile) throws IOException {
        if (!Files.isRegularFile(profile)) {
            return null;
        }
        List<String> lines = Files.readAllLines(profile, StandardCharsets.UTF_8);
        boolean inTheKey = false;
        for (String line : lines) {
            if (!line.startsWith(" ")) {
                inTheKey = line.equals("confluenceFolderTitle:");
            } else if (inTheKey && line.startsWith("  value: \"") && line.endsWith("\"")) {
                return line.substring("  value: \"".length(), line.length() - 1);
            }
        }
        return null;
    }
}
