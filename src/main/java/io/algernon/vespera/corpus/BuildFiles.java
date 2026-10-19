package io.algernon.vespera.corpus;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which file names mark the folder a build begins in (ADR-284 section 1): the list of build files built into
 * {@code corpus}, as the generators' marks are, and its version, recorded among stage 1's configuration consumed
 * (section 10). A name alone says it; no byte is read, and a file's detected format and verdicts do not matter.
 */
final class BuildFiles {

    private BuildFiles() {}

    /** The version of the list below; a change to it is a new version. */
    static final String VERSION = "v1";

    /** The names that are a build file whole, in lower case. */
    private static final Set<String> NAMES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "cmakelists.txt", "makefile", "gnumakefile", "configure.ac", "package.json", "cargo.toml", "go.mod",
            "pyproject.toml", "setup.py", "build.bazel", "module.bazel", "build.xml", "build.sbt", "meson.build",
            "gemfile", "composer.json", "mix.exs", "package.swift", "pubspec.yaml", "project.clj", "deps.edn",
            "stack.yaml", "dune-project", "rebar.config", "sconstruct");

    /** The endings that make a name a build file when at least one character comes before them. */
    private static final List<String> ENDINGS =
            List.of(".sln", ".csproj", ".vbproj", ".fsproj", ".vcxproj", ".vcproj", ".gemspec", ".cabal");

    /** Whether {@code name}, the last element of an occurrence's path, is a build file's. */
    static boolean isBuildFile(String name) {
        String folded = name.toLowerCase(Locale.ROOT);
        if (NAMES.contains(folded)) {
            return true;
        }
        for (String ending : ENDINGS) {
            if (folded.length() > ending.length() && folded.endsWith(ending)) {
                return true;
            }
        }
        return false;
    }
}
