package io.algernon.vespera.deliverable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Where {@link Deliverable} asks for the code sets of the run and, one set at a time, their members (ADR-296 §8), so
 * it can be given the reader of {@code corpus} through {@code pipeline} without naming it.
 *
 * <p>{@link #sets} answers in the order of the sets' folders, as {@code code_set}'s primary key reads them, and
 * {@link #membersOf} answers one set's members in ascending occurrence id. Both may be asked more than once and must
 * give the same answer each time. Nothing here holds a set's members: the tree holds one set's at a time (ADR-296
 * §3.4).
 */
public interface ListedCodeSets {

    /** A run with no code set: the tree has no {@code code/}, no {@code code.csv} and no section of the index. */
    ListedCodeSets NONE = reading(List::of, set -> List.of());

    /** The run's sets in the order of their folders. */
    List<ListedCodeSet> sets();

    /** The members of {@code set} in ascending occurrence id. */
    List<ListedCodeFile> membersOf(ListedCodeSet set);

    /** Code sets that answer by calling {@code sets} and {@code members}. */
    static ListedCodeSets reading(
            Supplier<List<ListedCodeSet>> sets, Function<ListedCodeSet, List<ListedCodeFile>> members) {
        return new ListedCodeSets() {
            @Override
            public List<ListedCodeSet> sets() {
                return sets.get();
            }

            @Override
            public List<ListedCodeFile> membersOf(ListedCodeSet set) {
                return members.apply(set);
            }
        };
    }
}
