package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the Code sets section checks of a folder outside the corpus root before it adds it to the profile's
 * {@code codeSetFolders} (ADR-334 §5, which ADR-342 keeps and ADR-344 §1 draws in the sidebar): the whole path of a
 * folder that is there, apart from the corpus root, the working directory, the seed folder, the deliverable root and
 * every folder already stated, each refusal one line. Held without a page, in {@code CodeSetFolderChecks} itself.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class CodeSetFolderChecksTest {

    @TempDir
    Path folder;

    @Test
    @Story("The operator adds code kept outside the corpus root")
    @DisplayName("A folder of code outside the corpus root is added only where it is a whole path, there, and apart from every folder Vespera is told of")
    void aFolderIsAddedOnlyWhereItIsThereAndApartFromTheOthers() throws IOException {
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        Path work = Files.createDirectory(folder.resolve("work"));
        Path code = Files.createDirectory(folder.resolve("code"));
        Path inside = Files.createDirectory(corpusRoot.resolve("src"));
        List<CodeSetFolderChecks.Apart> apart = List.of(
                new CodeSetFolderChecks.Apart("corpus root", corpusRoot.toString()),
                new CodeSetFolderChecks.Apart("working directory", work.toString()));

        claim("a folder that is there and apart from the others is added",
                () -> assertThat(CodeSetFolderChecks.refusal(code.toString(), apart)).isEmpty());
        claim("a folder inside the corpus root is refused, the line saying so",
                () -> assertThat(CodeSetFolderChecks.refusal(inside.toString(), apart)).hasValueSatisfying(line ->
                        assertThat(line).startsWith("the folder " + inside + " is not added: it is inside the corpus root")));
        claim("a folder holding the corpus root is refused as above it",
                () -> assertThat(CodeSetFolderChecks.refusal(folder.toString(), apart)).hasValueSatisfying(line ->
                        assertThat(line).contains("it is above the corpus root")));
        claim("a folder not there, and a path that is not whole, are refused",
                () -> assertThat(List.of(CodeSetFolderChecks.refusal(folder.resolve("gone").toString(), apart),
                                CodeSetFolderChecks.refusal("relative/code", apart)))
                        .containsExactly(
                                Optional.of("the folder " + folder.resolve("gone") + " is not added: there is no such folder"),
                                Optional.of("the folder relative/code is not added: it is not the whole path of a folder")));
    }

    @Test
    @Story("The operator adds code kept outside the corpus root")
    @DisplayName("The folders the profile states are read in their order, split on the system's path separator")
    void theFoldersStatedAreReadInTheirOrder() {
        // No drive letter: on Linux the separator is ':', which a drive letter would split.
        String stated = "/code/billing" + File.pathSeparator + " " + File.pathSeparator + "/code/tools ";

        claim("each folder stated is read, stripped, in its order, an empty piece left out",
                () -> assertThat(CodeSetFolderChecks.statedIn(stated)).containsExactly("/code/billing", "/code/tools"));
        claim("and a profile stating none gives none",
                () -> assertThat(CodeSetFolderChecks.statedIn(null)).isEmpty());
    }
}
