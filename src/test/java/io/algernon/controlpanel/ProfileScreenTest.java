package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile, {@code /profile} (ADR-302 §8): with no next act read, a form for every profile key, in its order, each
 * with its cost, saving that one key with its provenance.
 *
 * <p>The keys are {@link ProfileKey}'s, iterated rather than listed, so the test holds of a key added to the profile.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
@Disabled(ScreenServer.UNTIL_THE_SCREENS_ARE_BUILT)
class ProfileScreenTest {

    @TempDir
    Path folder;

    @Test
    @Story("The operator writes a profile value")
    @DisplayName("Every profile value has a form of its own, in order, with its cost, and saving one writes it with its provenance")
    void everyKeyHasAFormOfItsOwnWithItsCostAndOneIsSaved() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        List<String> keys = Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList();
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page page = browser.page();

            claim("with no next act read, the page holds a form for every profile value, named by it, in its order",
                    () -> assertThat(page.document().select("form").eachAttr("name")).containsExactlyElementsOf(keys));
            claim("each form holds the value's name, its value and its provenance, and one Save",
                    () -> assertThat(keys.stream().map(key -> List.of(controlNames(form(page, key)),
                                    buttonNames(form(page, key)))))
                            .containsOnly(List.of(List.of("key", "value", "provenance"), List.of("save"))));
            claim("each value's cost is said beside its form",
                    () -> assertThat(keys.stream().map(key -> byId(page, "cost-" + key).text().isBlank()))
                            .containsOnly(false));

            String key = ProfileKey.EXTRACTION_ATTEMPT.key();
            Page landed = browser.press(key, "save",
                    Map.of("value", "2", "provenance", "the converter was busy on the first attempt")).page();

            claim("saving lands back on the profile page",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/profile"));
            claim("and the profile holds that value with the provenance given",
                    () -> assertThat(ProfileEdit.read(Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME),
                                    StandardCharsets.UTF_8)).get(ProfileKey.EXTRACTION_ATTEMPT))
                            .isEqualTo(new ProfileEdit.Stated("2", "the converter was busy on the first attempt")));
        }
    }
}
