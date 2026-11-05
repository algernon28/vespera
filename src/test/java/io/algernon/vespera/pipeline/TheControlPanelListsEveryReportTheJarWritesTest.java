package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.algernon.controlpanel.Reports;
import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.Deliverable;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel lists every report the jar writes in the working directory, and no other (ADR-344 §6, keeping
 * ADR-306 §2's sixteenth): its list, {@link Reports#ALL}, is the set of report file names the classes of
 * {@code pipeline} and {@code deliverable} hold.
 *
 * <p>The jar's report file names are read from its compiled classes rather than written out here: every constant
 * {@code String} of a shipped class of {@code pipeline} or {@code deliverable} whose value is a file name ending in
 * {@code .html}, but the tree's own page name, {@link Deliverable#INDEX_FILE_NAME}, which is never written in the
 * working directory. A report a later stage adds under a constant of its own fails this until the list has it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class TheControlPanelListsEveryReportTheJarWritesTest {

    /** The packages whose classes name the reports of the working directory. */
    private static final List<String> REPORTING_PACKAGES = List.of(
            TheControlPanelListsEveryReportTheJarWritesTest.class.getPackageName(),
            Deliverable.class.getPackageName());

    /** A file name a report constant holds: a name of letters, digits and dashes, ending in {@code .html}. */
    private static final String REPORT_FILE_NAME = "[a-z0-9-]+\\.html";

    @Test
    @Story("The operator opens each report from the control panel")
    @DisplayName("Every report the jar writes beside its database is one the control panel lists, and it lists no other")
    void everyReportTheJarWritesIsListedByTheControlPanel() {
        Set<String> written = reportFileNames();

        claim("the jar's classes name reports at all, the progress page among them, so the comparison below reads"
                        + " something",
                () -> assertThat(written).contains("progress.html", "cluster-page-sizes.html"));
        claim("and the control panel's list holds each of them once, and no other",
                () -> assertThat(Reports.ALL).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(written));
    }

    private static Set<String> reportFileNames() {
        Set<String> names = new TreeSet<>();
        for (JavaClass type : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(REPORTING_PACKAGES)) {
            if (!REPORTING_PACKAGES.contains(type.getPackageName())) {
                continue;
            }
            for (Field field : declaredFieldsOf(type.getName())) {
                int modifiers = field.getModifiers();
                if (field.getType() != String.class || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)
                        || !field.trySetAccessible()) {
                    continue;
                }
                try {
                    if (field.get(null) instanceof String name && name.matches(REPORT_FILE_NAME)
                            && !name.equals(Deliverable.INDEX_FILE_NAME)) {
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
                    TheControlPanelListsEveryReportTheJarWritesTest.class.getClassLoader()).getDeclaredFields());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " was imported and is not on the class path", e);
        }
    }
}
