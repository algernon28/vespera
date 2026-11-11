package io.algernon.vespera.ui;

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
 * The control panel and the jar link no class of each other (ADR-285 §3, as ADR-342 §3 leaves it, and ADR-367 for the
 * package names): the control panel, {@code io.algernon.vespera.ui}, names only its own package, the JDK module
 * {@code java.base}, JavaFX's public packages, those whose names begin {@code javafx.}, and Jackson's, with which it
 * reads the jar's {@code application.yaml} (ADR-344 §3); so nothing else of {@code io.algernon.vespera}, neither
 * {@code engine} nor {@code publish} nor the application class nor the entry point, nor Spring, {@code java.sql},
 * {@code java.desktop}, {@code jdk.httpserver} or JavaFX's internals. No class of the jar, which is every class
 * of {@code io.algernon.vespera} but the control panel's and the entry point, which names both, names the control
 * panel or JavaFX, so a command of the jar never loads JavaFX. Its contract with the jar is the process and the files
 * ADR-278 names.
 *
 * <p>Compiled classes of {@code src/main} only: a test may name both, as the contract test of the holder line does.
 * The control panel is no module ({@code ui} carries no {@code @ApplicationModule}), so {@code ModuleBoundariesTest}
 * does not see it; this is the boundary that holds it. JavaFX and Jackson run on the class path, in no named module,
 * so they are admitted by their packages; the JDK's modules by the module a type is loaded from.
 *
 * <p>One class names one JDK module more (ADR-360 §3): {@code ClassPathLine}, the filter the control panel's start puts
 * on JavaFX's logger, names {@code java.logging}, and no other class of the control panel does.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Issue("562")
@Issue("700")
@Issue("723")
@Issue("759")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
@Link(name = "ADR-360", url = Adr.THE_CONTROL_PANEL_DROPS_JAVAFXS_CLASS_PATH_LINE, type = "adr")
@Link(name = "ADR-367", url = Adr.THE_JARS_PACKAGES_LIE_UNDER_ENGINE_PUBLISH_AND_UI, type = "adr")
class TheControlPanelLinksNoClassOfTheJarTest {

    private static final String CONTROL_PANEL = "io.algernon.vespera.ui";

    private static final String THE_APPLICATION_PACKAGE = "io.algernon.vespera";

    /** The entry point, which names the control panel and the command line both, and is no part of the jar's own. */
    private static final String THE_ENTRY_POINT = THE_APPLICATION_PACKAGE + ".EntryPoint";

    /** The JDK modules whose types the control panel may name: the base alone. */
    private static final Set<String> JDK_MODULES = Set.of("java.base");

    /** The one class of the control panel that may also name {@code java.logging}: the filter on JavaFX's logger. */
    private static final String THE_CLASS_PATH_LINE = CONTROL_PANEL + ".ClassPathLine";

    /** The JDK modules that class may name: the base, and the one {@code java.util.logging} is in. */
    private static final Set<String> JDK_MODULES_OF_THE_CLASS_PATH_LINE = Set.of("java.base", "java.logging");

    /** The packages of libraries the control panel may name, by their names' beginnings: JavaFX's API, and Jackson's. */
    private static final List<String> LIBRARY_PACKAGES = List.of("javafx.", "tools.jackson.");

    /** JavaFX's public packages, which no class of the jar names. */
    private static final String JAVAFX = "javafx..";

    /** A class the import of the control panel must find, so that an empty import fails rather than passes. */
    private static final String A_CLASS_OF_THE_CONTROL_PANEL = CONTROL_PANEL + ".HolderLine";

    /** A class the import of the jar must find, for the same reason. */
    private static final String A_CLASS_OF_THE_JAR = THE_APPLICATION_PACKAGE + ".VesperaApplication";

    private static final JavaClasses CONTROL_PANEL_CLASSES = shipped(CONTROL_PANEL);

    private static final JavaClasses JAR_CLASSES = shipped(THE_APPLICATION_PACKAGE);

