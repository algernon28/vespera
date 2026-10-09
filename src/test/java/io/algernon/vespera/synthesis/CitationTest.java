package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.regex.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A citation as the model writes it: a bracketed ordinal into the documents one call sent (ADR-109).
 * One pattern, read by the range check in {@code ClusterSynthesis} and by the link rewrite on a
 * cluster's page (ADR-213 §4), so the two can never disagree about what a citation is.
 */
@Epic("Synthesis")
@Feature("How writing points at a document")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
class CitationTest {

    @Test
    @Story("A citation is a bracketed ordinal")
    @DisplayName("The pattern is the one both readers used before it had one home")
    void isThePatternBothReadersUsed() {
        claim(
                "the pattern is \\[(\\d+)\\], character for character what ClusterSynthesis and Deliverable each"
                        + " compiled at 4b99a03",
                () -> assertThat(Citation.AS_WRITTEN.pattern()).isEqualTo("\\[(\\d+)\\]"));
    }

    @Test
    @Story("A citation is a bracketed ordinal")
    @DisplayName("Digits in brackets are citations, and the ordinal is the first group")
    void findsEachOrdinalInBrackets() {
        claim(
                "each bracketed run of digits is found, and its first group is the ordinal alone",
                () -> assertThat(Citation.AS_WRITTEN.matcher("Both [2] and [1] agree; [12] too.").results()
                                .map(match -> match.group(1))
                                .toList())
                        .containsExactly("2", "1", "12"));
    }

    @Test
    @Story("A citation is a bracketed ordinal")
    @DisplayName("Brackets around anything but digits alone, and digits in braces, are not citations")
    void leavesEverythingElseAlone() {
        List<MatchResult> found = Citation.AS_WRITTEN.matcher("See [x], [ 1], [1a], [] and {1}.").results().toList();

        claim(
                "a word, a spaced number, a number with a letter, empty brackets and a number in braces are"
                        + " none of them a citation: a model that wrote {1} cited nothing (ADR-109)",
                () -> assertThat(found).isEmpty());
    }
}
