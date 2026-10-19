package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.KeysetPages;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceAtPath;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stage 1's set pass, between the first pass and the hashing, and {@code corpus}'s own record of the code sets
 * it finds (ADR-284): the {@code code_set} and {@code code_set_member} tables, kept under the run that wrote
 * them as {@link DetectedFormats} keeps its own.
 *
 * <p>What it holds is folders: the folders holding a build file, a marked page or a companion file, and the
 * sets. It reads the walk's occurrences and their verdicts a page at a time and keeps no occurrence between
 * pages (ADR-220); the first pass's hand-over, {@link MarkedFolders}, is folders too, and a marked page's
 * generator is read again from its bytes when its member row is written.
 */
public class CodeSets {

    private final JdbcTemplate jdbcTemplate;
    private final DetectedFormats detectedFormats;

    public CodeSets(JdbcTemplate jdbcTemplate, DetectedFormats detectedFormats) {
        this.jdbcTemplate = jdbcTemplate;
        this.detectedFormats = detectedFormats;
    }

    /** What the set pass tells its caller as it goes. */
    public interface Progress {

        /** After a file's {@code code} verdict is written by the set pass, with its reason. */
        void kept(OccurrenceId occurrence, String reason);

        /** A file that could not be read to see whether it reads as text, so it is not touched. */
        default void unreadable(OccurrenceId occurrence, Exception cause) {}

        /** A marked page that could not be read again to see which generator's mark it carries. */
        default void markUnreadable(OccurrenceId occurrence, Exception cause) {}
    }

    /**
     * What the set pass found, for the format-mix page: the sets, and their members by role. A companion is
     * counted under the generator of its tree in {@code companionsByGenerator}.
     */
    public record Tally(int sets, int code, int build, int companion, Map<String, Integer> companionsByGenerator) {}

    /** The members' roles, as the table stores them. */
    private static final String CODE = "code";

    private static final String COMPANION = "companion";
    private static final String BUILD = "build";

