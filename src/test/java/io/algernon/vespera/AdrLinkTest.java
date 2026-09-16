package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-052's link from a test to the decision it exists because of, checked end to end: the label a
 * reader sees, the constant in {@link Adr} it was written beside, and the file that constant opens.
 *
 * <p>Three ways the chain comes apart, and the compiler notices none of them: a constant naming a
 * file no longer under {@code docs/adr/}, a {@code @Link} labelled with one id while its constant
 * opens another, and a javadoc line in {@link Adr} naming an id its own filename does not. Every one
 * of those still compiles and every affected test still passes, so the only symptom is a report
 * reader following a link to the wrong decision or to nothing -- and ADR-052 says that reader has no
 * access to this repository, which makes them exactly the person who cannot tell.
 *
 * <p><b>A renumber is what breaks it.</b> Two branches took ADR-120 at once; one record had to
 * become ADR-121, and a blanket rename over the merge text relabelled six links and three javadoc
 * lines belonging to the other decision. It was caught by a script written for one review. This is
 * that script, kept.
 *
 * <p>Source rather than reflection, for all three claims, and one mechanism on purpose. The javadoc
 * claim cannot be reached any other way -- a doc comment does not survive compilation -- and the
 * constant a {@code @Link} was written with does not survive it either, because the compiler inlines
 * its value. Reflection would see a URL and have to guess its way back to a constant name; reading
 * the source names the constant the way the person who has to fix it wrote it.
 *
 * <p>Nothing here names a class to look in. Every {@code .java} under the test source tree is read,
 * so a link on a class and a link on a single test are found alike, and a test class added tomorrow
 * is covered without editing this one. That both positions are really reached is claimed rather than
 * assumed, because a reading that had quietly stopped finding one of them would pass everything else.
 *
 * <p>This class carries a link of its own, and that is deliberate rather than circular: the link is
 * one of the links it reads, so mislabelling it fails here like any other. What it does not do is
 * treat its own link as evidence of anything -- it is a check over literals, and its literal answers
 * to the same rule as everyone else's.
 */
@Epic("Architecture")
@Feature("Decision links")
@Link(name = "ADR-052", url = Adr.THE_REPORT_IS_WRITTEN_FOR_AN_OUTSIDE_READER, type = "adr")
@Issue("210")
class AdrLinkTest {

    /** Where the decisions themselves live, and the only place a link is allowed to point. */
    private static final Path DECISION_RECORDS = Path.of("docs", "adr");

    /** The whole test source tree, so that no list of classes has to be kept in step with it. */
    private static final Path TEST_SOURCE = Path.of("src", "test", "java");

    /** The id-to-file map itself, read as text because its javadoc is one third of what is checked. */
    private static final Path DECISION_MAP = TEST_SOURCE.resolve(Path.of("io", "algernon", "vespera", "Adr.java"));

    /**
     * One entry of the map: its javadoc, its constant, its filename.
     *
     * <p>The javadoc group is tempered so that it cannot swallow a comment terminator and reach back
     * to an earlier comment. Without that, the first match would begin at the class javadoc and the id
     * read out of it would belong to whichever decision that prose happens to mention.
     *
     * <p>The filename is matched across whatever whitespace the formatter left, because a long one is
     * wrapped onto the line below its constant and a pattern insisting on a single space reads two
     * thirds of the map and calls it whole.
     */
    private static final Pattern MAPPED_DECISION = Pattern.compile(
            "/\\*\\*((?:(?!\\*/).)*)\\*/\\s+public static final String (\\w+)\\s*=\\s*FILE\\s*\\+\\s*\"([^\"]+)\";",
            Pattern.DOTALL);

    /** Every constant the map declares, whether or not it was readable as a whole entry above. */
    private static final Pattern DECLARED_CONSTANT = Pattern.compile("public static final String (\\w+)");

    /** A decision id as a person writes it, in prose or in a label. */
    private static final Pattern ID_IN_TEXT = Pattern.compile("ADR-(\\d+)");

    /** A decision id as a filename carries it: the digits it opens with, and nothing else counts. */
    private static final Pattern ID_IN_FILENAME = Pattern.compile("^(\\d+)-");

    /** A link annotation, whose arguments are then picked out by name rather than by their order. */
    private static final Pattern LINK = Pattern.compile("^@Link\\((.*)\\)$");

