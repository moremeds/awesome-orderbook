package com.orderbook;

import com.orderbook.reference.TreeMapOrderBook;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Audits BOTH implementations against hand-written expectations — the oracle-independence check. */
class OrderBookContractTest {

    static Stream<Named<Supplier<OrderBook>>> impls() {
        return Stream.of(
            Named.of("ActiveArray", (Supplier<OrderBook>) ActiveArrayOrderBook::new),
            Named.of("TreeMap", (Supplier<OrderBook>) TreeMapOrderBook::new));
    }

    @ParameterizedTest(name = "[{0}] add creates level with qty")
    @MethodSource("impls")
    void addCreatesLevel(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        LevelSnapshot lvl = b.getByPrice(Side.BID, 100);
        assertNotNull(lvl);
        assertEquals(10, lvl.totalQty());
        assertEquals(1, lvl.orders().size());
    }

    @ParameterizedTest(name = "[{0}] same-level qty accumulates, FIFO preserved")
    @MethodSource("impls")
    void sameLevelAccumulates(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        b.add(2, Side.BID, 100, 20);
        LevelSnapshot lvl = b.getByPrice(Side.BID, 100);
        assertEquals(30, lvl.totalQty());
        assertEquals(List.of(1L, 2L), lvl.orders().stream().map(OrderSnapshot::orderId).toList());
    }

    @ParameterizedTest(name = "[{0}] bids descend, asks ascend")
    @MethodSource("impls")
    void ordering(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 1);
        b.add(2, Side.BID, 102, 1);
        b.add(3, Side.BID, 101, 1);
        assertEquals(List.of(102L, 101L, 100L),
            b.snapshot().bids().stream().map(LevelSnapshot::price).toList());

        b.add(4, Side.ASK, 105, 1);
        b.add(5, Side.ASK, 103, 1);
        b.add(6, Side.ASK, 104, 1);
        assertEquals(List.of(103L, 104L, 105L),
            b.snapshot().asks().stream().map(LevelSnapshot::price).toList());
    }

    @ParameterizedTest(name = "[{0}] getByLevel index = depth")
    @MethodSource("impls")
    void getByLevel(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 1);
        b.add(2, Side.BID, 102, 1);
        b.add(3, Side.BID, 101, 1);
        assertEquals(102, b.getByLevel(Side.BID, 0).price());
        assertEquals(101, b.getByLevel(Side.BID, 1).price());
        assertEquals(100, b.getByLevel(Side.BID, 2).price());
        assertNull(b.getByLevel(Side.BID, 3));
        assertNull(b.getByLevel(Side.BID, -1));
    }

    @ParameterizedTest(name = "[{0}] getByPrice miss returns null")
    @MethodSource("impls")
    void getByPriceMiss(Supplier<OrderBook> f) {
        assertNull(f.get().getByPrice(Side.BID, 999));
    }

    @ParameterizedTest(name = "[{0}] update changes only target order")
    @MethodSource("impls")
    void updateTargetOnly(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        b.add(2, Side.BID, 100, 20);
        b.update(1, 5);
        assertEquals(25, b.getByPrice(Side.BID, 100).totalQty());
    }

    @ParameterizedTest(name = "[{0}] update to zero removes order")
    @MethodSource("impls")
    void updateZeroRemoves(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        b.add(2, Side.BID, 100, 5);
        b.update(1, 0);
        LevelSnapshot lvl = b.getByPrice(Side.BID, 100);
        assertEquals(5, lvl.totalQty());
        assertEquals(1, lvl.orders().size());
    }

    @ParameterizedTest(name = "[{0}] update zero on last order removes level")
    @MethodSource("impls")
    void updateZeroRemovesLevel(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        b.update(1, 0);
        assertNull(b.getByPrice(Side.BID, 100));
        assertEquals(0, b.levelCount(Side.BID));
    }

    @ParameterizedTest(name = "[{0}] remove last order deletes level")
    @MethodSource("impls")
    void removeDeletesEmptyLevel(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        b.remove(1);
        assertNull(b.getByPrice(Side.BID, 100));
        assertEquals(0, b.levelCount(Side.BID));
        assertEquals(0, b.orderCount());
    }

    @ParameterizedTest(name = "[{0}] FIFO survives middle removal")
    @MethodSource("impls")
    void fifoAfterMiddleRemoval(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 1);
        b.add(2, Side.BID, 100, 1);
        b.add(3, Side.BID, 100, 1);
        b.remove(2);
        b.add(4, Side.BID, 100, 1);
        assertEquals(List.of(1L, 3L, 4L),
            b.getByPrice(Side.BID, 100).orders().stream().map(OrderSnapshot::orderId).toList());
    }

    @ParameterizedTest(name = "[{0}] duplicate add rejected")
    @MethodSource("impls")
    void duplicateAdd(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        assertThrows(IllegalArgumentException.class, () -> b.add(1, Side.BID, 101, 5));
    }

    @ParameterizedTest(name = "[{0}] non-positive add rejected")
    @MethodSource("impls")
    void nonPositiveAdd(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        assertThrows(IllegalArgumentException.class, () -> b.add(1, Side.BID, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> b.add(2, Side.BID, 100, -5));
    }

    @ParameterizedTest(name = "[{0}] negative update rejected")
    @MethodSource("impls")
    void negativeUpdate(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        assertThrows(IllegalArgumentException.class, () -> b.update(1, -1));
    }

    @ParameterizedTest(name = "[{0}] unknown update/remove throw")
    @MethodSource("impls")
    void unknownIdThrows(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        assertThrows(NoSuchElementException.class, () -> b.update(9, 5));
        assertThrows(NoSuchElementException.class, () -> b.remove(9));
    }

    @ParameterizedTest(name = "[{0}] trim keeps best levels and cleans index")
    @MethodSource("impls")
    void trim(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 102, 1);
        b.add(2, Side.BID, 101, 1);
        b.add(3, Side.BID, 100, 1);
        b.add(4, Side.BID, 99, 1);
        b.trim(Side.BID, 2);
        assertEquals(2, b.levelCount(Side.BID));
        assertNotNull(b.getByPrice(Side.BID, 102));
        assertNotNull(b.getByPrice(Side.BID, 101));
        assertNull(b.getByPrice(Side.BID, 100));
        assertEquals(2, b.orderCount());
        assertThrows(NoSuchElementException.class, () -> b.remove(3));
    }

    @ParameterizedTest(name = "[{0}] trim to zero clears side")
    @MethodSource("impls")
    void trimToZero(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        b.add(2, Side.BID, 101, 5);
        b.trim(Side.BID, 0);
        assertEquals(0, b.levelCount(Side.BID));
        assertEquals(0, b.orderCount());
    }

    @ParameterizedTest(name = "[{0}] sides independent")
    @MethodSource("impls")
    void sidesIndependent(Supplier<OrderBook> f) {
        OrderBook b = f.get();
        b.add(1, Side.BID, 100, 10);
        b.add(2, Side.ASK, 100, 5);
        assertEquals(1, b.levelCount(Side.BID));
        assertEquals(1, b.levelCount(Side.ASK));
        assertEquals(10, b.getByPrice(Side.BID, 100).totalQty());
        assertEquals(5, b.getByPrice(Side.ASK, 100).totalQty());
    }
}
