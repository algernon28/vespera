#!/usr/bin/env groovy
// Drives one work item from undecided to gated through the five agents in .claude/agents/:
// analyst settles it, spec-implementer builds it, two testers say what is true and what nothing
// defends, debugger repairs what came back red, architect gates the result.
//
// Why this shells out instead of calling anything: the Claude Code Workflow tool runs JavaScript in
// a sandbox with no Groovy runtime, so a Groovy port cannot be a workflow script. It is an
// orchestrator in its own right, and `claude -p --agent <name>` is the only seam it needs -- one
// process per agent, its own context, its JSON on stdout. The agent definitions in .claude/agents/
// are what constrain each one; this script chooses the order and reads the results.
//
// It never commits, pushes or merges, and neither may anything it starts (AGENTS.md). Every process
// it runs leaves its prompt and its raw output under the transcripts directory, so a run that went
// wrong can be read rather than re-run.
//
// Usage:
//   groovy .claude/workflows/settle-and-land.groovy "stage 1: the broken verdict" [more items...]
//
//   --rounds=N              implement/verify/gate rounds before stopping for a person (default 2)
//   --model=<alias>         model for every agent, e.g. opus, sonnet (default: each agent's own)
//   --permission-mode=<m>   claude permission mode (default acceptEdits; bypassPermissions is what
//                           makes a run genuinely unattended, and is the operator's call to make)
//   --transcripts=<dir>     where prompts and raw output land (default target/settle-and-land)
//   --timeout-minutes=N     per-agent ceiling (default 45)
//   --dry-run               print what each phase would run, invoke nothing
//
// Exit code is 0 only when every item came back ready-to-merge.

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Field final File REPO = new File(System.getProperty('user.dir'))
@Field final boolean WINDOWS = System.getProperty('os.name').toLowerCase().contains('windows')

// Every agent starts with no memory of this repository beyond what it reads, so the standing rules
// travel in every prompt rather than being assumed.
@Field final String HOUSE_RULES = '''
Read AGENTS.md first; it is the single source of truth and everything it points at is authoritative over it.
CONTEXT.md is binding vocabulary, not background -- keep the words its _Avoid_ lines reject out of identifiers, tests and messages.
Do not commit, push, merge or open a pull request. Leave the working tree for a person to review, and say what you would have committed and why.
Report test results as numbers: Tests run / Failures / Errors / Skipped. A skipped test is not a passing test.
'''.trim()

@Field Map options = [
        rounds        : 2,
        model         : null,
        permissionMode: 'acceptEdits',
        transcripts   : 'target/settle-and-land',
        timeoutMinutes: 45,
        dryRun        : false,
]
@Field List<String> items = []
@Field File transcriptDir = null
@Field int agentsRun = 0

// --- Schemas ---------------------------------------------------------------------------------
// There is no structured-output tool in print mode, so each schema travels in the prompt and the
// reply is expected to end in one fenced json block. A schema that is not honoured is a failed
// stage rather than a silently empty result: runAgent returns null, the console row says which
// agent and where its raw output is, and the caller stops instead of continuing on nothing.

@Field final Map SETTLED = [
        decided      : 'boolean -- false when the item could not be settled without a person',
        summary      : 'string -- what was decided, in two or three sentences',
        recordedAt   : 'array of string -- where the decision now lives: an ADR path, a spec, a resolution comment url',
        testsAdded   : 'array of string -- test files or methods written to pin the decision, each expected to fail until the code exists',
        openQuestions: 'array of string',
]

@Field final Map IMPLEMENTED = [
        blocked       : 'boolean -- true when the decision was not settled enough to implement, or the tests are wrong',
        summary       : 'string',
        filesChanged  : 'array of string',
        blockedBecause: 'string -- exactly what is missing, when blocked is true',
]

@Field final Map BUILD = [
        green   : 'boolean -- false if anything failed, errored, or did not run at all',
        testsRun: 'integer',
        failures: 'integer',
        errors  : 'integer',
        skipped : 'integer',
        report  : 'string -- what failed and why, with the real cause from target/surefire-reports rather than the truncated console line',
]

