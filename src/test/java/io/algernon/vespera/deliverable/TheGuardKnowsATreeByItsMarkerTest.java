package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The agent guard knows a deliverable tree by the file {@link DeliverableRoot} writes at its top (ADR-283 §2), so
 * the name the guard looks for is the name {@link DeliverableRoot#MARKER_FILE_NAME} gives.
 *
 * <p>The guard held is the draft in {@code docs/agents/guard-draft} while it is there, and the installed one in
 * {@code .claude/hooks} once the operator has put the draft in place and removed it (ADR-215 §7), as
 * {@code src/test/hooks/deliverable-tree-guard.test.mjs} holds the same file.
 */
@Epic("The deliverable")
@Feature("Where the tree is drawn")
@Issue("569")
@Link(name = "ADR-283", url = Adr.THE_GUARD_READS_A_DELIVERABLE_TREE_AS_OUTSIDE_THE_ALLOW_LIST, type = "adr")
class TheGuardKnowsATreeByItsMarkerTest {

    /** The guard as drafted, outside every {@code .claude} folder. */
    private static final Path DRAFTED = Path.of("docs", "agents", "guard-draft", "hooks", "private-paths-guard.mjs");

    /** The guard as installed. */
    private static final Path INSTALLED = Path.of(".claude", "hooks", "private-paths-guard.mjs");

    @Test
    @Story("A tree is known by the file at its top")
    @DisplayName("The agent guard looks for the very file name every tree is marked with")
    void theGuardLooksForTheMarkerTheWriterWrites() throws IOException {
        Path guard = Files.exists(DRAFTED) ? DRAFTED : INSTALLED;
        String text = Files.readString(guard, StandardCharsets.UTF_8);
        String declaration = "const DELIVERABLE_MARKER = \"" + DeliverableRoot.MARKER_FILE_NAME + "\";";

        claim(
                "The agent guard at " + guard + " declares the file it knows a tree by as " + declaration,
                () -> assertThat(text).contains(declaration));
    }
}
