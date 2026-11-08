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
 * The folder each code set is given directly in the tree's {@code code/} (ADR-296 §3.1): its typed name, or its
 * default name, made safe and cut as a copy's name is (ADR-280 §1.2, §3), and only then told apart from every other
 * set's by ADR-280 §1.4's two keys, a typed name first and then the set whose lowest member occurrence id is lower,
 * each other taking {@code ~} and its own lowest member occurrence id at the end of the name. And the entries of the
 * names file that name no set of the run, which are dormant and counted (ADR-284 §9, ADR-296 §6.2).
 *
 * <p>Asked of {@code CodeSetFolderNames} directly: the names are worked out from recorded rows alone, reading no
 * file (ADR-296 §3.4). What a tree does with them is {@code CodeSetsInTheTreeTest}'s.
 */
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("573")
@Link(name = "ADR-296", url = Adr.EACH_CODE_SET_IS_COPIED_WHOLE_UNDER_CODE, type = "adr")
class CodeSetFolderNamesTest {

    @Test
    @Story("Each code set is copied whole into a folder of its own")
    @DisplayName("A name the operator typed outranks a default name, and of two default names alike the set with the lower-numbered first file keeps it")
    void aTypedNameOutranksADefaultNameAndAmongAlikeTheLowestMemberKeepsIt() {
        List<ListedCodeSet> sets = List.of(
                set("apps/core", "core", 5),
                set("libs/kernel", "kernel", 9),
                set("old/core", "core", 3),
                set("x/Core", "Core", 2));
        List<CodeSetNames.Entry> typed = List.of(new CodeSetNames.Entry("libs/kernel", "CORE"));
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("apps/core", "core~5");
        expected.put("libs/kernel", "CORE");
        expected.put("old/core", "core~3");
        expected.put("x/Core", "Core~2");

        claim(
                "the typed CORE keeps its name though its set's first file is numbered 9, and the three default names"
                        + " alike with it, whose first files are numbered 2, 3 and 5, each take a tilde and that number"
                        + " at the end",
                () -> assertThat(CodeSetFolderNames.of(sets, typed)).containsExactlyInAnyOrderEntriesOf(expected));

        List<ListedCodeSet> defaultsOnly = List.of(set("apps/core", "core", 5), set("old/core", "CORE", 3));
        claim(
                "with no name typed, of two default names alike, the set whose first file is numbered 3 keeps its name"
                        + " and the one numbered 5 takes its number",
                () -> assertThat(CodeSetFolderNames.of(defaultsOnly, List.of()))
                        .containsExactlyInAnyOrderEntriesOf(Map.of("old/core", "CORE", "apps/core", "core~5")));

        List<ListedCodeSet> reversed = new ArrayList<>(sets);
        Collections.reverse(reversed);
        claim(
                "and the same names are given whatever order the sets are handed over in",
                () -> assertThat(CodeSetFolderNames.of(reversed, typed)).containsExactlyInAnyOrderEntriesOf(expected));
    }

    /** A set at {@code folder} of the corpus root, named {@code defaultName}, its lowest member {@code lowestMember}. */
    private static ListedCodeSet set(String folder, String defaultName, long lowestMember) {
        return new ListedCodeSet(folder, defaultName, new OccurrenceId(lowestMember));
    }
}
