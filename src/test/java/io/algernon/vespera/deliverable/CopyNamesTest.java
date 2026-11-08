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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The name each copy is given in its cluster folder (ADR-280 §1, §3): its original's own file name, made safe for
 * every operating system and cut to {@value CopyNames#LONGEST_NAME} UTF-16 code units and to what, with {@code .part}
 * after it, fits in 255 UTF-8 bytes, and, only where it collides with another name in the folder without regard to
 * case, its occurrence id after a tilde before its extension.
 *
 * <p>Asked of {@link CopyNames#of} directly: the names are worked out from the cluster's members alone, reading no
 * file (§2, ADR-286 §4), so no archive is needed to hold them, and a name no file system here
 * would create, a control character or a device name among them, can still be handed over. What a tree does with the
 * names is {@code TheClusterFolderHoldsItsPageAndItsCopiesTest}'s.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("570")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class CopyNamesTest {

    /** The seed whose partition every member sits in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** {@code é} written as one code point, as normalisation form C writes it. */
    private static final String E_ACUTE_COMPOSED = "é";

    /** {@code é} written as {@code e} and a combining acute accent, as normalisation form D writes it. */
    private static final String E_ACUTE_DECOMPOSED = "é";

    /**
     * Names a file system holds as one file (ADR-280 §1.4, §1.6): the key a name is compared by is lower-cased, then
     * upper-cased, then decomposed and composed again, and two names NTFS upper-cases unit by unit to one name collide
     * though their keys differ. The lowest-numbered document keeps its name as it was written, every other takes a
     * tilde and its number before its extension, and the order the documents are handed over in changes nothing.
     */
    static Stream<Arguments> namesAFileSystemHoldsAsOne() {
        String anAlphaWithYpogegrammeniThenAPerispomeni = "\u03B1\u0345\u0342";
        String anAlphaWithAnIotaThenAPerispomeni = "\u03B1\u03B9\u0342";
        return Stream.of(
                Arguments.of(
                        Named.of(
                                "Names that differ only in case or in how an accent is written",
                                List.of(
                                        member(7, "a/Report.pdf"),
                                        member(3, "b/REPORT.PDF"),
                                        member(5, "c/report.pdf"),
                                        member(9, "d/r" + E_ACUTE_COMPOSED + "sum" + E_ACUTE_COMPOSED + ".txt"),
                                        member(4, "e/r" + E_ACUTE_DECOMPOSED + "sum" + E_ACUTE_DECOMPOSED + ".txt"),
                                        member(11, "f/stra\u00dfe.txt"),
                                        member(12, "g/STRASSE.txt"),
                                        member(6, "h/notes.txt"))),
                        "three names one case apart are kept plain by the lowest-numbered document alone, the other two"
                                + " taking a tilde and their number before the extension, each keeping its own case;"
                                + " two spellings of one accented name collide, the first-numbered keeping its name"
                                + " exactly as it was written; a sharp s and a double S collide too, which costs only a"
                                + " number; and a name that collides with nothing is kept as it is",
                        Map.of(
                                3L, "REPORT.PDF",
                                5L, "report~5.pdf",
                                7L, "Report~7.pdf",
                                4L, "r" + E_ACUTE_DECOMPOSED + "sum" + E_ACUTE_DECOMPOSED + ".txt",
                                9L, "r" + E_ACUTE_COMPOSED + "sum" + E_ACUTE_COMPOSED + "~9.txt",
                                11L, "stra\u00dfe.txt",
                                12L, "STRASSE~12.txt",
                                6L, "notes.txt")),
                Arguments.of(
                        Named.of(
                                "Two names Windows upper-cases to one file though decomposing orders their marks apart",
                                List.of(
                                        member(81, "a/" + anAlphaWithYpogegrammeniThenAPerispomeni + ".pdf"),
                                        member(82, "b/" + anAlphaWithAnIotaThenAPerispomeni + ".pdf"))),
                        "NTFS upper-cases each unit, so U+03B1 U+0345 U+0342 and U+03B1 U+03B9 U+0342 both become U+0391"
                                + " U+0399 U+0342 and are one file there, though normalisation form D moves the"
                                + " subscript after the perispomeni in the first and the decomposed keys differ: the"
                                + " names collide, document 81 keeping its name and document 82 taking its number"
                                + " before the extension",
                        Map.of(
                                81L, anAlphaWithYpogegrammeniThenAPerispomeni + ".pdf",
                                82L, anAlphaWithAnIotaThenAPerispomeni + "~82.pdf")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("namesAFileSystemHoldsAsOne")
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("Of names a file system holds as one file, the lowest-numbered document keeps its name and the others take their number, whatever order they arrive in")
    void namesAFileSystemHoldsAsOneCollideAndTheLowestOccurrenceKeepsItsName(
            List<ListedSurvivor> members, String whyTheyCollide, Map<Long, String> expected) {
        claim(whyTheyCollide, () -> assertThat(namesOf(members)).containsExactlyInAnyOrderEntriesOf(expected));
        claim(
                "and the same names are given whatever order the documents are handed over in",
                () -> assertThat(namesOf(members.reversed())).containsExactlyInAnyOrderEntriesOf(expected));
    }

    /**
     * ADR-286 §3 and §4: a page is written as its name and {@code .part} before it is moved into place, so a copy yields
     * to that part as to the page; and no picture file is written, so a name a picture once had is reserved no more.
     */
    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("A document named as the group's page, a further page or the unfinished copy of either takes its number, even the lowest-numbered, and one named as a picture keeps its name")
    @Issue("560")
    @Link(name = "ADR-286", url = Adr.A_CLUSTERS_PAGE_IS_ONE_FILE_ITS_PICTURES_INLINED, type = "adr")
    void aCopyYieldsToThePagesNamesAndTheirPartsAndNotToAPicturesName() {
        List<ListedSurvivor> namedAsPages = List.of(
                member(2, "a/index.html"),
                member(7, "b/Index.HTML"),
                member(3, "c/INDEX-2.HTML"),
                member(4, "d/index-12.html"),
                member(6, "f/index-.html"));
        List<ListedSurvivor> namedAsPartsOrAPicture = List.of(
                member(8, "g/index.html.part"),
                member(9, "h/INDEX-2.HTML.PART"),
                member(10, "i/index-12.html.part"),
                member(5, "e/0123456789ABCDEF.PNG"));
        Map<Long, String> pages = new TreeMap<>();
        pages.put(2L, "index~2.html");
        pages.put(3L, "INDEX-2~3.HTML");
        pages.put(4L, "index-12~4.html");
        pages.put(6L, "index-.html");
        pages.put(7L, "Index~7.HTML");
        Map<Long, String> parts = new TreeMap<>();
        parts.put(5L, "0123456789ABCDEF.PNG");
        parts.put(8L, "index.html~8.part");
        parts.put(9L, "INDEX-2.HTML~9.PART");
        parts.put(10L, "index-12.html~10.part");

        claim(
                "the group's page is index.html and a further page index- and digits .html, so every document named"
                        + " as one of them, in any case, takes its number, the lowest-numbered of two such included,"
                        + " and index- with no digits is no page's name and is kept",
                () -> assertThat(namesOf(namedAsPages)).containsExactlyEntriesOf(pages));
        claim(
                "each page is written first as its name and .part, so a document named as such a part takes its"
                        + " number though no document of the group is named as the page itself; and a document named"
                        + " as sixteen hexadecimal characters and .png, as a picture once was, keeps its own name, no"
                        + " picture being written beside the page",
                () -> assertThat(namesOf(namedAsPartsOrAPicture)).containsExactlyEntriesOf(parts));
    }

    /**
     * Names Windows would refuse, made safe (ADR-280 §1.2, §1.3): a reserved or control character becomes an underscore;
     * a device name, the part before the first dot with the spaces at its end not looked at, gains a leading underscore;
     * trailing dots and spaces go; and a name left with nothing is the document's number. The rules hold of a name as it
     * is cut.
     */
    static Stream<Arguments> namesWindowsWouldRefuse() {
        Map<String, String> refusedCharactersAndDeviceNames = new LinkedHashMap<>();
        refusedCharactersAndDeviceNames.put("a/what: a \"test\" <draft> | v2?*.txt", "what_ a _test_ _draft_ _ v2__.txt");
        refusedCharactersAndDeviceNames.put("b/back\\slash.txt", "back_slash.txt");
        refusedCharactersAndDeviceNames.put("c/tab\there\u001f.txt", "tab_here_.txt");
        refusedCharactersAndDeviceNames.put("d/CON.txt", "_CON.txt");
        refusedCharactersAndDeviceNames.put("e/nul", "_nul");
        refusedCharactersAndDeviceNames.put("f/Lpt9.tar.gz", "_Lpt9.tar.gz");
        refusedCharactersAndDeviceNames.put("g/COM10.txt", "COM10.txt");
        refusedCharactersAndDeviceNames.put("h/CONSOLE.log", "CONSOLE.log");
        refusedCharactersAndDeviceNames.put("i/notes. . .", "notes");
        refusedCharactersAndDeviceNames.put("j/AUX. ", "_AUX");
        refusedCharactersAndDeviceNames.put("k/. .", "");
        Map<String, String> portsAndSpaces = new LinkedHashMap<>();
        portsAndSpaces.put("a/COM0.txt", "_COM0.txt");
        portsAndSpaces.put("b/lpt0", "_lpt0");
        portsAndSpaces.put("c/COM\u00b9.log", "_COM\u00b9.log");
        portsAndSpaces.put("d/com\u00b2.txt", "_com\u00b2.txt");
        portsAndSpaces.put("e/COM\u00b3", "_COM\u00b3");
        portsAndSpaces.put("f/LPT\u00b9.txt", "_LPT\u00b9.txt");
        portsAndSpaces.put("g/Lpt\u00b2.txt", "_Lpt\u00b2.txt");
        portsAndSpaces.put("h/LPT\u00b3.tar.gz", "_LPT\u00b3.tar.gz");
        portsAndSpaces.put("i/CON .txt", "_CON .txt");
        portsAndSpaces.put("j/nul   .tar.gz", "_nul   .tar.gz");
        portsAndSpaces.put("k/COM\u2074.txt", "COM\u2074.txt");
        portsAndSpaces.put("l/CON x.txt", "CON x.txt");
        Map<String, String> leftByTheCut = new LinkedHashMap<>();
        leftByTheCut.put("a/" + " ".repeat(100) + "abc", "");
        leftByTheCut.put("b/AUX" + " ".repeat(120) + "z.pdf", "_AUX.pdf");
        return Stream.of(
                aTableOfNames(
                        "Characters, device names and trailing dots Windows refuses, and a name left empty",
                        refusedCharactersAndDeviceNames,
                        20,
                        "each of the nine characters Windows refuses in a name and each control character becomes an"
                                + " underscore; a name whose part before its first dot is a device name, in any case"
                                + " and with any extension, gains a leading underscore, while COM10 and CONSOLE are no"
                                + " device; trailing dots and spaces go, before the device name is looked for; and a"
                                + " name left with nothing is the document's number"),
                aTableOfNames(
                        "The zero and superscript ports, and spaces before the first dot",
                        portsAndSpaces,
                        80,
                        "COM0 and LPT0, and COM and LPT followed by a superscript one, two or three, are device names"
                                + " on Windows, in any case and with any extension, and gain a leading underscore;"
                                + " spaces at the end of the part before the first dot are not looked at, so a device"
                                + " name followed by spaces is one too, the name otherwise kept as written; and a"
                                + " superscript four, or a letter after the space, makes no device name"),
                aTableOfNames(
                        "A name the cut leaves empty, or leaves a device name",
                        leftByTheCut,
                        71,
                        "a name of a hundred spaces and three letters is cut to its spaces, which go, so it is left"
                                + " empty and becomes the document's number; and a device name followed by a hundred"
                                + " and twenty spaces is cut to the device name and its extension, so it gains a"
                                + " leading underscore: the rules that make a name safe hold of the name as it is cut"));
    }

    /**
     * One case of {@link #namesWindowsWouldRefuse}: each path of {@code safeNameOf} a member numbered on from {@code
     * first}, and the name it is given, the document's number where the safe name is empty.
     */
    private static Arguments aTableOfNames(String what, Map<String, String> safeNameOf, long first, String claimed) {
        List<ListedSurvivor> members = new ArrayList<>();
        Map<Long, String> expected = new TreeMap<>();
        long occurrence = first;
        for (Map.Entry<String, String> name : safeNameOf.entrySet()) {
            members.add(member(occurrence, name.getKey()));
            expected.put(occurrence, name.getValue().isEmpty() ? String.valueOf(occurrence) : name.getValue());
            occurrence++;
        }
        return Arguments.of(Named.of(what, members), claimed, expected);
    }

    /** Every member's name in one cluster folder, by its occurrence id. */
    private static Map<Long, String> namesOf(List<ListedSurvivor> members) {
        Map<Long, String> names = new TreeMap<>();
        CopyNames.of(members).forEach((occurrence, name) -> names.put(occurrence.value(), name));
        return names;
    }

    /** A member of one cluster, at {@code path}; no file is read, so its key and score are placeholders. */
    private static ListedSurvivor member(long occurrence, String path) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence), new OccurrencePath(path), "0".repeat(64), THE_SEED, "seeds/a.docx", 0, 0.5);
    }
}
