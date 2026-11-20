package io.algernon.vespera.engine.deliverable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What stage 6b draws (ADR-434 §1, §2): the HTML tree, named {@value #TREE}, and each plugin's rendering, named by the
 * plugin's name, read from the profile's {@code outputs}. {@code deliverable} reads no {@code Profile} (ADR-110):
 * {@code pipeline} hands it the key's text and this reads it.
 *
 * <p><b>Read so</b>: the value split at {@code ,}, each piece stripped of the blanks around it and lower-cased, {@code
 * Locale.ROOT}, an empty piece skipped, a name written twice kept once at its first place. A key unset, blank or
 * naming nothing reads as {@link #DEFAULT}, what is drawn without the key.
 */
public final class Outputs {

    /** The name of the HTML tree among the outputs. */
    public static final String TREE = "tree";

    /** What is drawn where the key is unset, blank or names nothing: the tree, Confluence and the website. */
    public static final Outputs DEFAULT = new Outputs(List.of(TREE, "confluence", "website"));

    private final List<String> names;

    private Outputs(List<String> names) {
        this.names = List.copyOf(names);
    }

    /** Reads {@code value}, the profile's {@code outputs}, or {@code null} where it is unset. */
    public static Outputs read(String value) {
        Set<String> read = new LinkedHashSet<>();
        if (value != null) {
            for (String piece : value.split(",")) {
                String name = piece.strip().toLowerCase(Locale.ROOT);
                if (!name.isEmpty()) {
                    read.add(name);
                }
            }
        }
        return read.isEmpty() ? DEFAULT : new Outputs(new ArrayList<>(read));
    }

    /** The tree and every one of {@code plugins}, which is what a drawing made without a choice draws. */
    static Outputs everyOf(List<Plugin> plugins) {
        List<String> all = new ArrayList<>();
        all.add(TREE);
        plugins.stream().map(Plugin::name).forEach(all::add);
        return new Outputs(all);
    }

    /** The names read, in the order they were first written. */
    public List<String> names() {
        return names;
    }

    /** Whether the HTML tree is drawn. */
    public boolean drawsTheTree() {
        return names.contains(TREE);
    }

    /** Whether {@code plugin}'s rendering is drawn. */
    public boolean draws(Plugin plugin) {
        return names.contains(plugin.name());
    }

    /** Why these outputs cannot be drawn by {@code plugins}, and what to do; empty where they can (ADR-434 §3). */
    public Optional<Refusal> refusal(List<Plugin> plugins) {
        List<Plugin> inOrder = plugins.stream().sorted(Comparator.comparing(Plugin::name)).toList();
        List<String> known = new ArrayList<>();
        known.add(TREE);
        inOrder.forEach(plugin -> known.add(plugin.name()));
        List<String> unknown = names.stream().filter(name -> !known.contains(name)).toList();
        if (!unknown.isEmpty()) {
            return Optional.of(new Refusal(
                    "outputs names " + listed(unknown) + ", which " + (unknown.size() == 1 ? "is no output" : "are no outputs")
                            + " Vespera draws: it knows " + listed(known),
                    "name outputs among those in profile.yaml, or leave it unset for tree, confluence and website"));
        }
        if (!drawsTheTree()) {
            List<String> fromTheTree = names.stream()
                    .filter(name -> inOrder.stream()
                            .anyMatch(plugin -> plugin.name().equals(name) && plugin.drawsFromTheTree()))
                    .toList();
            if (!fromTheTree.isEmpty()) {
                return Optional.of(new Refusal(
                        "outputs names " + listed(fromTheTree) + " but not tree, and "
                                + (fromTheTree.size() == 1 ? "it draws" : "they draw") + " from the HTML tree",
                        "add tree to outputs in profile.yaml, or take " + listed(fromTheTree) + " out of it"));
            }
        }
        return Optional.empty();
    }

    /** Names written {@code a}, {@code a and b}, {@code a, b and c}. */
    static String listed(List<String> names) {
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }

    /** A reason the outputs cannot be drawn and the advice that mends them (ADR-434 §3). */
    public record Refusal(String reason, String advice) {}
}