@Field final Map COVERAGE = [
        undefendedDecisions: 'array of object, each {decision: string -- the ADR id or spec clause nothing checks, whatIsUnchecked: string, proposedTest: string -- the test as code, for someone else to commit}',
]

@Field final Map REPAIRED = [
        fixed          : 'boolean',
        rootCause      : 'string -- the cause, not the symptom; say so plainly if it was not found',
        regressionTest : 'string -- the new test that goes red on this defect, or why none was added',
]

@Field final Map VERDICT = [
        verdict    : 'string -- one of ready-to-merge, request-changes, route-to-analyst',
        evidence   : 'string -- each gate item with the numbers behind it',
        amendments : 'array of object, each {addressee: string -- spec-implementer or analyst, what: string -- file:line, what is wrong, what it should be, and the decision or test it violates}',
        notChecked : 'string -- gates that could not be run, and why; a silent skip is worse than an admitted one',
]

// --- Arguments -------------------------------------------------------------------------------

// Every option carries its value with an '=' and is refused without one. The space form is what
// makes a mistyped option dangerous rather than merely wrong: `--model opus` would leave the model
// unset and hand 'opus' to the run as a work item, which is five agents deep into the wrong job
// before anyone reads the banner. A missing or empty value is an exit 2 naming the option, like
// every other argument error here.
args.each { String arg ->
    if (!arg.startsWith('--')) {
        items << arg
        return
    }
    int equals = arg.indexOf('=')
    String name = equals < 0 ? arg.substring(2) : arg.substring(2, equals)
    String value = equals < 0 ? null : arg.substring(equals + 1)
    switch (name) {
        case 'rounds' -> options.rounds = wholeNumber(name, value)
        case 'timeout-minutes' -> options.timeoutMinutes = wholeNumber(name, value)
        case 'model' -> options.model = required(name, value)
        case 'permission-mode' -> options.permissionMode = required(name, value)
        case 'transcripts' -> options.transcripts = required(name, value)
        case 'dry-run' -> {
            if (value != null) {
                fail("--dry-run takes no value, and was given '${value}'")
            }
            options.dryRun = true
        }
        default -> fail("unknown option --${name}. Read the header of this script for the ones there are.")
    }
}

if (items.isEmpty()) {
    fail('settle-and-land takes the work items as arguments: a ticket number, a spec section, or a description.')
}
if (!new File(REPO, 'AGENTS.md').exists()) {
    fail("run this from the repository root: no AGENTS.md in ${REPO}")
}
if (options.rounds < 1) {
    fail('--rounds has to be at least 1')
}

// After the dry run, not before it: a run that invokes nothing has nothing to write down, and an
// empty timestamped directory per dry run is litter that reads like a run that happened.
if (options.dryRun) {
    printPlan()
    System.exit(0)
}

transcriptDir = new File(REPO, "${options.transcripts}/${LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyyMMdd-HHmmss'))}")
if (!transcriptDir.mkdirs() && !transcriptDir.isDirectory()) {
    fail("could not create the transcripts directory at ${transcriptDir} -- is --transcripts a path this can write?")
}

println "settle-and-land: ${items.size()} item(s), up to ${options.rounds} round(s) each"
println "transcripts: ${relative(transcriptDir)}"
println "nothing is committed, pushed or merged -- the working tree is left for review"
println ''

// --- The run ---------------------------------------------------------------------------------

List results = items.withIndex().collect { String item, int index -> settleAndLand(item, index + 1, items.size()) }

println ''
println '=' * 78
results.each { Map r ->
    String verdict = r.gate?.verdict ?: "stopped at ${r.stoppedAt}"
    println "  ${verdict.padRight(18)} ${trim(r.item, 56)}"
}
println '=' * 78

new File(transcriptDir, 'summary.json').text = JsonOutput.prettyPrint(JsonOutput.toJson([
        items  : results,
        options: options,
        note   : 'Nothing was committed, pushed or merged. The working tree is left for review, per AGENTS.md.',
]))
println "  ${agentsRun} agent run(s), summary at ${relative(new File(transcriptDir, 'summary.json'))}"

boolean allReady = results.every { it.gate?.verdict == 'ready-to-merge' }
if (!allReady) {
    println '  something needs a person: read the verdicts above before touching the tree'
}
System.exit(allReady ? 0 : 1)

