package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The publish's four plain values, the site's address, the space's key, the parent by its id or its title, and the
 * account's email, are five text keys of the profile, the last of its record, and no key holds the API token
 * (ADR-359 §1).
 */
@Epic("Operation")
@Feature("Profile")
@Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
class TheConfluenceValuesAreProfileKeysTest {

    /** The five keys, in the order the record declares them: the site, the space, the parent two ways, the email. */
    private static final List<String> THE_FIVE = ProfileKeys.CONFLUENCE_KEYS;

    /** The values {@link #THE_FIVE_ANSWERED} writes, each one no other key carries. */
    private static final List<String> THE_FIVE_VALUES = List.of("https://wiki.example.com", "DOCS", "123456789",
            "Hand-overs", "operator@example.org");

    @Test
    @Story("The operator names where a publish sends in the profile")
    @DisplayName("No value of the profile holds a token, a secret or a password")
    void noKeyHoldsAToken() {
        claim("no key of the profile is named for a token, a secret, a password or a credential: the API token is never"
                        + " written into profile.yaml",
                () -> assertThat(Arrays.stream(Profile.class.getRecordComponents()).map(RecordComponent::getName))
                        .noneMatch(name -> name.matches("(?i).*(token|secret|password|credential).*")));
    }
}
