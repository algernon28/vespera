package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shape ADR-209 section 1 gives the ledger: one type that holds four records and does nothing itself,
 * with each of the twenty-eight things the ledger did before on the record whose tables it touches.
 *
 * <p>What each method does is held where it was, by {@code LedgerTest}, {@code SurvivorsTest} and the
 * tests beside them, which call it through its record. This holds only where it lives, so that a method
 * added to the wrong record, or to {@code Ledger} itself, is noticed. The lists below are what moved, not
 * all a record may ever hold: a record may gain a method, and {@code Ledger} may not.
 */
@Epic("Census")
@Feature("Ledger")
@Issue("350")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
class LedgerHoldsFourRecordsTest {

    /** What was asked of the ledger about a walk: nine things, on the {@code walk} table. */
    private static final Set<String> ABOUT_A_WALK = Set.of(
            "unfinishedWalk",
            "startWalk",
            "recordProgress",
            "finishWalk",
            "countsFor",
            "walkFinished",
            "finishedWalkFor",
            "finishedWalkBefore",
            "discardWalk");

    /** What was asked about the files a walk found: six things, on the {@code file_occurrence} table. */
    private static final Set<String> ABOUT_OCCURRENCES =
            Set.of("fileOccurrence", "occurrencesForWalk", "occurrenceCount", "factsFor", "occurrenceId", "occurrencesOf");

    /** What was asked about a run and its steps: six things, on {@code run}, {@code run_upstream} and {@code finished_step}. */
    private static final Set<String> ABOUT_A_RUN =
            Set.of("startRun", "walkOf", "stageOf", "upstreamRuns", "finishStep", "stepFinished");

    /** What was asked about verdicts and what they leave: seven things, on the {@code verdict} table. */
    private static final Set<String> ABOUT_VERDICTS = Set.of(
            "verdict",
            "discardVerdicts",
            "discardVerdictsAgainst",
            "extractionFailures",
            "survivorCount",
            "survivors",
            "survivorsBySize");

    /**
     * What the ledger did before it was divided and nothing asks any more: ADR-216 removed {@code walkFinished},
     * which only tests called. It stays in the list above, which records what the ledger did.
     */
    private static final Set<String> REMOVED_SINCE_THE_DIVISION = Set.of("walkFinished");

    /** Nine, six, six and seven: every public method the ledger had before it was divided. */
    private static final int EVERYTHING_THE_LEDGER_DID = 28;

    @Test
    @Story("The ledger is four records behind one type")
    @DisplayName("The ledger itself only hands out its four records")
    void theLedgerOnlyHandsOutItsFourRecords() {
        claim(
                "the ledger's own public methods are the four that hand out a record, and nothing else: a"
                        + " fifth named here is something the ledger does itself again, which is how one type"
                        + " came to do four jobs",
                () -> assertThat(publicMethodsOf(Ledger.class))
                        .containsExactlyInAnyOrder("walks", "occurrences", "runs", "verdicts"));
        claim(
                "and each hands out the record its name says",
                () -> assertThat(List.of(
                                returnTypeOf("walks"),
                                returnTypeOf("occurrences"),
                                returnTypeOf("runs"),
                                returnTypeOf("verdicts")))
                        .containsExactly(Walks.class, Occurrences.class, Runs.class, Verdicts.class));
    }

    @Test
    @Story("The ledger is four records behind one type")
    @DisplayName("Everything the ledger did is on the record whose table it touches, and on no other")
    void everythingTheLedgerDidIsOnOneRecord() {
        Set<String> walks = publicMethodsOf(Walks.class);
        Set<String> occurrences = publicMethodsOf(Occurrences.class);
        Set<String> runs = publicMethodsOf(Runs.class);
        Set<String> verdicts = publicMethodsOf(Verdicts.class);

        claim(
                "the four lists this test holds add up to the " + EVERYTHING_THE_LEDGER_DID + " things the"
                        + " ledger did before it was divided, so none is left out of the claims below",
                () -> assertThat(ABOUT_A_WALK.size() + ABOUT_OCCURRENCES.size() + ABOUT_A_RUN.size()
                                + ABOUT_VERDICTS.size())
                        .isEqualTo(EVERYTHING_THE_LEDGER_DID));
        claim(
                "what is asked about a walk is asked of the walks, but for what has been removed since",
                () -> assertThat(walks).containsAll(without(ABOUT_A_WALK, REMOVED_SINCE_THE_DIVISION)));
        claim(
                "what is asked about the files a walk found is asked of the occurrences",
                () -> assertThat(occurrences).containsAll(ABOUT_OCCURRENCES));
        claim("what is asked about a run and its steps is asked of the runs", () -> assertThat(runs).containsAll(ABOUT_A_RUN));
        claim(
                "what is asked about verdicts, and about the documents they leave, is asked of the verdicts",
                () -> assertThat(verdicts).containsAll(ABOUT_VERDICTS));
        claim(
                "and none of it is on a second record: a walk's methods are on the walks alone, and so on for"
                        + " each, so there is one place to ask each thing",
                () -> {
                    assertThat(union(occurrences, runs, verdicts)).doesNotContainAnyElementsOf(ABOUT_A_WALK);
                    assertThat(union(walks, runs, verdicts)).doesNotContainAnyElementsOf(ABOUT_OCCURRENCES);
                    assertThat(union(walks, occurrences, verdicts)).doesNotContainAnyElementsOf(ABOUT_A_RUN);
                    assertThat(union(walks, occurrences, runs)).doesNotContainAnyElementsOf(ABOUT_VERDICTS);
                });
    }

    @Test
    @Story("The ledger is four records behind one type")
    @DisplayName("The three long reads are handed out as something to go through, not as a batch reader")
    void theLongReadsAreIterables() throws Exception {
        List<Class<?>> handedOut = List.of(
                Verdicts.class.getMethod("survivors", RunId.class).getReturnType(),
                Verdicts.class.getMethod("survivorsBySize", RunId.class).getReturnType(),
                Occurrences.class.getMethod("occurrencesOf", WalkId.class).getReturnType());

        claim(
                "a run's surviving documents, the same by size, and a walk's files are each handed out as a"
                        + " plain Iterable, so a module that reads them needs nothing of the batch framework",
                () -> assertThat(handedOut).containsOnly(Iterable.class));
    }

    private static Class<?> returnTypeOf(String record) throws NoSuchMethodException {
        return Ledger.class.getMethod(record).getReturnType();
    }

    private static Set<String> publicMethodsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> without(Set<String> all, Set<String> removed) {
        Set<String> kept = new TreeSet<>(all);
        kept.removeAll(removed);
        return kept;
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> all = new TreeSet<>();
        for (Set<String> set : sets) {
            all.addAll(set);
        }
        return all;
    }
}
