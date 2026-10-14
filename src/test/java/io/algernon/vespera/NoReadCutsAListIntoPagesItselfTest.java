package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.corpus.AnomalyLog;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No read of {@code src/main} cuts a list of keys into pages with a loop of its own: the cut is {@code
 * ListedPages}' (ADR-257), and the one read of the walk anomalies that was paged by hand goes through {@code
 * KeysetPages} like the others (ADR-257, ADR-220 section 3).
 *
 * <p>Read as source, because a loop is not a thing a compiled class shows: any {@code for (int from = 0; from <
 * …; from += …)} in a class other than {@code ListedPages} is a cut of a list by hand. {@code Shingler} cuts
 * the rows it inserts, a write and no read, and is the one other. The anomaly log is read by reflection for a
 * page size of its own, as {@code TheReadsByKeyTakeTheLedgersPageTest} reads the others.
 */
@Epic("Architecture")
@Feature("Reads a page at a time")
@Link(name = "ADR-257", url = Adr.ONE_CUT_OF_A_LIST_OF_KEYS_INTO_PAGES, type = "adr")
class NoReadCutsAListIntoPagesItselfTest {

    private static final Path MAIN_SOURCE = Path.of("src", "main", "java");

    private static final Pattern A_LIST_CUT_BY_HAND = Pattern.compile("for \\(int from = 0; from < [^;]+; from \\+= ");

    /** The one class, besides the cut itself, whose loop is not a read: it batches the rows it inserts. */
    private static final String A_WRITE = "Shingler.java";

    @Test
    @Story("A list of keys is cut once")
    @DisplayName("No class of src/main cuts a list into pages with a loop of its own, but the cut itself")
    void noClassCutsAListByHand() throws IOException {
        List<String> byHand = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(MAIN_SOURCE)) {
            for (Path source : sources.filter(file -> file.toString().endsWith(".java")).toList()) {
                String name = source.getFileName().toString();
                if (name.equals("ListedPages.java") || name.equals(A_WRITE)) {
                    continue;
                }
                if (A_LIST_CUT_BY_HAND.matcher(Files.readString(source)).find()) {
                    byHand.add(name);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        claim(
                "the reads that ask a list of keys, the content keys, the paths, the measures, the scores, the"
                        + " clusters' places, the survivors among a page, the verdicts to discard and the seeds'"
                        + " pages, go through the one cut and none keeps a loop of its own for it",
                () -> assertThat(byHand).isEmpty());
    }

    @Test
    @Story("A list of keys is cut once")
    @DisplayName("The anomaly log keeps no page size of its own: it pages by keyset like the other reads")
    void theAnomalyLogKeepsNoPageSizeOfItsOwn() {
        List<String> ownPageSizes = new ArrayList<>();
        for (Field field : AnomalyLog.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && field.getType() == int.class) {
                ownPageSizes.add(AnomalyLog.class.getSimpleName() + "." + field.getName());
            }
        }

        claim(
                "the comparison of two walks' anomalies asks its pages of the ledger's size, so that it cannot drift"
                        + " from the others",
                () -> assertThat(ownPageSizes).isEmpty());
    }
}
