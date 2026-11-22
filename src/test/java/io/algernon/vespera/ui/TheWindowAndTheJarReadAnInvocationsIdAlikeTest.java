package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The form of an invocation's id is written three times, since the window links no class of the jar (ADR-285 §3): the
 * jar's {@code PublishedInvocation.ID}, which refuses a {@code publish --invocation} of any other form (ADR-439 §1), the
 * window's {@link ChildCommand#INVOCATION_ID}, which refuses to build such a command line (§7), and the window's
 * {@code EarlierDeliverable.COPY}, which finds the copies a row is named by (§6). This holds the three to one form, so
 * that a row the window lists is one the window will start and the jar will take.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("923")
@Link(name = "ADR-439", url = Adr.A_PUBLISH_MAY_NAME_AN_EARLIER_RUN_BY_ITS_INVOCATION, type = "adr")
class TheWindowAndTheJarReadAnInvocationsIdAlikeTest {

    /** Ids of the form, the second and the twelfth of their second among them. */
    private static final List<String> OF_THE_FORM =
            List.of("20261114T083000Z", "20261114T083000Z-2", "20261114T083000Z-12");

    /** Ids that are not: a lower-case letter, a digit short, a bare or lettered suffix, a space, a path, nothing. */
    private static final List<String> NOT_OF_THE_FORM = List.of(
            "20261114t083000Z", "20261114T083000z", "2026111T083000Z", "20261114T083000Z-", "20261114T083000Z-a",
            " 20261114T083000Z", "20261114T083000Z --to=website", "../20261114T083000Z", "");

    @Test
    @Story("Publish delivers the earlier deliverable chosen")
    @DisplayName("The window's list, the window's command line and the publish command take and refuse the same run ids")
    void theThreeFormsTakeAndRefuseTheSameIds() throws ReflectiveOperationException {
        Pattern jars = staticPattern("io.algernon.vespera.engine.pipeline.PublishedInvocation", "ID");
        Pattern copies = staticPattern(EarlierDeliverable.class.getName(), "COPY");

        Map<String, List<Boolean>> taken = new LinkedHashMap<>();
        for (String id : concat(OF_THE_FORM, NOT_OF_THE_FORM)) {
            taken.put(id, List.of(
                    jars.matcher(id).matches(),
                    ChildCommand.INVOCATION_ID.matcher(id).matches(),
                    copies.matcher("report-" + id + ".json").matches()));
        }

        for (String id : OF_THE_FORM) {
            claim("the id " + id + " is taken by the publish command, by the window's command line and as the name of"
                    + " a kept report", () -> assertThat(taken.get(id)).containsExactly(true, true, true));
        }
        for (String id : NOT_OF_THE_FORM) {
            claim("the id \"" + id + "\" is refused by all three alike",
                    () -> assertThat(taken.get(id)).containsExactly(false, false, false));
        }
    }

    private static Pattern staticPattern(String className, String fieldName) throws ReflectiveOperationException {
        Field field = Class.forName(className).getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Pattern) field.get(null);
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
