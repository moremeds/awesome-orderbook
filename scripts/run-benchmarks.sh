#!/usr/bin/env bash
# Compiles tests (triggers JMH annotation processing) and runs the benchmark suite.
# Latency percentiles by default (SampleTime). For throughput / allocation, see below.
set -euo pipefail
cd "$(dirname "$0")/.."

mvn -q test-compile
mvn -q exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.orderbook.bench.BenchmarkRunner

# Throughput:  mvn -q exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.orderbook.bench.BenchmarkRunner -Dexec.args="-bm thrpt"
# Allocation:  ... -Dexec.args="-prof gc"
