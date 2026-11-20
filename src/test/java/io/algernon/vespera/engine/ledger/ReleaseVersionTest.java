package io.algernon.vespera.engine.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the jar reads its release version (ADR-430 §4): from the {@code Implementation-Version} its manifest names, and
 * {@code unknown} where none is named, which is what classes run outside a jar, these among them, read.
 *
 * <p>{@code ReleaseVersion} is reached by its name rather than linked, so that the suite compiles before it is written;
 * each test fails until it is, ADR-430's "What the implementation owes" giving its four members.
 */
@Epic("Operation")
@Feature("Vespera's version")
@Issue("904")
@Link(name = "ADR-430", url = Adr.THE_RELEASE_VERSION_IS_THE_TAGS_AND_ENTERS_NO_RUN_ID, type = "adr")
class ReleaseVersionTest {

    private static final String THE_TYPE = "io.algernon.vespera.engine.ledger.ReleaseVersion";

    /** A release's version, as the build given the tag {@code v2.0.0} writes it into the manifest. */
    private static final String A_RELEASE = "2.0.0";

    /** The version of a build no tag made. */
    private static final String NO_TAG = "0-SNAPSHOT";

    /** What is shown where no manifest names a version. */
    private static final String UNKNOWN = "unknown";

    @Test
    @Story("Vespera says which version it is")
    @DisplayName("The version the jar's manifest names is the version")
    void theManifestsVersionIsTheReleaseVersion() throws Exception {
        claim("a manifest naming 2.0.0 reads 2.0.0",
                () -> assertThat(valueOf(A_RELEASE)).isEqualTo(A_RELEASE));
        claim("a manifest naming 0-SNAPSHOT, a build no tag made, reads 0-SNAPSHOT",
                () -> assertThat(valueOf(NO_TAG)).isEqualTo(NO_TAG));
    }

    @Test
    @Story("Vespera says which version it is")
    @DisplayName("Where no version is named, the version is unknown")
    void noVersionNamedIsUnknown() throws Exception {
        claim("no version named reads unknown",
                () -> assertThat(valueOf(null)).isEqualTo(UNKNOWN));
        claim("a blank version reads unknown",
                () -> assertThat(valueOf("  ")).isEqualTo(UNKNOWN));
        claim("the type's own word for it is unknown",
                () -> assertThat(type().getField("UNKNOWN").get(null)).isEqualTo(UNKNOWN));
        claim("these classes, which lie in no jar and so have no manifest, read unknown",
                () -> assertThat(value(type().getMethod("ofThisJar").invoke(null))).isEqualTo(UNKNOWN));
    }

    private static Class<?> type() throws ClassNotFoundException {
        return Class.forName(THE_TYPE);
    }

    private static String valueOf(String named) throws Exception {
        Method of = type().getMethod("of", String.class);
        return value(of.invoke(null, named));
    }

    private static String value(Object version) throws Exception {
        return (String) version.getClass().getMethod("value").invoke(version);
    }
}
