package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What version of the code a stage ran as (ADR-058).
 *
 * <p>The case worth pinning is the refusal. A run's identity is derived from this value, so a
 * placeholder read back as though it were a version would mint an identity that means nothing —
 * and it would do so silently, which is the failure that survives to production.
 *
 * <p>These build their own answers rather than reading what the build wrote. What the build wrote is
 * a fact about which commits exist in the working tree at the time, so a test asserting against it
 * would be asserting about git history rather than about this class.
 */
@Epic("Ledger")
@Feature("Implementation version")
@Issue("8")
@Link(name = "ADR-058", url = Adr.IMPLEMENTATION_VERSION_IS_THE_LAST_COMMIT, type = "adr")
class ImplementationVersionsTest {

    /** The shape of what the build records: the SHA of the last commit touching a module. */
    private static final String A_RECORDED_SHA = "765e0dd026347ddf972cc306b8057764ddfd5649";

    private static ImplementationVersions recording(String module, String version) {
        Properties versions = new Properties();
        versions.setProperty(module, version);
        return new ImplementationVersions(versions);
    }

    @Test
    @Story("What version of the code a stage ran as")
    @DisplayName("The version recorded for a module is read back exactly")
    void readsBackWhatTheBuildRecorded() {
        claim(
                "the commit the build recorded for a module is returned unchanged, so a run's identity is"
                        + " traceable to a commit a person can go and read",
                () -> assertThat(recording("corpus", A_RECORDED_SHA).of("corpus")).isEqualTo(A_RECORDED_SHA));
    }

    @Test
    @Story("What version of the code a stage ran as")
    @DisplayName("A module the build could not attribute refuses to be read at all")
    void refusesTheUnattributedPlaceholder() {
        ImplementationVersions versions = recording("pipeline", "unknown");

        claim(
                "a module the build could not ask git about is refused rather than returned, so no run is"
                        + " ever identified by a placeholder",
                () -> assertThatThrownBy(() -> versions.of("pipeline"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("pipeline"));
    }

    @Test
    @Story("What version of the code a stage ran as")
    @DisplayName("A module the build never mentioned refuses in the same way")
    void refusesAModuleTheBuildNeverRecorded() {
        ImplementationVersions versions = recording("corpus", A_RECORDED_SHA);

        claim(
                "a module missing from the build's record is refused exactly as an unattributed one is:"
                        + " both mean nobody knows what code would run",
                () -> assertThatThrownBy(() -> versions.of("embedding"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("embedding"));
    }

    @Test
    @Story("A stage whose pass writes rows owned by more than one module")
    @DisplayName("Naming a second module changes the version, so an edit to either one mints a fresh run")
    @Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
    void changesWhenEitherNamedModuleChanges() {
        String similaritySha = "111e0dd026347ddf972cc306b8057764ddfd111";
        Properties both = new Properties();
        both.setProperty("extraction", A_RECORDED_SHA);
        both.setProperty("similarity", similaritySha);
        ImplementationVersions versions = new ImplementationVersions(both);

        String extractionOnly = versions.of("extraction");
        String extractionAndSimilarity = versions.of("extraction", "similarity");

        claim(
                "naming similarity alongside extraction changes the composed version, so a"
                        + " shingler-only commit — one that never touches extraction at all — still mints a"
                        + " fresh run instead of reusing extraction's own",
                () -> assertThat(extractionAndSimilarity).isNotEqualTo(extractionOnly));

        both.setProperty("similarity", "222e0dd026347ddf972cc306b8057764ddfd222");
        ImplementationVersions afterASimilarityOnlyChange = new ImplementationVersions(both);

        claim(
                "changing only similarity's recorded commit, with extraction's untouched, still changes"
                        + " the composed version stage 2's run is minted under",
                () -> assertThat(afterASimilarityOnlyChange.of("extraction", "similarity"))
                        .isNotEqualTo(extractionAndSimilarity));
    }
}
