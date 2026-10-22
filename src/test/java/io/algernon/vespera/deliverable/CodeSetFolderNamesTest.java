package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
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

    /** The most UTF-16 code units a folder's name may hold, as a copy's name may. */
    private static final int A_HUNDRED_UNITS = 100;

    /** A name longer than {@link #A_HUNDRED_UNITS}, by twenty units. */
    private static final int A_HUNDRED_AND_TWENTY_UNITS = 120;

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

    @Test
    @Story("Each code set is copied whole into a folder of its own")
    @DisplayName("Two names are told apart once they are made safe, so two that differ only in a character no system allows meet, and the suffix goes at the end of a folder's name")
    void namesAreToldApartOnceTheyAreMadeSafe() {
        claim(
                "a:b and a_b are one name once the colon is made an underscore, so the set whose first file is numbered"
                        + " 4 takes its number",
                () -> assertThat(CodeSetFolderNames.of(
                                List.of(set("p/a:b", "a:b", 3), set("q/a_b", "a_b", 4)), List.of()))
                        .containsExactlyInAnyOrderEntriesOf(Map.of("p/a:b", "a_b", "q/a_b", "a_b~4")));
        claim(
                "Shop.Core and shop.core collide without regard to case, and the second takes its number after the"
                        + " whole name, a folder opening by no extension",
                () -> assertThat(CodeSetFolderNames.of(
                                List.of(set("a/Shop.Core", "Shop.Core", 3), set("b/shop.core", "shop.core", 4)),
                                List.of()))
                        .containsExactlyInAnyOrderEntriesOf(Map.of("a/Shop.Core", "Shop.Core", "b/shop.core", "shop.core~4")));
    }

    @Test
    @Story("Each code set is copied whole into a folder of its own")
    @DisplayName("A set's name is made safe on every system and cut to a hundred units, and a name left empty is the number of its first file")
    void aNameIsMadeSafeAndCutAndOneLeftEmptyIsTheLowestMemberOccurrence() {
        String aLongName = "a".repeat(A_HUNDRED_AND_TWENTY_UNITS);
        Map<String, String> names = CodeSetFolderNames.of(
                List.of(set("dots", "dots", 12), set("devices", "devices", 13), set("long", "long", 14)),
                List.of(
                        new CodeSetNames.Entry("dots", "..."),
                        new CodeSetNames.Entry("devices", "CON"),
                        new CodeSetNames.Entry("long", aLongName)));

        claim(
                "a typed name of three dots, whose trailing dots go, is the number of its set's first file, 12",
                () -> assertThat(names).containsEntry("dots", "12"));
        claim(
                "a typed Windows device name takes a leading underscore",
                () -> assertThat(names).containsEntry("devices", "_CON"));
        claim(
                "and a name of 120 units is cut to its first 100",
                () -> assertThat(names).containsEntry("long", "a".repeat(A_HUNDRED_UNITS)));
    }

    @Test
    @Story("Each code set is copied whole into a folder of its own")
    @DisplayName("A suffixed name that still meets another set's name takes the suffix again")
    void aSuffixedNameThatStillCollidesTakesTheSuffixAgain() {
        claim(
                "of core (first file 5), core~7 (first file 6) and CORE (first file 7), CORE meets core and takes ~7,"
                        + " which meets core~7, and so takes it again",
                () -> assertThat(CodeSetFolderNames.of(
                                List.of(set("a/core", "core", 5), set("b/core~7", "core~7", 6), set("c/CORE", "CORE", 7)),
                                List.of()))
                        .containsExactlyInAnyOrderEntriesOf(
                                Map.of("a/core", "core", "b/core~7", "core~7", "c/CORE", "CORE~7~7")));
    }

    @Test
    @Story("Each code set is copied whole into a folder of its own")
    @DisplayName("A rename naming a folder that holds no code set of this run is dormant: it renames nothing and is counted")
    void anEntryNamingNoSetOfTheRunIsDormantAndCounted() {
        List<ListedCodeSet> sets = List.of(set("shop/core", "core", 5), set("shop/api", "api", 6));
        List<CodeSetNames.Entry> typed = List.of(
                new CodeSetNames.Entry("gone/core", "api"),
                new CodeSetNames.Entry("shop/core", "Shop core"),
                new CodeSetNames.Entry("", "Archive"));

        claim(
                "the two entries naming no set of the run, one of them the archive's top, are counted",
                () -> assertThat(CodeSetFolderNames.dormant(sets, typed)).isEqualTo(2L));
        claim(
                "and neither changes a name: api keeps its own name though a dormant entry names another folder api",
                () -> assertThat(CodeSetFolderNames.of(sets, typed))
                        .containsExactlyInAnyOrderEntriesOf(Map.of("shop/core", "Shop core", "shop/api", "api")));
    }

    /** A set at {@code folder} of the corpus root, named {@code defaultName}, its lowest member {@code lowestMember}. */
    private static ListedCodeSet set(String folder, String defaultName, long lowestMember) {
        return new ListedCodeSet(folder, defaultName, new OccurrenceId(lowestMember));
    }
}
