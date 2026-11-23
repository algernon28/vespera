# ADR-443 — Picocli's refusals and usage carry no colour, whatever the environment

- **Date**: 2026-11-22
- **Status**: accepted on 2026-11-22.
- **Decided**: under the operator's delegation, on a defect of [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §4, escape codes in the lines it calls words alone: that the command line the jar parses through Spring draws no colour, whatever picocli would read of the environment (§1); that the fix lies where the lines are made, the control panel's child keeping its environment (§2); and that `HelpAndVersion` and `ServeCommand` keep picocli's own choice (§3).
- **Keeps**: [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §4, whose picocli refusals and usage are logged on the operator logger as their words alone: this holds those words against picocli's colour, and amends nothing of it. [ADR-340](0340-the-control-panel-is-a-home-page-under-a-top-bar-with-every-value-in-one-drawer-and-a-folder-the-profile-names-is-not-walked.md) §6 and [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1, the child's environment and encodings. [ADR-436](0436-the-jar-and-the-control-panel-use-no-working-directory-nothing-names-and-browse-chooses-one.md) §1 and [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §8, `--help`, `--version` and `vespera serve` answered before Spring.

## Context

ADR-364 §4 has `VesperaCommand.commandLine()` hand picocli's writers to `OperatorLines.out()` and `OperatorLines.err()`, so that a refusal of picocli's, `Unknown option: '--bogus-option'` or a missing parameter, and the usage after it, are logged on the operator logger a line at a time, as their words alone. That logger writes to the console, to `vespera.log` and to the invocation's output file, which the control panel's output panel shows; a child of the control panel's has its two streams merged into one, which the window holds and shows until there is an output file (ADR-364 §7).

Picocli 4.7.7 colours its own lines, the refusal through its colour scheme's `errorText` and the usage's command, option and parameter names, wherever its `Help.Ansi.AUTO` is enabled. It reads that from the environment, not from the stream it writes to: `NO_COLOR` turns it off, `CLICOLOR_FORCE` on, and on Windows an installed Jansi console on; then `CLICOLOR=0` or `ConEmuANSI=OFF` turns it off. Otherwise, off Windows, it is on where the JVM's console is a terminal; on Windows, it is on where `TERM` starts with `xterm` or holds `cygwin`, or `OSTYPE` is set at all, and where the console is a terminal and `ANSICON`, `CLICOLOR=1` or `ConEmuANSI=ON` is set. Git Bash sets both. The system property `picocli.ansi` at `true` turns it on too. Measured on Java 26 with standard output sent to a file: `AUTO` on with `TERM=xterm-256color`, on with `OSTYPE=msys`, off with neither.

So on Windows, from such a shell, a refusal written to a pipe or a file still carries escape codes: on the console, in `vespera.log`, in the output file, and, since the control panel's child inherits the operator's environment less the variables ADR-340 §6 removes, in the output panel, a `TextArea` that shows them as characters.

## Decision

### 1. The command line the jar parses draws no colour

**The `CommandLine` that `VesperaCommand.commandLine()` builds carries `CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF)`**, set with `setColorScheme` on the built command line, after its subcommands exist, so that `run`, `label`, `publish` and `serve` carry it too: picocli's `setColorScheme` passes a scheme on to the subcommands already added, and a refusal or a usage is drawn with the scheme of the command it is about.

`Ansi.OFF` is enabled nowhere, so neither `TERM`, `OSTYPE`, `CLICOLOR_FORCE`, `ANSICON`, a console nor `picocli.ansi` turns colour on for it. Each line picocli writes through the operator logger is its words alone, as ADR-364 §4 has it, whatever started the jar.

### 2. The child keeps its environment

`ChildCommand` is unchanged: the child of the control panel still inherits `TERM` and `OSTYPE`, which a tool other than picocli may read, and is given neither `NO_COLOR` nor `-Dpicocli.ansi=false`.

### 3. What keeps picocli's own choice

**`HelpAndVersion`**, which answers `vespera --help` and `--version` before Spring (ADR-436 §1) and prints straight to standard output, outside logback, and which the control panel never starts; and **`ServeCommand`'s parse**, also before Spring and outside logback (ADR-333 §8, ADR-364 §4). Neither writes on the operator logger, so neither reaches `vespera.log`, an output file or the output panel; each keeps picocli's default scheme. `HelpAndVersion`'s usage and version are coloured on a terminal that asks for it; `ServeCommand` writes only the refused argument's message, plain whatever the scheme, and draws no usage.

## Alternatives refused

- **Remove `TERM` and `OSTYPE` from the child's environment, or give it `NO_COLOR` or `-Dpicocli.ansi=false`.** It mends the control panel's child alone: a jar started from Git Bash still logs escape codes into `vespera.log` and its output file. `NO_COLOR` would beat every other switch of picocli's, but is read for the whole process, by tools other than picocli too; and the removed variables are the operator's, which another tool may need.
- **Strip escape codes in `OperatorLines`.** It would clean a line after it was made, for one library's codes, and leave the scheme saying colour; the source of the lines is the place that decides they are plain.
- **`Ansi.OFF` for `HelpAndVersion` and `ServeCommand` too.** It would change `HelpAndVersion`'s output alone, `ServeCommand`'s message being plain already, and those lines reach the terminal alone, where colour is what the operator's terminal asked for.

## Consequences

- **A refusal of picocli's and the usage after it are plain text everywhere they are written**: the console, `vespera.log`, the output file and the output panel, whatever the shell or the control panel's environment.
- **The usage bare `vespera` prints** (ADR-364 §4), drawn by `VesperaCommand.call` from the same command line, is plain too.
- **`vespera --help` on a terminal keeps its colour** (§3).
- **No run id moves**: `pipeline` is named by no stage's version.

## Tests

| Test | Holds |
|---|---|
| `pipeline.PicoclisOwnLinesCarryNoColourTest.aRefusedOptionAndItsUsageCarryNoColourWhereColourIsForcedOn` | §1: with `picocli.ansi` at `true`, put back after, `run --no-such-option` executed on `VesperaCommand.commandLine()` logs on the operator logger no line holding the escape character, the refusal `Unknown option: '--no-such-option'` as a line whole and the usage of `vespera run` after it; and the command line and its four subcommands, `run`, `label`, `publish` and `serve`, each carry `Ansi.OFF` |

What no unit test shows: an environment variable, `TERM`, `OSTYPE` or `CLICOLOR_FORCE`, which a test cannot set in its own JVM; `Ansi.OFF` is read before any of them, and `picocli.ansi` stands for them. `CliExitIT` launches the jar without `TERM` and `OSTYPE`, and so shows nothing of §1.

## What the implementation owes

In `VesperaCommand.commandLine()`, `setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF))` on the built command line, beside `setOut` and `setErr`, so that its four subcommands carry it. Nothing else under `src/main/java`.
