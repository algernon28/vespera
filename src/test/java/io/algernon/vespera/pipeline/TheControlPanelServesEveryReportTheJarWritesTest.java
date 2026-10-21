package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.algernon.controlpanel.ChildRuntime;
import io.algernon.controlpanel.ControlPanelPages;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel serves every report the jar writes in the working directory, and no other (ADR-302 §3): its list
 * of fifteen file names, {@link ControlPanelPages#REPORTS}, is the set of report file names the classes of
 * {@code pipeline} hold, and each is a path {@code /report/<name>} of the pages the server is started with.
 *
 * <p>The jar's report file names are read from its compiled classes rather than written out here: every constant
 * {@code String} of a shipped class of {@code pipeline} whose value is a file name ending in {@code .html}. The
 * progress page and its three companions, and each stage's reports, are named so, each by one constant; a report a
 * later stage adds under a constant of its own fails this until the control panel's list has it (ADR-302,
 * Consequences). The deliverable's pages, named in {@code deliverable}, are not reports of the working directory and
 * are not read.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class TheControlPanelServesEveryReportTheJarWritesTest {

    /** How many reports ADR-302's table lists: the progress page, its three companions, and eleven of the stages. */
    private static final int FIFTEEN = 15;

    /** The path every report is served under, followed by its file name. */
    private static final String REPORT_PATH = "/report/";

    /** A file name a report constant holds: a name of letters, digits and dashes, ending in {@code .html}. */
    private static final String REPORT_FILE_NAME = "[a-z0-9-]+\\.html";

    @TempDir
    Path folder;

    @Test
    @Story("The operator opens each report from the control panel")
    @DisplayName("Every report the jar writes beside its database is one the control panel lists and serves at its own path, and the control panel lists no other")
    void everyReportTheJarWritesIsListedAndServedByTheControlPanel() {
        Set<String> written = reportFileNamesOfThePipeline();
        Set<String> served = ControlPanelPages.pages(
                        new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                        folder.resolve("launch-values.properties"), folder.resolve("root-locks"),
                        System.getProperty("user.name"))
                .keySet().stream()
                .filter(path -> path.startsWith(REPORT_PATH))
                .collect(Collectors.toCollection(TreeSet::new));

        claim("the jar's classes name " + FIFTEEN + " reports: the progress page, its three companions, and the"
                        + " reports of the stages",
                () -> assertThat(written).hasSize(FIFTEEN));
        claim("the control panel's list of reports holds each of them once, and no other",
                () -> assertThat(ControlPanelPages.REPORTS).doesNotHaveDuplicates()
                        .containsExactlyInAnyOrderElementsOf(written));
        claim("and the pages the server is started with answer each at /report/ and its file name, and no other"
                        + " report path",
                () -> assertThat(served).containsExactlyInAnyOrderElementsOf(
                        written.stream().map(name -> REPORT_PATH + name).toList()));
    }

    /** Every constant {@code String} of a shipped class of {@code pipeline} that is a file name ending in .html. */
    private static Set<String> reportFileNamesOfThePipeline() {
        Set<String> names = new TreeSet<>();
        for (JavaClass type : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(TheControlPanelServesEveryReportTheJarWritesTest.class.getPackageName())) {
            if (!type.getPackageName().equals(TheControlPanelServesEveryReportTheJarWritesTest.class.getPackageName())) {
                continue;
            }
            for (Field field : declaredFieldsOf(type.getName())) {
                int modifiers = field.getModifiers();
                if (field.getType() != String.class || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)
                        || !field.trySetAccessible()) {
                    continue;
                }
                try {
                    Object value = field.get(null);
                    if (value instanceof String name && name.matches(REPORT_FILE_NAME)) {
                        names.add(name);
                    }
                } catch (IllegalAccessException e) {
                    throw new AssertionError(type.getName() + "." + field.getName() + " could not be read", e);
                }
            }
        }
        return names;
    }

    private static List<Field> declaredFieldsOf(String className) {
        try {
            return List.of(Class.forName(className, false,
                    TheControlPanelServesEveryReportTheJarWritesTest.class.getClassLoader()).getDeclaredFields());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " was imported and is not on the class path", e);
        }
    }
}
