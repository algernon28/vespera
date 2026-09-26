package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two guards ADR-157 owes once the change exists: every stage from 2 on reaches its run through
 * {@link StageRuns} and nothing else, and every persisted stage or step name is written in one of two
 * tables and read from there.
 *
 * <p><b>Source, not bytecode.</b> A string constant is inlined wherever it is read, so the compiled
 * class of a step that reads {@code StepNames.GENERATION} holds the same {@code "generation"} as one that
 * wrote it out. Only the source says which of the two was written. The source is read from the
 * working tree Maven runs the tests in, so comments and Javadoc are stripped first: a sentence that
 * mentions a run class or a stage name by way of explanation is not a use of one.
 *
 * <p><b>Neither guard could pass on {@code 5cba9ef},</b> where seven run classes were reached through
 * {@code ObjectProvider} and fourteen classes each named a step. So neither was in the tree before the
 * change it checks.
 */
@Epic("Pipeline")
@Feature("How a stage reaches its run")
@Issue("303")
@Link(name = "ADR-157", url = Adr.A_STAGE_ASKS_FOR_ITS_RUN_AFTER_ITS_OWN_GATE, type = "adr")
class RunsAndNamesHaveOnePlaceTest {

    /** The {@code pipeline} module's own source, relative to the directory Maven runs the tests in. */
    private static final Path PIPELINE_SOURCE =
            Path.of("src", "main", "java", "io", "algernon", "vespera", "pipeline");

    /**
     * Every persisted {@code run.stage} and {@code finished_step.step} value, written out rather than
     * read from the tables it checks, because a persisted name is pinned by its value (ADR-157, Tests).
     * The first claim of {@link #noPipelineSourceWritesAPersistedNameOutsideItsTwoTables} checks that
     * this list still covers both tables.
     */
    private static final Set<String> PERSISTED_NAMES = Set.of(
            "census",
            "byte-level-reduction",
            "extraction",
            "content-census",
            "redundancy-signature",
            "content-redundancy",
            "seed-extraction",
            "seed-measurement",
            "seed-corpus-comparison",
            "embedding-scoring",
            "relevance-scoring",
            "relevance-floor",
            "clustering",
            "relevance-report",
            "arrangement",
            "generation");

    /**
     * The two tables a persisted name is declared in (ADR-157 §1, §7), and {@code package-info.java},
     * whose {@code allowedDependencies} names modules. {@code extraction} is a module as well as a stage
     * and a step, and a module name is not a stage's or a step's.
     */
    private static final Set<String> WHERE_A_NAME_MAY_BE_WRITTEN =
            Set.of("StageModules.java", "StepNames.java", "package-info.java");

    /** An {@code ObjectProvider} of some named type, generic arguments and whitespace allowed. */
    private static final Pattern OBJECT_PROVIDER_OF = Pattern.compile("ObjectProvider\\s*<\\s*([\\w.]+)\\s*>");

    @Test
    @Story("A stage asks for its run through one holder")
    @DisplayName("No pipeline source reaches a run through an ObjectProvider of a run type")
    void noPipelineSourceHoldsAnObjectProviderOfARun() throws IOException {
        Map<String, List<String>> providedTypes = new TreeMap<>();
        for (Path source : pipelineSources()) {
            Matcher provider = OBJECT_PROVIDER_OF.matcher(codeOf(Files.readString(source)));
            List<String> types = new ArrayList<>();
            while (provider.find()) {
                types.add(simpleNameOf(provider.group(1)));
            }
            if (!types.isEmpty()) {
                providedTypes.put(source.getFileName().toString(), types);
            }
        }
        Map<String, List<String>> providedRuns = new TreeMap<>();
        providedTypes.forEach((file, types) -> {
            List<String> runs = types.stream().filter(type -> type.endsWith("Run")).toList();
            if (!runs.isEmpty()) {
                providedRuns.put(file, runs);
            }
        });

        claim(
                "the scan finds the one ObjectProvider pipeline is known to hold, StageRuns' provider of the"
                        + " extractor's identity, so a scan that had stopped finding anything fails here rather"
                        + " than passing the claim below",
                () -> assertThat(providedTypes.getOrDefault("StageRuns.java", List.of()))
                        .contains("ExtractorIdentity"));
        claim(
                "no pipeline source holds an ObjectProvider of a type named ...Run. A stage reaches its run by"
                        + " calling its StageRuns accessor after its own gate (ADR-157 §3, §4), and a provider"
                        + " of a run would be a second way in, one that mints wherever it is first called",
                () -> assertThat(providedRuns).isEmpty());
    }

