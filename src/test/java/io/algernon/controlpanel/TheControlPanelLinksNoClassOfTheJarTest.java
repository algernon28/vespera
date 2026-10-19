package io.algernon.controlpanel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel and the jar link no class of each other (ADR-285 §3): the control panel names only its own
 * package and the JDK modules {@code java.base} and {@code jdk.httpserver}, so nothing of {@code io.algernon.vespera},
 * of Spring or of {@code java.sql}, and no class of the jar names the control panel. Its contract with the jar is the
 * process and the files ADR-278 names.
 *
 * <p>Compiled classes of {@code src/main} only: a test may name both, as the contract test of the holder line does.
 * The control panel is outside {@code io.algernon.vespera}, so {@code ModuleBoundariesTest} does not see it; this is
 * the boundary that holds it.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Issue("562")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
class TheControlPanelLinksNoClassOfTheJarTest {

    private static final String CONTROL_PANEL = "io.algernon.controlpanel";

    private static final String THE_JAR = "io.algernon.vespera";

    /** The JDK modules whose types the control panel may name: the base, and the HTTP server its forms are served by. */
    private static final Set<String> JDK_MODULES = Set.of("java.base", "jdk.httpserver");

    /** A class the import of the control panel must find, so that an empty import fails rather than passes. */
    private static final String A_CLASS_OF_THE_CONTROL_PANEL = CONTROL_PANEL + ".HolderLine";

    /** A class the import of the jar must find, for the same reason. */
    private static final String A_CLASS_OF_THE_JAR = THE_JAR + ".VesperaApplication";

    private static final JavaClasses CONTROL_PANEL_CLASSES = shipped(CONTROL_PANEL);

    private static final JavaClasses JAR_CLASSES = shipped(THE_JAR);

    @Test
    @Story("The control panel and the jar meet only at the process")
    @DisplayName("The control panel names only types of its own package and of the JDK's base and HTTP server modules")
    void theControlPanelNamesOnlyTypesOfItsOwnPackageAndOfTheJdksBaseAndHttpServerModules() {
        claim("the import read the control panel's compiled classes, checked by one it must hold",
                () -> assertThat(CONTROL_PANEL_CLASSES.contain(A_CLASS_OF_THE_CONTROL_PANEL)).isTrue());
        claim("and every type each of them names is of the control panel's own package or of the JDK's java.base or"
                        + " jdk.httpserver, so none is of the jar, of Spring or of the JDK's database module",
                () -> assertThatCode(() -> classes()
                                .that().resideInAPackage(CONTROL_PANEL + "..")
                                .should().onlyDependOnClassesThat(ofTheControlPanelOrTheAllowedJdkModules())
                                .check(CONTROL_PANEL_CLASSES))
                        .doesNotThrowAnyException());
    }

    @Test
    @Story("The control panel and the jar meet only at the process")
    @DisplayName("No class of the jar names a class of the control panel")
    void noClassOfTheJarNamesTheControlPanel() {
        claim("the import read the jar's compiled classes, checked by one it must hold",
                () -> assertThat(JAR_CLASSES.contain(A_CLASS_OF_THE_JAR)).isTrue());
        claim("and none of them names a type of the control panel's package",
                () -> assertThatCode(() -> noClasses()
                                .that().resideInAPackage(THE_JAR + "..")
                                .should().dependOnClassesThat().resideInAPackage(CONTROL_PANEL + "..")
                                .check(JAR_CLASSES))
                        .doesNotThrowAnyException());
    }

    private static JavaClasses shipped(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }

    private static DescribedPredicate<JavaClass> ofTheControlPanelOrTheAllowedJdkModules() {
        return DescribedPredicate.describe(
                "are of " + CONTROL_PANEL + " or of the JDK modules " + JDK_MODULES,
                type -> {
                    JavaClass base = type.getBaseComponentType();
                    if (base.isPrimitive()
                            || base.getPackageName().equals(CONTROL_PANEL)
                            || base.getPackageName().startsWith(CONTROL_PANEL + ".")) {
                        return true;
                    }
                    try {
                        Module module = Class.forName(
                                        base.getName(), false, TheControlPanelLinksNoClassOfTheJarTest.class.getClassLoader())
                                .getModule();
                        return module.isNamed() && JDK_MODULES.contains(module.getName());
                    } catch (ClassNotFoundException notOnTheClassPath) {
                        return false;
                    }
                });
    }
}
