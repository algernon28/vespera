package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.controlpanel.CodeSetRenames.Rename;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the control panel writes code-set names (ADR-295 §7, in ADR-284 §9's shape): the whole list, in the order
 * given, as double-quoted {@code folder} and {@code name}; read back in the same form and order; a list or file the
 * jar's reader would refuse, or a file in another form, refused. That {@code CodeSetNames} reads what is written is
 * the contract test's, {@code pipeline.TheJarReadsWhatTheControlPanelWritesTest}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("566")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
@Link(name = "ADR-284", url = Adr.CODE_IS_KEPT_IN_CODE_SETS_NAMED_FOR_THEIR_FOLDER, type = "adr")
class CodeSetRenamesTest {

    private static final String THREE_WRITTEN = """
            ---
            - folder: "shop/core"
              name: "Shop core"
            - folder: ""
              name: "Archive"
            - folder: "lib"
              name: "The \\"lib\\" \\\\ set"
            """;

    private static final String NOT_IN_FORM =
            "code-set-names.yaml is not in the form the jar writes it, so the control panel does not change it";

    @Test
    @Story("The control panel renames code sets")
    @DisplayName("A list the jar's reader would refuse is not written, and a file in another form is not read")
    void aListCodeSetNamesWouldRefuseIsNotWritten() {
        String outsideTheRoot = "is not written relative to the corpus root, with / between its elements, none empty,"
                + " and neither beginning nor ending with /";
        Map<List<Rename>, String> refused = new LinkedHashMap<>();
        refused.put(List.of(new Rename("lib", "One"), new Rename("lib", "Two")), "the folder \"lib\" is given twice");
        refused.put(List.of(new Rename("lib", " ")), "the name for the folder \"lib\" is blank");
        refused.put(List.of(new Rename("shop\\core", "Shop")), "the folder \"shop\\core\" " + outsideTheRoot);
        refused.put(List.of(new Rename("/shop//core", "Shop")), "the folder \"/shop//core\" " + outsideTheRoot);
        refused.put(List.of(new Rename("D:/", "Shop")), "the folder \"D:/\" " + outsideTheRoot);
        refused.put(List.of(new Rename("D:\\code\\billing", "Billing")),
                "the folder \"D:\\code\\billing\" " + outsideTheRoot);
        refused.put(List.of(new Rename("shop/", "Shop")), "the folder \"shop/\" " + outsideTheRoot);
        refused.put(List.of(new Rename("shop//core", "Shop")), "the folder \"shop//core\" " + outsideTheRoot);

        refused.forEach((list, reason) -> claim("a list where " + reason + " is refused with that reason",
                () -> assertThatThrownBy(() -> CodeSetRenames.render(list))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("code-set-names.yaml is not saved: " + reason)));
        for (String other : List.of(
                "---\n- {folder: \"lib\", name: \"Lib\"}\n",
                "---\n# mine\n- folder: \"lib\"\n  name: \"Lib\"\n",
                THREE_WRITTEN + "- folder: \"lib\"\n  name: \"Again\"\n",
                "---\n- folder: \"lib\"\n  name: \"Lib\"\n  note: \"mine\"\n")) {
            claim("a flow mapping, a comment, a folder twice or a third key is refused whole",
                    () -> assertThatThrownBy(() -> CodeSetRenames.read(Optional.of(other)))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage(NOT_IN_FORM));
        }
    }
}
