package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The release version enters no run id (ADR-431 §3): no class of a module a stage's implementation version names
 * (ADR-058, ADR-216 §7), and none of the classes a run's id is derived through, names {@code ReleaseVersion} or asks a
 * package for the version its manifest names. So a jar of one commit mints the same runs whatever version it was
 * built as, and a release that touches no stage's module re-runs nothing.
 *
 * <p>{@code RunMint} is not among them: it hands the version to {@code Runs.recordRelease}, which writes it beside the
 * run, as a text, after the id is derived (ADR-431 §5); {@code ARunKeepsEveryReleaseThatMintedOrContinuedItTest}
 * holds that the id does not move with it.
 *
 * <p>The modules are read from {@link StageModules}, so a stage naming another module brings it under the claim.
 * {@code ReleaseVersion} is named by its class name, so that the suite compiles before it is written; the first claim
 * fails until it is, so the others are not held over a type that does not exist.
 */
@Epic("Operation")
@Feature("Vespera's version")
@Issue("904")
@Link(name = "ADR-431", url = Adr.THE_RELEASE_VERSION_IS_THE_TAGS_AND_ENTERS_NO_RUN_ID, type = "adr")
@Link(name = "ADR-058", url = Adr.IMPLEMENTATION_VERSION_IS_THE_LAST_COMMIT, type = "adr")
class NoRunIsMintedFromTheReleaseVersionTest {

    private static final String THE_APPLICATION = "io.algernon.vespera";

    private static final String THE_ENGINE = THE_APPLICATION + ".engine.";

    private static final String THE_RELEASE_VERSION = THE_ENGINE + "ledger.ReleaseVersion";

    /**
     * The classes a run's id is derived through, whose modules no stage's version names: its id, its rows, its
     * versions and the configuration each stage consumed.
     */
    private static final Set<String> THE_MINTING_CLASSES = Set.of(
            THE_ENGINE + "ledger.RunId",
            THE_ENGINE + "ledger.Runs",
            THE_ENGINE + "ledger.ImplementationVersions",
            THE_ENGINE + "pipeline.StageRuns");

    /** How the jar reads the version its manifest names. */
    private static final String THE_PACKAGES_VERSION = "getImplementationVersion";

    @Test
    @Story("Vespera's version never decides which work is done again")
    @DisplayName("No class a piece of work is identified through reads Vespera's version")
    void noClassARunIsMintedThroughReadsTheReleaseVersion() {
        JavaClasses jar = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(THE_APPLICATION);
        Set<String> stagesModules = Arrays.stream(StageModules.values())
                .flatMap(stage -> stage.modules().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        List<JavaClass> held = jar.stream()
                .filter(each -> THE_MINTING_CLASSES.contains(each.getName())
                        || stagesModules.stream().anyMatch(module -> lies(each, module)))
                .toList();

        claim("the class that reads Vespera's version exists, so the claims below are held over a real type",
                () -> assertThat(jar.contain(THE_RELEASE_VERSION)).isTrue());
        claim("every class a run is minted through, and some class of each module a stage's version names, is held",
                () -> {
                    assertThat(held.stream().map(JavaClass::getName)).containsAll(THE_MINTING_CLASSES);
                    assertThat(stagesModules)
                            .allMatch(module -> held.stream().anyMatch(each -> lies(each, module)));
                });
        claim("none of them names the class that reads Vespera's version",
                () -> assertThat(held.stream().filter(NoRunIsMintedFromTheReleaseVersionTest::namesTheReleaseVersion)
                        .map(JavaClass::getName)).isEmpty());
        claim("none of them asks a package for the version its manifest names",
                () -> assertThat(held.stream().filter(NoRunIsMintedFromTheReleaseVersionTest::asksTheManifest)
                        .map(JavaClass::getName)).isEmpty());
    }

    private static boolean lies(JavaClass each, String module) {
        String folder = THE_ENGINE + module;
        return each.getPackageName().equals(folder) || each.getPackageName().startsWith(folder + ".");
    }

    private static boolean namesTheReleaseVersion(JavaClass each) {
        return each.getDirectDependenciesFromSelf().stream()
                .map(Dependency::getTargetClass)
                .anyMatch(target -> target.getName().equals(THE_RELEASE_VERSION));
    }

    private static boolean asksTheManifest(JavaClass each) {
        return each.getMethodCallsFromSelf().stream()
                .map(JavaMethodCall::getTarget)
                .anyMatch(target -> target.getOwner().isEquivalentTo(Package.class)
                        && target.getName().equals(THE_PACKAGES_VERSION));
    }
}
