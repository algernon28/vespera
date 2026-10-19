package io.algernon.vespera.deliverable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Reaches {@code io.algernon.vespera.deliverable.CodeSetNames} (ADR-284 §9) by its name, so that
 * {@link CodeSetNamesTest} compiles before the class exists and fails, until it does, on a missing class.
 *
 * <p>The class it expects, public and final, with these public members:
 *
 * <ul>
 *   <li>{@code static final String FILE_NAME = "code-set-names.yaml"};
 *   <li>{@code record Entry(String folder, String name)}: one entry of the file, as written;
 *   <li>{@code static List<Entry> parse(String yaml)}: the entries of the file's text, in its order, none for a
 *       text holding nothing, or an {@code IllegalArgumentException} naming {@code FILE_NAME} where the file is not
 *       a list of folders and names;
 *   <li>{@code static List<Entry> read(Path workingDirectory)}: the same, read from {@code FILE_NAME} in the working
 *       directory, none where it is absent.
 * </ul>
 */
final class CodeSetNamesByItsName {

    private static final String CLASS_NAME = "io.algernon.vespera.deliverable.CodeSetNames";

    private CodeSetNamesByItsName() {
    }

    static String fileName() {
        try {
            return (String) theClass().getField("FILE_NAME").get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(CLASS_NAME + " has no public FILE_NAME", e);
        }
    }

    /** The entries of {@code yaml}, each as {@code folder=name}. */
    static List<String> parse(String yaml) {
        return entries(invoke("parse", yaml));
    }

    /** The entries of the file in {@code workingDirectory}, each as {@code folder=name}. */
    static List<String> read(Path workingDirectory) {
        return entries(invoke("read", workingDirectory));
    }

    private static List<String> entries(Object entries) {
        return ((List<?>) entries).stream().map(entry -> {
            try {
                return entry.getClass().getMethod("folder").invoke(entry) + "="
                        + entry.getClass().getMethod("name").invoke(entry);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("an entry of " + CLASS_NAME + " carries no folder or name", e);
            }
        }).toList();
    }

    private static Object invoke(String name, Object argument) {
        Method method = Arrays.stream(theClass().getMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(CLASS_NAME + " has no public method " + name + " of one parameter"));
        try {
            return method.invoke(null, argument);
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
            throw new IllegalStateException(CLASS_NAME + " is not built yet (ADR-284 §9)", e);
        }
    }
}
