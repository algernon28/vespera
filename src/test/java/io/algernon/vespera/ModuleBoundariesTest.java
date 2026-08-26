package io.algernon.vespera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
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
 */
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
    void moduleDependenciesAreAllowed() {
        MODULES.verify();
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

        assertEquals(
                List.of(),
                undeclared,
                "these modules do not declare allowedDependencies, so the boundary rule does not apply to them");
    }

    /**
     * A package under the application root that is not one of the nine is a module nobody decided
     * on. Catching it here is cheaper than finding it once things depend on it.
     */
    @Test
    void everyModuleIsOneOfTheNineRecorded() {
        List<String> unrecorded =
                identifiers(MODULES.stream()).stream()
                        .filter(identifier -> !RECORDED_MODULES.contains(identifier))
                        .toList();

        assertTrue(unrecorded.isEmpty(), () -> "modules not recorded in ADR-040: " + unrecorded);
    }
}
