package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The operator's renames of code sets, read from {@code code-set-names.yaml} beside the database (ADR-284 §9): the
 * file's entries as written, and the refusal of a file that is not a list of folders and names. Applying them is
 * the drawing's (#573). {@code CodeSetNames} is reached through {@link CodeSetNamesByItsName}, so this compiles
 * before it is built and fails, until it is, on the class being absent.
 */
@Epic("The deliverable")
@Feature("Names of code sets")
@Issue("572")
@Link(name = "ADR-284", url = Adr.CODE_IS_KEPT_IN_CODE_SETS_NAMED_FOR_THEIR_FOLDER, type = "adr")
class CodeSetNamesTest {

    private static final String FILE_NAME = "code-set-names.yaml";

    @Test
    @Story("The operator renames a code set")
    @DisplayName("The file of renames beside the database is read entry by entry, in its order, the set at the archive's root by an empty folder; an absent or empty file renames nothing")
    void theEntriesAreReadAsWritten(@TempDir Path workingDirectory) throws Exception {
        String yaml = "- folder: shop/core\n  name: Shop core\n- folder: \"\"\n  name: Archivio\n- folder: tools\n  name: \"tools, old\"\n";

        claim(
                "the file is named code-set-names.yaml",
                () -> assertThat(CodeSetNamesByItsName.fileName()).isEqualTo(FILE_NAME));
        claim(
                "its three entries are read as written, in its order",
                () -> assertThat(CodeSetNamesByItsName.parse(yaml))
                        .containsExactly("shop/core=Shop core", "=Archivio", "tools=tools, old"));
        claim(
                "with no file beside the database there is no entry, and the file once written is read",
                () -> {
                    assertThat(CodeSetNamesByItsName.read(workingDirectory)).isEmpty();
                    Files.writeString(workingDirectory.resolve(FILE_NAME), yaml, StandardCharsets.UTF_8);
                    assertThat(CodeSetNamesByItsName.read(workingDirectory)).hasSize(3);
                });
        claim(
                "a file holding nothing has no entry",
                () -> assertThat(CodeSetNamesByItsName.parse("")).isEmpty());
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("A file of renames that is not a list of folders and names is refused whole, naming the file")
    void aFileThatIsNotAListOfFoldersAndNamesIsRefusedNamingIt() {
        Map<String, String> refused = new LinkedHashMap<>();
        refused.put("a mapping and not a list", "folder: a\nname: b\n");
        refused.put("an entry with a third key", "- folder: a\n  name: b\n  colour: red\n");
        refused.put("an entry with no name", "- folder: a\n");
        refused.put("a name that is a number", "- folder: a\n  name: 42\n");
        refused.put("a blank name", "- folder: a\n  name: \"  \"\n");
        refused.put("a folder written with a backslash", "- folder: \"a\\\\b\"\n  name: b\n");
        refused.put("a folder beginning with a slash", "- folder: /a\n  name: b\n");
        refused.put("a folder ending with a slash", "- folder: a/\n  name: b\n");
        refused.put("a folder with an empty element", "- folder: a//b\n  name: b\n");
        refused.put("a folder given twice", "- folder: a\n  name: b\n- folder: a\n  name: c\n");
        refused.put("text that is not YAML", "- folder: [a\n");

        refused.forEach((what, yaml) -> claim(
                "a file holding " + what + " is refused, the refusal naming the file",
                () -> assertThatThrownBy(() -> CodeSetNamesByItsName.parse(yaml))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(FILE_NAME)));
    }
}
