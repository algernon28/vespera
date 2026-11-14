package io.algernon.vespera.engine.deliverable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The folder each code set is given directly in the tree's {@code code/} (ADR-296 §3.1, as ADR-377 §9 amends it).
 *
 * <p>A name starts from the set's default name, its folder's last element, made safe and cut as a copy's folder is
 * ({@link CopyNames#folderName}), and is only then told apart from the others by ADR-280 §1.4's two keys: the set whose
 * lowest member is lower comes first and keeps its name; each other takes {@code ~} and its own lowest member's
 * occurrence id at the end, again until it is free. No name is typed: a set is named by its folder alone (ADR-377 §9).
 * Every name is worked out from the sets alone, reading no file, so a name worked out again from the same rows is the
 * same name (ADR-296 §3.4).
 */
final class CodeSetFolderNames {

    private CodeSetFolderNames() {}

    /**
     * The name of each set's folder in {@code code/}, by the set's folder in the archive.
     *
     * @param sets the run's sets, in any order
     */
    static Map<String, String> of(List<ListedCodeSet> sets) {
        List<ListedCodeSet> ranked = new ArrayList<>(sets);
        ranked.sort(Comparator.comparingLong(set -> set.lowestMember().value()));

        Map<String, String> names = new HashMap<>();
        Set<String> taken = new HashSet<>();
        for (ListedCodeSet set : ranked) {
            String made = CopyNames.folderName(set.defaultName());
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
}
