package io.algernon.vespera.ledger;

import java.util.List;
import java.util.Properties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * An {@link ImplementationVersions} that records each module's own name as its version, so a run's
 * {@code implementation_version} reads as the list of modules it was minted over, in order.
 *
 * <p>Why a test needs this rather than the build's real answer: what the build records is the SHA of
 * the last commit touching each module (ADR-058), and several modules routinely share one — a single
 * commit touching {@code extraction}, {@code pipeline} and {@code synthesis} gives all three the same
 * SHA. Read back from those, {@code extraction+pipeline} and {@code pipeline+extraction} are the same
 * string, so a test could not tell that a stage's modules had been reordered, and a reorder mints a
 * different run id for the same work. Recording the name instead makes the order visible while the
 * joining itself is still the real {@link ImplementationVersions#of}.
 *
 * <p>{@link Primary} because the slice it is imported into registers the real one too, and bean
 * overriding is off: two beans of this type, this one preferred wherever a single one is injected.
 * Lives in {@code ledger}'s test package because the constructor taking the recorded answers is
 * package-private here, deliberately; {@code @TestConfiguration} for the reason {@code
 * StubbedExtractionBeans} documents — left plain, component scanning would pick it up.
 */
@TestConfiguration
public class ModuleNamedVersionsBeans {

    /** Every module the build records a version for, which is every module a stage can name. */
    private static final List<String> MODULES =
            List.of("ledger", "corpus", "extraction", "similarity", "embedding", "synthesis", "profile", "pipeline");

    @Bean
    @Primary
    ImplementationVersions moduleNamedImplementationVersions() {
        Properties versions = new Properties();
        MODULES.forEach(module -> versions.setProperty(module, module));
        return new ImplementationVersions(versions);
    }
}
