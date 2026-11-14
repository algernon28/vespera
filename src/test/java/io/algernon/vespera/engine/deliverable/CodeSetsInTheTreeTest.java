package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.ListedTree.cluster;
import static io.algernon.vespera.engine.deliverable.ListedTree.csvOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.engine.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.engine.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.survivor;
import static io.algernon.vespera.engine.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestLinks;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The code sets in the tree (ADR-296): every member of every set copied byte for byte to {@code code/<set>/<kept
 * path>}, each checked against the size and last-modified time the walk recorded and {@code changed} where either
 * differs, with the outcomes of ADR-274 §2.3 otherwise as for documents; one row a member in {@code code.csv}, and none
 * in {@code documents.csv}; a section of the index after the groups, one row a set; each set named by its folder, a
 * names file in the working directory read by nothing, and a set whose folder in {@code code/} changed since the
 * earlier listing told once that listing is replaced, a change of case alone being none (ADR-377 §9); a folder in
 * {@code code/} this drawing did not write counted as left behind, and one whose name differs in case alone from a
 * set's this drawing wrote counted only where the file system holds the two apart (ADR-373); and the code copies
 * surveyed in numbers only.
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

    /** A code set's folder and default name, and the name the operator renames it to, differing from it in case alone. */
    private static final String THE_SET = "Core";

    private static final String THE_RENAMED_SET = "core";

    /** A folder in code/ an earlier drawing's listing gives the set {@code core}, other than its folder's name. */
    private static final String AN_EARLIER_FOLDER = "billing-core";

    /** The file of code-set names the control panel once wrote beside the database, which nothing reads now. */
    private static final String A_NAMES_FILE = "code-set-names.yaml";

    /** The name that file gives a set. */
    private static final String A_NAME_TYPED = "Payments";

    /** That set's one member, its occurrence and its text. */
    private static final long THE_SETS_MEMBER = 170;

    private static final String THE_SETS_TEXT = "int a;\n";

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
    @Story("A code set is named by its folder")
    @DisplayName("A file of code-set names left in the working directory renames nothing: every set is drawn under its folder's name, and nothing is told of the file")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void aNamesFileLeftInTheWorkingDirectoryRenamesNothing(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(base.resolve("work"));
        Files.writeString(base.resolve("work").resolve(A_NAMES_FILE),
                "- folder: apps/billing\n  name: " + A_NAME_TYPED + "\n", StandardCharsets.UTF_8);
        Map<ListedCodeSet, List<ListedCodeFile>> sets = new LinkedHashMap<>();
        sets.put(set("apps/billing", "billing", 240),
                List.of(file(archive, 240, "apps/billing/a.c", "apps/billing", "code", null, "int a;\n")));
        sets.put(set("libs/kernel", "kernel", 250),
                List.of(file(archive, 250, "libs/kernel/k.c", "libs/kernel", "code", null, "int k;\n")));
        List<String> heard = new ArrayList<>();

        Path tree = draw(base, archive, codeSets(sets), everyCallOf(heard), () -> ROOM_ENOUGH);
        String index = Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME), StandardCharsets.UTF_8);

        claim(
                "both sets are copied under their folders' names, billing and kernel, and none under the name the file"
                        + " gives",
                () -> assertThat(filesUnder(tree.resolve("code"))).containsExactly("billing/a.c", "kernel/k.c"));
        claim(
                "the index links each set by its folder's name and never shows the name the file gives",
                () -> assertThat(index).contains("href=\"code/billing/\"", "href=\"code/kernel/\"")
                        .doesNotContain(A_NAME_TYPED));
        claim(
                "code.csv names each member's copy under its set's folder's name",
                () -> assertThat(outcomesOf(tree)).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "apps/billing/a.c", "code/billing/a.c copied",
                        "libs/kernel/k.c", "code/kernel/k.c copied")));
        claim(
                "and the drawing tells nothing of the file: no warning that it was refused and no count of names that"
                        + " name no set",
                () -> assertThat(heard).isNotEmpty()
                        .noneMatch(call -> call.toLowerCase(Locale.ROOT).contains("codesetnames")));
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A code set whose folder in code/ changed since the tree was last drawn is told once, after the new code listing is in place, and a set whose folder stayed is not")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void aSetWhoseFolderInCodeChangedIsToldOnceAfterTheListingIsInPlace(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = coreAndKernel(archive);
        Path tree = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        respell(tree.resolve("code.csv"), "code/core/", "code/" + AN_EARLIER_FOLDER + "/");
        List<String> told = new ArrayList<>();

        draw(base, archive, code, tellingChanges(told, tree.resolve("code.csv")), () -> ROOM_ENOUGH);

        claim(
                "the drawing is told once, of the set core: its folder was " + AN_EARLIER_FOLDER + " and is core, and"
                        + " code.csv already names code/core/ when it is told; kernel, whose folder stayed, is not told",
                () -> assertThat(told).containsExactly(AN_EARLIER_FOLDER + " to core, the listing naming code/core/"));

        told.clear();
        draw(base, archive, code, tellingChanges(told, tree.resolve("code.csv")), () -> ROOM_ENOUGH);
        claim(
                "and the tree drawn a third time tells nothing, the listing the second drawing left naming core",
                () -> assertThat(told).isEmpty());
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A drawing that fails before its code listing is put in place tells no change of folder, and the next drawing tells it once")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void aDrawingThatFailsBeforeTheListingMovesTellsNothing(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = coreAndKernel(archive);
        Path tree = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        respell(tree.resolve("code.csv"), "code/core/", "code/" + AN_EARLIER_FOLDER + "/");
        List<String> told = new ArrayList<>();
        ReplacingMove refusingTheListing = new ReplacingMove(
                (from, to) -> {
                    if (to.getFileName().toString().equals("code.csv")) {
                        throw new IOException("the code listing's place refuses it");
                    }
                    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        Throwable failed = catchThrowable(() -> draw(base, archive, code,
                tellingChanges(told, tree.resolve("code.csv")), refusingTheListing));

        claim(
                "the precondition: the drawing failed, its code listing refused its place",
                () -> assertThat(failed).isNotNull());
        claim(
                "and it told no change of folder",
                () -> assertThat(told).isEmpty());

        draw(base, archive, code, tellingChanges(told, tree.resolve("code.csv")), () -> ROOM_ENOUGH);
        claim(
                "the next drawing, its moves working, reads the same earlier listing and tells the change once",
                () -> assertThat(told).containsExactly(AN_EARLIER_FOLDER + " to core, the listing naming code/core/"));
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A code set whose folder in code/ differs from the earlier one in case alone is no change and is told nothing")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void aChangeOfCaseAloneIsNoChange(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = codeSets(Map.of(set("core", "core", THE_SETS_MEMBER), List.of(
                file(archive, THE_SETS_MEMBER, "core/a.c", "core", "code", null, THE_SETS_TEXT))));
        Path tree = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        Path spelt = tree.resolve("code").resolve(THE_SET);
        Path between = Files.move(tree.resolve("code").resolve("core"), tree.resolve("code").resolve("moving"));
        Files.move(between, spelt);
        respell(tree.resolve("code.csv"), "code/core/", "code/" + THE_SET + "/");
        boolean folds = foldsCase(base);
        List<String> told = new ArrayList<>();

        draw(base, archive, code, tellingChanges(told, tree.resolve("code.csv")), () -> ROOM_ENOUGH);

        claim(
                "the drawing tells nothing: the earlier listing put the set core in code/" + THE_SET + "/, which"
                        + " differs from core in case alone",
                () -> assertThat(told).isEmpty());
        if (folds) {
            claim(
                    "and, the file system under the test's folder taking both spellings for one folder, the copies"
                            + " stand in code/" + THE_SET + "/, the one folder of code/",
                    () -> {
                        assertThat(foldersIn(tree.resolve("code"))).containsExactly(THE_SET);
                        assertThat(spelt.resolve("a.c")).hasBinaryContent(bytes(THE_SETS_TEXT));
                    });
        }
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A tree with no earlier code listing, one cut short inside a quoted cell, or one whose header names no copy column tells no change of folder, and copies every set as without it")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void anEarlierListingAbsentOrUnreadableTellsNothing(@TempDir Path base) throws IOException {
        String row = "200,billing/core/a.c,billing/core,code,,7,2026-01-02T03:04:05Z,code/" + AN_EARLIER_FOLDER
                + "/a.c,copied\n";
        String header = "occurrence_id,path,code_set,role,generator,size_bytes,last_modified,copy,copy_outcome\n";
        Map<String, String> earlier = new LinkedHashMap<>();
        earlier.put("absent", null);
        earlier.put("cut short", header + row + "201,\"billing/core/b");
        earlier.put("no copy column", header.replace(",copy,", ",place,") + row);
        Map<String, List<String>> told = new LinkedHashMap<>();
        Map<String, List<String>> copied = new LinkedHashMap<>();

        for (Map.Entry<String, String> each : earlier.entrySet()) {
            Path folder = base.resolve(each.getKey().replace(' ', '-'));
            Path archive = folder.resolve("archive");
            ListedCodeSets code = codeSets(Map.of(set("billing/core", "core", 200), List.of(
                    file(archive, 200, "billing/core/a.c", "billing/core", "code", null, THE_SETS_TEXT))));
            Path tree = DeliverableRoot.UNNAMED.treeOf(folder.resolve("work"), ListedTree.RUN_ID);
            if (each.getValue() != null) {
                Files.createDirectories(tree);
                Files.writeString(tree.resolve("code.csv"), each.getValue(), StandardCharsets.UTF_8);
            }
            List<String> heard = new ArrayList<>();
            draw(folder, archive, code, tellingChanges(heard, tree.resolve("code.csv")), () -> ROOM_ENOUGH);
            told.put(each.getKey(), heard);
            copied.put(each.getKey(), filesUnder(tree.resolve("code")));
        }

        claim(
                "none of the three drawings tells a change of folder",
                () -> assertThat(told).allSatisfy((listing, heard) -> assertThat(heard).as(listing).isEmpty()));
        claim(
                "and each copies the set under its folder's name, core, as a tree with no earlier listing does",
                () -> assertThat(copied).allSatisfy((listing, files) -> assertThat(files).as(listing)
                        .containsExactly("core/a.c")));
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("A code set renamed in case alone, on a file system that takes both spellings for one folder, is copied into the folder it had, and no folder is counted as left behind")
    @Issue("782")
    @Link(name = "ADR-373", url = Adr.A_FOLDER_DIFFERING_IN_CASE_ALONE_IS_LEFT_BEHIND_ONLY_WHERE_HELD_APART, type = "adr")
    void aSetRenamedInCaseAloneLeavesNoFolderBehindWhereTheFileSystemFoldsCase(@TempDir Path base)
            throws IOException {
        assumeTrue(foldsCase(base), "the file system under the test's folder takes a name and its upper case for one"
                + " file, as NTFS and APFS by default do");
        List<String> reported = new ArrayList<>();

        Path tree = renameInCaseAlone(base, reported);

        claim(
                "code/ holds one folder, the one the first drawing wrote, and the set's file is reached through the"
                        + " name the second drawing gave it",
                () -> {
                    assertThat(foldersIn(tree.resolve("code"))).hasSize(1);
                    assertThat(tree.resolve("code").resolve(THE_RENAMED_SET).resolve("a.c"))
                            .hasBinaryContent(bytes(THE_SETS_TEXT));
                });
        claim(
                "and the second drawing reports once that nothing was left behind: no documents folder and 0 other"
                        + " folders, the folder spelt as the set was first named being the folder it wrote",
                () -> assertThat(reported).containsExactly("documents folder false, other folders 0"));
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("A code set renamed in case alone, on a file system that tells the two spellings apart, is copied into a new folder, and the one it had is counted as left behind")
    @Issue("782")
    @Link(name = "ADR-373", url = Adr.A_FOLDER_DIFFERING_IN_CASE_ALONE_IS_LEFT_BEHIND_ONLY_WHERE_HELD_APART, type = "adr")
    void aSetRenamedInCaseAloneLeavesItsEarlierFolderBehindWhereTheFileSystemTellsThemApart(@TempDir Path base)
            throws IOException {
        assumeTrue(!foldsCase(base), "the file system under the test's folder holds a name and its upper case as two"
                + " files, as ext4 does");
        List<String> reported = new ArrayList<>();

        Path tree = renameInCaseAlone(base, reported);

        claim(
                "code/ holds two folders, the one the first drawing wrote and the one the second did, each spelt as"
                        + " its drawing named the set",
                () -> assertThat(foldersIn(tree.resolve("code"))).containsExactly(THE_SET, THE_RENAMED_SET));
        claim(
                "and the second drawing counts 1 other folder left behind, the first drawing's, though its name"
                        + " differs from the one written in case alone",
                () -> assertThat(reported).containsExactly("documents folder false, other folders 1"));
    }

    @Test
    @Story("The operator renames a code set")
    @DisplayName("A folder in code/ whose name differs in case alone from a set's folder and which leads to that folder is not counted as left behind")
    @Issue("782")
    @Link(name = "ADR-373", url = Adr.A_FOLDER_DIFFERING_IN_CASE_ALONE_IS_LEFT_BEHIND_ONLY_WHERE_HELD_APART, type = "adr")
    void aFolderDifferingInCaseAloneThatLeadsToTheSetsFolderIsNotLeftBehind(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = codeSets(Map.of(set(THE_RENAMED_SET, THE_RENAMED_SET, THE_SETS_MEMBER), List.of(
                file(archive, THE_SETS_MEMBER, THE_RENAMED_SET + "/a.c", THE_RENAMED_SET, "code", null,
                        THE_SETS_TEXT))));
        Path tree = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        Path written = tree.resolve("code").resolve(THE_RENAMED_SET);
        assumeTrue(
                TestLinks.make(tree.resolve("code").resolve(THE_SET), written.toAbsolutePath()),
                "a link spelt as the set's folder in upper case can be made beside it, which a file system that"
                        + " takes both spellings for one folder refuses");
        List<String> reported = new ArrayList<>();

        draw(base, archive, code, hearing(reported), () -> ROOM_ENOUGH);

        claim(
                "the drawing reports once that nothing was left behind: the link stands for the set's folder under"
                        + " a name differing in case alone, as a file system that folds case presents it, and is"
                        + " not counted",
                () -> assertThat(reported).containsExactly("documents folder false, other folders 0"));
    }

    /**
     * Draws the set of the folder {@link #THE_SET}, then again with its folder in the archive renamed {@link
     * #THE_RENAMED_SET}, which names the set by its folder (ADR-377 §9), and returns the tree; {@code reported} hears
     * what the second drawing found left behind.
     */
    private static Path renameInCaseAlone(Path base, List<String> reported) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets first = codeSets(Map.of(set(THE_SET, THE_SET, THE_SETS_MEMBER), List.of(
                file(archive, THE_SETS_MEMBER, THE_SET + "/a.c", THE_SET, "code", null, THE_SETS_TEXT))));
        draw(base, archive, first, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        Path between = Files.move(archive.resolve(THE_SET), archive.resolve("moving"));
        Files.move(between, archive.resolve(THE_RENAMED_SET));
        ListedCodeSets renamed = codeSets(Map.of(set(THE_RENAMED_SET, THE_RENAMED_SET, THE_SETS_MEMBER), List.of(
                file(archive, THE_SETS_MEMBER, THE_RENAMED_SET + "/a.c", THE_RENAMED_SET, "code", null,
                        THE_SETS_TEXT))));
        return draw(base, archive, renamed, hearing(reported), () -> ROOM_ENOUGH);
    }

    /** The sets {@code core}, of the folder {@code billing/core}, and {@code kernel}, one member each. */
    private static ListedCodeSets coreAndKernel(Path archive) throws IOException {
        Map<ListedCodeSet, List<ListedCodeFile>> sets = new LinkedHashMap<>();
        sets.put(set("billing/core", "core", 200),
                List.of(file(archive, 200, "billing/core/a.c", "billing/core", "code", null, THE_SETS_TEXT)));
        sets.put(set("kernel", "kernel", 210),
                List.of(file(archive, 210, "kernel/k.c", "kernel", "code", null, "int k;\n")));
        return codeSets(sets);
    }

    /** Rewrites {@code listing} with every {@code from} in its text replaced by {@code to}. */
    private static void respell(Path listing, String from, String to) throws IOException {
        String text = Files.readString(listing, StandardCharsets.UTF_8);
        if (!text.contains(from)) {
            throw new IllegalStateException("the listing to respell holds no " + from + ": " + text);
        }
        Files.writeString(listing, text.replace(from, to), StandardCharsets.UTF_8);
    }

    /**
     * A hearing that adds to {@code told}, for each change of a set's folder it is told of, the earlier folder, the one
     * now, and whether {@code listing} then names the one now: {@code <earlier> to <now>, the listing naming
     * code/<now>/}, or {@code ..., the listing not yet naming code/<now>/}.
     */
    private static DeliverableProgress tellingChanges(List<String> told, Path listing) {
        return new DeliverableProgress() {
            @Override
            public void codeSetFolderChanged(String earlier, String now) {
                String text;
                try {
                    text = Files.isRegularFile(listing) ? Files.readString(listing, StandardCharsets.UTF_8) : "";
                } catch (IOException unread) {
                    text = "";
                }
                told.add(earlier + " to " + now + (text.contains("code/" + now + "/")
                        ? ", the listing naming code/" + now + "/"
                        : ", the listing not yet naming code/" + now + "/"));
            }
        };
    }

    /** A progress that adds to {@code heard} the name of every method of it the drawing calls, in order. */
    private static DeliverableProgress everyCallOf(List<String> heard) {
        return (DeliverableProgress) Proxy.newProxyInstance(
                DeliverableProgress.class.getClassLoader(),
                new Class<?>[] {DeliverableProgress.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "every call of the drawing's progress";
                    default -> {
                        heard.add(method.getName());
                        yield null;
                    }
                });
    }

    /** A hearing that adds to {@code reported} each listing of what was left behind it is told of. */
    private static DeliverableProgress hearing(List<String> reported) {
        return new DeliverableProgress() {
            @Override
            public void leftBehind(boolean documentsFolder, long otherFolders) {
                reported.add("documents folder " + documentsFolder + ", other folders " + otherFolders);
            }
        };
    }

    /** The names of the folders directly in {@code folder}, in order. */
    private static List<String> foldersIn(Path folder) throws IOException {
        try (Stream<Path> inside = Files.list(folder)) {
            return inside.filter(Files::isDirectory).map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }

    /**
     * Whether the file system under {@code folder} takes a name and its upper case for one file: a file written under
     * a lower-case name is looked for under its upper case, and deleted.
     */
    private static boolean foldsCase(Path folder) throws IOException {
        Path probe = Files.writeString(folder.resolve("case-probe"), "", StandardCharsets.UTF_8);
        try {
            return Files.exists(folder.resolve("CASE-PROBE"));
        } finally {
            Files.delete(probe);
        }
    }

    /** The tree drawn over {@code archive} from {@code code}, with the one group, at {@code <base>/work}. */
    private static Path draw(
            Path base, Path archive, ListedCodeSets code, DeliverableProgress progress, LongSupplier usableSpace)
            throws IOException {
        return draw(base, archive, code, progress, ReplacingMove.ATOMIC, usableSpace);
    }

    /** As above, every part moved into place by {@code moves}, with room enough. */
    private static Path draw(
            Path base, Path archive, ListedCodeSets code, DeliverableProgress progress, ReplacingMove moves)
            throws IOException {
        return draw(base, archive, code, progress, moves, () -> ROOM_ENOUGH);
    }

    private static Path draw(Path base, Path archive, ListedCodeSets code, DeliverableProgress progress,
            ReplacingMove moves, LongSupplier usableSpace) throws IOException {
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
                moves,
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

    /**
     * A member written into the code set folder {@code folder}, outside the corpus root, at {@code below}, its time set
     * to the walk's, and as the walk recorded it: by the folder's spelling {@code spelt} and the path below it.
     */
    private static ListedCodeFile outside(Path folder, long occurrence, String spelt, String below, String role,
            String text) throws IOException {
        Path original = folder.resolve(below);
        Files.createDirectories(original.getParent());
        Files.write(original, bytes(text));
        Files.setLastModifiedTime(original, FileTime.from(THE_WALKS_TIME));
        return new ListedCodeFile(new OccurrenceId(occurrence), new OccurrencePath(spelt + "/" + below), spelt, role,
                null, bytes(text).length, THE_WALKS_TIME);
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

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
