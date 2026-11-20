package io.algernon.vespera.engine.ledger;

/**
 * Which Vespera did something: the {@code Implementation-Version} the manifest of the jar it runs
 * from names (ADR-431 §4), {@code unknown} where none is named, as for classes run outside a jar.
 *
 * <p>Provenance and nothing more: it enters no run id and no stage's implementation version, and
 * nothing refuses for it.
 */
public final class ReleaseVersion {

    /** What is shown where no manifest names a version. */
    public static final String UNKNOWN = "unknown";

    private final String value;

    private ReleaseVersion(String value) {
        this.value = value;
    }

    /** {@code UNKNOWN} for {@code null} or blank, the value stripped otherwise. */
    public static ReleaseVersion of(String named) {
        if (named == null || named.isBlank()) {
            return new ReleaseVersion(UNKNOWN);
        }
        return new ReleaseVersion(named.strip());
    }

    /** The version of the jar this class runs from. */
    public static ReleaseVersion ofThisJar() {
        return of(ReleaseVersion.class.getPackage().getImplementationVersion());
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
