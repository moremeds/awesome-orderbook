# Runs the JMH suite in FORKED JVMs on Windows. PowerShell mirror of run-benchmarks.sh.
#
# JMH forks by re-launching `java` with the current process's java.class.path, so we assemble the
# full test classpath and invoke `java` directly (forks then inherit a usable classpath). The
# classpath separator on Windows is ';'.
#
# Usage (from a PowerShell prompt at the repo root, or anywhere):
#   pwsh scripts/run-benchmarks.ps1            # latency (SampleTime), forked
#   pwsh scripts/run-benchmarks.ps1 -bm thrpt  # throughput
#   pwsh scripts/run-benchmarks.ps1 -prof gc   # allocation profile
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

mvn -q test-compile
mvn -q dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=target/bench-cp.txt
$deps = (Get-Content target/bench-cp.txt -Raw).Trim()
$cp = "target/classes;target/test-classes;$deps"

$jmhArgs = $args
if ($jmhArgs.Count -eq 0) { $jmhArgs = @('com.orderbook.bench.OrderBookBenchmark', '-f', '2') }
java -cp $cp org.openjdk.jmh.Main @jmhArgs