    private static final Pattern LINK_LABEL = Pattern.compile("name\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern LINK_CONSTANT = Pattern.compile("url\\s*=\\s*Adr\\.(\\w+)");

    private static final Pattern LINK_KIND = Pattern.compile("type\\s*=\\s*\"([^\"]*)\"");

    /** The kind of link this check is about; the report resolves it to a decision. */
    private static final String DECISION_KIND = "adr";

    /** What a line declaring a type says, which is how a link above one is told from a link above a test. */
    private static final Pattern TYPE_DECLARATION = Pattern.compile("\\b(class|interface|enum|record)\\b");

    private static final String JAVA_SOURCE = ".java";

    @Test
    @Story("A decision link opens a record that is there")
    @DisplayName("Every entry in the decision map names a file that exists")
    void everyMappedDecisionExists() {
        List<MappedDecision> mapped = mappedDecisions();
        List<String> declared = declaredConstants();
        List<String> missing = mapped.stream()
                .filter(decision -> !Files.exists(DECISION_RECORDS.resolve(decision.filename())))
                .map(decision -> decision.constant() + " names " + decision.filename() + ", which is not there")
                .sorted()
                .toList();

        claim(
                "the map was read whole -- as many entries came back as it declares constants, both counts"
                        + " taken from the map itself rather than written down here, so an entry whose comment"
                        + " or whose address could not be made sense of fails now rather than being quietly"
                        + " left out of the claim below",
                () -> assertThat(mapped).hasSameSizeAs(declared));
        claim(
                "and every entry names a file that is present among the decisions, so a label a report"
                        + " reader follows lands on a decision rather than on nothing; an entry named here is"
                        + " one whose decision was renamed or removed after the link to it was written",
                () -> assertThat(missing).isEmpty());
    }

    @Test
    @Story("A decision link's label names the decision it opens")
    @DisplayName("Every decision link is labelled with the id of the file it points at")
    void everyLinkLabelMatchesTheDecisionItOpens() throws IOException {
        List<MappedDecision> mapped = mappedDecisions();
        List<DecisionLink> links = decisionLinks();
        List<Position> positions =
                links.stream().map(DecisionLink::position).distinct().toList();
        List<String> mislabelled = links.stream()
                .map(link -> disagreementIn(link, mapped))
                .flatMap(Optional::stream)
                .sorted()
                .toList();

        claim(
                "the reading found decision links in both of the places one can be written -- above a class"
                        + " and above a single test -- so a reading that had stopped seeing one of the two"
                        + " positions fails now rather than passing the claim below having checked half of"
                        + " what is written",
                () -> assertThat(positions).containsExactlyInAnyOrder(Position.ON_A_TYPE, Position.ON_A_TEST));
        claim(
                "and every link's label carries the id of the file its address opens, so a reader who"
                        + " follows the label arrives at the decision the label promised; a link named here"
                        + " says one decision and opens another, which no reader of the report can detect",
                () -> assertThat(mislabelled).isEmpty());
    }

    @Test
    @Story("The decision map's comments agree with the files beneath them")
    @DisplayName("Every entry's comment names the id in the filename it maps to")
    void everyCommentMatchesItsFilename() {
        List<MappedDecision> mapped = mappedDecisions();
        List<String> disagreeing = mapped.stream()
                .filter(decision -> idIn(decision.javadoc()) != idOf(decision.filename()))
                .map(decision -> decision.constant() + " is described as " + written(idIn(decision.javadoc()))
                        + " but names " + decision.filename() + ", which is " + written(idOf(decision.filename())))
                .sorted()
                .toList();

        claim(
                "the map had entries to check at all, so a reading that came back empty -- a moved source"
                        + " root, a shape it no longer recognises -- fails now instead of satisfying the claim"
                        + " below by having looked at nothing",
                () -> assertThat(mapped).isNotEmpty());
        claim(
                "and the comment above each entry names the same decision as the filename beneath it, so"
                        + " somebody choosing a constant by reading the comments picks the decision they meant;"
                        + " an entry named here is one a renumber relabelled on one side only",
                () -> assertThat(disagreeing).isEmpty());
    }

    /** What one link disagrees about, naming the constant and both ids, or nothing when it agrees. */
    private static Optional<String> disagreementIn(DecisionLink link, List<MappedDecision> mapped) {
        MappedDecision opened = mapped.stream()
                .filter(decision -> decision.constant().equals(link.constant()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("a link at " + link.where() + " names " + link.constant()
                        + ", which the decision map does not declare as a readable entry"));
        int labelled = idIn(link.label());
        int opens = idOf(opened.filename());
        if (labelled == opens) {
            return Optional.empty();
        }
        return Optional.of(link.where() + " is labelled " + written(labelled) + " but " + link.constant() + " opens "
                + opened.filename() + ", which is " + written(opens));
    }

    /** Every entry of the id-to-file map, read from its source with its own javadoc attached. */
    private static List<MappedDecision> mappedDecisions() {
        Matcher entries = MAPPED_DECISION.matcher(read(DECISION_MAP));
        List<MappedDecision> mapped = new ArrayList<>();
        while (entries.find()) {
            mapped.add(new MappedDecision(entries.group(2), entries.group(3), entries.group(1)));
        }
        return mapped;
    }

    /** Every constant the map declares, read apart so the entry reading has something to answer to. */
    private static List<String> declaredConstants() {
        return DECLARED_CONSTANT
                .matcher(read(DECISION_MAP))
                .results()
                .map(found -> found.group(1))
                .toList();
    }

    /** Every decision link in the test tree, wherever it was written. */
    private static List<DecisionLink> decisionLinks() throws IOException {
        try (Stream<Path> sources = Files.walk(TEST_SOURCE)) {
            return sources.filter(path -> path.toString().endsWith(JAVA_SOURCE))
                    .sorted()
                    .flatMap(source -> linksIn(source).stream())
                    .toList();
        }
    }

    /**
     * The decision links in one compilation unit.
     *
     * <p>An annotation is recognised by the line it opens, which is what keeps the javadoc in {@link
     * Adr} -- where the annotation is quoted as prose -- out of the population. A link spread over
     * more than one line would be missed in silence, so it is refused aloud instead.
     */
    private static List<DecisionLink> linksIn(Path source) {
        List<String> lines = List.of(read(source).split("\\R", -1));
        List<DecisionLink> links = new ArrayList<>();
        for (int at = 0; at < lines.size(); at++) {
            String line = lines.get(at).strip();
            if (!line.startsWith("@Link(")) {
                continue;
            }
            String where = source.getFileName() + ":" + (at + 1);
            Matcher annotation = LINK.matcher(line);
            if (!annotation.matches()) {
                throw new IllegalStateException("a link at " + where
                        + " is written over more than one line, which this check cannot read: " + line);
            }
            String arguments = annotation.group(1);
            if (!DECISION_KIND.equals(argument(LINK_KIND, arguments))) {
                continue;
            }
            String label = argument(LINK_LABEL, arguments);
            String constant = argument(LINK_CONSTANT, arguments);
            if (label == null || constant == null) {
                throw new IllegalStateException("a decision link at " + where
                        + " does not carry both a label and an address from the decision map: " + line);
            }
            links.add(new DecisionLink(where, label, constant, positionOf(lines, at)));
        }
        return links;
    }

    /** What a link was written above: a type, or one test within one. */
    private static Position positionOf(List<String> lines, int annotation) {
        for (int at = annotation + 1; at < lines.size(); at++) {
            String line = lines.get(at).strip();
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("*") || line.startsWith("/")) {
                continue;
            }
            return TYPE_DECLARATION.matcher(line).find() ? Position.ON_A_TYPE : Position.ON_A_TEST;
        }
        throw new IllegalStateException("a link was written with nothing beneath it to belong to");
    }

    private static String argument(Pattern argument, String arguments) {
        Matcher found = argument.matcher(arguments);
        return found.find() ? found.group(1) : null;
    }

    /** The decision an id names, numerically, so that how it is padded is never the difference. */
    private static int idIn(String text) {
        Matcher id = ID_IN_TEXT.matcher(text);
        if (!id.find()) {
            throw new IllegalStateException("no decision id is written in: " + text.strip());
        }
        return Integer.parseInt(id.group(1));
    }

    /**
     * A decision id as this project writes it, three digits wide, so that a failure reads the way the
     * source it sends somebody to does. Compared as a number and reported as text, because the map's
     * filenames pad to four digits and its labels to three.
     */
    private static String written(int id) {
        return "ADR-%03d".formatted(id);
    }

    private static int idOf(String filename) {
        Matcher id = ID_IN_FILENAME.matcher(filename);
        if (!id.find()) {
            throw new IllegalStateException("a filename in the decision map opens with no id: " + filename);
        }
        return Integer.parseInt(id.group(1));
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException couldNotRead) {
            throw new UncheckedIOException("could not read " + source, couldNotRead);
        }
    }

    /** One entry of the id-to-file map: what it is called, what it opens, and what it says it is. */
    private record MappedDecision(String constant, String filename, String javadoc) {}

    /** One written link: where it is, the label a reader sees, and the constant it was written with. */
    private record DecisionLink(String where, String label, String constant, Position position) {}

    /** Where a link was written. Decides nothing, and has to be reached. */
    private enum Position {
        ON_A_TYPE,
        ON_A_TEST
    }
}
