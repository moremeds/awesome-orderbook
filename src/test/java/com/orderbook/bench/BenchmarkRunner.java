package com.orderbook.bench;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public final class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        CommandLineOptions cli = new CommandLineOptions(args);
        ChainedOptionsBuilder b = new OptionsBuilder()
            .parent(cli)
            .include(OrderBookBenchmark.class.getSimpleName());
        // Default to in-process (forks(0)) because under `mvn exec:java` a forked VM inherits
        // Maven's launcher classpath, not the project test classpath, and cannot find the
        // benchmark classes (ForkedMain ClassNotFound). Honor an explicit -f from the CLI for
        // environments where forking works (IDE run, shaded jar). All other CLI args (-bm, -wi,
        // -i, -prof, a benchmark-name filter) are passed through via the parent options.
        if (!cli.getForkCount().hasValue()) {
            b.forks(0);
        }
        new Runner(b.build()).run();
    }
}
