package com.orderbook.bench;

import com.orderbook.ActiveArrayOrderBook;
import com.orderbook.LevelVisitor;
import com.orderbook.OrderVisitor;
import com.orderbook.Side;
import com.orderbook.reference.TreeMapOrderBook;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Array (primary) vs TreeMap (baseline). Separate @Benchmark methods per impl — the impl is
 * NOT chosen via a polymorphic field inside the measured path, to keep interface dispatch out
 * of the numbers. State is restored each invocation (add/remove are paired) so no per-invocation
 * setup is needed. Read benchmarks use priceAtLevel(...) to avoid measuring snapshot allocation.
 *
 * SampleTime (latency) is the default; pass -bm thrpt for throughput; -prof gc for allocation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OrderBookBenchmark implements LevelVisitor, OrderVisitor {

    @Param({"100"})
    int levels;

    @Param({"10"})
    int ordersPerLevel;

    // The state IS the visitor: writing to a field instead of a captured lambda keeps the
    // iteration benchmarks allocation-free, so the number measures the walk, not a per-call lambda.
    long acc;
    @Override public void accept(long price, long totalQty, int orderCount) { acc += price; }   // LevelVisitor
    @Override public void accept(long price, long orderId, long qty) { acc += orderId; }         // OrderVisitor

    long basePrice = 10_000;
    long midPrice;
    long newMidPrice;   // a price NOT initially present, inside the populated range
    long probeId;       // a live id used by update
    long scratchId;     // id used by the add/remove pair (never permanently in the book)

    ActiveArrayOrderBook array;
    TreeMapOrderBook tree;

    @Setup(Level.Trial)
    public void setup() {
        array = new ActiveArrayOrderBook(levels * ordersPerLevel + 16);
        tree = new TreeMapOrderBook();
        long id = 1;
        // populate asks at even ticks so an odd tick in range is a guaranteed "new middle level"
        for (int l = 0; l < levels; l++) {
            long price = basePrice + 2L * l;
            for (int o = 0; o < ordersPerLevel; o++) {
                array.add(id, Side.ASK, price, 10);
                tree.add(id, Side.ASK, price, 10);
                id++;
            }
        }
        midPrice = basePrice + 2L * (levels / 2);
        newMidPrice = midPrice + 1;     // odd tick, absent, lands mid-array
        probeId = 1;                    // first order, at best level
        scratchId = id;                 // unused id beyond the populated set
    }

    // ---- read: locate level 5 ----
    @Benchmark public long arrayGetByLevel5() { return array.priceAtLevel(Side.ASK, 5); }
    @Benchmark public long treeGetByLevel5() { return tree.priceAtLevel(Side.ASK, 5); }

    // ---- read: 10 INDEPENDENT get-by-index calls (NOT iteration) ----
    // Each call locates a level from scratch: array O(1); TreeMap re-walks from the front, O(k) per
    // call + a fresh iterator allocation per call. This is the cost of repeated point access by depth.
    @Benchmark public long arrayRepeatedGetByIndex() {
        long sum = 0;
        for (int i = 0; i < 10; i++) sum += array.priceAtLevel(Side.ASK, i);
        return sum;
    }
    @Benchmark public long treeRepeatedGetByIndex() {
        long sum = 0;
        for (int i = 0; i < 10; i++) sum += tree.priceAtLevel(Side.ASK, i);
        return sum;
    }

    // ---- read: TRUE best→worst level iteration via the allocation-free visitor ----
    // One pass: array = sequential reference walk; TreeMap = one native iterator (O(1)/step amortized).
    // This is the fair test of the README's cache-locality claim (vs the repeated-access bench above).
    @Benchmark public long arrayIterateLevels() { acc = 0; array.forEachLevel(Side.ASK, this); return acc; }
    @Benchmark public long treeIterateLevels()  { acc = 0; tree.forEachLevel(Side.ASK, this);  return acc; }

    // ---- read: full L3 order walk (every order, levels best→worst, FIFO within) ----
    @Benchmark public long arrayIterateOrders() { acc = 0; array.forEachOrder(Side.ASK, this); return acc; }
    @Benchmark public long treeIterateOrders()  { acc = 0; tree.forEachOrder(Side.ASK, this);  return acc; }

    // ---- update qty (toggles, state preserved) ----
    @Benchmark public void arrayUpdate() { array.update(probeId, 11); array.update(probeId, 10); }
    @Benchmark public void treeUpdate() { tree.update(probeId, 11); tree.update(probeId, 10); }

    // ---- add a new middle level then remove it (state restored) ----
    @Benchmark public void arrayAddRemoveMiddleLevel() {
        array.add(scratchId, Side.ASK, newMidPrice, 7);
        array.remove(scratchId);
    }
    @Benchmark public void treeAddRemoveMiddleLevel() {
        tree.add(scratchId, Side.ASK, newMidPrice, 7);
        tree.remove(scratchId);
    }
}
