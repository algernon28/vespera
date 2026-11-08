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

    /** What the walk recorded of each member; no file is read here, so the values are placeholders. */
    private static final long A_SIZE = 10;

    private static final Instant A_TIME = Instant.parse("2026-01-02T03:04:05Z");

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
