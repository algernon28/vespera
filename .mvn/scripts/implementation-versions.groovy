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
//
// So is a module git answers only with the boundary of a shallow clone (ADR-272). The boundary is
// held without its parents, so git reports it as adding every file, and names it for every module
// whose last change lies before the clone's window. A commit with a parent inside the window is
// exact and keeps its SHA.

def git = { List<String> arguments ->
    def process = new ProcessBuilder(['git'] + arguments*.toString())
        .directory(project.basedir)
        .redirectErrorStream(true)
        .start()
    def output = process.inputStream.text.trim()
    [exit: process.waitFor(), output: output]
}
def probe = git(['rev-parse', '--is-shallow-repository'])
def shallow = probe.exit == 0 && probe.output == 'true'
def modules = new File(project.basedir, 'src/main/java/io/algernon/vespera')
    .listFiles()
    .findAll { it.directory }
    .sort { it.name }
def lines = modules.collect { module ->
    def relative = "src/main/java/io/algernon/vespera/${module.name}"
    def answer = git(['log', '-1', '--format=%H %P', '--', relative])
    def fields = answer.output.split(/\s+/).findAll { it }
    def sha = fields ? fields[0] : ''
    if (answer.exit != 0 || !(sha ==~ /[0-9a-f]{40}/) || (shallow && fields.size() < 2)) {
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
