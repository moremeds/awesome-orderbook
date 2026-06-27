package com.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook book;

    @BeforeEach
    void setUp() {
        book = new OrderBook();
    }

    // -------------------------------------------------------------------------
    // Add
    // -------------------------------------------------------------------------

    @Test
    void addBidCreatesLevel() {
        book.add(1, Side.BID, 100, 10);
        PriceLevel level = book.getByPrice(Side.BID, 100);
        assertNotNull(level);
        assertEquals(10, level.getTotalQty());
        assertEquals(1, level.getOrderCount());
    }

    @Test
    void addAskCreatesLevel() {
        book.add(1, Side.ASK, 101, 5);
        PriceLevel level = book.getByPrice(Side.ASK, 101);
        assertNotNull(level);
        assertEquals(5, level.getTotalQty());
    }

    @Test
    void addMultipleOrdersSameLevelAccumulatesQty() {
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 100, 20);
        PriceLevel level = book.getByPrice(Side.BID, 100);
        assertEquals(30, level.getTotalQty());
        assertEquals(2, level.getOrderCount());
    }

    @Test
    void addZeroQtyThrows() {
        assertThrows(IllegalArgumentException.class, () -> book.add(1, Side.BID, 100, 0));
    }

    @Test
    void addNegativeQtyThrows() {
        assertThrows(IllegalArgumentException.class, () -> book.add(1, Side.BID, 100, -5));
    }

    @Test
    void addDuplicateOrderIdThrows() {
        book.add(1, Side.BID, 100, 10);
        assertThrows(IllegalArgumentException.class, () -> book.add(1, Side.BID, 101, 5));
    }

    // -------------------------------------------------------------------------
    // Ordering: bids descending, asks ascending
    // -------------------------------------------------------------------------

    @Test
    void bidsIterateBestToWorst_highestPriceFirst() {
        book.add(1, Side.BID, 100, 1);
        book.add(2, Side.BID, 102, 1);
        book.add(3, Side.BID, 101, 1);

        List<Long> prices = new ArrayList<>();
        for (PriceLevel lv : book.getLevels(Side.BID)) prices.add(lv.price);
        assertEquals(List.of(102L, 101L, 100L), prices);
    }

    @Test
    void asksIterateBestToWorst_lowestPriceFirst() {
        book.add(1, Side.ASK, 105, 1);
        book.add(2, Side.ASK, 103, 1);
        book.add(3, Side.ASK, 104, 1);

        List<Long> prices = new ArrayList<>();
        for (PriceLevel lv : book.getLevels(Side.ASK)) prices.add(lv.price);
        assertEquals(List.of(103L, 104L, 105L), prices);
    }

    // -------------------------------------------------------------------------
    // Query by level index
    // -------------------------------------------------------------------------

    @Test
    void getByLevelReturnsCorrectLevel() {
        book.add(1, Side.BID, 100, 1);
        book.add(2, Side.BID, 102, 1);
        book.add(3, Side.BID, 101, 1);

        assertEquals(102, book.getByLevel(Side.BID, 0).price); // best
        assertEquals(101, book.getByLevel(Side.BID, 1).price);
        assertEquals(100, book.getByLevel(Side.BID, 2).price); // worst
    }

    @Test
    void getByLevelOutOfRangeReturnsNull() {
        book.add(1, Side.BID, 100, 1);
        assertNull(book.getByLevel(Side.BID, 1));
        assertNull(book.getByLevel(Side.BID, -1));
    }

    @Test
    void getByPriceMissingReturnsNull() {
        assertNull(book.getByPrice(Side.BID, 999));
    }

    // -------------------------------------------------------------------------
    // Update qty
    // -------------------------------------------------------------------------

    @Test
    void updateQtyAdjustsTotalQty() {
        book.add(1, Side.BID, 100, 10);
        book.update(1, 25);
        assertEquals(25, book.getByPrice(Side.BID, 100).getTotalQty());
    }

    @Test
    void updateQtyAffectsOnlyTargetOrder() {
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 100, 20);
        book.update(1, 5);
        assertEquals(25, book.getByPrice(Side.BID, 100).getTotalQty()); // 5 + 20
    }

    @Test
    void updateQtyZeroRemovesOrder() {
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 100, 5);
        book.update(1, 0);

        PriceLevel level = book.getByPrice(Side.BID, 100);
        assertNotNull(level);
        assertEquals(5, level.getTotalQty());
        assertEquals(1, level.getOrderCount());
    }

    @Test
    void updateQtyZeroOnLastOrderRemovesLevel() {
        book.add(1, Side.BID, 100, 10);
        book.update(1, 0);
        assertNull(book.getByPrice(Side.BID, 100));
        assertEquals(0, book.getLevelCount(Side.BID));
    }

    @Test
    void updateUnknownOrderThrows() {
        assertThrows(NoSuchElementException.class, () -> book.update(999, 10));
    }

    // -------------------------------------------------------------------------
    // Remove
    // -------------------------------------------------------------------------

    @Test
    void removeOrderDecrementsLevel() {
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 100, 5);
        book.remove(1);

        PriceLevel level = book.getByPrice(Side.BID, 100);
        assertEquals(5, level.getTotalQty());
        assertEquals(1, level.getOrderCount());
    }

    @Test
    void removeLastOrderDeletesLevel() {
        book.add(1, Side.BID, 100, 10);
        book.remove(1);
        assertNull(book.getByPrice(Side.BID, 100));
        assertEquals(0, book.getLevelCount(Side.BID));
    }

    @Test
    void removeUnknownOrderThrows() {
        assertThrows(NoSuchElementException.class, () -> book.remove(999));
    }

    @Test
    void removeSameOrderTwiceThrows() {
        book.add(1, Side.BID, 100, 10);
        book.remove(1);
        assertThrows(NoSuchElementException.class, () -> book.remove(1));
    }

    // -------------------------------------------------------------------------
    // Within-level FIFO iteration
    // -------------------------------------------------------------------------

    @Test
    void ordersWithinLevelIterateInFIFOOrder() {
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 100, 20);
        book.add(3, Side.BID, 100, 30);

        List<Long> ids = new ArrayList<>();
        for (Order o : book.getByPrice(Side.BID, 100).getOrders()) ids.add(o.orderId);
        assertEquals(List.of(1L, 2L, 3L), ids);
    }

    // -------------------------------------------------------------------------
    // Trim
    // -------------------------------------------------------------------------

    @Test
    void trimBidsKeepsBestLevels() {
        book.add(1, Side.BID, 102, 1);
        book.add(2, Side.BID, 101, 1);
        book.add(3, Side.BID, 100, 1);
        book.add(4, Side.BID, 99,  1);
        book.add(5, Side.BID, 98,  1);

        book.trim(Side.BID, 3);

        assertEquals(3, book.getLevelCount(Side.BID));
        assertNotNull(book.getByPrice(Side.BID, 102)); // best — kept
        assertNotNull(book.getByPrice(Side.BID, 101));
        assertNotNull(book.getByPrice(Side.BID, 100));
        assertNull(book.getByPrice(Side.BID, 99));    // trimmed
        assertNull(book.getByPrice(Side.BID, 98));    // trimmed
    }

    @Test
    void trimAsksKeepsBestLevels() {
        book.add(1, Side.ASK, 100, 1);
        book.add(2, Side.ASK, 101, 1);
        book.add(3, Side.ASK, 102, 1);

        book.trim(Side.ASK, 2);

        assertEquals(2, book.getLevelCount(Side.ASK));
        assertNotNull(book.getByPrice(Side.ASK, 100)); // best — kept
        assertNotNull(book.getByPrice(Side.ASK, 101));
        assertNull(book.getByPrice(Side.ASK, 102));   // trimmed
    }

    @Test
    void trimEvictsOrdersFromGlobalIndex() {
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 99, 5);  // worst level

        book.trim(Side.BID, 1);

        assertEquals(1, book.getTotalOrderCount());
        // orderId 2 is gone from the index — remove should throw
        assertThrows(NoSuchElementException.class, () -> book.remove(2));
    }

    @Test
    void trimNoOpWhenAlreadyUnderLimit() {
        book.add(1, Side.BID, 100, 1);
        book.add(2, Side.BID, 101, 1);

        book.trim(Side.BID, 5); // 2 levels < 5 — nothing trimmed
        assertEquals(2, book.getLevelCount(Side.BID));
    }

    @Test
    void trimToZeroClearsAllLevels() {
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 101, 5);

        book.trim(Side.BID, 0);
        assertEquals(0, book.getLevelCount(Side.BID));
        assertEquals(0, book.getTotalOrderCount());
    }

    // -------------------------------------------------------------------------
    // Empty book edge cases
    // -------------------------------------------------------------------------

    @Test
    void emptyBookQueriesReturnNullOrZero() {
        assertNull(book.getByLevel(Side.BID, 0));
        assertNull(book.getByPrice(Side.ASK, 100));
        assertEquals(0, book.getLevelCount(Side.BID));
        assertEquals(0, book.getTotalOrderCount());
    }

    @Test
    void bidAndAskSidesAreIndependent() {
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.ASK, 100, 5);

        assertEquals(1, book.getLevelCount(Side.BID));
        assertEquals(1, book.getLevelCount(Side.ASK));
        assertEquals(10, book.getByPrice(Side.BID, 100).getTotalQty());
        assertEquals(5,  book.getByPrice(Side.ASK, 100).getTotalQty());
    }
}
