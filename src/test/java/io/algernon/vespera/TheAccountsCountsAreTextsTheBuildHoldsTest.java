package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each of the invocation account's three counts by a closed enumeration is a text the compiled class holds,
 * naming every constant of its enumeration (ADR-224 section 1).
 *
 * <p>It has to be a text of the compiled class, completed at run time by the list of runs alone, because
 * that is what {@link EveryStatementThatSortsIsRecordedTest} plans: a statement put together from the
 * enumeration at run time is in no compiled text, so that test would pass it unread whatever it sorted.
 * And it has to name every constant, because the account reads one count back for each constant of the
 * enumeration: where the enumeration has a constant the text leaves out, the read of that count fails in a
 * run and the account ends there, without that count's lines or anything after them. This test is what
 * fails first, when an enumeration gains a constant and the text does not, so that no build ships so.
 *
 * <p>Read from the compiled form, as {@link EachTableIsNamedOnlyByItsOwnerTest} reads the account's
 * statements. Before ADR-224 was built the three texts were {@code GROUP BY}s that named no constant, and
 * this failed on the first.
 */
@Epic("Pipeline")
@Feature("The invocation account")
@Issue("477")
@Link(name = "ADR-224", url = Adr.THE_ACCOUNTS_COUNTS_AND_THE_EMBEDDER_IDENTITY_READS_SORT_NOTHING, type = "adr")
class TheAccountsCountsAreTextsTheBuildHoldsTest {

    private static final String THE_CLASS_THAT_COUNTS_FOR_THE_ACCOUNT = "io.algernon.vespera.pipeline.InvocationAccount";

    @Test
    @Story("A count by kind reads its rows once and sorts none of them")
    @DisplayName("The text of each of the three counts is written out in the class, and names every declared kind")
    void eachCountsTextNamesEveryConstantOfItsEnumeration() throws Exception {
        List<String> texts = ShippedClasses.stringsByClass().getOrDefault(THE_CLASS_THAT_COUNTS_FOR_THE_ACCOUNT, List.of());

        claim("the class that writes the account holds texts, so what follows did read it", () -> assertThat(texts).isNotEmpty());
        claim(
                "one text of the class reads the verdicts, and it names every declared kind of verdict: a kind"
                        + " declared later and not added to it would make the reading of the counts fail, and"
                        + " the account would end there",
                () -> assertThat(textsOver(texts, "verdict"))
                        .hasSize(1)
                        .allSatisfy(text -> assertThat(text).contains(quotedNamesOf(VerdictKind.values()))));
        claim(
                "one text of the class reads the conversion faults, and it names every declared category",
                () -> assertThat(textsOver(texts, "extraction_fault"))
                        .hasSize(1)
                        .allSatisfy(text -> assertThat(text).contains(quotedNamesOf(FailureCategory.values()))));
        claim(
                "one text of the class reads the faults of groups, and it names every declared kind",
                () -> assertThat(textsOver(texts, "cluster_fault"))
                        .hasSize(1)
                        .allSatisfy(text -> assertThat(text).contains(quotedNamesOf(ClusterFaultKind.values()))));
    }

    /** The texts that read {@code table}, and no table whose name only begins so. */
    private static List<String> textsOver(List<String> texts, String table) {
        Pattern reads = Pattern.compile("\\bFROM " + table + "\\b");
        return texts.stream().filter(text -> reads.matcher(text).find()).toList();
    }

    /** Each constant's name between single quotes, as a statement's text names a stored value. */
    private static String[] quotedNamesOf(Enum<?>[] constants) {
        return Arrays.stream(constants).map(constant -> "'" + constant.name() + "'").toArray(String[]::new);
    }
}
