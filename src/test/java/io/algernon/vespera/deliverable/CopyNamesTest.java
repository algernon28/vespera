package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
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
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The name each copy is given in its cluster folder (ADR-280 §1, §3): its original's own file name, made safe for
 * every operating system and cut to {@value CopyNames#LONGEST_NAME} UTF-16 code units, and, only where it collides
 * with another name in the folder without regard to case, its occurrence id after a tilde before its extension.
 *
 * <p>Asked of {@link CopyNames#of} directly: the names are worked out from the cluster's members and the names of the
 * pictures its page shows, reading no file (§2), so no archive is needed to hold them, and a name no file system here
 * would create, a control character or a device name among them, can still be handed over. What a tree does with the
 * names is {@code TheClusterFolderHoldsItsPageAndItsCopiesTest}'s.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("570")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class CopyNamesTest {

    /** A page that shows no picture, so no picture's name is reserved. */
    private static final Set<String> NO_PICTURES = Set.of();

    /** The most UTF-16 code units a copy's whole name may hold, its suffix and its extension included. */
    private static final int A_HUNDRED_UNITS = 100;

    /** The seed whose partition every member sits in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** {@code é} written as one code point, as normalisation form C writes it. */
    private static final String E_ACUTE_COMPOSED = "é";

    /** {@code é} written as {@code e} and a combining acute accent, as normalisation form D writes it. */
    private static final String E_ACUTE_DECOMPOSED = "é";

    /** A character outside the Basic Multilingual Plane, written as a surrogate pair of two code units. */
    private static final String A_SURROGATE_PAIR = "😀";

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("Of names that differ only in case or in how an accent is written, the lowest-numbered document keeps its name and the others take their number")
    void aNameThatDiffersOnlyInCaseOrNormalisationIsKeptPlainByTheLowestOccurrenceOnly() {
        List<ListedSurvivor> members = List.of(
                member(7, "a/Report.pdf"),
                member(3, "b/REPORT.PDF"),
                member(5, "c/report.pdf"),
                member(9, "d/r" + E_ACUTE_COMPOSED + "sum" + E_ACUTE_COMPOSED + ".txt"),
                member(4, "e/r" + E_ACUTE_DECOMPOSED + "sum" + E_ACUTE_DECOMPOSED + ".txt"),
                member(11, "f/straße.txt"),
                member(12, "g/STRASSE.txt"),
                member(6, "h/notes.txt"));
        Map<Long, String> expected = new TreeMap<>();
        expected.put(3L, "REPORT.PDF");
        expected.put(5L, "report~5.pdf");
        expected.put(7L, "Report~7.pdf");
        expected.put(4L, "r" + E_ACUTE_DECOMPOSED + "sum" + E_ACUTE_DECOMPOSED + ".txt");
        expected.put(9L, "r" + E_ACUTE_COMPOSED + "sum" + E_ACUTE_COMPOSED + "~9.txt");
        expected.put(11L, "straße.txt");
        expected.put(12L, "STRASSE~12.txt");
        expected.put(6L, "notes.txt");

        Map<Long, String> names = namesOf(members, NO_PICTURES);

        claim(
                "three names one case apart are kept plain by the lowest-numbered document alone, the other two"
                        + " taking a tilde and their number before the extension, each keeping its own case; two"
                        + " spellings of one accented name collide, the first-numbered keeping its name exactly as it"
                        + " was written; a sharp s and a double S collide too, which costs only a number; and a name"
                        + " that collides with nothing is kept as it is",
                () -> assertThat(names).containsExactlyEntriesOf(expected));
        List<ListedSurvivor> reversed = new ArrayList<>(members);
        Collections.reverse(reversed);
        claim(
                "and the same names are given whatever order the documents are handed over in",
                () -> assertThat(namesOf(reversed, NO_PICTURES)).containsExactlyEntriesOf(expected));
    }

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("A document named as the group's page, as a further page, or as one of its pictures takes its number, even the lowest-numbered")
    void aCopyYieldsToThePagesNamesAndToAPicturesName() {
        String aPicture = "0123456789abcdef.png";
        List<ListedSurvivor> members = List.of(
                member(2, "a/index.html"),
                member(7, "b/Index.HTML"),
                member(3, "c/INDEX-2.HTML"),
                member(4, "d/index-12.html"),
                member(5, "e/0123456789ABCDEF.PNG"),
                member(6, "f/index-.html"));
        Map<Long, String> expected = new TreeMap<>();
        expected.put(2L, "index~2.html");
        expected.put(7L, "Index~7.HTML");
        expected.put(3L, "INDEX-2~3.HTML");
        expected.put(4L, "index-12~4.html");
        expected.put(5L, "0123456789ABCDEF~5.PNG");
        expected.put(6L, "index-.html");

        Map<Long, String> names = namesOf(members, Set.of(aPicture));

        claim(
                "the group's page is index.html and a further page index- and digits .html, so every document"
                        + " named as one of them, in any case, takes its number, the lowest-numbered of two such"
                        + " included; a document named as a picture the page shows takes its number too; and"
                        + " index- with no digits is no page's name and is kept",
                () -> assertThat(names).containsExactlyEntriesOf(expected));
    }

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("A document named as another document's unfinished copy takes its number, though its own number is the lower")
    void aCopyYieldsToThePartOfAnotherCopy() {
        Map<Long, String> names = namesOf(
                List.of(
                        member(3, "a/notes.txt.part"),
                        member(8, "b/notes.txt"),
                        member(4, "c/minutes.docx"),
                        member(5, "d/MINUTES.DOCX.PART")),
                NO_PICTURES);

        claim(
                "a copy is written beside its name with .part added before it is moved into place, so a document"
                        + " named as another's name and .part takes its number whatever its own number is, and the"
                        + " other keeps its name",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                3L, "notes.txt~3.part",
                                8L, "notes.txt",
                                4L, "minutes.docx",
                                5L, "MINUTES.DOCX~5.PART")));
    }

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("A numbered name that still meets another name takes the number again")
    void aSuffixedNameThatStillCollidesTakesTheSuffixAgain() {
        Map<Long, String> names = namesOf(
                List.of(member(3, "a/plan.pdf"), member(5, "b/PLAN.PDF"), member(9, "c/plan~5.pdf")),
                NO_PICTURES);

        claim(
                "document 9 is called plan~5.pdf and collides with no other name, so it keeps it; document 5's"
                        + " PLAN.PDF meets document 3's plan.pdf, and with its number it would meet document 9's"
                        + " name, so it takes its number a second time",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                3L, "plan.pdf",
                                5L, "PLAN~5~5.PDF",
                                9L, "plan~5.pdf")));
    }

    @Test
    @Story("A document's name in the tree opens on every system")
    @DisplayName("Characters, device names and trailing dots Windows refuses are made safe, and an empty name becomes the document's number")
    void aNameIsMadeSafeOnEveryOperatingSystem() {
        Map<String, String> safeNameOf = new LinkedHashMap<>();
        safeNameOf.put("a/what: a \"test\" <draft> | v2?*.txt", "what_ a _test_ _draft_ _ v2__.txt");
        safeNameOf.put("b/back\\slash.txt", "back_slash.txt");
        safeNameOf.put("c/tab\there\u001f.txt", "tab_here_.txt");
        safeNameOf.put("d/CON.txt", "_CON.txt");
        safeNameOf.put("e/nul", "_nul");
        safeNameOf.put("f/Lpt9.tar.gz", "_Lpt9.tar.gz");
        safeNameOf.put("g/COM10.txt", "COM10.txt");
        safeNameOf.put("h/CONSOLE.log", "CONSOLE.log");
        safeNameOf.put("i/notes. . .", "notes");
        safeNameOf.put("j/AUX. ", "_AUX");
        List<ListedSurvivor> members = new ArrayList<>();
        Map<Long, String> expected = new TreeMap<>();
        long occurrence = 20;
        for (Map.Entry<String, String> name : safeNameOf.entrySet()) {
            members.add(member(occurrence, name.getKey()));
            expected.put(occurrence++, name.getValue());
        }
        long theEmptyOne = occurrence;
        members.add(member(theEmptyOne, "k/. ."));
        expected.put(theEmptyOne, String.valueOf(theEmptyOne));

        Map<Long, String> names = namesOf(members, NO_PICTURES);

        claim(
                "each of the nine characters Windows refuses in a name and each control character becomes an"
                        + " underscore; a name whose part before its first dot is a device name, in any case and"
                        + " with any extension, gains a leading underscore, while COM10 and CONSOLE are no device;"
                        + " trailing dots and spaces go, before the device name is looked for; and a name left with"
                        + " nothing is the document's number",
                () -> assertThat(names).containsExactlyEntriesOf(expected));
    }

    @Test
    @Story("A document's name in the tree opens on every system")
    @DisplayName("A long name is cut to a hundred characters, its number and its extension included")
    void aNameIsCutToAHundredUnitsWithItsSuffixAndItsExtension() {
        Map<Long, String> names = namesOf(
                List.of(member(21, "a/" + "r".repeat(120) + ".pdf"), member(22, "b/" + "R".repeat(120) + ".PDF")),
                NO_PICTURES);

        claim(
                "the first keeps its extension and as much of its name as fits in " + A_HUNDRED_UNITS
                        + " characters, and the second, colliding with it, keeps its number and its extension and"
                        + " gives up three more characters of its name for them",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                21L, "r".repeat(96) + ".pdf",
                                22L, "R".repeat(93) + "~22.PDF")));
        claim(
                "each is exactly " + A_HUNDRED_UNITS + " characters long, the most a name may hold",
                () -> {
                    assertThat(CopyNames.LONGEST_NAME).isEqualTo(A_HUNDRED_UNITS);
                    assertThat(names.values()).allSatisfy(name -> assertThat(name).hasSize(A_HUNDRED_UNITS));
                });
    }

    @Test
    @Story("A document's name in the tree opens on every system")
    @DisplayName("A cut never splits a character in two, and leaves no dot or space at the end of the name")
    void theCutSplitsNoSurrogatePairAndDropsTheDotsAndSpacesItLeaves() {
        Map<Long, String> names = namesOf(
                List.of(
                        member(41, "a/" + "a".repeat(95) + A_SURROGATE_PAIR + "bbb.txt"),
                        member(42, "b/" + "c".repeat(94) + " .zzzz.txt")),
                NO_PICTURES);

        claim(
                "a cut that would fall between the two halves of a character outside the basic plane falls"
                        + " before the character, so the name holds no half of it; and a cut that leaves a space"
                        + " and a dot at the end of the name drops both",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                41L, "a".repeat(95) + ".txt",
                                42L, "c".repeat(94) + ".txt")));
    }

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("An extension longer than sixteen characters counts as part of the name, so a number goes after it")
    void anExtensionLongerThanSixteenUnitsIsPartOfTheStem() {
        Map<Long, String> names = namesOf(
                List.of(
                        member(51, "a/notes.abcdefghijklmnopqrst"),
                        member(52, "b/NOTES.ABCDEFGHIJKLMNOPQRST"),
                        member(53, "c/data.abcdefghijklmnop"),
                        member(54, "d/DATA.ABCDEFGHIJKLMNOP"),
                        member(55, "e/README"),
                        member(56, "f/readme")),
                NO_PICTURES);

        claim(
                "an extension of twenty characters is no extension, so the number goes at the end of the name;"
                        + " one of sixteen is kept whole, so the number goes before it; and a name with no dot"
                        + " takes the number at its end",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                51L, "notes.abcdefghijklmnopqrst",
                                52L, "NOTES.ABCDEFGHIJKLMNOPQRST~52",
                                53L, "data.abcdefghijklmnop",
                                54L, "DATA~54.ABCDEFGHIJKLMNOP",
                                55L, "README",
                                56L, "readme~56")));
    }

    @Test
    @Story("A document's name in the tree opens on every system")
    @DisplayName("Two long names that differ only past the cut collide, and the second gives up more of its name for its number")
    void namesAreCutBeforeTheyAreCompared() {
        Map<Long, String> names = namesOf(
                List.of(member(31, "a/" + "x".repeat(120) + "1.txt"), member(32, "b/" + "x".repeat(120) + "2.txt")),
                NO_PICTURES);

        claim(
                "both names are cut before they are compared, so the two that differed only in their last"
                        + " character before the extension meet, and the second takes its number",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                31L, "x".repeat(96) + ".txt",
                                32L, "x".repeat(93) + "~32.txt")));
    }

    /** Every member's name in one cluster folder, by its occurrence id. */
    private static Map<Long, String> namesOf(List<ListedSurvivor> members, Set<String> pictureNames) {
        Map<Long, String> names = new TreeMap<>();
        CopyNames.of(members, pictureNames).forEach((occurrence, name) -> names.put(occurrence.value(), name));
        return names;
    }

    /** A member of one cluster, at {@code path}; no file is read, so its key and score are placeholders. */
    private static ListedSurvivor member(long occurrence, String path) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence), new OccurrencePath(path), "0".repeat(64), THE_SEED, "seeds/a.docx", 0, 0.5);
    }
}
