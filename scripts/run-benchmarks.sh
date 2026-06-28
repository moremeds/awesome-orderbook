#!/usr/bin/env bash
# Runs the JMH suite in FORKED JVMs (real isolation, the JMH default).
#
# JMH forks by re-launching `java` with the current process's java.class.path. Under `mvn exec:java`
# that classpath is Maven's launcher, not the project's, so forks fail with ClassNotFound. We instead
# assemble the full test classpath and invoke `java` directly — forks then inherit a classpath that
# can find the benchmark classes.
#
# Latency percentiles by default (SampleTime). Pass extra JMH args through, e.g.:
#   scripts/run-benchmarks.sh -bm thrpt      # throughput
#   scripts/run-benchmarks.sh -prof gc       # allocation profile
set -euo pipefail
cd "$(dirname "$0")/.."

mvn -q test-compile
mvn -q dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=target/bench-cp.txt
CP="target/classes:target/test-classes:$(cat target/bench-cp.txt)"

if [ "$#" -eq 0 ]; then
  set -- 'com.orderbook.bench.OrderBookBenchmark' -f 2
fi
java -cp "$CP" org.openjdk.jmh.Main "$@"
