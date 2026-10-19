package io.algernon.vespera.corpus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * For each documentation generator, the folders directly holding a page stage 1's first pass kept as code for
 * that generator's mark, handed in memory to {@link CodeSets} (ADR-284 sections 5 and 6). Folders only: no page
 * and no occurrence is held, and a tree is found from these and never from a verdict's reason.
 */
public final class MarkedFolders {

    private final Map<String, Set<String>> byGenerator = new HashMap<>();

    /** Notes that a page lying directly in {@code folder} was kept as code for the mark of {@code generator}. */
    void add(String folder, String generator) {
        byGenerator.computeIfAbsent(generator, g -> new HashSet<>()).add(folder);
    }

    /** The folders handed over for {@code generator}; none where no page was kept for its mark. */
    Set<String> foldersOf(String generator) {
        return byGenerator.getOrDefault(generator, Set.of());
    }

    /** Whether {@code folder} was handed over for some generator. */
    boolean holds(String folder) {
        return byGenerator.values().stream().anyMatch(folders -> folders.contains(folder));
    }
}
