package io.algernon.vespera.corpus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The files each documentation generator writes beside its pages (ADR-284 section 5): the list built into
 * {@code corpus} beside {@link Code}'s marks, and its version, recorded among stage 1's configuration consumed
 * (section 10). A generated tree is rooted where a marked page and one of these lie in one folder.
 */
final class GeneratorCompanions {

    private GeneratorCompanions() {}

    /** The version of the list below; a change to it is a new version. */
    static final String VERSION = "v1";

    /**
     * Each generator's companion names, in lower case, in the order of ADR-277 section 4's tables; a leading
     * {@code *} stands for at least one character, so the name ends with what follows it.
     */
    private static final Map<String, List<String>> COMPANIONS = Map.of(
            "Javadoc", List.of("element-list", "package-list", "*search-index.js"),
            "pdoc", List.of("search.js"),
            "mkdocstrings", List.of("objects.inv"),
            "Doxygen", List.of("doxygen.css", "dynsections.js", "*.map", "*.md5"),
            "RDoc", List.of("created.rid"));

    /** The generators that have companions, as {@link Code#rankOf} orders them. */
    static List<String> generators() {
        return COMPANIONS.keySet().stream().sorted(Comparator.comparingInt(Code::rankOf)).toList();
    }

    /** The generators one of whose companion files {@code name} is. */
    static List<String> generatorsOf(String name) {
        String folded = name.toLowerCase(Locale.ROOT);
        List<String> generators = new ArrayList<>();
        for (Map.Entry<String, List<String>> generator : COMPANIONS.entrySet()) {
            for (String companion : generator.getValue()) {
                boolean matches = companion.startsWith("*")
                        ? folded.length() > companion.length() - 1 && folded.endsWith(companion.substring(1))
                        : folded.equals(companion);
                if (matches) {
                    generators.add(generator.getKey());
                    break;
                }
            }
        }
        return generators;
    }
}
