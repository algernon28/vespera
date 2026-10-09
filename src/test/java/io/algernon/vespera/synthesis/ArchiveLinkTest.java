package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where a membership entry's link leads (ADR-135, ADR-137): a path relative to the page's own directory,
 * percent-encoded, or no link at all where no relative path exists. Never an absolute {@code file:}
 * target.
 *
 * <p><b>Every expected destination here is what {@code Deliverable.relativeDestination} wrote at
 * {@code 4b99a03}</b>, for a page four directories below the parent of the archive, as every page of a
 * tree is: its partition, the run's tree, {@code deliverable}, and the working directory.
 */
@Epic("Synthesis")
@Feature("Where a membership entry leads")
@Issue("351")
@Link(name = "ADR-212", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
@Link(name = "ADR-137", url = Adr.A_DESTINATIONS_AMPERSAND_IS_PERCENT_ENCODED, type = "adr")
class ArchiveLinkTest {

    /** The climb from a page to the directory the working directory and the archive share. */
    private static final String FOUR_LEVELS_UP = "../../../../archive/";

    /** A document whose path needs no encoding at all. */
    private static final String A_PLAIN_PATH = "reports/a.docx";

    /**
     * A document whose name carries a space, both parentheses, an ampersand, a hash, both brackets and a
     * percent sign: each one a character a Markdown destination or a URI would read as something else.
     */
    private static final String A_PATH_A_DESTINATION_WOULD_MISREAD = "reports/a b (draft) & notes #1 [x] 100%.pdf";

    /** That path as a destination: the URI's own quoting, plus {@code %28}, {@code %29} and {@code %26}. */
    private static final String THAT_PATH_ENCODED =
            "reports/a%20b%20%28draft%29%20%26%20notes%20%231%20%5Bx%5D%20100%25.pdf";

    /** A name carrying what would be an entity, a tag and a code span if it reached a renderer whole. */
    private static final String A_PATH_CARRYING_MARKUP = "reports/&copy; <x> `t`.pdf";

    /** That path as a destination: no entity, no tag and no backtick survives. */
    private static final String THAT_MARKUP_ENCODED = "reports/%26copy;%20%3Cx%3E%20%60t%60.pdf";

    /** A name outside ASCII, which the destination carries as UTF-8 percent-encoded. */
    private static final String A_PATH_OUTSIDE_ASCII = "relatórios/café.pdf";

    /** That path as a destination. */
    private static final String THAT_PATH_AS_UTF_8 = "relat%C3%B3rios/caf%C3%A9.pdf";

    /** A string no file system takes as a path, because it carries a NUL. */
    private static final String NOT_A_PATH = "archive\u0000root";

    @Test
    @Story("A membership entry links by a path relative to its own page")
    @DisplayName("A page four directories down links to the archive beside the working directory by climbing four")
    void climbsFromThePageToTheArchive(@TempDir Path base) {
        claim(
                "the destination climbs from the page's directory to the archive and descends to the document,"
                        + " so the link resolves on any machine the tree and the archive are moved to together",
                () -> assertThat(ArchiveLink.from(aPageDirectoryUnder(base), theArchiveUnder(base), A_PLAIN_PATH))
                        .contains(FOUR_LEVELS_UP + A_PLAIN_PATH));
    }

    @Test
    @Story("A destination escapes by percent-encoding, because it answers to a resolver")
    @DisplayName("A space, the parentheses, the ampersand, the hash, the brackets and the percent sign are all percent-encoded")
    void percentEncodesWhatADestinationWouldMisread(@TempDir Path base) {
        claim(
                "every character that would end the destination, open a fragment, or read as an entity is"
                        + " percent-encoded, the parentheses and the ampersand on top of the URI's own quoting"
                        + " (ADR-137 §1)",
                () -> assertThat(ArchiveLink.from(
                                aPageDirectoryUnder(base), theArchiveUnder(base), A_PATH_A_DESTINATION_WOULD_MISREAD))
                        .contains(FOUR_LEVELS_UP + THAT_PATH_ENCODED));
    }

    @Test
    @Story("A destination escapes by percent-encoding, because it answers to a resolver")
    @DisplayName("An entity, a tag and a code span in a name reach the destination as percent-encoding, never as a backslash")
    void percentEncodesMarkupRatherThanEscapingIt(@TempDir Path base) {
        claim(
                "the ampersand becomes %26, the angle brackets %3C and %3E and the backtick %60: the fifth"
                        + " surrounding's escape is percent-encoding, not the backslash of the three Markdown"
                        + " text surroundings (ADR-137 §4)",
                () -> assertThat(ArchiveLink.from(aPageDirectoryUnder(base), theArchiveUnder(base), A_PATH_CARRYING_MARKUP))
                        .contains(FOUR_LEVELS_UP + THAT_MARKUP_ENCODED));
    }

    @Test
    @Story("A destination escapes by percent-encoding, because it answers to a resolver")
    @DisplayName("A name outside ASCII is percent-encoded as UTF-8")
    void percentEncodesANameOutsideAsciiAsUtf8(@TempDir Path base) {
        claim(
                "an accented letter becomes its UTF-8 bytes, percent-encoded, which every renderer resolves",
                () -> assertThat(ArchiveLink.from(aPageDirectoryUnder(base), theArchiveUnder(base), A_PATH_OUTSIDE_ASCII))
                        .contains(FOUR_LEVELS_UP + THAT_PATH_AS_UTF_8));
    }

    @Test
    @Story("A membership entry links by a path relative to its own page")
    @DisplayName("A page in the archive's own directory links with no climb, and one below it climbs only as far as it is")
    void climbsOnlyAsFarAsThePageIsBelowTheArchive(@TempDir Path base) {
        Path archive = base.resolve("archive");
        claim(
                "a page sitting in the archive's own directory links to the document by its path alone, encoded",
                () -> assertThat(ArchiveLink.from(archive, archive.toString(), "a b.pdf")).contains("a%20b.pdf"));
        claim(
                "a page two directories below the archive climbs two",
                () -> assertThat(ArchiveLink.from(archive.resolve("wd").resolve("x"), archive.toString(), "a.pdf"))
                        .contains("../../a.pdf"));
    }

    @Test
    @Story("Where no relative path exists, the entry carries no link")
    @DisplayName("A page directory or an archive given as a relative path gives no destination")
    void givesNoDestinationWhereEitherSideIsRelative(@TempDir Path base) {
        claim(
                "a page directory that is not absolute has no route to the archive the entry could be sure of,"
                        + " so there is no link rather than a guessed one (ADR-135)",
                () -> assertThat(ArchiveLink.from(
                                Path.of("deliverable", "run", "1-seed"), theArchiveUnder(base), A_PLAIN_PATH))
                        .isEmpty());
        claim(
                "an archive recorded as a relative path gives no link either",
                () -> assertThat(ArchiveLink.from(aPageDirectoryUnder(base), "archive", A_PLAIN_PATH)).isEmpty());
    }

    @Test
    @Story("Where no relative path exists, the entry carries no link")
    @DisplayName("An archive recorded as a string no file system takes as a path gives no destination, and throws nothing")
    void givesNoDestinationForAnArchiveThatIsNotAPath(@TempDir Path base) {
        claim(
                "a recorded archive that cannot be read as a path gives no link and no exception: a throw here"
                        + " would leave the writer and roll back every fault row the invocation had recorded",
                () -> assertThat(ArchiveLink.from(aPageDirectoryUnder(base), NOT_A_PATH, A_PLAIN_PATH)).isEmpty());
    }

    /** A page's directory as every tree has one: a partition, a run's tree, the deliverable, the working directory. */
    private static Path aPageDirectoryUnder(Path base) {
        return base.resolve("wd").resolve("deliverable").resolve("run").resolve("1-seed").toAbsolutePath();
    }

    /** The archive, beside the working directory. */
    private static String theArchiveUnder(Path base) {
        return base.resolve("archive").toAbsolutePath().toString();
    }
}
