package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Which survivors are translations of which, and which copy of each translation set survives (ADR-271 §2,
 * §4), and the rows of the page the similarity floor is read off (§7).
 *
 * <p>Two kinds of link join survivors into a set: a translation by its path, two paths of the same length that
 * differ in exactly one segment, a language folder in both of two different languages, neither path holding
 * a second language folder; and a likeness (see {@link TranslationCandidates}) at or above the floor.
 * Nothing but the paths and the likenesses is read: not the size, not the text, and not the detected language
 * of a translation by its path.
 */
public final class Translations {

    private Translations() {}

    /**
     * A survivor entering the step.
     *
     * @param detectedLanguage the language stage 2 stored for it, Lingua's name, or {@code null}
     */
    public record Member(OccurrenceId occurrence, OccurrencePath path, String detectedLanguage) {}

    /** Two members each the other's nearest across two languages, with the cosine of their mean vectors. */
    public record Likeness(OccurrenceId first, OccurrenceId second, double similarity) {}

    /** What found a translation, as the {@code translation_of} row stores it. */
    public enum Evidence {
        PARALLEL_PATHS("parallel-paths"),
        EMBEDDINGS("embeddings"),
        BOTH("both");

        private final String stored;

        Evidence(String stored) {
            this.stored = stored;
        }

        /** The value the row holds. */
        public String stored() {
            return stored;
        }
    }

    /** A copy removed as a translation of the one that survives, with the two languages as ISO 639-1 codes. */
    public record Removal(
            OccurrenceId removed, OccurrenceId kept, String language, String keptLanguage, Evidence evidence) {}

    /**
     * One row of the page: the likenesses whose cosine, rounded down to the hundredth from its exact value, is
     * {@code similarity}; of them, those their paths confirm and those their paths refute.
     */
    public record SimilarityRow(BigDecimal similarity, long likenesses, long confirmed, long refuted) {}

    /**
     * What the step decides.
     *
     * @param removals the copies removed, in the members' order
     * @param rows one for every hundredth of similarity that holds a likeness, ascending
     * @param sets the translation sets found
     * @param setsStanding of them, those holding two members of one language, which stand whole
     * @param setsByPaths the sets the translations by their paths alone form
     * @param detectedLanguages the members with a detected language, counted by its ISO 639-1 code, in code order
     */
    public record Resolution(
            List<Removal> removals,
            List<SimilarityRow> rows,
            long sets,
            long setsStanding,
            long setsByPaths,
            SortedMap<String, Long> detectedLanguages) {}

    private static final int NONE = 0;
    private static final int PATH = 1;
    private static final int EMBEDDING = 2;

    /**
     * Decides the sets, the removals and the rows over {@code members}, in the order given, which must hold each
     * occurrence once. A likeness naming an occurrence not among them is left out.
     *
     * @param floor the similarity floor, empty where unset or unreadable: no likeness is applied
     * @param preferred the ISO 639-1 codes, most preferred first, empty where nothing is to be removed
     */
    public static Resolution resolve(
            List<Member> members, List<Likeness> likenesses, OptionalDouble floor, List<String> preferred) {
        int size = members.size();
        Map<Long, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < size; i++) {
            indexOf.put(members.get(i).occurrence().value(), i);
        }

