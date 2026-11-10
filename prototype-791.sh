#!/usr/bin/env bash
# PROTOTYPE #791, throwaway: opens the three progress-percentage variants on a fake run.
cd "$(dirname "$0")"
export JAVA_HOME=~/.jdks/openjdk-26.0.2.1
./mvnw -o -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
"$JAVA_HOME/bin/java" -cp "target/classes;$(cat target/cp.txt)" 'io.algernon.vespera.ui.Prototype791Percent$Main'
