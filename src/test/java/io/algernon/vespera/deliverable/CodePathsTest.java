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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where each member of a code set is copied below its set's folder in {@code code/} (ADR-296 §3.2): its path relative
 * to the set's folder in the archive, kept element by element, each element made safe and cut as a copy's name is
 * (ADR-280 §1.2, §3); folders that collide within one drawn folder are one, spelt as the member with the lowest
 * occurrence id beneath it spells it; file names that collide are suffixed as copies in a cluster folder are (ADR-280
 * §1.4 to §1.6); and a file that collides with a folder beside it takes the suffix.
 *
 * <p>Asked of {@code CodePaths} directly, from the members alone, reading no file (ADR-296 §3.4), so a name no file
 * system here would create can still be handed over.
 */
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("573")
@Link(name = "ADR-296", url = Adr.EACH_CODE_SET_IS_COPIED_WHOLE_UNDER_CODE, type = "adr")
class CodePathsTest {

    /** A set's folder in the archive, as {@code code_set} holds it. */
    private static final String THE_SET = "shop/core";

    /** The root's set, as {@code code_set} holds it. */
    private static final String THE_ROOTS_SET = "";

    /** What the walk recorded of each member; no file is read here, so the values are placeholders. */
    private static final long A_SIZE = 10;

    private static final Instant A_TIME = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("A file's path below its set's folder is kept folder by folder, and a file of the set at the archive's top keeps its whole path")
    void aMembersPathBelowItsSetsFolderIsKeptElementByElement() {
        claim(
                "a file ten folders below its set's folder keeps every one of them, and the set's build file lies at"
                        + " the folder's top",
                () -> assertThat(pathsOf(
                                member(10, THE_SET, THE_SET + "/src/main/java/io/algernon/shop/core/model/order/Line.java"),
                                member(11, THE_SET, THE_SET + "/pom.xml")))
                        .containsExactlyEntriesOf(new TreeMap<>(Map.of(
                                10L, "src/main/java/io/algernon/shop/core/model/order/Line.java",
                                11L, "pom.xml"))));
        claim(
                "and a file of the set at the archive's top keeps its whole path",
                () -> assertThat(pathsOf(member(12, THE_ROOTS_SET, "run.sh"), member(13, THE_ROOTS_SET, "build.gradle")))
                        .containsExactlyEntriesOf(new TreeMap<>(Map.of(12L, "run.sh", 13L, "build.gradle"))));
    }

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("Each folder and file of a kept path is made safe on every system, and a folder left empty is the number of the first file beneath it")
    void eachElementIsMadeSafe() {
        Map<Long, String> paths = pathsOf(
                member(60, THE_SET, THE_SET + "/a:b/c?.txt"),
                member(61, THE_SET, THE_SET + "/con/x.txt"),
                member(62, THE_SET, THE_SET + "/v1./y.txt"),
                member(70, THE_SET, THE_SET + "/.../z.txt"),
                member(71, THE_SET, THE_SET + "/.../w.txt"));

        claim(
                "a colon in a folder and a question mark in a file each become an underscore",
                () -> assertThat(paths).containsEntry(60L, "a_b/c_.txt"));
        claim(
                "a folder named as a Windows device takes a leading underscore",
                () -> assertThat(paths).containsEntry(61L, "_con/x.txt"));
        claim(
                "a folder's trailing dot goes",
                () -> assertThat(paths).containsEntry(62L, "v1/y.txt"));
        claim(
                "and a folder of three dots, left empty, is named for the lowest-numbered file beneath it, 70, for"
                        + " both its files",
                () -> {
                    assertThat(paths).containsEntry(70L, "70/z.txt");
                    assertThat(paths).containsEntry(71L, "70/w.txt");
                });
    }

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("Two folders whose names differ only in case are one folder, spelt as the lowest-numbered file beneath it spells it")
    void foldersThatCollideAreOneFolderSpeltAsTheLowestMemberBeneathItSpellsIt() {
        List<ListedCodeFile> members = List.of(
                member(21, THE_SET, THE_SET + "/Src/B.java"),
                member(20, THE_SET, THE_SET + "/src/A.java"),
                member(22, THE_SET, THE_SET + "/SRC/Main/C.java"),
                member(23, THE_SET, THE_SET + "/src/main/D.java"));
        Map<Long, String> expected = new TreeMap<>(Map.of(
                20L, "src/A.java",
                21L, "src/B.java",
                22L, "src/Main/C.java",
                23L, "src/Main/D.java"));

        claim(
                "src, Src and SRC are one folder spelt src, as file 20 beneath it spells it, and Main and main below"
                        + " it one spelt Main, as file 22 spells it",
                () -> assertThat(pathsOf(members)).containsExactlyEntriesOf(expected));
        List<ListedCodeFile> reversed = new ArrayList<>(members);
        Collections.reverse(reversed);
        claim(
                "and the same paths are given whatever order the files are handed over in",
                () -> assertThat(pathsOf(reversed)).containsExactlyEntriesOf(expected));
    }

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("Folders of one folder that are each left with no name once made safe are one folder, named by the lowest-numbered file beneath any of them")
    void foldersLeftEmptyOnceMadeSafeAreOneFolderNamedByTheLowestMemberBeneathThem() {
        List<ListedCodeFile> members = List.of(
                member(81, THE_SET, THE_SET + "/.../a.txt"),
                member(80, THE_SET, THE_SET + "/..../b.txt"),
                member(82, THE_SET, THE_SET + "/. . ./c.txt"));
        Map<Long, String> expected = new TreeMap<>(Map.of(
                80L, "80/b.txt",
                81L, "80/a.txt",
                82L, "80/c.txt"));

        claim(
                "three dots, four dots, and three dots with spaces between them, each left with no name, are one folder named 80, the"
                        + " lowest-numbered file beneath any of them, though they were spelt differently",
                () -> assertThat(pathsOf(members)).containsExactlyEntriesOf(expected));
        List<ListedCodeFile> reversed = new ArrayList<>(members);
        Collections.reverse(reversed);
        claim(
                "and the same paths are given whatever order the files are handed over in",
                () -> assertThat(pathsOf(reversed)).containsExactlyEntriesOf(expected));
    }

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("Two files of one folder whose names differ only in case keep the lower-numbered one's name, and a file named as another's unfinished copy yields to it")
    void fileNamesThatCollideInOneFolderAreSuffixedAsCopiesAre() {
        claim(
                "Readme.txt of 30 keeps its name and README.TXT of 31 takes its number before the extension",
                () -> assertThat(pathsOf(
                                member(30, THE_SET, THE_SET + "/doc/Readme.txt"),
                                member(31, THE_SET, THE_SET + "/doc/README.TXT")))
                        .containsExactlyEntriesOf(new TreeMap<>(Map.of(30L, "doc/Readme.txt", 31L, "doc/README~31.TXT"))));
        claim(
                "and notes.txt.part of 50 yields to notes.txt of 51, whose unfinished copy it is named as, though its"
                        + " number is the lower",
                () -> assertThat(pathsOf(
                                member(50, THE_SET, THE_SET + "/notes.txt.part"),
                                member(51, THE_SET, THE_SET + "/notes.txt")))
                        .containsExactlyEntriesOf(new TreeMap<>(Map.of(50L, "notes.txt~50.part", 51L, "notes.txt"))));
    }

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("A file whose name meets a folder's beside it takes its number, and the folder keeps its name")
    void aFileThatCollidesWithAFolderBesideItTakesTheSuffix() {
        claim(
                "a file lib of 40 beside a folder LIB holding x.c of 41 is lib~40, and the folder stays LIB, though the"
                        + " file's number is the lower",
                () -> assertThat(pathsOf(
                                member(40, THE_SET, THE_SET + "/lib"),
                                member(41, THE_SET, THE_SET + "/LIB/x.c")))
                        .containsExactlyEntriesOf(new TreeMap<>(Map.of(40L, "lib~40", 41L, "LIB/x.c"))));
    }

    private static Map<Long, String> pathsOf(ListedCodeFile... members) {
        return pathsOf(List.of(members));
    }

    private static Map<Long, String> pathsOf(List<ListedCodeFile> members) {
        Map<Long, String> paths = new TreeMap<>();
        CodePaths.of(members).forEach((occurrence, path) -> paths.put(occurrence.value(), path));
        return paths;
    }

    /** A member of the set at {@code setFolder}, at {@code path} of the corpus root, as code. */
    private static ListedCodeFile member(long occurrence, String setFolder, String path) {
        return new ListedCodeFile(
                new OccurrenceId(occurrence), new OccurrencePath(path), setFolder, "code", null, A_SIZE, A_TIME);
    }
}
