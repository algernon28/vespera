package io.algernon.vespera;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Where the jar's code lies (ADR-367): the eleven capability modules in two folders of the application package,
 * {@code engine} for the nine of the cascade and {@code publish} for the two plugins' modules, the control panel in
 * {@code ui}, and the application class and the entry point at the root, so the application package holds nothing else.
 *
 * <p>Spring Modulith finds a module as a package carrying {@code @ApplicationModule} (the detection strategy is
 * {@code explicitly-annotated}, set in {@code src/test/resources/application.properties}), and it finds none of a
 * package that does not: a new folder under {@code engine} or {@code publish} left without the annotation would be
 * outside every rule {@code ModuleBoundariesTest} holds and nothing there would say so. This holds that each folder
 * is a module, and each module is in the folder its kind belongs to.
 *
 * <p>It holds the direction between the two folders too. A plugin implements the seam {@code deliverable} makes
 * public (ADR-333 §1), so {@code publish} names {@code engine}; {@code engine} names {@code publish} nowhere but in
 * {@code pipeline}, the composition root that hands the plugins' beans to {@code deliverable}, which names no plugin.
 * Every module's own allowed dependencies are {@code ModuleBoundariesTest}'s.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Link(name = "ADR-367", url = Adr.THE_JARS_PACKAGES_LIE_UNDER_ENGINE_PUBLISH_AND_UI, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class TheJarsCodeLiesUnderEngineAndPublishAndUiTest {

    private static final String ROOT = "io.algernon.vespera";

    private static final String ENGINE = ROOT + ".engine";

    private static final String PUBLISH = ROOT + ".publish";

    /** The nine modules of the cascade: the ledger they all depend on, seven capabilities and the composition root. */
    private static final Set<String> ENGINE_MODULES = Set.of(
            "ledger", "corpus", "extraction", "similarity", "embedding", "synthesis", "deliverable", "profile", "pipeline");

    /** The two plugins' modules (ADR-333 §1). */
    private static final Set<String> PUBLISH_MODULES = Set.of("confluence", "website");

    /** The folders of the application package: the two that hold modules, and the control panel's. */
    private static final Set<String> FOLDERS = Set.of("engine", "publish", "ui");

    /** The two classes of the application package itself. */
    private static final Set<String> ROOT_CLASSES = Set.of(ROOT + ".VesperaApplication", ROOT + ".EntryPoint");

    private static final String PIPELINE = ENGINE + ".pipeline..";

    private static final JavaClasses SHIPPED =
            new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);

    private static final ApplicationModules MODULES = ApplicationModules.of(VesperaApplication.class);

    @Test
    @Story("Every package of the jar lies in one place")
    @DisplayName("The application package holds the engine, publish and ui folders and its two classes, and nothing else")
    void theApplicationPackageHoldsTheThreeFoldersAndItsTwoClasses() {
        Set<String> folders = SHIPPED.stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(ROOT + "."))
                .map(name -> name.substring(ROOT.length() + 1))
                .map(TheJarsCodeLiesUnderEngineAndPublishAndUiTest::firstSegment)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> rootClasses = SHIPPED.stream()
                .filter(type -> type.getPackageName().equals(ROOT))
                .map(type -> type.getName().contains("$") ? type.getName().substring(0, type.getName().indexOf('$')) : type.getName())
                .collect(Collectors.toCollection(TreeSet::new));

        claim("every package of the jar lies under one of the " + FOLDERS.size() + " folders, " + FOLDERS
                        + ", so a package added directly under the application package is a layout nobody decided on",
                () -> assertThat(folders).containsExactlyInAnyOrderElementsOf(FOLDERS));
        claim("and the application package's own classes are the application class and the entry point, "
                        + ROOT_CLASSES.size() + " in all",
                () -> assertThat(rootClasses).containsExactlyInAnyOrderElementsOf(ROOT_CLASSES));
    }

    @Test
    @Story("Every package of the jar lies in one place")
    @DisplayName("Every package directly under engine and under publish is a module, and each module lies in the folder of its kind")
    void everyModuleLiesInTheFolderOfItsKindAndEveryFolderIsAModule() {
        Map<String, String> found = new TreeMap<>();
        MODULES.forEach(module -> found.put(module.getIdentifier().toString(), module.getBasePackage().getName()));
        Map<String, String> expected = new TreeMap<>();
        ENGINE_MODULES.forEach(name -> expected.put(name, ENGINE + "." + name));
        PUBLISH_MODULES.forEach(name -> expected.put(name, PUBLISH + "." + name));
        Set<String> directlyUnderAFolder = new TreeSet<>();
        for (String folder : Set.of(ENGINE, PUBLISH)) {
            SHIPPED.stream()
                    .map(JavaClass::getPackageName)
                    .filter(name -> name.startsWith(folder + "."))
                    .map(name -> folder + "." + firstSegment(name.substring(folder.length() + 1)))
                    .forEach(directlyUnderAFolder::add);
        }

        claim("each module is found under its own short name, in the folder its kind belongs to: the nine of the cascade"
                        + " under engine, the two plugins' under publish",
                () -> assertThat(found).containsExactlyInAnyOrderEntriesOf(expected));
        claim("and every package directly under engine or publish is a module found, so none lies there without"
                        + " @ApplicationModule, outside every rule the modules are held to",
                () -> assertThat(directlyUnderAFolder).containsExactlyInAnyOrderElementsOf(found.values()));
    }

    @Test
    @Story("The direction between the two folders")
    @DisplayName("A plugin names the seam of the engine, and nothing of the engine but its pipeline names a plugin")
    void aPluginNamesTheEngineAndOnlyThePipelineNamesAPlugin() {
        claim("no class of engine but the pipeline's names a class of publish, so the seam deliverable makes public is"
                        + " implemented from publish and never reached into from it",
                () -> assertThatCode(() -> noClasses()
                                .that().resideInAPackage(ENGINE + "..")
                                .and().resideOutsideOfPackage(PIPELINE)
                                .should().dependOnClassesThat().resideInAPackage(PUBLISH + "..")
                                .check(SHIPPED))
                        .doesNotThrowAnyException());
        claim("the pipeline does name a plugin's module, so the one exception made above is one in use, and the plugins"
                        + " each name deliverable, whose seam they implement, so the direction held is the one that exists",
                () -> {
                    assertThat(namedBy(ENGINE + ".pipeline", PUBLISH)).contains("confluence", "website");
                    assertThat(namedBy(PUBLISH + ".confluence", ENGINE)).contains("deliverable");
                    assertThat(namedBy(PUBLISH + ".website", ENGINE)).contains("deliverable");
                });
    }

    /** The modules directly under {@code folder} that a class of {@code fromPackage} names. */
    private static Set<String> namedBy(String fromPackage, String folder) {
        return SHIPPED.stream()
                .filter(type -> type.getPackageName().equals(fromPackage) || type.getPackageName().startsWith(fromPackage + "."))
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getPackageName())
                .filter(name -> name.startsWith(folder + "."))
                .map(name -> firstSegment(name.substring(folder.length() + 1)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** The first segment of a dotted name, or the whole name where it has one. */
    private static String firstSegment(String name) {
        return name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
    }
}
