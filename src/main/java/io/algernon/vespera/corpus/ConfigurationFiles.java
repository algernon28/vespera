package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceAtPath;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * Stage 1's configuration pass, after the set pass and before the format-mix page and the hashing (ADR-287): the
 * rule for what a configuration file is, told from its bytes alone, and what becomes of one.
 *
 * <p>Inside a code set it is kept with the set as {@code code}, with the role {@code configuration}; outside every
 * set it is {@code out-of-scope}. Only while a floor is given: with none, nothing is a configuration file, a
 * configuration root included, and the share, the roots and the interface definitions are still counted for the
 * page. What it holds is nothing of the run's occurrences: it reads the walk's survivors a page at a time and asks
 * {@link CodeSets#setOf} about a file's folder (ADR-220).
 */
public class ConfigurationFiles {

    /** The version of the list of configuration roots (ADR-287 section 3.3), recorded in stage 1's configuration consumed. */
    static final String ROOTS_VERSION = "v1";

    /** The version of the list of interface definitions (ADR-287 section 3.2), recorded likewise. */
    static final String INTERFACE_DEFINITIONS_VERSION = "v1";

    /** The role a configuration file has as a member of a code set, as the table stores it. */
    static final String CONFIGURATION = "configuration";

    private static final String XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";
    private static final String WSDL_1 = "http://schemas.xmlsoap.org/wsdl/";
    private static final String WSDL_2 = "http://www.w3.org/ns/wsdl";

    /** A first element, by local name and namespace; the namespace is {@code null} for none. */
    private record Root(String localName, String namespace) {}

    /** The configuration roots (section 3.3), in the table's order. */
    private static final List<Root> CONFIGURATION_ROOTS = List.of(
            new Root("project", "http://maven.apache.org/POM/4.0.0"),
            new Root("configuration", null),
            new Root("Configuration", null),
            new Root("beans", "http://www.springframework.org/schema/beans"));

    private final CodeSets codeSets;
    private final DetectedFormats detectedFormats;

    ConfigurationFiles(CodeSets codeSets, DetectedFormats detectedFormats) {
        this.codeSets = codeSets;
        this.detectedFormats = detectedFormats;
    }

    /** What the pass tells its caller as it goes. */
    public interface Progress {

        /** After a configuration file inside a code set is verdicted {@code code}, with its reason. */
        void kept(OccurrenceId occurrence, String reason);

        /** After a configuration file outside every code set is verdicted {@code out-of-scope}, with its reason. */
        void leftOut(OccurrenceId occurrence, String reason);

        /** A file that could not be read to see whether it is a configuration file, so it is left as it was. */
        void unreadable(OccurrenceId occurrence, Exception cause);
    }

    /**
     * What the pass found, for the format-mix page: the configuration files kept with a code set and left out;
     * the files counted by their share, {@code fewerThanTenLines} of them in none of the ten bands of a tenth; the
     * files whose first element is a configuration root, by the root as the page names it; and the interface
     * definitions, by their name.
     */
    public record Tally(
            int keptWithCodeSet,
            int leftOut,
            int[] byShareBand,
            int fewerThanTenLines,
            Map<String, Integer> roots,
            Map<String, Integer> interfaceDefinitions) {

        /** The configuration files found, both kinds. */
        public int configurationFiles() {
            return keptWithCodeSet + leftOut;
        }
    }

    /** The tally of a pass that read nothing. */
    public static Tally nothing() {
        return new Tally(0, 0, new int[FormatMix.TIMESTAMP_BANDS], 0, Map.of(), Map.of());
    }

    /**
     * Reads every survivor of {@code runId} that section 3.1 reads. {@code floor} is the profile's
     * {@code configurationSettingShareFloor}, {@code null} where it is unset or unreadable.
     */
    public Tally pass(Ledger ledger, RunId runId, Path canonicalRoot, Double floor, Progress progress) {
        WalkId walk = ledger.runs().walkOf(runId).orElseThrow(() -> new IllegalStateException(
                "run " + runId.value() + " is recorded against no walk"));
        int[] bands = new int[FormatMix.TIMESTAMP_BANDS];
        int[] counts = new int[3];
        Map<String, Integer> roots = new TreeMap<>();
        Map<String, Integer> interfaceDefinitions = new LinkedHashMap<>();
        CodeSets.eachPage(ledger, walk, page -> {
            for (OccurrenceAtPath occurrence : CodeSets.survivorsOf(ledger, runId, page)) {
                Path file = canonicalRoot.resolve(occurrence.path().value());
                Optional<DetectedFormat> format = detectedFormats.formatFor(occurrence.occurrenceId(), runId);
                if (format.isEmpty()
                        || (format.get() != DetectedFormat.PLAIN_TEXT && format.get() != DetectedFormat.UNRECOGNISED)
                        || (format.get() == DetectedFormat.PLAIN_TEXT
                                && detectedFormats.subtypeFor(occurrence.occurrenceId(), runId).isPresent())) {
                    continue;
                }
                Optional<String> reason;
                try {
                    if (format.get() == DetectedFormat.UNRECOGNISED && !Code.readsAsText(file)) {
                        continue;
                    }
                    reason = judge(file, floor, bands, counts, roots, interfaceDefinitions);
                } catch (IOException | RuntimeException e) {
                    progress.unreadable(occurrence.occurrenceId(), e);
                    continue;
                }
                if (reason.isEmpty()) {
                    continue;
                }
                Optional<String> set = codeSets.setOf(runId, occurrence.path());
                if (set.isPresent()) {
                    String kept = "a configuration file of the code set at "
                            + (set.get().isEmpty() ? "the corpus root" : set.get());
                    ledger.verdicts().verdict(occurrence.occurrenceId(), runId, VerdictKind.CODE, kept);
                    codeSets.member(occurrence.occurrenceId(), runId, set.get(), CONFIGURATION, null);
                    counts[1]++;
                    progress.kept(occurrence.occurrenceId(), kept);
                } else {
                    String left = "a configuration file, and configuration files outside every code set are out of"
                            + " scope: " + reason.get();
                    ledger.verdicts().verdict(occurrence.occurrenceId(), runId, VerdictKind.OUT_OF_SCOPE, left);
                    counts[2]++;
                    progress.leftOut(occurrence.occurrenceId(), left);
                }
            }
        });
        return new Tally(counts[1], counts[2], bands, counts[0], orderedRoots(roots), orderedDefinitions(interfaceDefinitions));
    }

    /**
     * Section 3.6's order over one file: the part of the reason that follows the fixed words where it is a
     * configuration file, empty where it is not, or where {@code floor} is {@code null}. Counts what it finds.
     */
    private static Optional<String> judge(
            Path file,
            Double floor,
            int[] bands,
            int[] counts,
            Map<String, Integer> roots,
            Map<String, Integer> interfaceDefinitions)
            throws IOException {
        FirstElement.Opening opening = FirstElement.of(file);
        Optional<String> definition = interfaceDefinitionOf(opening);
        if (definition.isPresent()) {
            interfaceDefinitions.merge(definition.get(), 1, Integer::sum);
            return Optional.empty();
        }
        if (opening instanceof FirstElement.Opening.Xml xml && xml.root().isPresent()) {
            FirstElement.Element element = xml.root().get();
            for (Root root : CONFIGURATION_ROOTS) {
                if (root.localName().equals(element.localName())
                        && Objects.equals(root.namespace(), element.namespace())) {
                    String named = nameOf(root);
                    roots.merge(named, 1, Integer::sum);
                    return floor == null ? Optional.empty() : Optional.of("its first element is " + named);
                }
            }
        }
        SettingLines.Count count = SettingLines.count(file);
        if (count.counted() < SettingLines.MINIMUM_COUNTED_LINES) {
            counts[0]++;
        } else {
            bands[Math.min(FormatMix.TIMESTAMP_BANDS - 1, count.wholePercent() / 10)]++;
        }
        return floor != null && count.isConfiguration(floor)
                ? Optional.of(count.wholePercent() + "% of the lines read from its start and end are settings")
                : Optional.empty();
    }

    /** The interface definition {@code opening} is, by its name in section 3.2's table, or empty. */
    private static Optional<String> interfaceDefinitionOf(FirstElement.Opening opening) {
        if (opening instanceof FirstElement.Opening.Xml xml) {
            if (xml.root().isEmpty()) {
                return Optional.empty();
            }
            FirstElement.Element element = xml.root().get();
            if (element.localName().equals("schema") && XML_SCHEMA.equals(element.namespace())) {
                return Optional.of("XML Schema");
            }
            if (element.localName().equals("definitions") && WSDL_1.equals(element.namespace())) {
                return Optional.of("WSDL 1.1");
            }
            if (element.localName().equals("description") && WSDL_2.equals(element.namespace())) {
                return Optional.of("WSDL 2.0");
            }
            return Optional.empty();
        }
        for (String key : ((FirstElement.Opening.Keys) opening).keys()) {
            switch (key) {
                case "openapi" -> {
                    return Optional.of("OpenAPI");
                }
                case "swagger" -> {
                    return Optional.of("Swagger");
                }
                case "$schema" -> {
                    return Optional.of("JSON Schema");
                }
                default -> {}
            }
        }
        return Optional.empty();
    }

    /** The interface definitions found, in the order of section 3.2's table. */
    private static Map<String, Integer> orderedDefinitions(Map<String, Integer> found) {
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (String name : List.of("XML Schema", "WSDL 1.1", "WSDL 2.0", "OpenAPI", "Swagger", "JSON Schema")) {
            if (found.containsKey(name)) {
                ordered.put(name, found.get(name));
            }
        }
        return ordered;
    }

    /** A root as the page and a reason name it. */
    private static String nameOf(Root root) {
        return root.localName() + ", " + (root.namespace() == null ? "in no namespace" : "in the namespace " + root.namespace());
    }

    /** The roots found, in the order of the list. */
    private static Map<String, Integer> orderedRoots(Map<String, Integer> found) {
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (Root root : CONFIGURATION_ROOTS) {
            String named = nameOf(root);
            if (found.containsKey(named)) {
                ordered.put(named, found.get(named));
            }
        }
        return ordered;
    }
}