        // Translations by their paths: members with exactly one language folder, grouped by the rest of the path.
        String[] folderLanguage = new String[size];
        Map<String, List<Integer>> byRestOfPath = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String[] segments = members.get(i).path().value().split("/", -1);
            int slot = -1;
            int folders = 0;
            for (int s = 0; s < segments.length - 1; s++) {
                Optional<String> language = Languages.ofFolder(segments[s]);
                if (language.isPresent()) {
                    folders++;
                    slot = s;
                    folderLanguage[i] = language.get();
                }
            }
            if (folders != 1) {
                folderLanguage[i] = null;
                continue;
            }
            segments[slot] = "";
            byRestOfPath.computeIfAbsent(slot + "|" + String.join("/", segments), key -> new ArrayList<>()).add(i);
        }
        Map<Long, Integer> links = new HashMap<>();
        UnionFind pathSets = new UnionFind(size);
        boolean[] pathLinked = new boolean[size];
        List<Integer>[] pathPartners = newLists(size);
        for (List<Integer> group : byRestOfPath.values()) {
            for (int a = 0; a < group.size(); a++) {
                for (int b = a + 1; b < group.size(); b++) {
                    int left = group.get(a);
                    int right = group.get(b);
                    if (!folderLanguage[left].equals(folderLanguage[right])) {
                        links.merge(pair(left, right, size), PATH, (held, added) -> held | added);
                        pathSets.union(left, right);
                        pathLinked[left] = true;
                        pathLinked[right] = true;
                        pathPartners[left].add(right);
                        pathPartners[right].add(left);
                    }
                }
            }
        }
        String[] language = new String[size];
        for (int i = 0; i < size; i++) {
            language[i] = pathLinked[i]
                    ? folderLanguage[i]
                    : Languages.ofDetected(members.get(i).detectedLanguage()).orElse(null);
        }

        // Likenesses: every one is counted on its row; those at or above the floor are applied.
        UnionFind sets = new UnionFind(size);
        for (int i = 0; i < size; i++) {
            for (int partner : pathPartners[i]) {
                sets.union(i, partner);
            }
        }
        Map<BigDecimal, long[]> rows = new TreeMap<>();
        for (Likeness likeness : likenesses) {
            Integer left = indexOf.get(likeness.first().value());
            Integer right = indexOf.get(likeness.second().value());
            if (left == null || right == null) {
                continue;
            }
            BigDecimal exact = new BigDecimal(likeness.similarity());
            long[] row = rows.computeIfAbsent(exact.setScale(2, RoundingMode.FLOOR), key -> new long[3]);
            row[0]++;
            if (pathSets.find(left) == pathSets.find(right) && pathLinked[left]) {
                row[1]++;
            } else if (translatesIntoLanguageOf(left, right, pathPartners, folderLanguage, language)
                    || translatesIntoLanguageOf(right, left, pathPartners, folderLanguage, language)) {
                row[2]++;
            }
            if (floor.isPresent() && exact.compareTo(BigDecimal.valueOf(floor.getAsDouble())) >= 0) {
                links.merge(pair(left, right, size), EMBEDDING, (held, added) -> held | added);
                sets.union(left, right);
            }
        }

        // The sets: members by root, and the kinds of link each root holds.
        Map<Integer, List<Integer>> membersOfSet = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            membersOfSet.computeIfAbsent(sets.find(i), key -> new ArrayList<>()).add(i);
        }
        Map<Integer, Integer> kindsOfSet = new HashMap<>();
        for (Map.Entry<Long, Integer> link : links.entrySet()) {
            int left = (int) (link.getKey() / size);
            kindsOfSet.merge(sets.find(left), link.getValue(), (held, added) -> held | added);
        }

        long found = 0;
        long standing = 0;
        List<Removal> removals = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> set : membersOfSet.entrySet()) {
            List<Integer> inSet = set.getValue();
            if (inSet.size() < 2) {
                continue;
            }
            found++;
            Set<String> seen = new HashSet<>();
            boolean oneLanguageTwice = false;
            for (int member : inSet) {
                oneLanguageTwice |= language[member] != null && !seen.add(language[member]);
            }
            if (oneLanguageTwice) {
                standing++;
                continue;
            }
            if (preferred.isEmpty()) {
                continue;
            }
            int kept = keptOf(inSet, members, language, preferred);
            int kinds = kindsOfSet.getOrDefault(set.getKey(), NONE);
            Evidence evidence = (kinds & EMBEDDING) == 0
                    ? Evidence.PARALLEL_PATHS
                    : (kinds & PATH) == 0 ? Evidence.EMBEDDINGS : Evidence.BOTH;
            for (int member : inSet) {
                if (member != kept) {
                    removals.add(new Removal(
                            members.get(member).occurrence(),
                            members.get(kept).occurrence(),
                            language[member],
                            language[kept],
                            evidence));
                }
            }
        }
        removals.sort(Comparator.comparingInt(removal -> indexOf.get(removal.removed().value())));

        List<SimilarityRow> table = new ArrayList<>();
        rows.forEach((similarity, row) -> table.add(new SimilarityRow(similarity, row[0], row[1], row[2])));
        Map<Integer, Integer> sizeOfPathSet = new HashMap<>();
        for (int i = 0; i < size; i++) {
            if (pathLinked[i]) {
                sizeOfPathSet.merge(pathSets.find(i), 1, Integer::sum);
            }
        }
        SortedMap<String, Long> detected = new TreeMap<>();
        for (Member member : members) {
            Languages.ofDetected(member.detectedLanguage()).ifPresent(code -> detected.merge(code, 1L, Long::sum));
        }
        return new Resolution(
                List.copyOf(removals), List.copyOf(table), found, standing, (long) sizeOfPathSet.size(), detected);
    }

    /**
     * Whether {@code member} is a translation by its path of a third occurrence in {@code other}'s language: a
     * likeness between the two is then refuted by the paths.
     */
    private static boolean translatesIntoLanguageOf(
            int member, int other, List<Integer>[] pathPartners, String[] folderLanguage, String[] language) {
        if (language[other] == null) {
            return false;
        }
        for (int third : pathPartners[member]) {
            if (third != other && language[other].equals(folderLanguage[third])) {
                return true;
            }
        }
        return false;
    }

    /** The member kept: the first preferred language any member has, else the lowest path in code-point order. */
    private static int keptOf(List<Integer> inSet, List<Member> members, String[] language, List<String> preferred) {
        for (String wanted : preferred) {
            for (int member : inSet) {
                if (wanted.equals(language[member])) {
                    return member;
                }
            }
        }
        int lowest = inSet.getFirst();
        for (int member : inSet) {
            if (byCodePoint(members.get(member).path().value(), members.get(lowest).path().value()) < 0) {
                lowest = member;
            }
        }
        return lowest;
    }

    private static int byCodePoint(String left, String right) {
        int[] a = left.codePoints().toArray();
        int[] b = right.codePoints().toArray();
        return java.util.Arrays.compare(a, b);
    }

    private static long pair(int left, int right, int size) {
        return (long) Math.min(left, right) * size + Math.max(left, right);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer>[] newLists(int size) {
        List<Integer>[] lists = new List[size];
        for (int i = 0; i < size; i++) {
            lists[i] = new ArrayList<>();
        }
        return lists;
    }

    /** Disjoint sets over indexes, with path halving. */
    private static final class UnionFind {

        private final int[] parent;

        UnionFind(int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int find(int index) {
            while (parent[index] != index) {
                parent[index] = parent[parent[index]];
                index = parent[index];
            }
            return index;
        }

        void union(int left, int right) {
            int a = find(left);
            int b = find(right);
            if (a != b) {
                parent[Math.max(a, b)] = Math.min(a, b);
            }
        }
    }
}
