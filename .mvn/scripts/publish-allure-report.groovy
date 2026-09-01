// Keeps the generated Allure report as a file a person can open later.
//
// The generator overwrites target/allure-report on every build, and target is not committed, so
// the report of the run you are reading about is gone by the next one. This copies it out under a
// timestamp, into reports/, where it survives the build that made it.

def source = new File(project.build.directory, 'allure-report/index.html')
def stamp = java.time.LocalDateTime.now()
    .format(java.time.format.DateTimeFormatter.ofPattern('yyyyMMdd-HHmmss'))
def dest = new File(project.basedir, "reports/report_${stamp}.html")
dest.parentFile.mkdirs()
dest.bytes = source.bytes
log.info("Allure report: reports/report_${stamp}.html")
