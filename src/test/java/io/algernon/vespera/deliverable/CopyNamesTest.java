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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /** The most UTF-8 bytes one name in a folder may hold on ext4, XFS, Btrfs and APFS. */
    private static final int A_FILE_SYSTEMS_LONGEST_NAME_IN_BYTES = 255;

    /** What a copy is written under, after its name, before it is moved into place. */
    private static final String THE_PART = ".part";

    /** A character of one UTF-16 code unit that UTF-8 writes in three bytes. */
    private static final String A_THREE_BYTE_CHARACTER = "文";

    /** An occurrence id of nineteen digits, so its suffix, a tilde and the digits, is twenty units long. */
    private static final long A_NINETEEN_DIGIT_OCCURRENCE = 1_000_000_000_000_000_000L;

    /** That occurrence's suffix. */
    private static final String ITS_SUFFIX = "~" + A_NINETEEN_DIGIT_OCCURRENCE;

    /**
     * Names a file system holds as one file (ADR-280 §1.4, §1.6): the key a name is compared by is lower-cased, then
     * upper-cased, then decomposed and composed again, and two names NTFS upper-cases unit by unit to one name collide
     * though their keys differ. The lowest-numbered document keeps its name as it was written, every other takes a
     * tilde and its number before its extension, and the order the documents are handed over in changes nothing.
     */
    static Stream<Arguments> namesAFileSystemHoldsAsOne() {
        String aSmallTheta = "\u03b8";
        String aCapitalThetaSymbol = "\u03f4";
        String aSharpS = "\u00df";
        String aCapitalSharpS = "\u1e9e";
        String aDottedCapitalI = "\u0130";
        String anIAndACombiningDotAbove = "i\u0307";
        String anIotaWithDialytikaAndTonos = "\u0390";
        String aCapitalIotaWithDialytikaAndACombiningAcute = "\u03aa\u0301";
        String aCapitalAlphaWithProsgegrammeniAndAPerispomeni = "\u1fbc\u0342";
        String aSmallAlphaWithYpogegrammeniAndAPerispomeni = "\u1fb3\u0342";
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
                                "A capital that upper-casing leaves alone and its small letter",
                                List.of(
                                        member(65, "e/" + aSmallTheta + ".txt"),
                                        member(66, "f/" + aCapitalThetaSymbol + ".txt"),
                                        member(67, "g/" + aSharpS + ".txt"),
                                        member(68, "h/" + aCapitalSharpS + ".txt"))),
                        "a capital theta symbol and a capital sharp s are kept as they are by upper-casing, though their"
                                + " small letters, a theta and a sharp s, upper-case to a capital theta and to a double"
                                + " S; lower-cased first, each meets its small letter, as on a file system that folds"
                                + " case, and the higher-numbered of each pair takes its number",
                        Map.of(
                                65L, aSmallTheta + ".txt",
                                66L, aCapitalThetaSymbol + "~66.txt",
                                67L, aSharpS + ".txt",
                                68L, aCapitalSharpS + "~68.txt")),
                Arguments.of(
                        Named.of(
                                "Letters that are one once lower-cased, upper-cased and composed again",
                                List.of(
                                        member(61, "a/" + aDottedCapitalI + ".txt"),
                                        member(62, "b/" + anIAndACombiningDotAbove + ".txt"),
                                        member(63, "c/" + anIotaWithDialytikaAndTonos + ".txt"),
                                        member(64, "d/" + aCapitalIotaWithDialytikaAndACombiningAcute + ".txt"))),
                        "a dotted capital I and an i followed by a combining dot above are one letter once lower-cased,"
                                + " upper-cased and composed again, and so are a small iota with dialytika and tonos"
                                + " and a capital iota with dialytika followed by a combining acute: each pair"
                                + " collides, the lower-numbered keeping its name as written and the other taking its"
                                + " number",
                        Map.of(
                                61L, aDottedCapitalI + ".txt",
                                62L, anIAndACombiningDotAbove + "~62.txt",
                                63L, anIotaWithDialytikaAndTonos + ".txt",
                                64L, aCapitalIotaWithDialytikaAndACombiningAcute + "~64.txt")),
                Arguments.of(
                        Named.of(
                                "A dotted capital I and a plain i",
                                List.of(member(91, "a/i.txt"), member(92, "b/" + aDottedCapitalI + ".txt"))),
                        "a dotted capital I does not meet a plain i, which case folding keeps apart from it too",
                        Map.of(91L, "i.txt", 92L, aDottedCapitalI + ".txt")),
                Arguments.of(
                        Named.of(
                                "A capital alpha with an iota subscript and a small one, each followed by a"
                                        + " perispomeni",
                                List.of(
                                        member(71, "a/" + aCapitalAlphaWithProsgegrammeniAndAPerispomeni + ".pdf"),
                                        member(72, "b/" + aSmallAlphaWithYpogegrammeniAndAPerispomeni + ".pdf"))),
                        "NTFS upper-cases the small alpha with ypogegrammeni to the capital with prosgegrammeni, and"
                                + " APFS's case-insensitive form decomposes and folds both names to one; composed"
                                + " first, the iota subscript would upper-case to a capital iota before the perispomeni"
                                + " in one name and after it in the other, and both would keep their names. Decomposed"
                                + " first, the subscript stands after the perispomeni in both: they collide, document"
                                + " 71 keeping its name as written and document 72 taking its number before the"
                                + " extension",
                        Map.of(
                                71L, aCapitalAlphaWithProsgegrammeniAndAPerispomeni + ".pdf",
                                72L, aSmallAlphaWithYpogegrammeniAndAPerispomeni + "~72.pdf")),
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
                                82L, anAlphaWithAnIotaThenAPerispomeni + "~82.pdf")),
                Arguments.of(
                        Named.of(
                                "The same two names, the other way round",
                                List.of(
                                        member(81, "a/" + anAlphaWithAnIotaThenAPerispomeni + ".pdf"),
                                        member(82, "b/" + anAlphaWithYpogegrammeniThenAPerispomeni + ".pdf"))),
                        "with the two names the other way round, the lower occurrence still keeps its own",
                        Map.of(
                                81L, anAlphaWithAnIotaThenAPerispomeni + ".pdf",
                                82L, anAlphaWithYpogegrammeniThenAPerispomeni + "~82.pdf")));
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

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("A document named as another document's unfinished copy takes its number, though its own number is the lower")
    void aCopyYieldsToThePartOfAnotherCopy() {
        Map<Long, String> names = namesOf(
                List.of(
                        member(3, "a/notes.txt.part"),
                        member(8, "b/notes.txt"),
                        member(4, "c/minutes.docx"),
                        member(5, "d/MINUTES.DOCX.PART")));

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
                List.of(member(3, "a/plan.pdf"), member(5, "b/PLAN.PDF"), member(9, "c/plan~5.pdf")));

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("namesWindowsWouldRefuse")
    @Story("A document's name in the tree opens on every system")
    @DisplayName("A name Windows would refuse is made safe, before and after it is cut, and a name left empty becomes the document's number")
    void aNameIsMadeSafeOnEveryOperatingSystem(
            List<ListedSurvivor> members, String howEachIsMadeSafe, Map<Long, String> expected) {
        claim(howEachIsMadeSafe, () -> assertThat(namesOf(members)).containsExactlyInAnyOrderEntriesOf(expected));
    }

    @Test
    @Story("A document's name in the tree opens on every system")
    @DisplayName("A long name is cut to a hundred characters, its number and its extension included")
    void aNameIsCutToAHundredUnitsWithItsSuffixAndItsExtension() {
        Map<Long, String> names = namesOf(
                List.of(member(21, "a/" + "r".repeat(120) + ".pdf"), member(22, "b/" + "R".repeat(120) + ".PDF")));

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
                        member(42, "b/" + "c".repeat(94) + " .zzzz.txt")));

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
                        member(56, "f/readme")));

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
                List.of(member(31, "a/" + "x".repeat(120) + "1.txt"), member(32, "b/" + "x".repeat(120) + "2.txt")));

        claim(
                "both names are cut before they are compared, so the two that differed only in their last"
                        + " character before the extension meet, and the second takes its number",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                31L, "x".repeat(96) + ".txt",
                                32L, "x".repeat(93) + "~32.txt")));
    }

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("A numbered name whose unfinished copy would be written over another document's copy takes the number again")
    void aSuffixedNameYieldsWhereItsPartIsANameAlreadyGiven() {
        Map<Long, String> names = namesOf(
                List.of(member(1, "a/report.pdf"), member(2, "b/REPORT~5.PDF.part"), member(5, "c/REPORT.PDF")));

        claim(
                "document 5's REPORT.PDF meets document 1's report.pdf; with its number once, REPORT~5.PDF, its"
                        + " unfinished copy would be written under document 2's name, REPORT~5.PDF.part, so it takes"
                        + " its number a second time; documents 1 and 2 keep their names",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                1L, "report.pdf",
                                2L, "REPORT~5.PDF.part",
                                5L, "REPORT~5~5.PDF")));
    }

    @Test
    @Story("A document's name in the tree opens on every system")
    @DisplayName("A name of characters written in several bytes is cut so that it and its unfinished copy's ending fit what a file system allows")
    void aNameIsCutSoItAndItsPartFitInTwoHundredAndFiftyFiveBytes() {
        String eightyThreeCharacters = A_THREE_BYTE_CHARACTER.repeat(83);
        Map<Long, String> names = namesOf(
                List.of(member(6, "a/" + eightyThreeCharacters + ".pdf"), member(7, "b/" + eightyThreeCharacters + ".pdf")));

        claim(
                "a name of eighty-three characters of three bytes each and .pdf is short of " + A_HUNDRED_UNITS
                        + " characters but, with .part after it, over " + A_FILE_SYSTEMS_LONGEST_NAME_IN_BYTES
                        + " bytes, so it is cut a whole character at a time to eighty-two; and its collision, taking"
                        + " its number, is cut to eighty-one",
                () -> assertThat(names)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                6L, A_THREE_BYTE_CHARACTER.repeat(82) + ".pdf",
                                7L, A_THREE_BYTE_CHARACTER.repeat(81) + "~7.pdf")));
        claim(
                "each name with .part after it holds at most " + A_FILE_SYSTEMS_LONGEST_NAME_IN_BYTES + " bytes in"
                        + " UTF-8, the most one name may hold on the file systems most machines use",
                () -> assertThat(names.values())
                        .allSatisfy(name -> assertThat((name + THE_PART).getBytes(StandardCharsets.UTF_8).length)
                                .isLessThanOrEqualTo(A_FILE_SYSTEMS_LONGEST_NAME_IN_BYTES)));
    }

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("Six documents are enough for repeated numbers to leave none of a name and pass a hundred characters")
    void repeatedSuffixesCanLeaveNoStemAndPassAHundredUnits() {
        String theStem = "s".repeat(96);
        Map<Long, String> names = namesOf(
                List.of(
                        member(1, "a/" + theStem + ".pdf"),
                        member(2, "b/" + theStem.substring(0, 76) + ITS_SUFFIX + ".pdf"),
                        member(3, "c/" + theStem.substring(0, 56) + ITS_SUFFIX.repeat(2) + ".pdf"),
                        member(4, "d/" + theStem.substring(0, 36) + ITS_SUFFIX.repeat(3) + ".pdf"),
                        member(5, "e/" + theStem.substring(0, 16) + ITS_SUFFIX.repeat(4) + ".pdf"),
                        member(A_NINETEEN_DIGIT_OCCURRENCE, "f/" + theStem.toUpperCase(Locale.ROOT) + ".PDF")));
        String theLastName = names.get(A_NINETEEN_DIGIT_OCCURRENCE);

        claim(
                "the last document meets the first; each time it takes its twenty-character number again, its"
                        + " name is cut by twenty more to stay within " + A_HUNDRED_UNITS + " characters and meets"
                        + " the name another document keeps, until none of its own name is left and its fifth"
                        + " number stands with the four before it and its extension",
                () -> assertThat(theLastName).isEqualTo(ITS_SUFFIX.repeat(5) + ".PDF"));
        claim(
                "so that name passes " + A_HUNDRED_UNITS + " characters, and it is the only one: the five others"
                        + " keep their own names",
                () -> {
                    assertThat(theLastName.length()).isGreaterThan(A_HUNDRED_UNITS);
                    assertThat(names.values()).filteredOn(name -> name.length() > A_HUNDRED_UNITS).hasSize(1);
                    assertThat(names)
                            .containsAllEntriesOf(Map.of(
                                    1L, theStem + ".pdf",
                                    2L, theStem.substring(0, 76) + ITS_SUFFIX + ".pdf",
                                    3L, theStem.substring(0, 56) + ITS_SUFFIX.repeat(2) + ".pdf",
                                    4L, theStem.substring(0, 36) + ITS_SUFFIX.repeat(3) + ".pdf",
                                    5L, theStem.substring(0, 16) + ITS_SUFFIX.repeat(4) + ".pdf"));
                });
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