    /**
     * Finds the sets of {@code runId}'s walk, verdicts the build files and tree companions kept with them as
     * {@code code}, and records the sets and members. Only occurrences the first pass left surviving are
     * touched; {@code markedFolders} is the first pass's hand-over, and {@code codeEndings} are the endings
     * {@code codeNameEndings} lists, lower case.
     */
    public Tally pass(
            Ledger ledger,
            RunId runId,
            Path canonicalRoot,
            MarkedFolders markedFolders,
            List<String> codeEndings,
            Progress progress) {
        WalkId walk = ledger.runs().walkOf(runId).orElseThrow(() -> new IllegalStateException(
                "run " + runId.value() + " is recorded against no walk"));
        Found found = new Found();
        eachPage(ledger, walk, page -> {
            Set<OccurrenceId> code = ledger.verdicts().carryingAmong(runId, VerdictKind.CODE, ids(page));
            for (OccurrenceAtPath occurrence : page) {
                String folder = folderOf(occurrence.path());
                String name = nameOf(occurrence.path());
                if (!folder.isEmpty() && BuildFiles.isBuildFile(name)) {
                    found.buildFolders.add(folder);
                }
                for (String generator : GeneratorCompanions.generatorsOf(name)) {
                    found.companionFolders.computeIfAbsent(generator, g -> new HashSet<>()).add(folder);
                }
                if (code.contains(occurrence.occurrenceId())) {
                    found.codeFolders.add(folder);
                }
            }
        });
        found.findTrees(markedFolders);
        found.codeFolders.forEach(folder -> found.setFolders.add(found.anchorOf(folder)));
        if (!found.treeRoots.isEmpty()) {
            eachPage(ledger, walk, page -> {
                for (OccurrenceAtPath occurrence : survivorsOf(ledger, runId, page)) {
                    String folder = folderOf(occurrence.path());
                    if (found.setFolders.contains(found.anchorOf(folder))) {
                        continue;
                    }
                    if (companionGenerator(found, runId, canonicalRoot, occurrence, progress).isPresent()) {
                        found.setFolders.add(found.anchorOf(folder));
                    }
                }
            });
        }
        String rootName = rootNameOf(canonicalRoot);
        for (String folder : found.setFolders) {
            jdbcTemplate.update(
                    "INSERT INTO code_set (run_id, folder, default_name) VALUES (?, ?, ?)",
                    runId.value(),
                    folder,
                    folder.isEmpty() ? rootName : folder.substring(folder.lastIndexOf('/') + 1));
        }
        int[] counts = new int[3];
        Map<String, Integer> byGenerator = new TreeMap<>();
        eachPage(ledger, walk, page -> {
            Set<OccurrenceId> code = ledger.verdicts().carryingAmong(runId, VerdictKind.CODE, ids(page));
            List<OccurrenceAtPath> survivors = survivorsOf(ledger, runId, page);
            for (OccurrenceAtPath occurrence : page) {
                if (code.contains(occurrence.occurrenceId())) {
                    String set = found.extentOf(folderOf(occurrence.path()));
                    if (set != null) {
                        member(occurrence.occurrenceId(), runId, set, CODE,
                                markOf(runId, canonicalRoot, occurrence, markedFolders, codeEndings, progress));
                        counts[0]++;
                    }
                }
            }
            for (OccurrenceAtPath occurrence : survivors) {
                String set = found.extentOf(folderOf(occurrence.path()));
                if (set == null) {
                    continue;
                }
                Optional<String> generator = companionGenerator(found, runId, canonicalRoot, occurrence, progress);
                if (generator.isPresent()) {
                    String reason = "a file of the tree " + generator.get() + " generated";
                    keep(ledger, runId, occurrence.occurrenceId(), reason, progress);
                    member(occurrence.occurrenceId(), runId, set, COMPANION, generator.get());
                    counts[2]++;
                    byGenerator.merge(generator.get(), 1, Integer::sum);
                } else if (BuildFiles.isBuildFile(nameOf(occurrence.path()))
                        && readsAsText(runId, canonicalRoot, occurrence, progress)) {
                    keep(ledger, runId, occurrence.occurrenceId(),
                            "a build file of the code set at " + (set.isEmpty() ? "the corpus root" : set), progress);
                    member(occurrence.occurrenceId(), runId, set, BUILD, null);
                    counts[1]++;
                }
            }
        });
        return new Tally(found.setFolders.size(), counts[0], counts[1], counts[2], byGenerator);
    }

    /**
     * Deletes the run's rows of {@code code_set} and {@code code_set_member}: the discard half of ADR-115 and
     * ADR-116, for a stage 1 whose completion under this run is not recorded.
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM code_set_member WHERE run_id = ?", runId.value());
        jdbcTemplate.update("DELETE FROM code_set WHERE run_id = ?", runId.value());
    }

    /**
     * The folder of the set {@code path} lies in under {@code stageOneRun}, the run a later stage names upstream,
     * or none: the deepest set folder at or above the occurrence's own, the root's set covering only the files
     * lying directly at the root (ADR-284 section 3).
     */
    public Optional<String> setOf(RunId stageOneRun, OccurrencePath path) {
        String folder = folderOf(path);
        while (true) {
            if (isSet(stageOneRun, folder)) {
                return Optional.of(folder);
            }
            if (folder.isEmpty() || folder.indexOf('/') < 0) {
                return Optional.empty();
            }
            folder = folder.substring(0, folder.lastIndexOf('/'));
        }
    }

    /**
     * The generator whose mark a member as code carries, read again from its bytes (section 5): only for a page
     * lying in a folder handed over for some generator, whose name ends with no listed ending. None where no
     * mark is named, and, said to {@code progress}, where the page cannot be read.
     */
    private static String markOf(
            RunId runId,
            Path canonicalRoot,
            OccurrenceAtPath occurrence,
            MarkedFolders markedFolders,
            List<String> codeEndings,
            Progress progress) {
        if (!markedFolders.holds(folderOf(occurrence.path()))
                || Code.endsWithAny(nameOf(occurrence.path()), codeEndings)) {
            return null;
        }
        try {
            return Code.markOf(canonicalRoot.resolve(occurrence.path().value())).orElse(null);
        } catch (IOException e) {
            progress.markUnreadable(occurrence.occurrenceId(), e);
            return null;
        }
    }

