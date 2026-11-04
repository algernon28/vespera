package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * A deliverable root named as {@code <working directory>/deliverable}, the folder every unnamed tree is drawn in, is read
 * as no root named (ADR-334 §1): each generation run's tree is drawn in its own folder there, nothing of ADR-276 §3 is
 * asked of it, and a folder below it is a named root as before.
 *
 * <p>Pure, as {@code DeliverableRootTest} is: {@code <base>/archive} is the corpus root and {@code <base>/work} the
 * working directory. The working directory's {@code deliverable} folder holds two runs' trees as the unnamed root draws
 * them, each with its marker and none at the folder's own top, which ADR-276 §3 refused as a named root.
 */
@Epic("The deliverable")
@Feature("Where the tree is drawn")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-276", url = Adr.THE_DELIVERABLE_IS_DRAWN_AT_A_ROOT_THE_OPERATOR_MAY_NAME, type = "adr")
class NamingTheWorkingDirectorysDeliverableFolderNamesNoRootTest {

    /** A generation run's id, of the shape the ledger mints, whose tree the folder already holds. */
    private static final String A_GENERATION_RUN = "c".repeat(48) + "0123456789abcdef";

    /** Another, whose tree the folder holds too. */
    private static final String ANOTHER_GENERATION_RUN = "d".repeat(48) + "0123456789abcdef";

    /** A third, whose tree the folder does not hold yet. */
    private static final String A_NEW_GENERATION_RUN = "e".repeat(48) + "0123456789abcdef";

    /** The working directory's folder of unnamed trees. */
    private static final String DELIVERABLE = "deliverable";

    /** What the tree's marker is called, at the top of every tree. */
    private static final String MARKER = "vespera-deliverable.txt";

    @TempDir
    Path base;

    private Path archive;

    private Path work;

    @BeforeEach
    void layOutTheCorpusAndTheWorkingDirectory() throws IOException {
        archive = Files.createDirectories(base.resolve("archive"));
        work = Files.createDirectories(base.resolve("work"));
    }

    @Test
    @Story("The tree is drawn at the folder the operator names, or in the working directory")
    @DisplayName("Naming the working directory's deliverable folder, which holds the trees of earlier runs, draws each run's tree there in its own folder, as naming none does")
    void theDefaultFolderNamedDrawsWhereNoneNamedDraws() throws IOException {
        Path theDefault = twoRunsDrawnUnnamed();
        DeliverableRoot named = DeliverableRoot.named(theDefault);

        claim("it is not refused at the start, though it holds no marker at its top",
                () -> assertThat(named.refusalAtTheStart(work, archive, Optional.empty())).isEmpty());
        claim("a run whose tree it holds draws it again in that tree's folder, and a new run in a folder of its own,"
                        + " exactly where naming none draws them",
                () -> assertThat(List.of(named.treeOf(work, A_GENERATION_RUN), named.treeOf(work, A_NEW_GENERATION_RUN)))
                        .containsExactly(DeliverableRoot.UNNAMED.treeOf(work, A_GENERATION_RUN),
                                DeliverableRoot.UNNAMED.treeOf(work, A_NEW_GENERATION_RUN))
                        .containsExactly(theDefault.resolve(A_GENERATION_RUN), theDefault.resolve(A_NEW_GENERATION_RUN)));
        claim("and no drawing of either is refused",
                () -> assertThat(List.of(named.refusalAtTheDrawing(work, A_GENERATION_RUN),
                                named.refusalAtTheDrawing(work, A_NEW_GENERATION_RUN)))
                        .containsOnly(Optional.empty()));
    }

    @Test
    @Story("The tree is drawn at the folder the operator names, or in the working directory")
    @DisplayName("The working directory's deliverable folder is the same folder however its path is written: with a separator after it, through a parent's parent, or before it is made")
    void theDefaultFolderSpeltAnotherWayIsTheSame() throws IOException {
        Path notMadeYet = work.resolve(DELIVERABLE);
        DeliverableRoot beforeItIsMade = DeliverableRoot.of(notMadeYet.toString());

        claim("before the folder is made, a run's tree is drawn in a folder of its own inside it",
                () -> assertThat(beforeItIsMade.treeOf(work, A_NEW_GENERATION_RUN))
                        .isEqualTo(notMadeYet.resolve(A_NEW_GENERATION_RUN)));

        Path theDefault = twoRunsDrawnUnnamed();
        List<DeliverableRoot> spelt = List.of(
                DeliverableRoot.of(theDefault + File.separator),
                DeliverableRoot.of(work.resolve("..").resolve(work.getFileName()).resolve(DELIVERABLE).toString()));

        for (DeliverableRoot root : spelt) {
            claim("spelt another way, it is not refused at the start, and draws each run in its own folder",
                    () -> {
                        assertThat(root.refusalAtTheStart(work, archive, Optional.empty())).isEmpty();
                        assertThat(root.treeOf(work, ANOTHER_GENERATION_RUN))
                                .isEqualTo(theDefault.resolve(ANOTHER_GENERATION_RUN));
                    });
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Story("The tree is drawn at the folder the operator names, or in the working directory")
    @DisplayName("On Windows the working directory's deliverable folder written in capitals is the same folder, as NTFS opens it")
    void onWindowsTheDefaultFolderInAnotherCaseIsTheSame() throws IOException {
        Path theDefault = twoRunsDrawnUnnamed();
        DeliverableRoot inCapitals = DeliverableRoot.of(theDefault.toString().toUpperCase(Locale.ROOT));

        claim("it is not refused at the start",
                () -> assertThat(inCapitals.refusalAtTheStart(work, archive, Optional.empty())).isEmpty());
        claim("and a run's tree is drawn in its own folder there",
                () -> assertThat(inCapitals.refusalAtTheDrawing(work, A_NEW_GENERATION_RUN)).isEmpty());
    }

    @Test
    @Story("The tree is drawn at the folder the operator names, or in the working directory")
    @DisplayName("A folder below the working directory's deliverable folder is a folder named, which holds one run's tree itself")
    void aFolderBelowTheDefaultFolderIsANamedRoot() throws IOException {
        Path theDefault = twoRunsDrawnUnnamed();
        Path mine = theDefault.resolve("mine");
        DeliverableRoot named = DeliverableRoot.named(mine);

        claim("the tree is that folder itself, whatever the run",
                () -> assertThat(named.treeOf(work, A_NEW_GENERATION_RUN)).isEqualTo(mine.toAbsolutePath().normalize()));
    }

    /** The working directory's deliverable folder holding the trees of two runs as the unnamed root draws them. */
    private Path twoRunsDrawnUnnamed() throws IOException {
        Path theDefault = Files.createDirectories(work.resolve(DELIVERABLE));
        for (String run : List.of(A_GENERATION_RUN, ANOTHER_GENERATION_RUN)) {
            Path tree = Files.createDirectories(theDefault.resolve(run));
            Files.writeString(tree.resolve(MARKER),
                    "working-directory=" + work.toRealPath() + "\ngeneration-run=" + run + "\n", StandardCharsets.UTF_8);
            Files.writeString(tree.resolve("index.html"), "the index of " + run, StandardCharsets.UTF_8);
        }
        return theDefault;
    }
}
