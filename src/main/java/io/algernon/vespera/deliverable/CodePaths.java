package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The path each member of a code set is copied to below its set's folder (ADR-296 §3.2): the member's path relative to
 * the set's folder in the archive, kept element by element, each element made safe and cut as a copy's name is
 * ({@link CopyNames}) and a folder's as a set's folder is ({@link CopyNames#folderName}).
 *
 * <p>Folders whose names collide by ADR-280 §1.4's keys within one drawn folder are one folder, spelt as the member
 * with the lowest occurrence id beneath it spells it; files whose names collide are suffixed as copies in a cluster
 * folder are, with no page's name reserved; and a file that meets a folder beside it takes the suffix, the folder
 * keeping its name. Every path is worked out from the members alone, reading no file, so a path worked out again from
 * the same members is the same path whatever order they arrive in (ADR-296 §3.4).
 */
final class CodePaths {

    private CodePaths() {}

    /**
     * Each member's path below its set's folder, {@code /} between its elements.
     *
     * @param members the members of one set, or of several: names are told apart within a set and never across two
     */
    static Map<OccurrenceId, String> of(List<ListedCodeFile> members) {
        Map<String, List<ListedCodeFile>> bySet = new LinkedHashMap<>();
        for (ListedCodeFile member : members) {
            bySet.computeIfAbsent(member.setFolder(), folder -> new ArrayList<>()).add(member);
        }
        Map<OccurrenceId, String> paths = new HashMap<>();
        bySet.values().forEach(set -> paths.putAll(ofOneSet(set)));
        return paths;
    }

    private static Map<OccurrenceId, String> ofOneSet(List<ListedCodeFile> members) {
        List<ListedCodeFile> ascending = new ArrayList<>(members);
        ascending.sort(Comparator.comparingLong(member -> member.occurrence().value()));

        Folder top = new Folder();
        Map<OccurrenceId, Placement> placements = new HashMap<>();
        for (ListedCodeFile member : ascending) {
            String[] elements = keptPath(member).split("/");
            Folder inside = top;
            List<Branch> chain = new ArrayList<>();
            for (int at = 0; at < elements.length - 1; at++) {
                Branch branch = inside.branchFor(elements[at], member.occurrence());
                chain.add(branch);
                inside = branch.inside;
            }
            String fileName = elements[elements.length - 1];
            inside.files.add(new CopyNames.Named(member.occurrence(), fileName));
            placements.put(member.occurrence(), new Placement(chain));
        }
        Map<OccurrenceId, String> fileNames = new HashMap<>();
        name(top, fileNames);

        Map<OccurrenceId, String> paths = new HashMap<>();
        for (ListedCodeFile member : ascending) {
            Placement placement = placements.get(member.occurrence());
            List<String> parts = new ArrayList<>();
            placement.chain.forEach(branch -> parts.add(branch.drawn));
            parts.add(fileNames.get(member.occurrence()));
            paths.put(member.occurrence(), String.join("/", parts));
        }
        return paths;
    }

    /** The member's path below its set's folder as the archive spells it; the whole path for the root's set. */
    private static String keptPath(ListedCodeFile member) {
        String path = member.path().value();
        String prefix = member.setFolder() + "/";
        return !member.setFolder().isEmpty() && path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    /** Gives every folder beneath {@code folder} its drawn name and every file in it and beneath it its own. */
    private static void name(Folder folder, Map<OccurrenceId, String> fileNames) {
        Set<String> taken = new HashSet<>();
        for (Branch branch : folder.branches) {
            String name = branch.name;
            int times = 0;
            while (CopyNames.keysOf(name).stream().anyMatch(taken::contains)) {
                times++;
                name = CopyNames.folderNameWithSuffixes(branch.name, "~" + branch.lowest.value(), times);
            }
            taken.addAll(CopyNames.keysOf(name));
            branch.drawn = name;
        }
        fileNames.putAll(CopyNames.namesIn(folder.files, taken, false));
        folder.branches.forEach(branch -> name(branch.inside, fileNames));
    }

    /** One drawn folder: the folders and the files directly in it. */
    private static final class Folder {

        final List<Branch> branches = new ArrayList<>();
        final List<CopyNames.Named> files = new ArrayList<>();

        /**
         * The folder here that {@code element} names, made by the first member beneath it, which is the one with the
         * lowest occurrence id because members arrive in ascending order.
         */
        Branch branchFor(String element, OccurrenceId member) {
            String made = CopyNames.folderName(element);
            Set<String> keys = CopyNames.keysOf(made.isEmpty() ? "" : made);
            for (Branch branch : branches) {
                if (branch.keys.stream().anyMatch(keys::contains)) {
                    return branch;
                }
            }
            Branch branch = new Branch(made.isEmpty() ? String.valueOf(member.value()) : made, keys, member);
            branches.add(branch);
            return branch;
        }
    }

    /** A folder as drawn: its name, the keys that tell it apart, the lowest member beneath it and what lies inside. */
    private static final class Branch {

        final String name;
        final Set<String> keys;
        final OccurrenceId lowest;
        final Folder inside = new Folder();
        String drawn;

        Branch(String name, Set<String> keys, OccurrenceId lowest) {
            this.name = name;
            this.keys = keys;
            this.lowest = lowest;
        }
    }

    /** The folders a member's file lies beneath, outermost first. */
    private record Placement(List<Branch> chain) {}
}
