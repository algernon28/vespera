package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The folders not relevant, as the Inclusions tab writes and reads them (ADR-334 §6, which ADR-342 keeps and ADR-344
 * §1 draws as a tree): {@code not-relevant-folders.yaml} written whole and read back equal, a folder written as
 * occurrences' paths spell one, and a question below a folder listed lying below it. Held without a page, in
 * {@code NotRelevantFolders} itself.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class NotRelevantFoldersTest {

    @Test
    @Story("The operator leaves folders out of what is kept")
    @DisplayName("The list written is read back as it was, in its order, and an empty list as none")
    void theListWrittenIsReadBackAsItWas() {
        List<String> folders = List.of("manuals/old", "D:/code/billing/legacy", "notes \"draft\"");

        claim("the folders written are read back, the same and in the same order, a quote within one among them",
                () -> assertThat(NotRelevantFolders.read(Optional.of(NotRelevantFolders.render(folders))))
                        .containsExactlyElementsOf(folders));
        claim("and an empty list is written as one, and read back as none",
                () -> assertThat(NotRelevantFolders.read(Optional.of(NotRelevantFolders.render(List.of())))).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("folders")
    @Story("The operator leaves folders out of what is kept")
    @DisplayName("A folder is written as an occurrence's path spells one, or not at all")
    void aFolderIsWrittenAsAnOccurrencesPathSpellsOne(String folder, boolean written) {
        claim("a folder relative to the corpus root with a slash between its elements, or absolute below a code set's"
                        + " folder, may be written; a backslash, an empty element, a dot or two, or a slash at the end"
                        + " may not",
                () -> assertThat(NotRelevantFolders.isWritten(folder)).isEqualTo(written));
    }

    static Stream<Arguments> folders() {
        return Stream.of(
                Arguments.of(Named.of("relative", "manuals/old"), true),
                Arguments.of(Named.of("absolute with a drive", "D:/code/billing"), true),
                Arguments.of(Named.of("a backslash", "manuals\\old"), false),
                Arguments.of(Named.of("an empty element", "manuals//old"), false),
                Arguments.of(Named.of("two dots", "manuals/../old"), false),
                Arguments.of(Named.of("a slash at the end", "manuals/"), false),
                Arguments.of(Named.of("nothing", ""), false));
    }

    @Test
    @Story("The operator leaves folders out of what is kept")
    @DisplayName("A question lies below a folder listed by whole elements, and a file the operator wrote otherwise is refused")
    void aQuestionLiesBelowAFolderByWholeElements() {
        claim("a question in a folder listed, or deeper, lies below it",
                () -> assertThat(List.of(NotRelevantFolders.lies("manuals/old/a.pdf", "manuals/old"),
                                NotRelevantFolders.lies("manuals/old", "manuals/old")))
                        .containsExactly(true, true));
        claim("and one in a folder whose name only begins the same does not",
                () -> assertThat(NotRelevantFolders.lies("manuals/older/a.pdf", "manuals/old")).isFalse());
        claim("a file in another form is refused, so the window changes nothing in it",
                () -> assertThatThrownBy(() -> NotRelevantFolders.read(Optional.of("folders: [a, b]\n")))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
