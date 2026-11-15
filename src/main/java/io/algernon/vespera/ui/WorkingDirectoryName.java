package io.algernon.vespera.ui;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The name the window proposes for a new working directory, and the warning where Run starts one named for another
 * corpus root (ADR-393 §2, §3), in {@code java.base} alone (ADR-285 §3). The name is the working directory's folder's
 * name: nothing stores it, and nothing locates a working directory by it.
 */
public final class WorkingDirectoryName {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** §3's form: {@code <X>_<yyyyMMdd>} or {@code <X>_<yyyyMMdd>-<n>}. */
    private static final Pattern NAMED_FOR = Pattern.compile("^(.+)_([0-9]{8})(-[0-9]+)?$");

    private WorkingDirectoryName() {
    }

    /**
     * {@code parent.resolve(the name)}: the corpus root's folder name, {@code _} and the date of {@code now} in
     * {@code zone} as {@code yyyyMMdd}, or the date alone where the corpus root is not named or has no last element;
     * where an entry of that name is in {@code parent}, a folder, a file or a link, the first of {@code -2}, {@code -3}
     * and on that is free.
     */
    public static Path proposed(Path parent, Optional<Path> corpusRoot, Instant now, ZoneId zone) {
        String day = DAY.format(now.atZone(zone));
        String name = rootsFolderName(corpusRoot).map(folder -> folder + "_" + day).orElse(day);
        Path candidate = parent.resolve(name);
        for (int number = 2; !Files.notExists(candidate, LinkOption.NOFOLLOW_LINKS); number++) {
            candidate = parent.resolve(name + "-" + number);
        }
        return candidate;
    }

    /**
     * The one line Run adds where {@code workingDirectory}'s name is of §3's form and names a corpus root other than
     * {@code corpusRoot}'s folder name, case ignored; empty otherwise.
     */
    public static Optional<String> warningToRun(Path workingDirectory, Path corpusRoot) {
        Path last = workingDirectory.getFileName();
        if (last == null) {
            return Optional.empty();
        }
        String name = last.toString();
        Matcher form = NAMED_FOR.matcher(name);
        Optional<String> rootsName = rootsFolderName(Optional.of(corpusRoot));
        if (!form.matches() || rootsName.isEmpty() || form.group(1).equalsIgnoreCase(rootsName.get())) {
            return Optional.empty();
        }
        return Optional.of("the working directory " + name + " is named for a corpus root named " + form.group(1)
                + ", and Run starts it on " + corpusRoot);
    }

    private static Optional<String> rootsFolderName(Optional<Path> corpusRoot) {
        return corpusRoot.map(Path::getFileName).map(Path::toString).filter(name -> !name.isEmpty());
    }
}