    @Test
    @Story("Every step is named once")
    @DisplayName("No pipeline source outside StageModules and StepNames writes a persisted stage or step name")
    void noPipelineSourceWritesAPersistedNameOutsideItsTwoTables() throws IOException, IllegalAccessException {
        Set<String> declared = new TreeSet<>();
        Arrays.stream(StageModules.values()).map(StageModules::stage).forEach(declared::add);
        declared.addAll(stepNames());
        Map<String, List<String>> written = new TreeMap<>();
        List<String> foundInStepNames = List.of();
        for (Path source : pipelineSources()) {
            String file = source.getFileName().toString();
            List<String> names = literalsIn(Files.readString(source)).stream()
                    .filter(PERSISTED_NAMES::contains)
                    .toList();
            if (file.equals("StepNames.java")) {
                foundInStepNames = names;
            }
            if (!names.isEmpty() && !WHERE_A_NAME_MAY_BE_WRITTEN.contains(file)) {
                written.put(file, names);
            }
        }
        List<String> stepNamesLiterals = foundInStepNames;

        claim(
                "this test's list of persisted names covers every stage StageModules declares and every step"
                        + " StepNames declares, so a name added to either table is a name this guard looks for",
                () -> assertThat(PERSISTED_NAMES).containsAll(declared));
        claim(
                "the scan reads StepNames' own fifteen literals, so a scan that had stopped reading literals"
                        + " fails here rather than passing the claim below",
                () -> assertThat(stepNamesLiterals).hasSize(15));
        claim(
                "no pipeline source outside StageModules and StepNames writes a literal equal to a persisted"
                        + " stage or step name. Each name is written in one table and read from there"
                        + " (ADR-157 §7). The one exception is package-info.java, whose allowedDependencies"
                        + " names modules, and a module name is not a stage's or a step's",
                () -> assertThat(written).isEmpty());
    }

    /** Every {@code .java} file directly in {@code pipeline}, which has no subpackages. */
    private static List<Path> pipelineSources() throws IOException {
        try (Stream<Path> files = Files.list(PIPELINE_SOURCE)) {
            List<Path> sources = files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
            if (sources.isEmpty()) {
                throw new IllegalStateException("no pipeline source was found under " + PIPELINE_SOURCE.toAbsolutePath());
            }
            return sources;
        }
    }

    /** The values of StepNames' constants, read reflectively since the class exposes no list of them. */
    private static List<String> stepNames() throws IllegalAccessException {
        List<String> names = new ArrayList<>();
        for (Field field : StepNames.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                field.setAccessible(true);
                names.add((String) field.get(null));
            }
        }
        return names;
    }

    private static String simpleNameOf(String typeName) {
        return typeName.substring(typeName.lastIndexOf('.') + 1);
    }

    /** {@code source} with every comment and every string or character literal blanked out. */
    private static String codeOf(String source) {
        return scan(source, new ArrayList<>());
    }

    /** The content of every string literal and text block in {@code source}, comments ignored. */
    private static List<String> literalsIn(String source) {
        List<String> literals = new ArrayList<>();
        scan(source, literals);
        return literals;
    }

    /**
     * One pass over Java source that knows comments, string literals, text blocks and character
     * literals apart. Returns the code with each of them replaced by a space, and adds each string
     * literal's raw content to {@code literals}. Escapes are kept as written, since no persisted name
     * contains one.
     */
    private static String scan(String source, List<String> literals) {
        StringBuilder code = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? n : end;
                code.append(' ');
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                code.append(' ');
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int stop = end < 0 ? n : end;
                literals.add(source.substring(i + 3, stop));
                i = end < 0 ? n : end + 3;
                code.append(' ');
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && source.charAt(j) != c) {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                if (c == '"') {
                    literals.add(source.substring(i + 1, Math.min(j, n)));
                }
                i = j + 1;
                code.append(' ');
            } else {
                code.append(c);
                i++;
            }
        }
        return code.toString();
    }
}
