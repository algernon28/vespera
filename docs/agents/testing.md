# Running the tests

Detail on running the suite that `AGENTS.md` leaves out to stay short. Its *Building and testing* section is the rule; this page adds what a session may need once a test has failed.

- **An IDE running JUnit itself shows everything a test logs.** Surefire and Failsafe write each class's output to `target/surefire-reports/<class>-output.txt` and `target/failsafe-reports/<class>-output.txt` (ADR-194), so a green build prints no test's errors or stack traces. IntelliJ, unless told to delegate to Maven, runs JUnit itself and puts that output back on its console.
