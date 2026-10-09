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
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Markdown text surroundings a value this tool did not compose is written into, as one table with
 * one constant per surrounding (ADR-213 §2): ADR-138 §5's table, its three Markdown rows made code.
 *
 * <p><b>Every expected value here is what {@code Deliverable} wrote at {@code 4b99a03}</b>, read off
 * its {@code inACell}, {@code inAHeading}, {@code escapeLinkText} and {@code onOneLine} before the move,
 * so the move is held to be byte-identical rather than merely plausible.
 *
 * <p>The other two surroundings, the link destination and the CSV, are not rows of this enum: what a
 * surrounding answers to decides the form of its rule (ADR-137 §4). {@link ArchiveLinkTest} and
 * {@link ManifestCsvTest} hold them.
 */
@Epic("Synthesis")
@Feature("The surroundings a value is written into")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-134", url = Adr.A_BREAK_IS_FOLDED_AND_THREE_ESCAPING_RULES_STAND, type = "adr")
@Link(name = "ADR-138", url = Adr.A_BRACKET_IS_ESCAPED_IN_EVERY_SURROUNDING_A_VALUE_IS_READ_IN, type = "adr")
class MarkdownSurroundingsTest {

    /**
     * A value carrying every character any of the three rows escapes, a line break, a blank line, a tab
     * and whitespace at either end: what a Docling title or a stored path can hold.
     */
    private static final String A_HOSTILE_VALUE = "  Report \\ <draft> & [2019] | `v2`\n\nfinal\tcopy  ";

    /** That value in a table cell: folded, trimmed, and all seven characters escaped, the pipe included. */
    private static final String IN_A_TABLE_CELL = "Report \\\\ \\<draft> \\& \\[2019\\] \\| \\`v2\\` final copy";

    /** That value in an ATX heading: folded and trimmed like the cell, with the pipe left as it is. */
    private static final String IN_AN_ATX_HEADING = "Report \\\\ \\<draft> \\& \\[2019\\] | \\`v2\\` final copy";

    /** That value in a membership entry: escaped like the heading, and not folded, so the breaks stay. */
    private static final String IN_A_MEMBERSHIP_ENTRY =
            "  Report \\\\ \\<draft> \\& \\[2019\\] | \\`v2\\`\n\nfinal\tcopy  ";

    /** That value folded and nothing else: every run of ASCII whitespace one space, the ends trimmed. */
    private static final String ON_ONE_LINE = "Report \\ <draft> & [2019] | `v2` final copy";

    /** A label carrying a literal backslash beside a pipe, the case ADR-134 §4 composes its rule from. */
    private static final String A_BACKSLASH_BESIDE_A_PIPE = "Retrofits \\| Phase 2";

    /** That label in a cell: the backslash escaped first, so the pipe's own escape cannot merge with it. */
    private static final String THAT_LABEL_IN_A_CELL = "Retrofits \\\\\\| Phase 2";

    /** A no-break space and a line separator: whitespace to Unicode, and not to ADR-134 §3's class. */
    private static final String UNICODE_SPACES_ONLY = "a b c";

    /** The characters every Markdown row escapes, the backslash first because each row inserts one. */
    private static final Set<Character> THE_SHARED_ESCAPES = Set.of('\\', '<', '&', '[', ']', '`');

    @Test
    @Story("Each surrounding is one row of one table")
    @DisplayName("There are three Markdown surroundings, the table cell, the ATX heading and the membership entry, and no fourth")
    void hasThreeRowsAndNoFourth() {
        claim(
                "the table has one constant per Markdown text position a value is written into -- the cell, the"
                        + " heading and the membership entry (ADR-138 §5) -- and no fourth: a picture's alt text is"
                        + " the membership entry folded first (ADR-149 §5), and the index's link text is the cell"
                        + " (ADR-138), so neither is a row of its own",
                () -> assertThat(List.of(MarkdownSurroundings.values()))
                        .containsExactly(
                                MarkdownSurroundings.TABLE_CELL,
                                MarkdownSurroundings.ATX_HEADING,
                                MarkdownSurroundings.MEMBERSHIP_ENTRY));
    }

    @Test
    @Story("Each surrounding is one row of one table")
    @DisplayName("Each row says whether it folds and what it escapes, the backslash first, and only the cell escapes the pipe")
    void eachRowStatesItsFoldAndItsEscapes() {
        claim(
                "the cell and the heading fold a value onto one line, because a break ends a table row or a"
                        + " heading; the membership entry does not, because the path it shows is the archive's"
                        + " own spelling (ADR-138 §5)",
                () -> assertThat(List.of(
                                MarkdownSurroundings.TABLE_CELL.folds(),
                                MarkdownSurroundings.ATX_HEADING.folds(),
                                MarkdownSurroundings.MEMBERSHIP_ENTRY.folds()))
                        .containsExactly(true, true, false));
        for (MarkdownSurroundings row : MarkdownSurroundings.values()) {
            claim(
                    "the " + row + " row escapes the backslash before anything else, because the row inserts"
                            + " backslashes of its own and a literal one must not merge with them (ADR-134 §4)",
                    () -> assertThat(row.escaped()).startsWith("\\"));
        }
        claim(
                "the cell escapes the six characters every row escapes and the pipe, which only a table cell"
                        + " reads as markup",
                () -> assertThat(charactersOf(MarkdownSurroundings.TABLE_CELL.escaped()))
                        .containsExactlyInAnyOrder('\\', '<', '&', '[', ']', '`', '|'));
        claim(
                "the heading escapes exactly the six characters every row escapes, and not the pipe",
                () -> assertThat(charactersOf(MarkdownSurroundings.ATX_HEADING.escaped()))
                        .containsExactlyInAnyOrderElementsOf(THE_SHARED_ESCAPES));
        claim(
                "the membership entry escapes exactly the six characters every row escapes, and not the pipe",
                () -> assertThat(charactersOf(MarkdownSurroundings.MEMBERSHIP_ENTRY.escaped()))
                        .containsExactlyInAnyOrderElementsOf(THE_SHARED_ESCAPES));
    }

