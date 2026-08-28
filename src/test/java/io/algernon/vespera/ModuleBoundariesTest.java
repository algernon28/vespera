package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * ADR-040 and ADR-041 as a test. Modules are capability-shaped rather than stage-shaped, because
 * stage assignment moved twice during design while the underlying capability did not — so the rule
 * they encode cannot be checked by reading package names, and is checked here instead.
 *
 * <p>One limit worth knowing rather than discovering: this verifies Java type references, via
 * ArchUnit over bytecode. A raw SQL string reaching into a table another capability owns is
 * invisible to it. ADR-041 records that gap, and it stays a matter for review by eye.
 *
 * <p>Every assertion sits inside a {@code claim(...)}, which names it in the report. Two of these
 * assert that a list is empty, and an empty list is exactly the case a report cannot render: the
 * claim has to say which list, and what a name appearing in it would have meant.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Link(name = "ADR-037", url = Adr.MODULITH_RETAINED_FOR_BOUNDARY_CHECKS, type = "adr")
@Link(name = "ADR-040", url = Adr.MODULES_ARE_CAPABILITY_SHAPED, type = "adr")
@Link(name = "ADR-041", url = Adr.LEDGER_OWNS_IDENTITY_AND_VERDICTS, type = "adr")
class ModuleBoundariesTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(VesperaApplication.class);

    /**
     * The nine modules ADR-040 defines. Seven are capability modules; {@code ledger} is what they
     * depend on, and {@code pipeline} is the composition root that depends on all of them.
     */
    private static final Set<String> RECORDED_MODULES =
            Set.of(
                    "ledger",
                    "corpus",
                    "extraction",
                    "similarity",
                    "embedding",
                    "synthesis",
                    "publication",
                    "profile",
                    "pipeline");

    private static List<String> identifiers(java.util.stream.Stream<org.springframework.modulith.core.ApplicationModule> modules) {
        return modules.map(module -> module.getIdentifier().toString()).sorted().toList();
    }

    /**
     * A capability module may depend on {@code ledger} and nothing else horizontal; {@code pipeline}
     * is the composition root and may depend on all of them.
     */
    @Test
    @Story("The boundary rule holds for every module")
    @DisplayName("No module depends on anything it has not declared")
    void moduleDependenciesAreAllowed() {
        claim(
                "every dependency a module declares is one the architecture allows, checked against compiled code",
                () -> assertThatCode(MODULES::verify).doesNotThrowAnyException());
    }

    /**
     * The check above only constrains a module that says what it may depend on. Both an absent
     * {@code @ApplicationModule} and one without an explicit list leave a module wide open, because
     * {@code allowedDependencies} defaults to {@code "*"} — so {@code verify()} passes and the rule
     * quietly stops applying to whatever shipped undeclared.
     *
     * <p>This is the test that keeps the rule alive as the remaining modules arrive.
     */
    @Test
    @Story("The boundary rule holds for every module")
    @DisplayName("Every module declares what it may depend on, so the boundary rule applies to it")
    void everyModuleDeclaresWhatItMayDependOn() {
        List<String> undeclared =
                identifiers(
                        MODULES.stream()
                                .filter(
                                        module ->
                                                module.getBasePackage()
                                                        .getAnnotation(ApplicationModule.class)
                                                        .map(
                                                                declaration ->
                                                                        List.of(declaration.allowedDependencies())
                                                                                .equals(List.of(ApplicationModule.OPEN_TOKEN)))
                                                        .orElse(true)));

        claim(
                "no module leaves allowedDependencies unset; one named here would be a module the boundary rule"
                        + " has silently stopped applying to",
                () -> assertThat(undeclared).isEmpty());
    }

    /**
     * A package under the application root that is not one of the nine is a module nobody decided
     * on. Catching it here is cheaper than finding it once things depend on it.
     */
    @Test
    @Story("The boundary rule holds for every module")
    @DisplayName("Every module is one of the nine the architecture defines")
    void everyModuleIsOneOfTheNineRecorded() {
        List<String> unrecorded =
                identifiers(MODULES.stream()).stream()
                        .filter(identifier -> !RECORDED_MODULES.contains(identifier))
                        .toList();

        claim(
                "every module found in the application is one of the nine the architecture defines;"
                        + " one named here is a module nobody decided on",
                () -> assertThat(unrecorded).isEmpty());
    }
}
