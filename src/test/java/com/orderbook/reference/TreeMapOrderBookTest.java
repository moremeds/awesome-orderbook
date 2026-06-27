package com.orderbook.reference;

import com.orderbook.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.*;

class TreeMapOrderBookTest {

    @Test
    void snapshotIsBestToWorstFifo() {
        TreeMapOrderBook book = new TreeMapOrderBook();
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 102, 5);
        book.add(3, Side.BID, 100, 7);

        BookSnapshot snap = book.snapshot();
        assertEquals(List.of(102L, 100L), snap.bids().stream().map(LevelSnapshot::price).toList());
        LevelSnapshot best = snap.bids().get(1); // price 100
        assertEquals(17, best.totalQty());
        assertEquals(List.of(1L, 3L), best.orders().stream().map(OrderSnapshot::orderId).toList());
    }

    @Test
    void updateNegativeIsRejected() {
        TreeMapOrderBook book = new TreeMapOrderBook();
        book.add(1, Side.BID, 100, 10);
        assertThrows(IllegalArgumentException.class, () -> book.update(1, -1));
    }

    @Test
    void getByLevelReturnsNullOutOfRange() {
        TreeMapOrderBook book = new TreeMapOrderBook();
        book.add(1, Side.ASK, 100, 10);
        assertNull(book.getByLevel(Side.ASK, 1));
        assertNotNull(book.getByLevel(Side.ASK, 0));
    }

    @Test
    void unknownRemoveThrows() {
        assertThrows(NoSuchElementException.class, () -> new TreeMapOrderBook().remove(7));
    }
}