    private boolean isSet(RunId runId, String folder) {
        return !jdbcTemplate
                .queryForList("SELECT 1 FROM code_set WHERE run_id = ? AND folder = ?", runId.value(), folder)
                .isEmpty();
    }

    void member(OccurrenceId occurrence, RunId runId, String folder, String role, String generator) {
        jdbcTemplate.update(
                "INSERT INTO code_set_member (occurrence_id, run_id, folder, role, generator) VALUES (?, ?, ?, ?, ?)",
                occurrence.value(),
                runId.value(),
                folder,
                role,
                generator);
    }

    private static void keep(Ledger ledger, RunId runId, OccurrenceId occurrence, String reason, Progress progress) {
        ledger.verdicts().verdict(occurrence, runId, VerdictKind.CODE, reason);
        progress.kept(occurrence, reason);
    }

    /**
     * The generator of the tree {@code occurrence} lies in, where it is a file kept with the tree: detected
     * {@code IMAGE}, or text of no subtype or of no known kind whose bytes read as text (section 5).
     */
    private Optional<String> companionGenerator(
            Found found, RunId runId, Path canonicalRoot, OccurrenceAtPath occurrence, Progress progress) {
        Optional<String> generator = found.treeOf(folderOf(occurrence.path()));
        if (generator.isEmpty()) {
            return Optional.empty();
        }
        Optional<DetectedFormat> format = detectedFormats.formatFor(occurrence.occurrenceId(), runId);
        if (format.isEmpty()) {
            return Optional.empty();
        }
        if (format.get() == DetectedFormat.IMAGE) {
            return generator;
        }
        boolean text = (format.get() == DetectedFormat.PLAIN_TEXT
                        && detectedFormats.subtypeFor(occurrence.occurrenceId(), runId).isEmpty())
                || format.get() == DetectedFormat.UNRECOGNISED;
        return text && readsAsText(runId, canonicalRoot, occurrence, progress) ? generator : Optional.empty();
    }

    /** Whether {@code occurrence} is detected as text of either class and its bytes read as text. */
    private boolean readsAsText(RunId runId, Path canonicalRoot, OccurrenceAtPath occurrence, Progress progress) {
        Optional<DetectedFormat> format = detectedFormats.formatFor(occurrence.occurrenceId(), runId);
        if (format.isEmpty()
                || (format.get() != DetectedFormat.PLAIN_TEXT && format.get() != DetectedFormat.UNRECOGNISED)) {
            return false;
        }
        try {
            return Code.readsAsText(canonicalRoot.resolve(occurrence.path().value()));
        } catch (IOException e) {
            progress.unreadable(occurrence.occurrenceId(), e);
            return false;
        }
    }

    static List<OccurrenceAtPath> survivorsOf(Ledger ledger, RunId runId, List<OccurrenceAtPath> page) {
        Set<OccurrenceId> surviving = ledger.verdicts().survivingAmong(runId, ids(page));
        return page.stream().filter(occurrence -> surviving.contains(occurrence.occurrenceId())).toList();
    }

    static List<OccurrenceId> ids(List<OccurrenceAtPath> page) {
        return page.stream().map(OccurrenceAtPath::occurrenceId).toList();
    }

    /** Every occurrence of the walk with its path, a page of ledger's size at a time. */
    static void eachPage(Ledger ledger, WalkId walk, Consumer<List<OccurrenceAtPath>> page) {
        List<OccurrenceId> ids = new ArrayList<>();
        for (OccurrenceId id : ledger.occurrences().occurrencesOf(walk)) {
            ids.add(id);
            if (ids.size() == KeysetPages.ROWS_IN_A_PAGE) {
                hand(ledger, ids, page);
            }
        }
        hand(ledger, ids, page);
    }

    private static void hand(Ledger ledger, List<OccurrenceId> ids, Consumer<List<OccurrenceAtPath>> page) {
        if (ids.isEmpty()) {
            return;
        }
        Map<OccurrenceId, OccurrencePath> paths = ledger.occurrences().pathsOf(ids);
        List<OccurrenceAtPath> occurrences = new ArrayList<>();
        for (OccurrenceId id : ids) {
            OccurrencePath path = paths.get(id);
            if (path != null) {
                occurrences.add(new OccurrenceAtPath(id, path));
            }
        }
        ids.clear();
        page.accept(occurrences);
    }