// --- Phases ----------------------------------------------------------------------------------

Map settleAndLand(String item, int number, int total) {
    String tag = "${number}/${total}"
    println "-- item ${tag}: ${trim(item, 60)}"

    Map settled = runAgent('analyst', 'Settle', "settle:${number}", SETTLED, """
Work item: ${item}

Settle it. Interrogate what is undecided until it is decided, then write the decision down where it can be cited -- a new
ADR in docs/adr/ carrying its own context, decision and consequences (new decisions continue the numbering; read the
highest id present), a spec, or a resolution comment on the tracker. Then write the tests that pin it, to the
conventions in AGENTS.md: AssertJ inside TestSteps.claim, @Epic and @Feature on the class, @Story and @DisplayName on
each test, @Link(type = "adr") resolved through the Adr map, and report-visible text that stands on its own without this
repository.

Write no production code. The tests you add are expected to fail until someone else writes it.
If a recorded decision already settles the item, say so, cite it, and add only the tests that are missing.
If it cannot be settled without a person, set decided=false and put the question in openQuestions rather than inventing
an answer.
""")

    if (!settled?.decided) {
        println "   stopping at Settle -- building on an unrecorded decision is the one thing this order exists to prevent"
        return [item: item, stoppedAt: 'Settle', settled: settled]
    }

    String recordedAt = (settled.recordedAt ?: []).join(', ') ?: '(see the item)'
    Map implemented = null, build = null, coverage = null, repaired = null, gate = null
    // Which phase an item stopped at, for the summary row. Every break below sets it, because a
    // summary line that names no phase sends a reader to the transcripts directory to guess.
    String stoppedAt = null

    for (int round = 1; round <= options.rounds; round++) {
        String codeAmendments = (gate?.amendments ?: [])
                .findAll { it.addressee == 'spec-implementer' }
                .collect { "- ${it.what}" }
                .join('\n')

        implemented = runAgent('spec-implementer', 'Implement', "implement:${number}.${round}", IMPLEMENTED, """
Work item: ${item}
The decision is recorded at: ${recordedAt}
What it decided: ${settled.summary}
Tests already written against it: ${(settled.testsAdded ?: []).join(', ') ?: '(none listed)'}
${codeAmendments ? "\nAmendments from the architect, round ${round}:\n${codeAmendments}\n" : ''}
Implement it. Read the recorded decision and the tests, write code under src/main, and run ./mvnw test until it passes.

Never edit a test and never edit Markdown. If a test is wrong, stop and say so -- that is the analyst's to settle, not
yours.
If the decision does not say enough to implement without guessing, set blocked=true and name exactly what is missing.
""")

        if (!implemented || implemented.blocked) {
            println "   implementation blocked -- ${implemented?.blockedBecause ?: 'the agent returned nothing usable'}"
            stoppedAt = "Implement, round ${round}"
            break
        }

        // Both testers run at once because both are read-only by construction. Only the first is
        // allowed near Maven, though: two ./mvnw runs sharing one target/ directory is a race that
        // reports as a test failure, and a green build misread as red would send the debugger after
        // a defect nobody has.
        def pool = Executors.newFixedThreadPool(2)
        try {
            def buildCall = pool.submit({
                runAgent('tester', 'Verify', "build:${number}.${round}", BUILD, """
Establish what is true about this build. Run ./mvnw test, and ./mvnw verify only if a Docker daemon is available -- say
which you ran.
Report the numbers exactly as surefire gives them, count skips as unchecked rather than passed, and for anything red get
to the real cause in target/surefire-reports/<class>.txt rather than the truncated console line.
Change nothing.
""")
            } as Callable)
            def coverageCall = pool.submit({
                runAgent('tester', 'Verify', "coverage:${number}.${round}", COVERAGE, """
Work item: ${item}
Recorded at: ${recordedAt}
Files just changed: ${(implemented.filesChanged ?: []).join(', ') ?: '(none listed)'}

Audit what nobody is checking. For the decision above and the code that just landed for it, find the clauses no test
defends, and propose each missing test as code in your report for someone else to commit.
Judge a test by whether it pins a decision rather than an implementation detail, and note any Windows-specific behaviour
asserted without a guard.

Do not run ./mvnw at all: another agent is running the build right now, against the same target/ directory. Read the
tests and the records instead. Change nothing.
""")
            } as Callable)
            build = buildCall.get(options.timeoutMinutes + 5L, TimeUnit.MINUTES)
            coverage = coverageCall.get(options.timeoutMinutes + 5L, TimeUnit.MINUTES)
        } finally {
            pool.shutdown()
        }

        if (build && !build.green) {
            repaired = runAgent('debugger', 'Repair', "repair:${number}.${round}", REPAIRED, """
The build is red after implementing: ${item}
Tests run ${build.testsRun}, failures ${build.failures}, errors ${build.errors}, skipped ${build.skipped}.
What the tester found: ${build.report}

Reproduce it, find the root cause, and fix it. Get a tight feedback loop that already goes red on this before theorising
about it, and leave a regression test behind that would have caught it.
Add new tests freely; never touch an existing one -- a test that contradicts the code is the analyst's to settle. Do not
merge.
""")
        }

        gate = runAgent('architect', 'Gate', "gate:${number}.${round}", VERDICT, """
Review the working tree against what this project has decided, for: ${item}
Recorded at: ${recordedAt}
Build as last measured: ${build ? "run ${build.testsRun}, failures ${build.failures}, errors ${build.errors}, skipped ${build.skipped}" : 'not measured'}
${repaired ? "A repair ran this round. Root cause given as: ${repaired.rootCause}" : ''}
${(coverage?.undefendedDecisions ?: []) ? "The coverage audit calls these decisions undefended: ${coverage.undefendedDecisions.collect { it.decision }.join('; ')}" : ''}

Work your eight-point gate and report the verdict with the numbers behind each item. Read the diff for weakened tests
specifically -- deleted assertions, @Disabled, loosened matchers, tests moved out of the run -- since spec-implementer is
forbidden from touching tests and you are the one who notices when it did.

Do not merge, and do not treat ready-to-merge as an instruction to anyone: this run leaves the tree for a person. It is a
verdict about the change and nothing more.
Address every amendment to exactly one agent: code changes to spec-implementer, anything needing a spec, an ADR, a test
or a vocabulary change to analyst.
""")

        if (!gate) {
            println "   the gate returned nothing usable -- an ungated change is not a finished one"
            stoppedAt = "Gate, round ${round}"
            break
        }
        if (gate.verdict == 'ready-to-merge') {
            break
        }

        List forAnalyst = (gate.amendments ?: []).findAll { it.addressee == 'analyst' }
        if (gate.verdict == 'route-to-analyst' || forAnalyst) {
            Map reSettled = runAgent('analyst', 'Settle', "re-settle:${number}.${round}", SETTLED, """
Work item: ${item}
The architect routed these back to you, because each needs a decision, a test or a vocabulary change rather than a code
change:
${forAnalyst.collect { "- ${it.what}" }.join('\n') ?: '- (the verdict itself was route-to-analyst; read its evidence)'}

Architect evidence: ${gate.evidence}

Settle each one and record it where it can be cited, amending an existing ADR by writing a new one that names it rather
than editing the record in place. Adjust or add the tests that pin what you settled. Write no production code.
""")

            // The amending record is what the next round has to be told about. This project's ADRs
            // are append-only and their pointers run backwards -- the new ADR names the one it
            // amends, never the reverse -- so an implementer handed the old path has no way to
            // discover the amendment from the record it was given. And a re-settle that came back
            // undecided is the Settle stop rule again: it does not become safe to build on by
            // having happened in round two.
            if (!reSettled?.decided) {
                println "   stopping at the re-settle -- ${reSettled ? 'it needs a person' : 'the agent returned nothing usable'}"
                stoppedAt = "Settle, round ${round}"
                settled = reSettled ?: settled
                break
            }
            settled = reSettled
            recordedAt = (settled.recordedAt ?: []).join(', ') ?: '(see the item)'
        }

        if (round == options.rounds) {
            println "   still ${gate.verdict} after ${options.rounds} round(s) -- stopping for a person rather than iterating unattended"
        }
    }

    return [item: item, stoppedAt: stoppedAt, settled: settled, implemented: implemented, build: build,
            undefendedDecisions: coverage?.undefendedDecisions ?: [], repaired: repaired, gate: gate]
}

