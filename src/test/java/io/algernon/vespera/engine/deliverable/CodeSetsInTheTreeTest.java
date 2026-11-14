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
 * file of names left in the working directory read by nothing, and a set whose folder in {@code code/} changed since
 * the listing the tree held told once, after the new listing is in place, a change of case alone being none (ADR-377
 * §9); a folder in {@code code/} this drawing did not write counted as left behind, and one whose name differs in case
 * alone from a set's this drawing wrote counted only where the file system holds the two apart (ADR-373); and the code
 * copies surveyed in numbers only.
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

    /** That set's one member, its occurrence and its text. */
    private static final long THE_SETS_MEMBER = 170;

    private static final String THE_SETS_TEXT = "int a;\n";

    /** The file of names the control panel once wrote, which no class of the jar reads (ADR-377 §9). */
    private static final String A_NAMES_FILE = "code-set-names.yaml";

    /** The folder in code/ an earlier listing puts set core's copies in, other than its default name. */
    private static final String CORE_EARLIER = "billing-core";

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
    @DisplayName("A file of code set names left in the working directory renames nothing and is told of nowhere")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void aNamesFileLeftInTheWorkingDirectoryRenamesNothing(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(base.resolve("work"));
        Files.writeString(base.resolve("work").resolve(A_NAMES_FILE),
                "- folder: shop/core\n  name: Shop core\n- folder: gone/away\n  name: Gone\n", StandardCharsets.UTF_8);
        Map<ListedCodeSet, List<ListedCodeFile>> sets = new LinkedHashMap<>();
        sets.put(set("libs/kernel", "kernel", 150), List.of(
                file(archive, 150, "libs/kernel/k.c", "libs/kernel", "code", null, "int k;\n")));
        sets.put(set("shop/core", "core", 160), List.of(
                file(archive, 160, "shop/core/a.c", "shop/core", "code", null, "int a;\n")));
        List<String> told = new ArrayList<>();

        Path tree = draw(base, archive, codeSets(sets), everyCall(told), () -> ROOM_ENOUGH);

        claim(
                "both sets are copied under their default names, kernel and core, though the file names the second Shop"
                        + " core",
                () -> assertThat(filesUnder(tree.resolve("code"))).containsExactly("core/a.c", "kernel/k.c"));
        claim(
                "the listing of code names each copy in its set's default folder",
                () -> assertThat(outcomesOf(tree)).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "libs/kernel/k.c", "code/kernel/k.c copied", "shop/core/a.c", "code/core/a.c copied")));
        claim(
                "the index links each set by its default folder, and names neither name the file gives",
                () -> assertThat(Files.readString(tree.resolve("index.html"), StandardCharsets.UTF_8))
                        .contains("href=\"code/core/\"", "href=\"code/kernel/\"")
                        .doesNotContain("Shop core", "Gone"));
        claim(
                "and the drawing tells nothing of the file: no call it hears is about code set names",
                () -> assertThat(told).noneMatch(call -> call.contains("CodeSetNames")));
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A code set whose folder in code/ changed since the last drawing is told once, after the listing of code is in place")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void aSetWhoseFolderInCodeChangedIsToldOnceAfterTheListingIsInPlace(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = coreAndKernel(archive);
        Path first = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        listedIn(first, CORE_EARLIER);
        List<String> second = new ArrayList<>();
        List<String> listingWhenTold = new ArrayList<>();

        draw(base, archive, code, changes(second, first, listingWhenTold), () -> ROOM_ENOUGH);
        List<String> third = new ArrayList<>();
        draw(base, archive, code, changes(third, first, new ArrayList<>()), () -> ROOM_ENOUGH);

        claim(
                "the drawing over a listing that put core's files in code/billing-core/ is told once, of core alone,"
                        + " from billing-core to core",
                () -> assertThat(second).containsExactly("billing-core -> core"));
        claim(
                "and when it is told, the listing in place already names core's copies in code/core/",
                () -> assertThat(listingWhenTold).singleElement().asString()
                        .contains("code/core/a.c").doesNotContain("code/billing-core/"));
        claim(
                "the drawing after it, reading the listing the second left, is told nothing",
                () -> assertThat(third).isEmpty());
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A drawing that fails before its listing of code is moved into place tells nothing, and the next tells the change")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void aDrawingThatFailsBeforeTheListingMovesTellsNothing(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = coreAndKernel(archive);
        Path first = draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        listedIn(first, CORE_EARLIER);
        ReplacingMove refusingTheListing = new ReplacingMove(
                (part, target) -> {
                    if (target.getFileName().toString().equals(CodeCopies.LISTING_FILE_NAME)) {
                        throw new IOException("the listing's place refuses it");
                    }
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});
        List<String> failing = new ArrayList<>();

        Throwable thrown = catchThrowable(() -> draw(base, archive, code, changes(failing, first, new ArrayList<>()),
                () -> ROOM_ENOUGH, refusingTheListing));
        List<String> next = new ArrayList<>();
        draw(base, archive, code, changes(next, first, new ArrayList<>()), () -> ROOM_ENOUGH);

        claim(
                "the drawing whose listing could not be moved fails",
                () -> assertThat(thrown).isNotNull());
        claim(
                "and it tells no change",
                () -> assertThat(failing).isEmpty());
        claim(
                "the next drawing reads the same earlier listing, and tells the change once",
                () -> assertThat(next).containsExactly("billing-core -> core"));
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A code set's folder in code/ that changed in case alone is no change, and is told nothing")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void aChangeOfCaseAloneIsNoChange(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeFile member = file(archive, THE_SETS_MEMBER, THE_RENAMED_SET + "/a.c", THE_RENAMED_SET, "code", null,
                THE_SETS_TEXT);
        // An earlier drawing that gave the set at core the folder Core in code/, as one under another name would.
        ListedCodeSets drawnAsCore = codeSets(Map.of(set(THE_RENAMED_SET, THE_SET, THE_SETS_MEMBER), List.of(member)));
        Path tree = draw(base, archive, drawnAsCore, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        Map<String, String> earlier = outcomesOf(tree);
        ListedCodeSets byItsFolder = codeSets(Map.of(set(THE_RENAMED_SET, THE_RENAMED_SET, THE_SETS_MEMBER),
                List.of(member)));
        List<String> told = new ArrayList<>();

        draw(base, archive, byItsFolder, changes(told, tree, new ArrayList<>()), () -> ROOM_ENOUGH);

        claim(
                "the earlier listing put the set's file in code/Core/",
                () -> assertThat(earlier).containsExactly(Map.entry("core/a.c", "code/Core/a.c copied")));
        claim(
                "the drawing over a listing that put the set's files in code/Core/, its folder now core, is told nothing",
                () -> assertThat(told).isEmpty());
        if (foldsCase(base)) {
            claim(
                    "and where the file system takes both spellings for one folder, the copies stand in code/Core/,"
                            + " the one folder of code/",
                    () -> {
                        assertThat(foldersIn(tree.resolve("code"))).containsExactly(THE_SET);
                        assertThat(tree.resolve("code").resolve(THE_SET).resolve("a.c"))
                                .hasBinaryContent(bytes(THE_SETS_TEXT));
                    });
        }
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("No earlier listing of code, one cut short in a quoted cell, or one whose header names no copy, is told nothing")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void anEarlierListingAbsentOrUnreadableTellsNothing(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = coreAndKernel(archive);
        List<String> withNone = new ArrayList<>();
        Path tree = draw(base, archive, code, changes(withNone, null, new ArrayList<>()), () -> ROOM_ENOUGH);
        Path listing = tree.resolve(CodeCopies.LISTING_FILE_NAME);
        List<String> copied = filesUnder(tree.resolve("code"));
        String header = Files.readAllLines(listing, StandardCharsets.UTF_8).getFirst();
        String earlierRow = "160,shop/core/a.c,shop/core,code,,7,2026-01-02T03:04:05Z,code/billing-core/a.c,copied";

        Files.writeString(listing, header + "\n" + earlierRow + "\n161,\"shop/core/b,", StandardCharsets.UTF_8);
        List<String> cutShort = new ArrayList<>();
        draw(base, archive, code, changes(cutShort, null, new ArrayList<>()), () -> ROOM_ENOUGH);
        List<String> copiedOverCutShort = filesUnder(tree.resolve("code"));

        Files.writeString(listing, header.replace(",copy,", ",kopie,") + "\n" + earlierRow + "\n",
                StandardCharsets.UTF_8);
        List<String> noCopyColumn = new ArrayList<>();
        draw(base, archive, code, changes(noCopyColumn, null, new ArrayList<>()), () -> ROOM_ENOUGH);

        claim(
                "a tree with no earlier listing is told nothing",
                () -> assertThat(withNone).isEmpty());
        claim(
                "an earlier listing whose last cell is a quote never closed is told nothing, though a row before it"
                        + " puts core's files in code/billing-core/",
                () -> assertThat(cutShort).isEmpty());
        claim(
                "an earlier listing whose header names no copy column is told nothing, though its row holds the same"
                        + " folder where the copy stood",
                () -> assertThat(noCopyColumn).isEmpty());
        claim(
                "and every set is copied as with no earlier listing",
                () -> assertThat(copiedOverCutShort).isEqualTo(copied).containsExactly("core/a.c", "kernel/k.c"));
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
     * Draws {@link #THE_SET} under its default name, then renames its folder in the archive {@link #THE_RENAMED_SET},
     * which names the set by its folder (ADR-377 §9), draws it again and returns the tree; {@code reported} hears what
     * the second drawing found left behind.
     */
    private static Path renameInCaseAlone(Path base, List<String> reported) throws IOException {
        Path archive = base.resolve("archive");
        ListedCodeSets code = codeSets(Map.of(set(THE_SET, THE_SET, THE_SETS_MEMBER), List.of(
                file(archive, THE_SETS_MEMBER, THE_SET + "/a.c", THE_SET, "code", null, THE_SETS_TEXT))));
        draw(base, archive, code, DeliverableProgress.NONE, () -> ROOM_ENOUGH);
        Files.move(archive.resolve(THE_SET), archive.resolve("moving"));
        Files.move(archive.resolve("moving"), archive.resolve(THE_RENAMED_SET));
        ListedCodeSets renamed = codeSets(Map.of(set(THE_RENAMED_SET, THE_RENAMED_SET, THE_SETS_MEMBER), List.of(
                file(archive, THE_SETS_MEMBER, THE_RENAMED_SET + "/a.c", THE_RENAMED_SET, "code", null,
                        THE_SETS_TEXT))));
        return draw(base, archive, renamed, hearing(reported), () -> ROOM_ENOUGH);
    }

    /** Two sets, {@code shop/core} named core and {@code libs/kernel} named kernel, one file each. */
    private static ListedCodeSets coreAndKernel(Path archive) throws IOException {
        Map<ListedCodeSet, List<ListedCodeFile>> sets = new LinkedHashMap<>();
        sets.put(set("libs/kernel", "kernel", 150), List.of(
                file(archive, 150, "libs/kernel/k.c", "libs/kernel", "code", null, "int k;\n")));
        sets.put(set("shop/core", "core", 160), List.of(
                file(archive, 160, "shop/core/a.c", "shop/core", "code", null, "int a;\n")));
        return codeSets(sets);
    }

    /** Rewrites the tree's listing of code as an earlier drawing would have left it, core's copies in {@code folder}. */
    private static void listedIn(Path tree, String folder) throws IOException {
        Path listing = tree.resolve(CodeCopies.LISTING_FILE_NAME);
        String text = Files.readString(listing, StandardCharsets.UTF_8);
        assumeTrue(text.contains("code/core/"), "the first drawing listed core's copies in code/core/");
        Files.writeString(listing, text.replace("code/core/", "code/" + folder + "/"), StandardCharsets.UTF_8);
    }

    /**
     * A hearing that adds {@code earlier -> now} to {@code told} for each set whose folder in code/ changed, and, where
     * {@code tree} is given, the listing of code standing in it at that moment to {@code listings}.
     */
    private static DeliverableProgress changes(List<String> told, Path tree, List<String> listings) {
        return new DeliverableProgress() {
            @Override
            public void codeSetFolderChanged(String earlier, String now) {
                told.add(earlier + " -> " + now);
                if (tree != null) {
                    try {
                        listings.add(Files.readString(tree.resolve(CodeCopies.LISTING_FILE_NAME), StandardCharsets.UTF_8));
                    } catch (IOException unread) {
                        listings.add("unread: " + unread);
                    }
                }
            }
        };
    }

    /** A hearing that adds the name of every method of it called to {@code told}, in order. */
    private static DeliverableProgress everyCall(List<String> told) {
        return (DeliverableProgress) Proxy.newProxyInstance(
                DeliverableProgress.class.getClassLoader(),
                new Class<?>[] {DeliverableProgress.class},
                (proxy, method, arguments) -> {
                    told.add(method.getName());
                    return null;
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
        return draw(base, archive, code, progress, usableSpace, ReplacingMove.ATOMIC);
    }

    /** As above, each file put in place by {@code moves}. */
    private static Path draw(
            Path base,
            Path archive,
            ListedCodeSets code,
            DeliverableProgress progress,
            LongSupplier usableSpace,
            ReplacingMove moves)
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
