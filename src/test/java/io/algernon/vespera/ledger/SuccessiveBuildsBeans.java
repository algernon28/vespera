package io.algernon.vespera.ledger;

import java.util.List;
import java.util.Properties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * An {@link ImplementationVersions} a test can move one module of between two invocations, so that
 * one test can play two builds of the application against one database (ADR-154, ADR-058).
 *
 * <p>What the build records is the SHA of the last commit touching each module (ADR-058), fixed for
 * the life of a jar. An operator meets a second build by installing it between two invocations over
 * the same working directory; a test has one context for both, so the versions it reads have to be
 * able to move while the context stands. Each module starts at {@code <module>@1}, and {@link
 * #aCommitTo} moves one of them to {@code <module>@2} — the same thing a commit to that module does to
 * the next build's answer, and nothing else.
 *
 * <p>The state is static because the bean is read by run beans the job builds per invocation, and a
 * test reaches it without holding the bean. {@link #theFirstBuild} puts it back; a test that moves a
 * module calls it afterwards, so the next test in the class starts from the first build.
 *
 * <p>{@link Primary} because the slice it is imported into registers the real one too, and bean
 * overriding is off, the reason {@link ModuleNamedVersionsBeans} gives. Lives in {@code ledger}'s test
 * package because the constructor taking the recorded answers is package-private here.
 */
@TestConfiguration
public class SuccessiveBuildsBeans {

    /** Every module the build records a version for, which is every module a stage can name. */
    private static final List<String> MODULES =
            List.of("ledger", "corpus", "extraction", "similarity", "embedding", "synthesis", "profile", "pipeline");

    /** The versions every invocation made through this bean reads, shared with the test driving it. */
    private static final Properties VERSIONS = new Properties();

    static {
        theFirstBuild();
    }

    /** Every module back at the version the first build recorded for it. */
    public static void theFirstBuild() {
        MODULES.forEach(module -> VERSIONS.setProperty(module, module + "@1"));
    }

    /** The build that follows a commit to {@code module}: that module's version moves, and no other. */
    public static void aCommitTo(String module) {
        if (!MODULES.contains(module)) {
            throw new IllegalArgumentException("no module named " + module + " records a version");
        }
        VERSIONS.setProperty(module, module + "@2");
    }

    @Bean
    @Primary
    ImplementationVersions successiveBuildsImplementationVersions() {
        return new ImplementationVersions(VERSIONS);
    }
}