// --- One agent -------------------------------------------------------------------------------

/**
 * Runs one agent to completion in its own process and returns the object it was asked for, or null.
 *
 * Null is a stage that did not produce a usable answer -- the process failed, timed out, or replied
 * without the json block it was told to end with. Every caller treats null as a stop rather than as
 * an empty result, because the two are not the same thing and only one of them is safe to continue
 * from.
 */
Map runAgent(String agentName, String phaseTitle, String label, Map schema, String body) {
    String prompt = """${HOUSE_RULES}

${body.trim()}

Finish your reply with a single fenced json block and nothing after it, matching exactly these keys:

```json
${JsonOutput.prettyPrint(JsonOutput.toJson(schema))}
```
The descriptions above say what each key is for; replace them with your values.
"""

    File promptFile = new File(transcriptDir, "${safe(label)}.prompt.md")
    promptFile.text = prompt

    List<String> command = ['claude', '-p', '--agent', agentName, '--output-format', 'json',
                            '--permission-mode', options.permissionMode]
    if (options.model) {
        command += ['--model', options.model]
    }
    if (WINDOWS) {
        command = ['cmd', '/c'] + command
    }

    Instant startedAt = Instant.now()
    File out = new File(transcriptDir, "${safe(label)}.out.json")
    File err = new File(transcriptDir, "${safe(label)}.err.txt")

    Process process = new ProcessBuilder(command)
            .directory(REPO)
            .redirectInput(promptFile)
            .redirectOutput(out)
            .redirectError(err)
            .start()
    agentsRun++

    if (!process.waitFor(options.timeoutMinutes, TimeUnit.MINUTES)) {
        // The whole tree, and the descendants first. What this started is `cmd /c claude`, and
        // `claude` is itself a batch shim, so destroying the process destroys the shell and leaves
        // the agent running -- with its stdout still redirected to a transcript this run has
        // written off, and, if it is the build tester, still working in target/ while the next
        // round starts. That is exactly the shared-target race the Verify interlock exists to
        // prevent, arriving by the back door.
        List<ProcessHandle> descendants = process.descendants().toList()
        descendants.each { it.destroyForcibly() }
        process.destroyForcibly()
        printRow(phaseTitle, agentName,
                "timed out after ${options.timeoutMinutes}m, killed ${descendants.size() + 1} process(es)",
                Duration.between(startedAt, Instant.now()))
        return null
    }

    Duration took = Duration.between(startedAt, Instant.now())
    if (process.exitValue() != 0) {
        printRow(phaseTitle, agentName, "exit ${process.exitValue()} -- see ${relative(err)}", took)
        return null
    }

    Map parsed = parse(out)
    if (parsed == null) {
        printRow(phaseTitle, agentName, "no usable json -- see ${relative(out)}", took)
        return null
    }
    printRow(phaseTitle, agentName, note(parsed), took)
    return parsed
}

