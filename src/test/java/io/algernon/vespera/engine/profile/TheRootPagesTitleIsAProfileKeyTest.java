package io.algernon.vespera.engine.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The root page's title is a key of the profile (ADR-398 §1): {@code confluenceRootTitle}, the record's last component,
 * a text value shipped unset; loaded and saved, {@code profile.yaml} keeps its value and the provenance the operator
 * wrote beside it; and, a value of the publish and not of how the deliverable was made, the deliverable's index does
 * not state it, as it states none of the other Confluence values (ADR-359 §7).
 */
@Epic("Operation")
@Feature("Profile")
@Issue("837")
@Link(name = "ADR-398", url = Adr.THE_CONFLUENCE_ROOT_PAGES_TITLE_IS_A_PROFILE_VALUE, type = "adr")
@Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
class TheRootPagesTitleIsAProfileKeyTest {

    /** The key, as {@code profile.yaml} spells it. */
    private static final String THE_KEY = "confluenceRootTitle";

    /** A title as the operator writes it, its language placeholder kept, and the provenance they write beside it. */
    private static final String A_TITLE = "Hand-overs {language}";

    private static final String WHY = "the name the team gives its hand-over pages";

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("The root page's title is the last key of the profile, a text value, unset in a profile drafted afresh")
    void itIsTheLastKeyOfTheProfileATextShippedUnset(@TempDir Path workingDirectory) {
        Optional<RecordComponent> component = Arrays.stream(Profile.class.getRecordComponents())
                .filter(each -> each.getName().equals(THE_KEY)).findFirst();
        Profile drafted = new ProfileStore(workingDirectory).load();

        claim("confluenceRootTitle is the profile's last key, after the email of the Confluence account",
                () -> assertThat(ProfileKeys.everyKey()).last().isEqualTo(THE_KEY));
        claim("its value is text", () -> assertThat(component.map(RecordComponent::getType))
                .isEqualTo(Optional.of(TextValue.class)));
        claim("and a profile drafted afresh leaves it unanswered: no value, no provenance, no pointer",
                () -> assertThat(Optional.ofNullable(ProfileKeys.valuesOf(drafted).get(THE_KEY))
                        .map(value -> Arrays.asList(value.value(), value.provenance(), value.measurement())))
                        .hasValueSatisfying(parts -> assertThat(parts).containsOnlyNulls()));
    }

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("A root page's title written in the profile, with its provenance, loads, and is saved back as written")
    void itsValueAndItsProvenanceAreKeptAsWritten(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), THE_KEY + ":\n  value: \"" + A_TITLE + "\"\n  provenance: \"" + WHY + "\"\n");

        Throwable loading = catchThrowable(() -> store.save(store.load()));
        Throwable reloading = catchThrowable(store::load);
        List<String> lines = Files.readAllLines(store.file());

        claim("the profile naming the root page's title loads, and is saved",
                () -> assertThat(loading).isNull());
        claim("saved, the file names the key at its top level",
                () -> assertThat(lines).contains(THE_KEY + ":"));
        claim("and loaded again, the key holds the title as written, its placeholder kept, with the operator's"
                        + " provenance beside it",
                () -> {
                    assertThat(reloading).isNull();
                    ProfileValue value = ProfileKeys.valuesOf(store.load()).get(THE_KEY);
                    assertThat(value).isNotNull();
                    assertThat(List.of(String.valueOf(value.value()), String.valueOf(value.provenance())))
                            .containsExactly(A_TITLE, WHY);
                });
    }

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("Every key of the profile names the root page's title, and the deliverable's index does not state it")
    void theDeliverablesIndexDoesNotStateIt(@TempDir Path workingDirectory) {
        Profile drafted = new ProfileStore(workingDirectory).load();

        claim("the keys as written, every key of the profile, name the root page's title",
                () -> assertThat(drafted.keysAsWritten().keySet()).contains(THE_KEY));
        claim("and the keys the deliverable's index states do not: it is a value of the publish, as the other"
                        + " Confluence values are, none of which it states",
                () -> assertThat(drafted.keysTheIndexStates().keySet())
                        .doesNotContain(THE_KEY)
                        .doesNotContainAnyElementsOf(ProfileKeys.CONFLUENCE_KEYS));
    }
}
