// What this writes, and why it is a commit rather than a number.
//
// A stage's implementation version is the SHA of the last commit touching its module's path
// (ADR-058): scoped, mechanical, and traceable to a commit a person can read. A repo-wide SHA
// would invalidate every stage on an unrelated change; a hand-kept constant relies on somebody
// remembering.
//
// gmavenplus rather than a new plugin, because the build already carries it for the report rename
// and this is a dozen lines of the same kind of work. No bytecode hashing: the toolchain would
// then be part of the answer.
//
// A module git cannot answer for is written as "unknown" rather than left out, and
// ImplementationVersions refuses to mint a run against that value. A build outside a git checkout
// therefore still compiles and still cannot silently invent a version.

def modules = new File(project.basedir, 'src/main/java/io/algernon/vespera')
    .listFiles()
    .findAll { it.directory }
    .sort { it.name }
def lines = modules.collect { module ->
    def relative = "src/main/java/io/algernon/vespera/${module.name}"
    def process = new ProcessBuilder('git', 'log', '-1', '--format=%H', '--', relative)
        .directory(project.basedir)
        .redirectErrorStream(true)
        .start()
    def sha = process.inputStream.text.trim()
    if (process.waitFor() != 0 || !(sha ==~ /[0-9a-f]{40}/)) {
        log.warn("No commit found for module ${module.name}; recording it as unknown")
        sha = 'unknown'
    }
    "${module.name}=${sha}"
}
def target = new File(project.build.directory,
    'generated-resources/implementation-versions/implementation-versions.properties')
target.parentFile.mkdirs()
target.text = lines.join(System.lineSeparator()) + System.lineSeparator()
log.info("Implementation versions: ${lines.join(', ')}")
