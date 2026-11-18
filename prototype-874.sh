#!/usr/bin/env bash
# PROTOTYPE #874, throwaway: opens the why-it-stopped panel variants on five fake endings.
cd "$(dirname "$0")"
export JAVA_HOME=~/.jdks/openjdk-26.0.2.1
./mvnw -o -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
"$JAVA_HOME/bin/java" -cp "target/classes;$(cat target/cp.txt)" 'io.algernon.vespera.ui.Prototype874WhyItStopped$Main'
