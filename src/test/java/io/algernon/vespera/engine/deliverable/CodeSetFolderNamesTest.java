package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The folder each code set is given directly in the tree's {@code code/} (ADR-296 §3.1, as ADR-377 §9 amends it): its
 * default name, its folder's last element, made safe and cut as a copy's name is (ADR-280 §1.2, §3), and only then told
 * apart from every other set's by the one key left, the set whose lowest member occurrence id is lower keeping the
 * name, each other taking {@code ~} and its own lowest member occurrence id at the end of the name. No name is typed:
 * a set is named by its folder alone.
 *
 * <p>Asked of {@code CodeSetFolderNames} directly: the names are worked out from recorded rows alone, reading no
 * file (ADR-296 §3.4). What a tree does with them is {@code CodeSetsInTheTreeTest}'s.
 */
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("573")
@Issue("807")
@Link(name = "ADR-296", url = Adr.EACH_CODE_SET_IS_COPIED_WHOLE_UNDER_CODE, type = "adr")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class CodeSetFolderNamesTest {

    @Test
    @Story("Each code set is copied whole into a folder of its own")
    @DisplayName("Of default names alike, case aside, the set with the lowest-numbered first file keeps the name and each other takes its number")
    void amongDefaultNamesAlikeTheLowestMemberKeepsIt() {
        List<ListedCodeSet> sets = List.of(
                set("apps/core", "core", 5),
                set("old/core", "core", 3),
                set("x/Core", "Core", 2),
                set("libs/kernel", "kernel", 9));
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("x/Core", "Core");
        expected.put("old/core", "core~3");
        expected.put("apps/core", "core~5");
        expected.put("libs/kernel", "kernel");

        claim(
                "the set whose first file is numbered 2 keeps Core, the two named core alike with it, whose first files"
                        + " are numbered 3 and 5, each take a tilde and that number at the end, and kernel, alike with"
                        + " none, keeps its name",
                () -> assertThat(CodeSetFolderNames.of(sets)).containsExactlyInAnyOrderEntriesOf(expected));

        List<ListedCodeSet> reversed = new ArrayList<>(sets);
        Collections.reverse(reversed);
        claim(
                "and the same names are given whatever order the sets are handed over in",
                () -> assertThat(CodeSetFolderNames.of(reversed)).containsExactlyInAnyOrderEntriesOf(expected));
    }

    /** A set at {@code folder} of the corpus root, named {@code defaultName}, its lowest member {@code lowestMember}. */
    private static ListedCodeSet set(String folder, String defaultName, long lowestMember) {
        return new ListedCodeSet(folder, defaultName, new OccurrenceId(lowestMember));
    }
}
