package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
 * (ADR-272 §8): the module declares no logger, and {@code pipeline} writes its lines, the progress page and the
 * account from what {@link DeliverableProgress} hands over and nothing else.
 *
 * <p>A claim about the interface rather than about the lines, because it is the interface that decides what a line
 * can carry: a method that took a path or a string would let a name out of the tree, whatever the caller did with it.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-272", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
class WhatTheTreeReportsNamesNoDocumentTest {

    @Test
    @Story("Nothing outside the tree names a document")
    @DisplayName("Everything the tree's writer reports is a number or a kind, so nothing it reports can name a document")
    void everyProgressMethodTakesOnlyNumbersAndKinds() {
        List<String> carryingMore = Arrays.stream(DeliverableProgress.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> Arrays.stream(method.getParameterTypes())
                        .anyMatch(type -> !type.isPrimitive() && !type.isEnum()))
                .map(Method::toString)
                .sorted()
                .toList();

        claim(
                "the progress the tree's writer reports has methods to report through, so the claim below is about"
                        + " some",
                () -> assertThat(DeliverableProgress.class.getDeclaredMethods()).isNotEmpty());
        claim(
                "no method takes anything but a number or a kind: one named here could hand a path, a name or text"
                        + " out of the tree",
                () -> assertThat(carryingMore).isEmpty());
    }
}