    @Test
    @Story("Each surrounding is one row of one table")
    @DisplayName("The heading and the membership entry escape the same characters and are still two rows, because one folds")
    void keepsTheHeadingAndTheEntryApartThoughTheirEscapesConverge() {
        claim(
                "the two rows escape the same set: ADR-138 §5 records that convergence as a result",
                () -> assertThat(charactersOf(MarkdownSurroundings.ATX_HEADING.escaped()))
                        .isEqualTo(charactersOf(MarkdownSurroundings.MEMBERSHIP_ENTRY.escaped())));
        claim(
                "and not as a merger: the same value comes out of them differently, because the heading folds"
                        + " and the entry does not, so merging them would either break a heading at a line break or"
                        + " show a path the archive does not hold",
                () -> assertThat(MarkdownSurroundings.ATX_HEADING.escape(A_HOSTILE_VALUE))
                        .isNotEqualTo(MarkdownSurroundings.MEMBERSHIP_ENTRY.escape(A_HOSTILE_VALUE)));
    }

    @Test
    @Story("A value is escaped for the surrounding it lands in")
    @DisplayName("One hostile value comes out of each row exactly as the deliverable wrote it before the move")
    void escapesOneHostileValueAsEachRowDid() {
        claim(
                "in a table cell the value is folded onto one line, trimmed, and its backslash, angle bracket,"
                        + " ampersand, brackets, pipe and backtick are each written behind a backslash, as"
                        + " Deliverable.inACell wrote it",
                () -> assertThat(MarkdownSurroundings.TABLE_CELL.escape(A_HOSTILE_VALUE)).isEqualTo(IN_A_TABLE_CELL));
        claim(
                "in an ATX heading the value is folded and escaped the same way, the pipe left as it is, as"
                        + " Deliverable.inAHeading wrote it",
                () -> assertThat(MarkdownSurroundings.ATX_HEADING.escape(A_HOSTILE_VALUE))
                        .isEqualTo(IN_AN_ATX_HEADING));
        claim(
                "in a membership entry the value is escaped like the heading and keeps its breaks, its tab and its"
                        + " outer whitespace, as Deliverable.escapeLinkText wrote it",
                () -> assertThat(MarkdownSurroundings.MEMBERSHIP_ENTRY.escape(A_HOSTILE_VALUE))
                        .isEqualTo(IN_A_MEMBERSHIP_ENTRY));
    }

    @Test
    @Story("A value is escaped for the surrounding it lands in")
    @DisplayName("A backslash beside a pipe in a cell is escaped first, so the pipe stays escaped")
    void escapesTheBackslashBeforeThePipe() {
        claim(
                "'" + A_BACKSLASH_BESIDE_A_PIPE + "' in a cell becomes an escaped backslash followed by an escaped"
                        + " pipe; without the backslash first it would be an escaped backslash and a live pipe,"
                        + " which ends the cell (ADR-134 §4)",
                () -> assertThat(MarkdownSurroundings.TABLE_CELL.escape(A_BACKSLASH_BESIDE_A_PIPE))
                        .isEqualTo(THAT_LABEL_IN_A_CELL));
    }

    @Test
    @Story("A line break in a value is folded to a space")
    @DisplayName("Folding turns every run of ASCII whitespace into one space and trims the ends")
    void foldsEveryRunOfAsciiWhitespace() {
        claim(
                "the line break, the blank line and the tab each become one space with whatever whitespace is"
                        + " beside them, the ends are trimmed, and nothing else is touched, as"
                        + " Deliverable.onOneLine folded it",
                () -> assertThat(MarkdownSurroundings.onOneLine(A_HOSTILE_VALUE)).isEqualTo(ON_ONE_LINE));
    }

    @Test
    @Story("A line break in a value is folded to a space")
    @DisplayName("Folding leaves a no-break space and a line separator alone, because the whitespace class is ASCII")
    void leavesUnicodeSpacesAlone() {
        claim(
                "a no-break space and a Unicode line separator are not folded: ADR-134 §3 decided the class is"
                        + " ASCII-only, and widens it to a character only when a renderer is shown to break on it",
                () -> assertThat(MarkdownSurroundings.onOneLine(UNICODE_SPACES_ONLY)).isEqualTo(UNICODE_SPACES_ONLY));
    }

    @Test
    @Story("A line break in a value is folded to a space")
    @DisplayName("A row that folds gives what folding then escaping gives, and the entry gives what escaping alone gives")
    void foldsBeforeItEscapes() {
        claim(
                "a row that folds folds first: the cell's answer is the cell's escapes applied to the folded value",
                () -> assertThat(MarkdownSurroundings.TABLE_CELL.escape(A_HOSTILE_VALUE))
                        .isEqualTo(MarkdownSurroundings.TABLE_CELL.escape(MarkdownSurroundings.onOneLine(A_HOSTILE_VALUE))));
        claim(
                "a picture's alt text, the membership entry's rule applied to the folded caption (ADR-149 §5), is"
                        + " what the heading gives for the same caption, which is how the deliverable wrote it",
                () -> assertThat(MarkdownSurroundings.MEMBERSHIP_ENTRY.escape(MarkdownSurroundings.onOneLine(A_HOSTILE_VALUE)))
                        .isEqualTo(IN_AN_ATX_HEADING));
    }

    private static Set<Character> charactersOf(String escaped) {
        return escaped.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
    }
}
