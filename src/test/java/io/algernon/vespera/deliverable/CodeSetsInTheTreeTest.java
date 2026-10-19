package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.csvOf;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.manifestOf;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The code sets in the tree (ADR-296): every member of every set copied byte for byte to {@code code/<set>/<kept
 * path>}, each checked against the size and last-modified time the walk recorded and {@code changed} where either
 * differs, with the outcomes of ADR-274 §2.3 otherwise as for documents; one row a member in {@code code.csv}, and none
 * in {@code documents.csv}; a section of the index after the groups, one row a set; the operator's names applied, the
 * dormant ones counted and a refused file told; a folder in {@code code/} this drawing did not write counted as left
 * behind; and the code copies surveyed in numbers only.
 *
 * <p>Each test lays out {@code <base>/archive} as the corpus root and {@code <base>/work} as the working directory,
 * the tree drawn at the working directory's {@code deliverable/} folder, with one group of one document beside the
 * code. How a set's folder and a member's path are named is {@code CodeSetFolderNamesTest}'s and {@code
 * CodePathsTest}'s.
 */
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("573")
@Link(name = "ADR-296", url = Adr.EACH_CODE_SET_IS_COPIED_WHOLE_UNDER_CODE, type = "adr")
class CodeSetsInTheTreeTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The one group of the tree, written over from its one document. */
    private static final RecordedCluster THE_CLUSTER = cluster(THE_SEED, 0, "Fire doors", 1, 1);

    /** The group's document, its path in the archive and its bytes. */
    private static final String THE_DOCUMENT = "reports/doors.pdf";

    private static final String THE_DOCUMENTS_TEXT = "the document";

    private static final long THE_DOCUMENTS_OCCURRENCE = 42;

    /** The last-modified time every code file is given in the archive and the walk recorded for it. */
    private static final Instant THE_WALKS_TIME = Instant.parse("2026-01-02T03:04:05Z");

    /** A time one second after it, recorded for a file that was given the earlier one. */
    private static final Instant A_SECOND_LATER = THE_WALKS_TIME.plusSeconds(1);

    /** Free bytes reported to the writer where room is not what a test is about: four gibibytes. */
    private static final long ROOM_ENOUGH = 4L << 30;

    /** What the code listing's header is, column by column. */
    private static final String THE_CODE_LISTING_HEADER =
            "occurrence_id,path,code_set,role,generator,size_bytes,last_modified,copy,copy_outcome";

    @Test
    @Story("Each code set is copied whole in its own layout")
    @DisplayName("Every file of every code set is copied byte for byte under code/, at its path below its set's folder, and a set inside another set's folder is a folder of its own beside it")
    void eachSetIsCopiedWholeUnderCodeItsPathsKeptAndSetsSideBySide(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Map<ListedCodeSet, List<ListedCodeFile>> sets = new LinkedHashMap<>();
        sets.put(set("Work", "Work", 100), List.of(
                file(archive, 100, "Work/Makefile", "Work", "build", null, "all:\n"),
                file(archive, 101, "Work/src/a.c", "Work", "code", null, "int a;\n")));
        sets.put(set("Work/shop", "shop", 102), List.of(
                file(archive, 102, "Work/shop/pom.xml", "Work/shop", "build", null, "<project/>\n"),
                file(archive, 103, "Work/shop/src/main/java/S.java", "Work/shop", "code", null, "class S {}\n"),
                file(archive, 104, "Work/shop/docs/api/element-list", "Work/shop", "companion", "Javadoc", "shop\n"),
                file(archive, 105, "Work/shop/app.properties", "Work/shop", "configuration", null, "a=b\n")));

        Path tree = draw(base, archive, codeSets(sets), DeliverableProgress.NONE, () -> ROOM_ENOUGH);

        claim(
                "code/ holds each set in a folder of its own, the outer set as Work and the set inside it as shop beside"
                        + " it, each file of every role at its path below its set's folder, and nothing else",
                () -> assertThat(filesUnder(tree.resolve("code")))
                        .containsExactly(
                                "Work/Makefile",
                                "Work/src/a.c",
                                "shop/app.properties",
                                "shop/docs/api/element-list",
                                "shop/pom.xml",
                                "shop/src/main/java/S.java"));
        claim(
                "each copy holds exactly the bytes of its file in the archive",
                () -> {
                    for (List<ListedCodeFile> members : sets.values()) {
                        for (ListedCodeFile member : members) {
                            String drawn = member.setFolder().equals("Work") ? "Work" : "shop";
                            String below = member.path().value().substring(member.setFolder().length() + 1);
                            assertThat(tree.resolve("code").resolve(drawn).resolve(below))
                                    .hasBinaryContent(Files.readAllBytes(archive.resolve(member.path().value())));
                        }
                    }
                });
        claim(
                "and the inner set is not copied into the outer set's folder",
                () -> assertThat(tree.resolve("code/Work/shop")).doesNotExist());
    }

    @Test
    @Story("Each code file is checked against what was recorded when the archive was walked")
    @DisplayName("A code file whose size or modification time differs from what was recorded when the archive was walked is changed and not copied, and one that matches is copied")
    void aFileWhoseSizeOrTimeDiffersFromTheWalksIsChangedAndNotCopied(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeFile matching = file(archive, 110, "lib/ok.c", "lib", "code", null, "int ok;\n");
        ListedCodeFile touched = file(archive, 111, "lib/touched.c", "lib", "code", null, "int touched;\n");
        ListedCodeFile grown = file(archive, 112, "lib/grown.c", "lib", "code", null, "int grown;\n");
        ListedCodeFile touchedAsRecorded = new ListedCodeFile(
                touched.occurrence(), touched.path(), touched.setFolder(), touched.role(), touched.generator(),
                touched.recordedSizeBytes(), A_SECOND_LATER);
        ListedCodeFile grownAsRecorded = new ListedCodeFile(
                grown.occurrence(), grown.path(), grown.setFolder(), grown.role(), grown.generator(),
                grown.recordedSizeBytes() - 1, grown.recordedLastModified());
        Map<ListedCodeSet, List<ListedCodeFile>> sets = Map.of(
                set("lib", "lib", 110), List.of(matching, touchedAsRecorded, grownAsRecorded));

        Path tree = draw(base, archive, codeSets(sets), DeliverableProgress.NONE, () -> ROOM_ENOUGH);

        claim(
                "the file whose size and time are those recorded is copied with its bytes",
                () -> assertThat(tree.resolve("code/lib/ok.c")).hasBinaryContent(bytes("int ok;\n")));
        claim(
                "the file whose time is a second earlier than the one recorded, and the one a byte longer than was"
                        + " recorded, stand nowhere in the tree, and neither leaves a part behind",
                () -> assertThat(filesUnder(tree.resolve("code"))).containsExactly("lib/ok.c"));
        claim(
                "and the code listing says each was changed, with no copy, and the matching one copied",
                () -> assertThat(outcomesOf(tree))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                "lib/ok.c", "code/lib/ok.c copied",
                                "lib/touched.c", " changed",
                                "lib/grown.c", " changed")));
    }

    @Test
    @Story("Each code file is checked against what was recorded when the archive was walked")
    @DisplayName("A code file's copy that already stands is kept on a later drawing without reading the archive")
    void aCodeCopyThatStandsIsKeptWithoutReadingTheArchive(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeFile member = file(archive, 120, "tool/run.py", "tool", "code", null, "print(1)\n");
        ListedCodeSets code = codeSets(Map.of(set("tool", "tool", 120), List.of(member)));
        Path tree = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        Files.delete(archive.resolve("tool/run.py"));
        long[] alreadyCopied = new long[1];
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void codeFileAlreadyCopied() {
                alreadyCopied[0]++;
            }
        };

        draw(base, archive, code, hearing, () -> ROOM_ENOUGH);

        claim(
                "with its original deleted, the copy still holds its bytes",
                () -> assertThat(tree.resolve("code/tool/run.py")).hasBinaryContent(bytes("print(1)\n")));
        claim(
                "the listing says it was copied",
                () -> assertThat(outcomesOf(tree)).containsExactlyInAnyOrderEntriesOf(Map.of("tool/run.py", "code/tool/run.py copied")));
        claim(
                "and the drawing is told once that it already stood",
                () -> assertThat(alreadyCopied[0]).isEqualTo(1L));
    }

    @Test
    @Story("Each code file is checked against what was recorded when the archive was walked")
    @DisplayName("A code file no longer in the archive is gone, and a drive with no free space read takes no copy of code")
    void aCodeFileGoneIsGoneAndADriveWithNoFreeSpaceReadTakesNoCopyOfCode(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeFile standing = file(archive, 130, "app/main.go", "app", "code", null, "package main\n");
        ListedCodeFile missing = new ListedCodeFile(
                new OccurrenceId(131), new OccurrencePath("app/gone.go"), "app", "code", null, 5, THE_WALKS_TIME);
        ListedCodeSets code = codeSets(Map.of(set("app", "app", 130), List.of(standing, missing)));

        Path tree = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);

        claim(
                "the file the archive no longer holds is gone, with no copy, and the other is copied",
                () -> assertThat(outcomesOf(tree))
                        .containsExactlyInAnyOrderEntriesOf(Map.of("app/main.go", "code/app/main.go copied", "app/gone.go", " gone")));

        Path otherBase = base.resolve("no-room");
        Path otherArchive = otherBase.resolve("archive");
        ListedCodeFile alone = file(otherArchive, 130, "app/main.go", "app", "code", null, "package main\n");
        Path roomless = draw(
                otherBase, otherArchive, codeSets(Map.of(set("app", "app", 130), List.of(alone))),
                DeliverableProgress.NONE, () -> 0);

        claim(
                "and with 0 bytes free, the file is not copied for want of room, and nothing stands under code/",
                () -> {
                    assertThat(outcomesOf(roomless)).containsExactlyInAnyOrderEntriesOf(Map.of("app/main.go", " no-room"));
                    assertThat(filesUnder(roomless.resolve("code"))).isEmpty();
                });
    }

    @Test
    @Story("The code sets are listed")
    @DisplayName("code.csv has one row for each file of each code set, in the sets' order and then by number, quoted as documents.csv is, and documents.csv lists no file of code")
    void theCodeListingHasARowForEachMemberAndDocumentsCsvNone(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Map<ListedCodeSet, List<ListedCodeFile>> sets = new LinkedHashMap<>();
        sets.put(set("", "archive", 199), List.of(file(archive, 199, "run.sh", "", "code", null, "echo\n")));
        sets.put(set("shop/core", "core", 200), List.of(
                file(archive, 200, "shop/core/pom.xml", "shop/core", "build", null, "<project/>\n"),
                file(archive, 201, "shop/core/docs/a, b.html", "shop/core", "code", "Javadoc", "<html></html>\n")));

        Path tree = draw(base, archive, codeSets(sets), DeliverableProgress.NONE, () -> ROOM_ENOUGH);

        claim(
                "the listing is the header and one row for each file, the archive's top first and then shop/core, each"
                        + " with its set's folder, role, generator, the walk's size and time, its copy's path and its"
                        + " outcome, a path holding a comma quoted",
                () -> assertThat(Files.readAllLines(tree.resolve("code.csv"), StandardCharsets.UTF_8))
                        .containsExactly(
                                THE_CODE_LISTING_HEADER,
                                "199,run.sh,,code,,5," + THE_WALKS_TIME + ",code/archive/run.sh,copied",
                                "200,shop/core/pom.xml,shop/core,build,,11," + THE_WALKS_TIME
                                        + ",code/core/pom.xml,copied",
                                "201,\"shop/core/docs/a, b.html\",shop/core,code,Javadoc,14," + THE_WALKS_TIME
                                        + ",\"code/core/docs/a, b.html\",copied"));
        claim(
                "and documents.csv holds the header and the group's one document, and no file of code",
                () -> assertThat(manifestOf(tree))
                        .extracting(row -> row.get(1))
                        .containsExactly("path", THE_DOCUMENT));
    }

    @Test
    @Story("The code sets are listed")
    @DisplayName("The index lists each code set after the groups, linking its folder, with how many of its files were copied and how many not, and never calls a set a group")
    void theIndexListsEachSetAfterTheGroupsAndNeverCallsItAGroup(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(base.resolve("work"));
        Files.writeString(base.resolve("work").resolve(CodeSetNames.FILE_NAME),
                "- folder: shop/core\n  name: Shop core\n", StandardCharsets.UTF_8);
        ListedCodeFile touched = file(archive, 201, "shop/core/b.c", "shop/core", "code", null, "int b;\n");
        Map<ListedCodeSet, List<ListedCodeFile>> sets = new LinkedHashMap<>();
        sets.put(set("", "archive", 199), List.of(file(archive, 199, "run.sh", "", "code", null, "echo\n")));
        sets.put(set("shop/core", "core", 200), List.of(
                file(archive, 200, "shop/core/a.c", "shop/core", "code", null, "int a;\n"),
                new ListedCodeFile(touched.occurrence(), touched.path(), touched.setFolder(), touched.role(),
                        touched.generator(), touched.recordedSizeBytes(), A_SECOND_LATER)));

        Path tree = draw(base, archive, codeSets(sets), DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        String index = Files.readString(tree.resolve("index.html"), StandardCharsets.UTF_8);
        String theSection = "<h2>Code</h2>\n"
                + "<p>Each code set is copied whole under code/, keeping its folders, and each of its files is checked"
                + " against the size and modification time recorded when the archive was walked.</p>\n"
                + "<table>\n"
                + "<tr><th>Code set</th><th>Folder in the archive</th><th>Files copied</th><th>Files not copied</th></tr>\n"
                + "<tr><td><a href=\"code/archive/\">archive</a></td><td>the archive&#39;s top</td><td>1</td><td>0</td></tr>\n"
                + "<tr><td><a href=\"code/Shop%20core/\">Shop core</a></td><td>shop/core</td><td>1</td><td>1</td></tr>\n"
                + "</table>\n";

        claim(
                "the index holds a section headed Code, its one sentence, and a table of one row for each set in the"
                        + " sets' order: the archive's top by its default name and shop/core by the name typed for it,"
                        + " each linking its folder under code/ percent-encoded, with one file copied and none not, and"
                        + " one copied and one not",
                () -> assertThat(index).contains(theSection));
        claim(
                "the section comes after the last group's table",
                () -> assertThat(index.indexOf(theSection)).isGreaterThan(index.indexOf("Fire doors")));
        claim(
                "and nothing in it says group",
                () -> assertThat(theSection.toLowerCase(java.util.Locale.ROOT)).doesNotContain("group"));
    }

    @Test
    @Story("The code sets are listed")
    @DisplayName("A tree whose run has no code set has no code folder, no code.csv and no section of code on its index")
    void aTreeWithNoCodeSetHasNoCodeFolderNoCodeListingAndNoCodeSection(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");

        Path tree = draw(base, archive, ListedCodeSets.NONE, DeliverableProgress.NONE, () -> ROOM_ENOUGH);

        claim(
                "no code/ and no code.csv stand in the tree",
                () -> {
                    assertThat(tree.resolve("code")).doesNotExist();
                    assertThat(tree.resolve("code.csv")).doesNotExist();
                });
        claim(
                "and the index has no section headed Code",
                () -> assertThat(Files.readString(tree.resolve("index.html"), StandardCharsets.UTF_8))
                        .doesNotContain("<h2>Code</h2>"));
    }

    @Test
    @Story("What an earlier drawing left is counted")
    @DisplayName("A folder in code/ that this drawing did not write is counted as left behind, and code/ itself is not")
    void aFolderInCodeThisDrawingDidNotWriteIsCountedAsLeftBehind(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = codeSets(Map.of(
                set("tool", "tool", 140), List.of(file(archive, 140, "tool/run.py", "tool", "code", null, "x\n"))));
        Path tree = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        Files.createDirectories(tree.resolve("code/an-old-name"));
        Files.writeString(tree.resolve("code/stray.txt"), "a file, which is not counted");
        long[] others = {-1};

        draw(base, archive, code, leftBehindInto(others), () -> ROOM_ENOUGH);

        claim(
                "the one folder in code/ this drawing did not write is counted, and code/ and the file beside it are not",
                () -> assertThat(others[0]).isEqualTo(1L));

        Path noSets = base.resolve("no-sets");
        ListedCodeSets aSetOnce = codeSets(Map.of(set("tool", "tool", 140),
                List.of(file(noSets.resolve("archive"), 140, "tool/run.py", "tool", "code", null, "x\n"))));
        Path firstDrawing = draw(noSets, noSets.resolve("archive"), aSetOnce, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        long[] whenNoSet = {-1};
        draw(noSets, noSets.resolve("archive"), ListedCodeSets.NONE, leftBehindInto(whenNoSet), () -> ROOM_ENOUGH);

        claim(
                "and a code/ an earlier drawing wrote, in a tree whose run now has no set, is itself a folder left"
                        + " behind",
                () -> {
                    assertThat(firstDrawing.resolve("code")).isDirectory();
                    assertThat(whenNoSet[0]).isEqualTo(1L);
                });
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("The names typed in the file of renames are given to their sets' folders, and the entries naming no set of this run are counted")
    void theTypedNamesAreAppliedAndTheDormantOnesToldAsACount(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(base.resolve("work"));
        Files.writeString(base.resolve("work").resolve(CodeSetNames.FILE_NAME),
                "- folder: shop/core\n  name: Shop core\n- folder: gone/x\n  name: X\n", StandardCharsets.UTF_8);
        ListedCodeSets code = codeSets(Map.of(set("shop/core", "core", 150),
                List.of(file(archive, 150, "shop/core/a.c", "shop/core", "code", null, "int a;\n"))));
        List<Long> dormant = new ArrayList<>();
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void dormantCodeSetNames(long entries) {
                dormant.add(entries);
            }
        };

        Path tree = draw(base, archive, code, hearing, () -> ROOM_ENOUGH);

        claim(
                "the set at shop/core is copied under the name typed for it",
                () -> assertThat(filesUnder(tree.resolve("code"))).containsExactly("Shop core/a.c"));
        claim(
                "and the drawing is told once that one entry names no set of this run",
                () -> assertThat(dormant).containsExactly(1L));
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("A file of renames that is refused whole leaves every set its default name, fails nothing, and is told once")
    void aNamesFileThatIsRefusedLeavesTheDefaultNamesAndIsToldOnce(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(base.resolve("work"));
        Files.writeString(base.resolve("work").resolve(CodeSetNames.FILE_NAME),
                "folder: shop/core\nname: Shop core\n", StandardCharsets.UTF_8);
        ListedCodeSets code = codeSets(Map.of(set("shop/core", "core", 160),
                List.of(file(archive, 160, "shop/core/a.c", "shop/core", "code", null, "int a;\n"))));
        long[] refused = new long[1];
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void codeSetNamesRefused() {
                refused[0]++;
            }
        };

        Path tree = draw(base, archive, code, hearing, () -> ROOM_ENOUGH);

        claim(
                "the set is copied under its default name, the file being a mapping and not a list",
                () -> assertThat(filesUnder(tree.resolve("code"))).containsExactly("core/a.c"));
        claim(
                "and the drawing is told once that the file was refused",
                () -> assertThat(refused[0]).isEqualTo(1L));
    }

    @Test
    @Story("The code copies are told in numbers only")
    @DisplayName("Before the first copy of code the drawing is told the sets, the files, the bytes recorded of them when the archive was walked and the free bytes, once, and then one outcome for each file")
    void theCodeCopiesAreSurveyedBeforeTheFirstInNumbersOnly(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Map<ListedCodeSet, List<ListedCodeFile>> sets = new LinkedHashMap<>();
        sets.put(set("a", "a", 170), List.of(
                file(archive, 170, "a/x.c", "a", "code", null, "abc"),
                file(archive, 171, "a/y.c", "a", "code", null, "abcd")));
        sets.put(set("b", "b", 172), List.of(file(archive, 172, "b/z.c", "b", "code", null, "abcde")));
        List<String> told = new ArrayList<>();
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void toCopyCodeFiles(long sets, long files, long bytes, long usableBytes) {
                told.add("survey " + sets + " " + files + " " + bytes + " " + usableBytes);
            }

            @Override
            public void codeFileCopied(long bytes) {
                told.add("copied " + bytes);
            }

            @Override
            public void codeFileAlreadyCopied() {
                told.add("already copied");
            }

            @Override
            public void codeFileChanged() {
                told.add("changed");
            }

            @Override
            public void codeFileGone() {
                told.add("gone");
            }

            @Override
            public void codeFileWithNoRoom() {
                told.add("no room");
            }

            @Override
            public void codeFileNotCopied() {
                told.add("not copied");
            }
        };

        draw(base, archive, codeSets(sets), hearing, () -> ROOM_ENOUGH);

        claim(
                "the survey comes first, once, with the 2 sets, the 3 files, the 3 + 4 + 5 = 12 bytes recorded of them"
                        + " and the four gibibytes free, then one outcome for each file in the sets' order, each copied"
                        + " with its bytes",
                () -> assertThat(told)
                        .containsExactly("survey 2 3 12 " + ROOM_ENOUGH, "copied 3", "copied 4", "copied 5"));
    }

    /** The tree drawn over {@code archive} from {@code code}, with the one group, at {@code <base>/work}. */
    private static Path draw(
            Path base, Path archive, ListedCodeSets code, DeliverableProgress progress, LongSupplier usableSpace)
            throws IOException {
        Path document = archive.resolve(THE_DOCUMENT);
        Files.createDirectories(document.getParent());
        Files.writeString(document, THE_DOCUMENTS_TEXT, StandardCharsets.UTF_8);
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(
                        List.of(THE_CLUSTER),
                        Map.of(slotOf(THE_CLUSTER),
                                writing("Doors", "About doors [1].", THE_DOCUMENTS_OCCURRENCE)),
                        List.of(survivor(
                                THE_DOCUMENTS_OCCURRENCE, THE_DOCUMENT, sha256Of(bytes(THE_DOCUMENTS_TEXT)), THE_SEED,
                                SEED_PATH, 0, 0.9)),
                        Map.of()),
                code,
                SurvivorPictures.none(),
                progress,
                ReplacingMove.ATOMIC,
                usableSpace);
    }

    /** The sets of {@code members}, in the map's order, each answering its own members. */
    private static ListedCodeSets codeSets(Map<ListedCodeSet, List<ListedCodeFile>> members) {
        List<ListedCodeSet> sets = List.copyOf(members.keySet());
        return ListedCodeSets.reading(() -> sets, members::get);
    }

    private static ListedCodeSet set(String folder, String defaultName, long lowestMember) {
        return new ListedCodeSet(folder, defaultName, new OccurrenceId(lowestMember));
    }

    /**
     * A member written into {@code archive} at {@code path} holding {@code text}, its time set to the walk's, and as
     * the walk recorded it: its size in bytes and that time.
     */
    private static ListedCodeFile file(
            Path archive, long occurrence, String path, String setFolder, String role, String generator, String text)
            throws IOException {
        Path original = archive.resolve(path);
        Files.createDirectories(original.getParent());
        Files.write(original, bytes(text));
        Files.setLastModifiedTime(original, FileTime.from(THE_WALKS_TIME));
        return new ListedCodeFile(
                new OccurrenceId(occurrence), new OccurrencePath(path), setFolder, role, generator,
                bytes(text).length, THE_WALKS_TIME);
    }

    /** Each row of {@code code.csv} by its path: its copy and its outcome, one space between. */
    private static Map<String, String> outcomesOf(Path tree) throws IOException {
        List<List<String>> rows = csvOf(tree.resolve("code.csv"));
        int path = rows.getFirst().indexOf("path");
        int copy = rows.getFirst().indexOf("copy");
        int outcome = rows.getFirst().indexOf("copy_outcome");
        Map<String, String> byPath = new LinkedHashMap<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            byPath.put(row.get(path), row.get(copy) + " " + row.get(outcome));
        }
        return byPath;
    }

    /** A progress that keeps, in {@code others}, the count of other folders the writer reports left behind. */
    private static DeliverableProgress leftBehindInto(long[] others) {
        return new DeliverableProgress() {
            @Override
            public void leftBehind(boolean documentsFolder, long otherFolders) {
                others[0] = otherFolders;
            }
        };
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
