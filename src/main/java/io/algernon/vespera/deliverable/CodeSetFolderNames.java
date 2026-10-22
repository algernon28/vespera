package io.algernon.vespera.deliverable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The folder each code set is given directly in the tree's {@code code/} (ADR-296 §3.1), and the entries of the names
 * file that name no set of the run (ADR-284 §9, ADR-296 §6.2).
 *
 * <p>A name is the one the operator typed for the set's folder or else its default name, made safe and cut as a copy's
 * folder is ({@link CopyNames#folderName}), and only then told apart from the others by ADR-280 §1.4's two keys: a
 * typed name outranks a default name, and among names of the same kind the set whose lowest member is lower comes
 * first. The first keeps its name; each other takes {@code ~} and its own lowest member's occurrence id at the end,
 * again until it is free. Every name is worked out from the sets and the entries alone, reading no file, so a name
 * worked out again from the same rows is the same name (ADR-296 §3.4).
 */
final class CodeSetFolderNames {

    private CodeSetFolderNames() {}

    /**
     * The name of each set's folder in {@code code/}, by the set's folder in the archive.
     *
     * @param sets the run's sets, in any order
     * @param typed the entries of the names file, in its order; an entry naming no set renames nothing
     */
    static Map<String, String> of(List<ListedCodeSet> sets, List<CodeSetNames.Entry> typed) {
        Map<String, String> typedNames = new HashMap<>();
        typed.forEach(entry -> typedNames.put(entry.folder(), entry.name()));

        List<ListedCodeSet> ranked = new ArrayList<>(sets);
        ranked.sort(Comparator.<ListedCodeSet>comparingInt(set -> typedNames.containsKey(set.folder()) ? 0 : 1)
                .thenComparingLong(set -> set.lowestMember().value()));

        Map<String, String> names = new HashMap<>();
        Set<String> taken = new HashSet<>();
        for (ListedCodeSet set : ranked) {
            String chosen = typedNames.getOrDefault(set.folder(), set.defaultName());
            String made = CopyNames.folderName(chosen);
            String name = made.isEmpty() ? String.valueOf(set.lowestMember().value()) : made;
            String suffix = "~" + set.lowestMember().value();
            int times = 0;
            String candidate = name;
            while (CopyNames.keysOf(candidate).stream().anyMatch(taken::contains)) {
                times++;
                candidate = CopyNames.folderNameWithSuffixes(name, suffix, times);
            }
            taken.addAll(CopyNames.keysOf(candidate));
            names.put(set.folder(), candidate);
        }
        return names;
    }

    /** How many entries of {@code typed} name a folder that holds no set of {@code sets}. */
    static long dormant(List<ListedCodeSet> sets, List<CodeSetNames.Entry> typed) {
        Set<String> folders = new HashSet<>();
        sets.forEach(set -> folders.add(set.folder()));
        return typed.stream().filter(entry -> !folders.contains(entry.folder())).count();
    }
}