    /** The folder an occurrence lies directly in, as its path spells it; empty for the corpus root. */
    static String folderOf(OccurrencePath path) {
        int slash = path.value().lastIndexOf('/');
        return slash < 0 ? "" : path.value().substring(0, slash);
    }

    private static String nameOf(OccurrencePath path) {
        return path.value().substring(path.value().lastIndexOf('/') + 1);
    }

    /** The corpus root's last element: a folder's name, a share's name, or a drive's letter (section 8). */
    private static String rootNameOf(Path canonicalRoot) {
        Path name = canonicalRoot.getFileName();
        if (name != null && !name.toString().isEmpty()) {
            return name.toString();
        }
        String spelt = canonicalRoot.toString();
        while (spelt.length() > 1 && (spelt.endsWith("\\") || spelt.endsWith("/"))) {
            spelt = spelt.substring(0, spelt.length() - 1);
        }
        int cut = Math.max(spelt.lastIndexOf('\\'), spelt.lastIndexOf('/'));
        String last = cut < 0 || cut == spelt.length() - 1 ? spelt : spelt.substring(cut + 1);
        return last.endsWith(":") ? last.substring(0, last.length() - 1) : last;
    }

    /** The folders the set pass holds (section 6), and the questions it asks of them. */
    private static final class Found {

        final Set<String> buildFolders = new HashSet<>();
        final Set<String> codeFolders = new HashSet<>();
        final Map<String, Set<String>> companionFolders = new HashMap<>();
        /** Each generator's tree roots, the highest of the folders holding both a marked page and a companion. */
        final Map<String, Set<String>> treeRoots = new HashMap<>();
        final Set<String> setFolders = new HashSet<>();

        void findTrees(MarkedFolders markedFolders) {
            for (String generator : GeneratorCompanions.generators()) {
                Set<String> both = new HashSet<>(markedFolders.foldersOf(generator));
                both.retainAll(companionFolders.getOrDefault(generator, Set.of()));
                Set<String> roots = new HashSet<>();
                for (String folder : both) {
                    if (both.stream().noneMatch(other -> !other.equals(folder) && atOrBelow(folder, other))) {
                        roots.add(folder);
                    }
                }
                if (!roots.isEmpty()) {
                    treeRoots.put(generator, roots);
                }
            }
        }

        /** The nearest folder holding a build file from {@code folder} up to the first folder under the root, else that one. */
        String anchorOf(String folder) {
            if (folder.isEmpty()) {
                return "";
            }
            String current = folder;
            while (true) {
                if (buildFolders.contains(current) || current.indexOf('/') < 0) {
                    return current;
                }
                current = current.substring(0, current.lastIndexOf('/'));
            }
        }

        /** The deepest set folder at or above {@code folder}, the root's set covering the root's own files only; null for none. */
        String extentOf(String folder) {
            if (folder.isEmpty()) {
                return setFolders.contains("") ? "" : null;
            }
            String current = folder;
            while (true) {
                if (setFolders.contains(current)) {
                    return current;
                }
                if (current.indexOf('/') < 0) {
                    return null;
                }
                current = current.substring(0, current.lastIndexOf('/'));
            }
        }

        /** The generator whose tree {@code folder} lies in: the deeper root wins, then the generator listed first. */
        Optional<String> treeOf(String folder) {
            String best = null;
            int bestDepth = -1;
            for (String generator : GeneratorCompanions.generators()) {
                for (String root : treeRoots.getOrDefault(generator, Set.of())) {
                    if (atOrBelow(folder, root) && depth(root) > bestDepth) {
                        best = generator;
                        bestDepth = depth(root);
                    }
                }
            }
            return Optional.ofNullable(best);
        }

        private static boolean atOrBelow(String folder, String above) {
            return above.isEmpty() || folder.equals(above) || folder.startsWith(above + "/");
        }

        private static int depth(String folder) {
            return folder.isEmpty() ? 0 : (int) folder.chars().filter(c -> c == '/').count() + 1;
        }
    }
}
