package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A code set is named by its folder alone (ADR-377 §9): no class of the jar or the control panel reads, writes or names
 * {@code code-set-names.yaml}, so a file of that name left in a working directory changes nothing. Read from the
 * sources, which name a file by its name where they read or write it.
 */
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("807")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class NoShippedClassNamesTheCodeSetNamesFileTest {

    /** Every shipped source lies beneath this folder: the jar's engine and plugins, and the control panel. */
    private static final Path SOURCES = Path.of("src/main/java/io/algernon");

    /** The file's name, as a source would spell it, without its extension. */
    private static final String THE_FILE = "code-set-names";

    /** The class that names the sets' folders in the tree, which the scan must read so that a scan of nothing fails. */
    private static final String THE_NAMING_CLASS = "CodeSetFolderNames.java";

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("No class of the jar or the control panel names the file of code-set names")
    void noClassOfTheJarOrTheControlPanelNamesIt() throws IOException {
        List<String> read = new ArrayList<>();
        List<String> naming = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(SOURCES)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                read.add(source.getFileName().toString());
                if (Files.readString(source, StandardCharsets.UTF_8).contains(THE_FILE)) {
                    naming.add(SOURCES.relativize(source).toString());
                }
            }
        }

        claim("the scan read the shipped sources, the class that names the sets' folders in the tree among them",
                () -> assertThat(read).contains(THE_NAMING_CLASS));
        claim("and no source holds " + THE_FILE,
                () -> assertThat(naming).isEmpty());
    }
}