    @Test
    @Story("The control panel and the jar meet only at the process")
    @DisplayName("Every class of the control panel but its filter on JavaFX's logger names only types of its own package, of the JDK's base module, of JavaFX and of Jackson")
    void theControlPanelNamesOnlyTypesOfItsOwnPackageTheJdksBaseJavaFxAndJackson() {
        claim("the import read the control panel's compiled classes, checked by one it must hold",
                () -> assertThat(CONTROL_PANEL_CLASSES.contain(A_CLASS_OF_THE_CONTROL_PANEL)).isTrue());
        claim("and every type each of them but the filter on JavaFX's logger names is of the control panel's own"
                        + " package, of the JDK's java.base, of a javafx package or of Jackson, so none is of the jar, of"
                        + " Spring, of the JDK's logging, database, desktop or HTTP server modules, or of JavaFX's"
                        + " internals",
                () -> assertThatCode(() -> classes()
                                .that().resideInAPackage(CONTROL_PANEL + "..")
                                .and(DescribedPredicate.not(theClassPathLine()))
                                .should().onlyDependOnClassesThat(ofTheControlPanelOrWhatItMayName(JDK_MODULES))
                                .check(CONTROL_PANEL_CLASSES))
                        .doesNotThrowAnyException());
    }

    @Test
    @Story("The control panel and the jar meet only at the process")
    @DisplayName("The control panel's filter on JavaFX's logger names what the rest may name, and the JDK's logging module besides")
    void onlyTheClassPathLineNamesTheJdksLoggingModule() {
        claim("the import read the filter the control panel puts on JavaFX's logger",
                () -> assertThat(CONTROL_PANEL_CLASSES.contain(THE_CLASS_PATH_LINE)).isTrue());
        claim("and every type it names is of the control panel's own package, of the JDK's java.base or java.logging,"
                        + " of a javafx package or of Jackson, so it names nothing of the jar, of Spring or of JavaFX's"
                        + " internals, and knows JavaFX's logger by its name alone",
                () -> assertThatCode(() -> classes()
                                .that(theClassPathLine())
                                .should().onlyDependOnClassesThat(
                                        ofTheControlPanelOrWhatItMayName(JDK_MODULES_OF_THE_CLASS_PATH_LINE))
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
                                .that(theJarsOwn())
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
                                .that(theJarsOwn())
                                .should().dependOnClassesThat().resideInAPackage(JAVAFX)
                                .check(JAR_CLASSES))
                        .doesNotThrowAnyException());
    }

    private static JavaClasses shipped(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }

    /**
     * A class of the jar itself: of {@code io.algernon.vespera}, and neither the control panel's nor the entry point,
     * which lie beneath the same package and name the control panel by design.
     */
    private static DescribedPredicate<JavaClass> theJarsOwn() {
        return DescribedPredicate.describe(
                "are of " + THE_APPLICATION_PACKAGE + " but neither of " + CONTROL_PANEL + " nor " + THE_ENTRY_POINT,
                type -> {
                    String name = type.getName();
                    return name.startsWith(THE_APPLICATION_PACKAGE + ".")
                            && !name.startsWith(CONTROL_PANEL + ".")
                            && !name.equals(THE_ENTRY_POINT)
                            && !name.startsWith(THE_ENTRY_POINT + "$");
                });
    }

    /** The filter on JavaFX's logger. */
    private static DescribedPredicate<JavaClass> theClassPathLine() {
        return DescribedPredicate.describe("are " + THE_CLASS_PATH_LINE,
                type -> type.getName().equals(THE_CLASS_PATH_LINE));
    }

    private static DescribedPredicate<JavaClass> ofTheControlPanelOrWhatItMayName(Set<String> jdkModules) {
        return DescribedPredicate.describe(
                "are of " + CONTROL_PANEL + ", of the JDK modules " + jdkModules + " or of the packages beginning "
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
                        return module.isNamed() && jdkModules.contains(module.getName());
                    } catch (ClassNotFoundException notOnTheClassPath) {
                        return false;
                    }
                });
    }
}
