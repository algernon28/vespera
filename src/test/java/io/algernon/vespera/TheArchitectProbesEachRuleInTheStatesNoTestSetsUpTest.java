package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The architect's gate probes the code a change carries against each sentence of the record it implements, in four
 * states no test may set up, and its report names the sentence each probe tested (ADR-354 §1, §2).
 *
 * <p>The definition held is the draft in {@code docs/agents/agent-draft} while it is there, and the installed one in
 * {@code .claude/agents} once the operator has put the draft in place and removed it (ADR-215 §7, ADR-354 §4). Each
 * claim matches whole lines of the definition, so a probe dropped, merged into another or reworded fails it.
 */
@Epic("Architecture")
@Feature("The gate before a change lands")
@Issue("695")
@Link(name = "ADR-354", url = Adr.THE_ARCHITECT_PROBES_HOW_THE_CHANGE_BEHAVES_AGAINST_EACH_SENTENCE_OF_ITS_RECORD,
        type = "adr")
class TheArchitectProbesEachRuleInTheStatesNoTestSetsUpTest {

    /** The definition as drafted, outside every {@code .claude} folder. */
    private static final Path DRAFTED = Path.of("docs", "agents", "agent-draft", "agents", "architect.md");

    /** The definition as installed. */
    private static final Path INSTALLED = Path.of(".claude", "agents", "architect.md");

    /** The gate's ninth item, which asks for the probes. */
    private static final String THE_STEP = "9. **The code does what each sentence of its record says, in the states no"
            + " test sets up** (ADR-354). Green tests and every amendment applied show only the states someone thought"
            + " to build. For each rule of the record the diff implements, read the changed code that carries it out"
            + " against the sentence, and probe it in these four states:";

    /** The four states, one line each, in the order the record gives them. */
    private static final List<String> THE_FOUR_STATES = List.of(
            "   - **Concurrent or stale input**: two tabs or windows, a page drawn before another save, a list rebuilt"
                    + " from what a form or a caller carried rather than from what the file or table holds when it is"
                    + " written.",
            "   - **A failure partway through a multi-step write**: what stands on disk or in the ledger when the second"
                    + " of two writes, moves or rows fails, against what the record says a refusal leaves.",
            "   - **A default value posted back by a form**: a preselected, prefilled or placeholder value that comes"
                    + " back with any submit, Browse and Reset among them, read as one the operator named.",
            "   - **The same name in another case or spelling on Windows**: another case, `\\` for `/`, a trailing"
                    + " separator or dot, a relative and an absolute spelling of one folder.");

    /** What a probe that finds the sentence false, and one with nothing to bite on, come to. */
    private static final String WHAT_A_PROBE_COMES_TO = "   Follow the code, not the tests: name the inputs, trace the"
            + " lines they pass through, and say what is written. A probe that finds the sentence false is a stop, as"
            + " item 4 is, with an amendment to `spec-implementer` for the code and to `analyst` for the test that sets"
            + " that state up. A probe that has nothing to bite on in this diff — no form, no write of more than one"
            + " file, no name compared — says so in one line, and is not skipped silently.";

    /** The report's line for the probes. */
    private static final String THE_REPORTS_LINE = "- **Probes**: for each of item 9's four states, the record"
            + " sentence it tested, quoted with its ADR and section, the inputs traced and the `file:line` they reach,"
            + " and whether the sentence held; or the one line saying why the state does not arise in this diff.";

    private static Path definition;

    private static List<String> lines;

    @BeforeAll
    static void readTheDefinition() throws IOException {
        definition = Files.exists(DRAFTED) ? DRAFTED : INSTALLED;
        lines = Files.readAllLines(definition, StandardCharsets.UTF_8);
    }

    @Test
    @Story("Each rule is probed in the states no test sets up")
    @DisplayName("The gate's last item probes the change in four states, in order, and says what a probe comes to")
    void theGateProbesFourStates() {
        int step = lines.indexOf(THE_STEP);
        claim(
                "The architect's definition at " + definition + " carries the gate item asking for the probes, then"
                        + " each of the four states on a line of its own in order, then what a probe comes to",
                () -> {
                    assertThat(step).as("the gate item asking for the probes").isNotNegative();
                    assertThat(lines.subList(step + 1, Math.min(lines.size(), step + 9)))
                            .containsExactly(
                                    "",
                                    THE_FOUR_STATES.get(0),
                                    THE_FOUR_STATES.get(1),
                                    THE_FOUR_STATES.get(2),
                                    THE_FOUR_STATES.get(3),
                                    "",
                                    WHAT_A_PROBE_COMES_TO,
                                    "");
                });
    }

    @Test
    @Story("The report names the sentence each probe tested")
    @DisplayName("The report gives each probe the sentence it tested, after the evidence and before the amendments")
    void theReportNamesTheSentenceEachProbeTested() {
        int report = lines.indexOf("## Report");
        claim(
                "The architect's definition at " + definition + " reports the probes on a line of their own, between"
                        + " the evidence for each gate item and the amendments",
                () -> {
                    assertThat(report).as("the report's heading").isNotNegative();
                    assertThat(lines.subList(report + 3, Math.min(lines.size(), report + 6)))
                            .containsExactly(
                                    "- **Evidence** for each gate item, with the numbers.",
                                    THE_REPORTS_LINE,
                                    "- **Amendments**, each addressed to one agent.");
                });
    }
}
