package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the Preflight section says of a lock's holder (ADR-440 §3): {@code <who> (pid <pid>) since <time>}, who read off
 * the first word of the line's {@code command=} field that is no option, the time the line's start at the zone of the
 * moment given, with its date where that start fell on another day there; a line not of the jar's form is a process
 * whose line cannot be read.
 *
 * <p>The lines are written as the jar writes them, {@code pid=<n> started=<ISO-8601 with offset> command=<arguments>},
 * the corpus root's with {@code root=} and {@code working-directory=} after them; their folders are made up and name
 * nothing on disk.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("924")
@Link(name = "ADR-440", url = Adr.THE_PREFLIGHT_IS_THE_FOLDERS_AND_THEIR_HOLDERS_A_HOLDER_SAID_ONCE_IN_WORDS, type = "adr")
class HolderWordsTest {

    /** The zone the words are read in, an hour east of UTC in November. */
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    /** The moment the words are drawn at: half past eleven at night, 20 November, in Rome. */
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 20, 23, 30, 0, 0, ROME);

    /** A start nineteen minutes past eleven that night, written at Rome's own offset. */
    private static final String STARTED_TONIGHT = "2026-11-20T23:19:04+01:00";

    private static final long PID = 44072;

    private static String line(String command) {
        return "pid=" + PID + " started=" + STARTED_TONIGHT + command;
    }

    @Test
    @Story("The Preflight section says a holder in words")
    @DisplayName("Who holds is vespera and the command's first word that is no option, another control panel for a control panel's write, and another Vespera for anything else")
    void whoIsTheCommandsFirstWordThatIsNoOption() {
        List<String> said = List.of(
                HolderWords.of(line(" command=run /a/corpus --db-dir=/a/working root=/a/corpus"
                        + " working-directory=/a/working"), NOW),
                HolderWords.of(line(" command=--headless label --auto --db-dir=/a/working"), NOW),
                HolderWords.of(line(" command=publish --to website --db-dir=/a/working"), NOW),
                HolderWords.of(line(" command=control-panel write profile.yaml"), NOW),
                HolderWords.of(line(" command=serve --db-dir=/a/working"), NOW),
                HolderWords.of(line(" command="), NOW),
                HolderWords.of(line(""), NOW));

        claim("run, label behind an option, publish, a control panel's write, another command, an empty command and no"
                        + " command field are said, in that order, by who, the pid and the time",
                () -> assertThat(said).containsExactly(
                        "vespera run (pid 44072) since 23:19",
                        "vespera label (pid 44072) since 23:19",
                        "vespera publish (pid 44072) since 23:19",
                        "another control panel (pid 44072) since 23:19",
                        "another Vespera (pid 44072) since 23:19",
                        "another Vespera (pid 44072) since 23:19",
                        "another Vespera (pid 44072) since 23:19"));
    }

    @Test
    @Story("The Preflight section says a holder in words")
    @DisplayName("The time is the start at the zone of the moment given, HH:mm on the same day there and with its date on another")
    void theTimeIsTheStartAtTheSystemsZoneWithTheDateWhereItIsAnotherDay() {
        String sameDayAtAnotherOffset = HolderWords.of(
                "pid=7 started=2026-11-20T22:19:04Z command=run /a/corpus", NOW);
        String dayBefore = HolderWords.of(
                "pid=7 started=2026-11-19T22:05:00Z command=run /a/corpus", NOW);
        String lateTheDayBeforeInUtcAndAfterMidnightInRome = HolderWords.of(
                "pid=7 started=2026-11-19T23:10:00Z command=run /a/corpus", NOW);

        claim("a start written in UTC that falls on the same day in Rome is said by its time in Rome alone",
                () -> assertThat(sameDayAtAnotherOffset).isEqualTo("vespera run (pid 7) since 23:19"));
        claim("a start the day before in Rome is said with its date and its time there",
                () -> assertThat(dayBefore).isEqualTo("vespera run (pid 7) since 2026-11-19 23:05"));
        claim("and the day is Rome's, not the line's offset's: 23:10 UTC on the 19th is 00:10 on the 20th in Rome, the"
                        + " same day as the moment given",
                () -> assertThat(lateTheDayBeforeInUtcAndAfterMidnightInRome)
                        .isEqualTo("vespera run (pid 7) since 00:10"));
    }

    @Test
    @Story("The Preflight section says a holder in words")
    @DisplayName("A line not of the jar's form is a process whose line cannot be read")
    void aLineNotOfTheFormIsAProcessWhoseLineCannotBeRead() {
        List<String> said = List.of(
                HolderWords.of("held, by whom it does not say", NOW),
                HolderWords.of("pid=seven started=" + STARTED_TONIGHT + " command=run", NOW),
                HolderWords.of("pid=7 started=yesterday command=run", NOW));

        claim("a line of no fields, a pid that is no number and a start that is no instant are each said alike",
                () -> assertThat(said).containsOnly("a process whose line cannot be read").hasSize(3));
    }
}