/**
 * Pulls the agent's object out of the CLI envelope.
 *
 * Two layers, because there are two: the envelope claude -p --output-format json writes, and the
 * fenced block the agent was asked to end its reply with. Only the last fence counts -- an agent
 * that quotes a schema mid-reply before filling it in would otherwise have its example read as its
 * answer.
 */
Map parse(File outputFile) {
    String text
    try {
        def envelope = new JsonSlurper().parse(outputFile)
        text = envelope.result ?: envelope.text ?: envelope.content
    } catch (Exception ignored) {
        text = outputFile.exists() ? outputFile.text : null
    }
    if (!text) {
        return null
    }
    def fences = (text =~ /(?s)```(?:json)?\s*(\{.*?})\s*```/).findAll()
    String json = fences ? fences[-1][1] : null
    if (!json) {
        return null
    }
    try {
        def parsed = new JsonSlurper().parseText(json)
        return parsed instanceof Map ? (Map) parsed : null
    } catch (Exception ignored) {
        return null
    }
}

/** The one line of an agent's answer worth putting on the console. */
String note(Map result) {
    if (result.containsKey('decided')) {
        return result.decided ? "settled: ${(result.recordedAt ?: []).join(', ')}" : 'not settled -- needs a person'
    }
    if (result.containsKey('blocked')) {
        return result.blocked ? "blocked: ${trim(result.blockedBecause ?: '', 40)}" : "${(result.filesChanged ?: []).size()} file(s) changed"
    }
    if (result.containsKey('green')) {
        return "${result.testsRun} run / ${result.failures} fail / ${result.errors} err / ${result.skipped} skip"
    }
    if (result.containsKey('undefendedDecisions')) {
        return "${(result.undefendedDecisions ?: []).size()} decision(s) undefended"
    }
    if (result.containsKey('fixed')) {
        return result.fixed ? "fixed: ${trim(result.rootCause ?: '', 40)}" : 'not fixed'
    }
    if (result.containsKey('verdict')) {
        return "${result.verdict} (${(result.amendments ?: []).size()} amendment(s))"
    }
    return 'done'
}

