package io.algernon.vespera.engine.pipeline;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Map;

/**
 * Whether a display is present where this process runs, and why (ADR-362 §1). Asked from the system alone: a window
 * server on macOS, a visible window station on Windows, {@code DISPLAY} or {@code WAYLAND_DISPLAY} elsewhere. No
 * graphics card, driver or OpenGL is asked of, and the operator cannot force a display present: {@code --headless},
 * which reaches here as the JVM's system property {@value #HEADLESS_PROPERTY}, is the one override, and it forces none.
 * It holds no rule: it decides no verdict and shapes no stage's output.
 */
public final class DisplayPresence {

    /** The JVM system property {@code EntryPoint} sets for {@code --headless}; read with {@code System.getProperty} alone. */
    public static final String HEADLESS_PROPERTY = "vespera.headless";

    /** One native question, answered by the system's own library; a test answers it on any system. */
    @FunctionalInterface
    public interface NativeDisplay {
        /** Whether the system has a display; throws where the call cannot be made or its library is not found. */
        boolean present() throws Throwable;
    }

    /**
     * The answer: whether a display is present, why, as a reason in §1's table, and the class name of what a native
     * call threw, {@code null} unless the reason is {@code not-answered}.
     */
    public record Answer(boolean present, String reason, String thrown) {

        /** The reason in words, for the line a start with no display says. */
        public String inWords() {
            return switch (reason) {
                case "window-server" -> "a window server answers";
                case "no-window-server" -> "no window server answers";
                case "visible-window-station" -> "the window station is visible";
                case "hidden-window-station" -> "the window station is hidden";
                case "display-variable" -> "DISPLAY or WAYLAND_DISPLAY is set";
                case "no-display-variable" -> "neither DISPLAY nor WAYLAND_DISPLAY is set";
                case "headless" -> "started with --headless";
                default -> "whether a display is present could not be asked: " + thrown;
            };
        }
    }

    private DisplayPresence() {
    }

    /** Decides from what it is given: the system's name as {@code os.name} gives it, its environment, and the two native seams. */
    public static Answer of(String osName, Map<String, String> environment, boolean headless,
                            NativeDisplay windows, NativeDisplay mac) {
        if (headless) {
            return new Answer(false, "headless", null);
        }
        try {
            return switch (HostSystem.named(osName)) {
                case MAC -> ask(mac, "window-server", "no-window-server");
                case WINDOWS -> ask(windows, "visible-window-station", "hidden-window-station");
                case LINUX -> {
                    boolean set = isSet(environment, "DISPLAY") || isSet(environment, "WAYLAND_DISPLAY");
                    yield new Answer(set, set ? "display-variable" : "no-display-variable", null);
                }
            };
        } catch (Throwable thrown) {
            return new Answer(false, "not-answered", thrown.getClass().getName());
        }
    }

    /** The system this process runs on, with {@code --headless} read from the JVM's property of that name. */
    public static Answer ofThisProcess() {
        return of(System.getProperty("os.name", ""), System.getenv(),
                System.getProperty(HEADLESS_PROPERTY) != null,
                DisplayPresence::windowStationIsVisible, DisplayPresence::windowServerAnswers);
    }

    private static Answer ask(NativeDisplay seam, String present, String none) throws Throwable {
        boolean answered = seam.present();
        return new Answer(answered, answered ? present : none, null);
    }

    private static boolean isSet(Map<String, String> environment, String name) {
        String value = environment.get(name);
        return value != null && !value.isBlank();
    }

    /** Windows: {@code GetProcessWindowStation}'s {@code UOI_FLAGS} carry {@code WSF_VISIBLE}. */
    private static boolean windowStationIsVisible() throws Throwable {
        final int uoiFlags = 1;
        final int wsfVisible = 1;
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", arena);
            MethodHandle station = linker.downcallHandle(
                    user32.find("GetProcessWindowStation").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            MethodHandle information = linker.downcallHandle(
                    user32.find("GetUserObjectInformationW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MemorySegment handle = (MemorySegment) station.invokeExact();
            if (handle.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("the process has no window station");
            }
            // USEROBJECTFLAGS: BOOL fInherit, BOOL fReserved, DWORD dwFlags
            MemorySegment flags = arena.allocate(12);
            MemorySegment needed = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) information.invokeExact(handle, uoiFlags, flags, 12, needed);
            if (ok == 0) {
                throw new IllegalStateException("the window station's flags could not be read");
            }
            return (flags.get(ValueLayout.JAVA_INT, 8) & wsfVisible) != 0;
        }
    }

    /** macOS: {@code CGSessionCopyCurrentDictionary} answers a dictionary, which is released, rather than none. */
    private static boolean windowServerAnswers() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup graphics = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", arena);
            SymbolLookup foundation = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena);
            MethodHandle copy = linker.downcallHandle(
                    graphics.find("CGSessionCopyCurrentDictionary").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            MethodHandle release = linker.downcallHandle(
                    foundation.find("CFRelease").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            MemorySegment dictionary = (MemorySegment) copy.invokeExact();
            if (dictionary.equals(MemorySegment.NULL)) {
                return false;
            }
            release.invokeExact(dictionary);
            return true;
        }
    }
}
