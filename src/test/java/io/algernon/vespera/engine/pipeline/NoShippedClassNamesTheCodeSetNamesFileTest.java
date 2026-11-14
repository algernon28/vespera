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
 * A code set is named by its folder, and the file of names the control panel once wrote is read, written and named by
 * no class of the jar or of the control panel (ADR-377 §9): a {@code code-set-names.yaml} left in a working directory
 * stays the operator's and changes nothing.
 *
 * <p>Every source under {@code src/main/java/io/algernon} is read whole, comments among it, so that neither a constant
 * nor a javadoc points a reader at the file as if it were read; and the scan must read {@code CodeSetFolderNames},
 * which names each set's folder, so that a scan of nothing cannot pass.
 */
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("807")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class NoShippedClassNamesTheCodeSetNamesFileTest {

    /** The shipped sources, the jar's and the control panel's. */
    private static final Path SOURCES = Path.of("src/main/java/io/algernon");

    /** The file's name, without its ending, as any spelling of it would hold it. */
    private static final String THE_FILE = "code-set-names";

    /** The class the scan must read. */
    private static final String MUST_READ = "CodeSetFolderNames.java";

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("No class of the application or its control panel names the file of code set names")
    void noClassOfTheJarOrTheControlPanelNamesIt() throws IOException {
        List<String> read = new ArrayList<>();
        List<String> naming = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                read.add(file.getFileName().toString());
                if (Files.readString(file, StandardCharsets.UTF_8).contains(THE_FILE)) {
                    naming.add(SOURCES.relativize(file).toString());
                }
            }
        }

        claim("the scan read the class that names each code set's folder, so that it cannot pass by reading nothing",
                () -> assertThat(read).contains(MUST_READ));
        claim("no shipped source names the file of code set names",
                () -> assertThat(naming).isEmpty());
    }
}