/**
 * What a run would do, without doing any of it.
 *
 * Only the first prompt can be rendered: every later one quotes the result of the stage before it,
 * so a dry run that printed them all would be printing invented answers. It prints the order and
 * the agent each phase goes to, which is what there is to check before spending a real run.
 */
void printPlan() {
    println "settle-and-land, dry run: ${items.size()} item(s), up to ${options.rounds} round(s) each"
    println "permission mode ${options.permissionMode}${options.model ? ", model ${options.model}" : ''}, ${options.timeoutMinutes}m per agent"
    println ''
    items.eachWithIndex { String item, int index ->
        println "-- item ${index + 1}/${items.size()}: ${trim(item, 60)}"
        printRow('Settle', 'analyst', 'records the decision and the tests that pin it', null)
        for (int round = 1; round <= options.rounds; round++) {
            printRow('Implement', 'spec-implementer', "round ${round}: code under src/main only", null)
            printRow('Verify', 'tester', "round ${round}: runs ./mvnw test", null)
            printRow('Verify', 'tester', "round ${round}: audits what no test defends", null)
            printRow('Repair', 'debugger', "round ${round}: only if the build came back red", null)
            printRow('Gate', 'architect', "round ${round}: the eight-point gate, then a verdict", null)
            printRow('Settle', 'analyst', "round ${round}: only if the gate routed anything back", null)
        }
    }
    println ''
    println 'Each command is: claude -p --agent <name> --output-format json --permission-mode ' + options.permissionMode
    println 'Prompts are fed on stdin, so nothing depends on shell quoting. Nothing is committed.'
}

// --- Console and paths -----------------------------------------------------------------------

void printRow(String phaseTitle, String agentName, String note, Duration took) {
    String elapsed = took == null ? '' : (took.toMinutes() ? "${took.toMinutes()}m${took.toSecondsPart()}s" : "${took.toSeconds()}s")
    println "   [${phaseTitle.padRight(9)}] ${agentName.padRight(17)} ${note.padRight(38)} ${elapsed}"
}

String relative(File file) {
    return REPO.toPath().relativize(file.toPath()).toString().replace('\\', '/')
}

String safe(String label) {
    return label.replaceAll(/[^A-Za-z0-9._-]/, '_')
}

String trim(String text, int width) {
    String one = (text ?: '').replaceAll(/\s+/, ' ').trim()
    return one.length() <= width ? one : one.substring(0, width - 3) + '...'
}

/** An option's value, or exit 2 saying which option went without one. */
String required(String name, String value) {
    if (value == null) {
        fail("--${name} needs its value attached with an '=', as --${name}=<value>")
    }
    if (value.isBlank()) {
        fail("--${name} was given an empty value")
    }
    return value
}

/** An option's value as a whole number, or exit 2 saying what arrived instead. */
int wholeNumber(String name, String value) {
    String text = required(name, value)
    if (!(text ==~ /\d+/)) {
        fail("--${name} takes a whole number, and was given '${text}'")
    }
    return text as int
}

void fail(String message) {
    System.err.println("settle-and-land: ${message}")
    System.exit(2)
}
