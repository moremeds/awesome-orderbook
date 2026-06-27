package com.orderbook;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.*;

class ActiveArrayOrderBookTest {

    @Test
    void addUpdateRemoveLifecycle() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 100, 20);
        assertEquals(30, book.getByPrice(Side.BID, 100).totalQty());

        book.update(1, 5);
        assertEquals(25, book.getByPrice(Side.BID, 100).totalQty());

        book.remove(2);
        assertEquals(5, book.getByPrice(Side.BID, 100).totalQty());
        assertEquals(1, book.orderCount());
    }

    @Test
    void updateToZeroRemovesOrderAndEmptyLevel() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.ASK, 101, 10);
        book.update(1, 0);
        assertNull(book.getByPrice(Side.ASK, 101));
        assertEquals(0, book.levelCount(Side.ASK));
        assertEquals(0, book.orderCount());
    }

    @Test
    void getByLevelOrdersBestToWorst() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.BID, 100, 1);
        book.add(2, Side.BID, 102, 1);
        book.add(3, Side.BID, 101, 1);
        assertEquals(102, book.getByLevel(Side.BID, 0).price());
        assertEquals(101, book.getByLevel(Side.BID, 1).price());
        assertEquals(100, book.getByLevel(Side.BID, 2).price());
        assertNull(book.getByLevel(Side.BID, 3));
    }

    @Test
    void duplicateAddRejected() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.BID, 100, 10);
        assertThrows(IllegalArgumentException.class, () -> book.add(1, Side.BID, 101, 5));
    }

    @Test
    void addNonPositiveRejected() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        assertThrows(IllegalArgumentException.class, () -> book.add(1, Side.BID, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> book.add(2, Side.BID, 100, -3));
    }

    @Test
    void updateNegativeRejected() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.BID, 100, 10);
        assertThrows(IllegalArgumentException.class, () -> book.update(1, -1));
    }

    @Test
    void unknownUpdateAndRemoveThrow() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        assertThrows(NoSuchElementException.class, () -> book.update(9, 5));
        assertThrows(NoSuchElementException.class, () -> book.remove(9));
    }

    @Test
    void trimKeepsBestAndCleansIndex() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.BID, 102, 1);
        book.add(2, Side.BID, 101, 1);
        book.add(3, Side.BID, 100, 1);
        book.trim(Side.BID, 2);
        assertEquals(2, book.levelCount(Side.BID));
        assertNotNull(book.getByPrice(Side.BID, 102));
        assertNotNull(book.getByPrice(Side.BID, 101));
        assertNull(book.getByPrice(Side.BID, 100));
        assertEquals(2, book.orderCount());
        assertThrows(NoSuchElementException.class, () -> book.remove(3));
    }

    @Test
    void sidesAreIndependent() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.ASK, 100, 5);
        assertEquals(1, book.levelCount(Side.BID));
        assertEquals(1, book.levelCount(Side.ASK));
        assertEquals(10, book.getByPrice(Side.BID, 100).totalQty());
        assertEquals(5, book.getByPrice(Side.ASK, 100).totalQty());
    }

    @Test
    void snapshotIsBestToWorstFifo() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 102, 5);
        book.add(3, Side.BID, 100, 7);
        BookSnapshot snap = book.snapshot();
        assertEquals(List.of(102L, 100L), snap.bids().stream().map(LevelSnapshot::price).toList());
        assertEquals(List.of(1L, 3L),
            snap.bids().get(1).orders().stream().map(OrderSnapshot::orderId).toList());
    }
}
