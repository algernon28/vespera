package io.algernon.vespera.deliverable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/**
 * Reaches {@code io.algernon.vespera.deliverable.CodeSetNames} (ADR-284 §8) by its name, so that
 * {@link CodeSetNamesTest} compiles before the class exists and fails, until it does, on a missing class rather
 * than leaving the build unable to compile.
 *
 * <p>The class it expects, public and final, with these public members:
 *
 * <ul>
 *   <li>{@code static final String FILE_NAME = "code-set-names.csv"};
 *   <li>{@code static Applied apply(String csv, SequencedMap<String, String> defaultNameByFolder)}: the file's text
 *       applied to the sets of a run, given by folder in canonical order with their default names;
 *   <li>{@code static Applied read(Path workingDirectory, SequencedMap<String, String> defaultNameByFolder)}: the
 *       same, read from {@code FILE_NAME} in the working directory, an absent file renaming nothing;
 *   <li>{@code static boolean accepts(String name)}: whether a name may be given to a set;
 *   <li>{@code record Applied(SequencedMap<String, String> nameByFolder, List<String> ignoredFolders)}: every set's
 *       effective name, in the order given, and the folder of each row ignored, as the row spells it, in the
 *       file's order.
 * </ul>
 */
final class CodeSetNamesByItsName {

    private static final String CLASS_NAME = "io.algernon.vespera.deliverable.CodeSetNames";

    /** What {@code Applied} carries, copied out of it. */
    record Applied(Map<String, String> nameByFolder, List<String> ignoredFolders) {}

    private CodeSetNamesByItsName() {
    }

    static String fileName() {
        try {
            return (String) theClass().getField("FILE_NAME").get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(CLASS_NAME + " has no public FILE_NAME", e);
        }
    }

    static Applied apply(String csv, SequencedMap<String, String> defaultNameByFolder) {
        return copied(invoke("apply", csv, defaultNameByFolder));
    }

    static Applied read(Path workingDirectory, SequencedMap<String, String> defaultNameByFolder) {
        return copied(invoke("read", workingDirectory, defaultNameByFolder));
    }

    static boolean accepts(String name) {
        return (Boolean) invoke("accepts", name);
    }

    /** {@code folders}, each paired with the name it is given, in order. */
    static SequencedMap<String, String> defaults(String... folderThenName) {
        SequencedMap<String, String> defaults = new LinkedHashMap<>();
        for (int i = 0; i < folderThenName.length; i += 2) {
            defaults.put(folderThenName[i], folderThenName[i + 1]);
        }
        return defaults;
    }

    @SuppressWarnings("unchecked")
    private static Applied copied(Object applied) {
        try {
            Object names = applied.getClass().getMethod("nameByFolder").invoke(applied);
            Object ignored = applied.getClass().getMethod("ignoredFolders").invoke(applied);
            return new Applied(new LinkedHashMap<>((Map<String, String>) names), List.copyOf((List<String>) ignored));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("what " + CLASS_NAME + " applied carries no nameByFolder or ignoredFolders", e);
        }
    }

    private static Object invoke(String name, Object... arguments) {
        Method method = Arrays.stream(theClass().getMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == arguments.length)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        CLASS_NAME + " has no public method " + name + " of " + arguments.length + " parameters"));
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(CLASS_NAME + "." + name + " is not public", e);
        }
    }

    private static Class<?> theClass() {
        try {
            return Class.forName(CLASS_NAME);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(CLASS_NAME + " is not built yet (ADR-284 §8)", e);
        }
    }
}
