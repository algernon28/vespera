package io.algernon.vespera.ledger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * What version of the code a stage ran as: the SHA of the last commit touching that stage's owning
 * module (ADR-058), computed at build time and read back from a generated resource here.
 *
 * <p>Scoped and mechanical, both deliberately. A repo-wide SHA changes when anything changes, so
 * every stage's runs would invalidate on an unrelated commit; a hand-maintained constant relies on a
 * human remembering. The accepted cost is the other way round: a comment-only edit inside a module
 * bumps its version and invalidates that stage's runs.
 *
 * <p>Dependency versions are deliberately not in this hash. A stage's behaviour does depend on them,
 * but they are configuration the run consumed, and {@code config_consumed} is where a run records
 * what it was given.
 */
@Component
public class ImplementationVersions {

    /** Written by the build, one {@code <module>=<sha>} line per module (see the pom). */
    static final String RESOURCE = "/implementation-versions.properties";

    /** What the build writes when it could not ask git — never a usable version. */
    static final String UNKNOWN = "unknown";

    private final Properties versions;

    public ImplementationVersions() {
        this(fromTheBuild());
    }

    /**
     * The seam a test needs: what the build wrote is a fact about this working tree, so a test
     * asserting against the real resource would assert about which commits happen to exist.
     */
    ImplementationVersions(Properties versions) {
        this.versions = versions;
    }

    private static Properties fromTheBuild() {
        Properties versions = new Properties();
        try (InputStream stream = ImplementationVersions.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                versions.load(stream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + RESOURCE, e);
        }
        return versions;
    }

    /**
     * The implementation version of {@code modules}, joined so that a change to any one of them
     * changes the result.
     *
     * <p>A stage whose single pass writes rows owned by more than one capability module reads none
     * of them accurately from one module's SHA alone (ADR-073's reading of ADR-058: "the last commit
     * touching any module the stage's pass invokes"). Naming every module the pass writes into is
     * what keeps a change to any one of them — a shingler-only edit included — from shipping under a
     * run id nothing minted a fresh row for.
     *
     * @throws IllegalStateException if the build did not record a version for any named module,
     *     which is the case where a run id would otherwise be minted from a version that means
     *     nothing
     */
    public String of(String... modules) {
        return Arrays.stream(modules).map(this::versionOf).collect(Collectors.joining("+"));
    }

    private String versionOf(String module) {
        String version = versions.getProperty(module);
        if (version == null || version.isBlank() || UNKNOWN.equals(version)) {
            throw new IllegalStateException(
                    "the build recorded no implementation version for module %s in %s, so no run may be minted against it"
                            .formatted(module, RESOURCE));
        }
        return version;
    }
}
