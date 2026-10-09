package io.algernon.vespera;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Every class the build compiled from {@code src/main}, with what its compiled form holds, for a test
 * that asks what the shipped code names (ADR-209 sections 2 and 3).
 *
 * <p>Read off the compiled classes and not off the sources, as {@code OnlyTheFirstReadHashesAFileTest}
 * reads them: a comment that names a table or a type is not in a class file, and a statement written as
 * several literals joined by {@code +} is one text in it, because the compiler joins them. A class is
 * found by scanning, so one added later is read without being named here. Test classes are left out by
 * where they were compiled to.
 */
final class ShippedClasses {

    private static final String SHIPPED_PACKAGE = "io.algernon.vespera";

    private ShippedClasses() {
    }

    /**
     * Every name each shipped class holds, by class name in order: the types it refers to, the
     * descriptors of its fields and methods and of the ones it calls, and its string texts.
     */
    static Map<String, List<String>> namesByClass() throws ClassNotFoundException, IOException {
        return byClass(entry -> entry instanceof Utf8Entry text ? text.stringValue() : null);
    }

    /**
     * The string texts each shipped class holds, by class name in order, and nothing else: no field,
     * method or type name. A text built at run time from fixed parts and values is held as its fixed
     * parts, with a mark where each value goes.
     */
    static Map<String, List<String>> stringsByClass() throws ClassNotFoundException, IOException {
        return byClass(entry -> entry instanceof StringEntry text ? text.stringValue() : null);
    }

    /**
     * The characters each shipped class appends to a text it is building, by class name in order: every
     * character written as a constant and handed straight to a method named {@code append} that takes
     * one {@code char}, which is how {@code StringBuilder.append('x')} is compiled. A character put in a
     * variable first is not among them, and neither is one passed to any other method.
     */
    static Map<String, List<String>> appendedCharactersByClass() throws ClassNotFoundException, IOException {
        return readByClass(ShippedClasses::appendedCharactersOf);
    }

    /** The module a shipped class belongs to: the package segment after {@code io.algernon.vespera}. */
    static String moduleOf(String className) {
        String rest = className.substring(SHIPPED_PACKAGE.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? "" : rest.substring(0, dot);
    }

    private static Map<String, List<String>> byClass(Function<PoolEntry, String> kept)
            throws ClassNotFoundException, IOException {
        return readByClass(compiled -> {
            List<String> entries = new ArrayList<>();
            for (PoolEntry entry : compiled.constantPool()) {
                String text = kept.apply(entry);
                if (text != null) {
                    entries.add(text);
                }
            }
            return entries;
        });
    }

    private static List<String> appendedCharactersOf(ClassModel compiled) {
        List<String> appended = new ArrayList<>();
        for (MethodModel method : compiled.methods()) {
            Integer loaded = null;
            for (CodeElement element : method.code().map(CodeModel::elementList).orElse(List.of())) {
                // A label or a line number sits between two instructions and is neither.
                if (!(element instanceof Instruction)) {
                    continue;
                }
                if (loaded != null
                        && element instanceof InvokeInstruction call
                        && call.name().equalsString("append")
                        && call.typeSymbol().parameterList().equals(List.of(ConstantDescs.CD_char))) {
                    appended.add(String.valueOf((char) loaded.intValue()));
                }
                loaded = element instanceof ConstantInstruction constant
                                && constant.constantValue() instanceof Integer value
                        ? value
                        : null;
            }
        }
        return appended;
    }

    private static Map<String, List<String>> readByClass(Function<ClassModel, List<String>> read)
            throws ClassNotFoundException, IOException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        Map<String, List<String>> held = new TreeMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(SHIPPED_PACKAGE)) {
            Class<?> type =
                    Class.forName(candidate.getBeanClassName(), false, Thread.currentThread().getContextClassLoader());
            if (compiledFromTheTestTree(type)) {
                continue;
            }
            held.put(type.getName(), read.apply(compiledFormOf(type)));
        }
        if (held.isEmpty()) {
            throw new IllegalStateException(
                    "the scan found no shipped class, so an answer naming no offender would be an empty scan");
        }
        return held;
    }

    private static boolean compiledFromTheTestTree(Class<?> type) {
        return type.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString()
                .contains("test-classes");
    }

    private static ClassModel compiledFormOf(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("the compiled class of " + type.getName() + " could not be read");
            }
            return ClassFile.of().parse(in.readAllBytes());
        }
    }
}
