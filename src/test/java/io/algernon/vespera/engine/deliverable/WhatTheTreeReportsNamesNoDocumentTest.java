package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the tree's writer tells the rest of the application is numbers and kinds, never a path or a document's text
 * (ADR-274 §8): {@code pipeline} writes its lines, the progress page and the account from what {@link
 * DeliverableProgress} hands over, and this holds that it hands over nothing else. That the module declares no logger
 * is not held here.
 *
 * <p>A claim about the interface rather than about the lines, because it is the interface that decides what a line
 * can carry: a method that took a path or a string would let a name out of the tree, whatever the caller did with it.
 *
 * <p><b>One method takes two words, each of ours</b> (ADR-333 §11, amending ADR-274 §8): the one telling of a
 * plugin whose drawing failed, which takes the plugin's name and the type of what it threw, and never the exception's
 * message. Neither word is a document's: a plugin's name is lowercase letters a to z, and a type is a class's name.
 *
 * <p><b>And one takes two names of the tree's own {@code code/}</b> (ADR-377 §9, amending ADR-274 §8): the one telling
 * of a code set whose folder there changed, each a set's folder's last element made safe, which names a set of code and
 * no document, file or path of the archive.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class WhatTheTreeReportsNamesNoDocumentTest {

    /** The one method that takes words: a plugin's name and the type of what its drawing threw (ADR-333 §11). */
    private static final String A_PLUGINS_DRAWING_FAILED = "pluginDrawingFailed";

    /** The one method that takes two folders of the tree's code/: a set's earlier folder and its folder now (ADR-377 §9). */
    private static final String A_SETS_FOLDER_CHANGED = "codeSetFolderChanged";

    /** The two methods that take words, each two strings. */
    private static final List<String> TAKING_WORDS = List.of(A_PLUGINS_DRAWING_FAILED, A_SETS_FOLDER_CHANGED);

    @Test
    @Story("Nothing outside the tree names a document")
    @DisplayName("Everything the tree's writer reports is a number or a kind, a plugin's name and an exception's type, or two folders of the tree's code, so nothing it reports can name a document")
    void everyProgressMethodTakesOnlyNumbersAndKinds() {
        List<String> carryingMore = Arrays.stream(DeliverableProgress.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !TAKING_WORDS.contains(method.getName()))
                .filter(method -> Arrays.stream(method.getParameterTypes())
                        .anyMatch(type -> !type.isPrimitive() && !type.isEnum()))
                .map(Method::toString)
                .sorted()
                .toList();

        assumeTrue(
                DeliverableProgress.class.getDeclaredMethods().length > 0,
                "the progress the tree's writer reports has methods to report through, so the claim below is about some");
        claim(
                "no method but the one telling of a plugin's failed drawing and the one telling of a code set's folder"
                        + " changed takes anything but a number or a kind: one named here could hand a path, a name or"
                        + " text out of the tree",
                () -> assertThat(carryingMore).isEmpty());
        claim(
                "and those two each take two strings and nothing else: no exception, whose message could name a"
                        + " document, and no path",
                () -> assertThat(Arrays.stream(DeliverableProgress.class.getDeclaredMethods())
                                .filter(method -> TAKING_WORDS.contains(method.getName()))
                                .map(method -> List.of(method.getParameterTypes()))
                                .toList())
                        .containsExactly(List.of(String.class, String.class), List.of(String.class, String.class)));
    }
}
