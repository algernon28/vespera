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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel and the jar link no class of each other (ADR-285 §3, as ADR-342 §3 leaves it): the control panel
 * names only its own package, the JDK module {@code java.base}, JavaFX's public packages, those whose names begin
 * {@code javafx.}, and Jackson's, with which it reads the jar's {@code application.yaml} (ADR-344 §3); so nothing of
 * {@code io.algernon.vespera}, of the jar's entry point, of Spring, of {@code java.sql}, of {@code java.desktop}, of
 * {@code jdk.httpserver} or of JavaFX's internals. No class of the jar names the control panel or JavaFX, so a command
 * of the jar never loads JavaFX. Its contract with the jar is the process and the files ADR-278 names.
 *
 * <p>Compiled classes of {@code src/main} only: a test may name both, as the contract test of the holder line does.
 * The control panel is outside {@code io.algernon.vespera}, so {@code ModuleBoundariesTest} does not see it; this is
 * the boundary that holds it. JavaFX and Jackson run on the class path, in no named module, so they are admitted by
 * their packages; the JDK's modules by the module a type is loaded from.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Issue("562")
@Issue("700")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheControlPanelLinksNoClassOfTheJarTest {

    private static final String CONTROL_PANEL = "io.algernon.controlpanel";

    private static final String THE_JAR = "io.algernon.vespera";

    /** The JDK modules whose types the control panel may name: the base alone. */
    private static final Set<String> JDK_MODULES = Set.of("java.base");

    /** The packages of libraries the control panel may name, by their names' beginnings: JavaFX's API, and Jackson's. */
    private static final List<String> LIBRARY_PACKAGES = List.of("javafx.", "tools.jackson.");

    /** JavaFX's public packages, which no class of the jar names. */
    private static final String JAVAFX = "javafx..";

    /** A class the import of the control panel must find, so that an empty import fails rather than passes. */
    private static final String A_CLASS_OF_THE_CONTROL_PANEL = CONTROL_PANEL + ".HolderLine";

    /** A class the import of the jar must find, for the same reason. */
    private static final String A_CLASS_OF_THE_JAR = THE_JAR + ".VesperaApplication";

    private static final JavaClasses CONTROL_PANEL_CLASSES = shipped(CONTROL_PANEL);

    private static final JavaClasses JAR_CLASSES = shipped(THE_JAR);

    @Test
    @Story("The control panel and the jar meet only at the process")
    @DisplayName("The control panel names only types of its own package, of the JDK's base module, of JavaFX and of Jackson")
    void theControlPanelNamesOnlyTypesOfItsOwnPackageTheJdksBaseJavaFxAndJackson() {
        claim("the import read the control panel's compiled classes, checked by one it must hold",
                () -> assertThat(CONTROL_PANEL_CLASSES.contain(A_CLASS_OF_THE_CONTROL_PANEL)).isTrue());
        claim("and every type each of them names is of the control panel's own package, of the JDK's java.base, of a"
                        + " javafx package or of Jackson, so none is of the jar, of Spring, of the JDK's database,"
                        + " desktop or HTTP server modules, or of JavaFX's internals",
                () -> assertThatCode(() -> classes()
                                .that().resideInAPackage(CONTROL_PANEL + "..")
                                .should().onlyDependOnClassesThat(ofTheControlPanelOrWhatItMayName())
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

    @Test
    @Story("The control panel and the jar meet only at the process")
    @DisplayName("No class of the jar names JavaFX, so a command never loads it")
    void noClassOfTheJarNamesJavaFx() {
        claim("no class of the jar names a type of a javafx package, so running a command loads nothing of the window's"
                        + " toolkit, on whatever platform the jar was built for",
                () -> assertThatCode(() -> noClasses()
                                .that().resideInAPackage(THE_JAR + "..")
                                .should().dependOnClassesThat().resideInAPackage(JAVAFX)
                                .check(JAR_CLASSES))
                        .doesNotThrowAnyException());
    }

    private static JavaClasses shipped(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }

    private static DescribedPredicate<JavaClass> ofTheControlPanelOrWhatItMayName() {
        return DescribedPredicate.describe(
                "are of " + CONTROL_PANEL + ", of the JDK modules " + JDK_MODULES + " or of the packages beginning "
                        + LIBRARY_PACKAGES,
                type -> {
                    JavaClass base = type.getBaseComponentType();
                    String packageName = base.getPackageName();
                    if (base.isPrimitive()
                            || packageName.equals(CONTROL_PANEL)
                            || packageName.startsWith(CONTROL_PANEL + ".")
                            || LIBRARY_PACKAGES.stream().anyMatch(prefix -> (packageName + ".").startsWith(prefix))) {
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
