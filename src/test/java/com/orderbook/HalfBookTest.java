package com.orderbook;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HalfBookTest {

    private OrderNode node(long id, Side side, long price, long qty) {
        return new OrderNode(id, side, price, qty);
    }

    @Test
    void bidsHeldHighestFirst() {
        HalfBook hb = new HalfBook(Side.BID);
        hb.getOrCreate(100).append(node(1, Side.BID, 100, 1));
        hb.getOrCreate(102).append(node(2, Side.BID, 102, 1));
        hb.getOrCreate(101).append(node(3, Side.BID, 101, 1));

        assertEquals(3, hb.size());
        assertEquals(102, hb.at(0).price);
        assertEquals(101, hb.at(1).price);
        assertEquals(100, hb.at(2).price);
    }

    @Test
    void asksHeldLowestFirst() {
        HalfBook hb = new HalfBook(Side.ASK);
        hb.getOrCreate(105).append(node(1, Side.ASK, 105, 1));
        hb.getOrCreate(103).append(node(2, Side.ASK, 103, 1));
        hb.getOrCreate(104).append(node(3, Side.ASK, 104, 1));

        assertEquals(103, hb.at(0).price);
        assertEquals(104, hb.at(1).price);
        assertEquals(105, hb.at(2).price);
    }

    @Test
    void getAndIndexOf() {
        HalfBook hb = new HalfBook(Side.BID);
        hb.getOrCreate(100);
        hb.getOrCreate(102);
        assertNotNull(hb.get(102));
        assertNull(hb.get(101));
        assertTrue(hb.indexOf(101) < 0);
        assertEquals(0, hb.indexOf(102));
    }

    @Test
    void getOrCreateIsIdempotent() {
        HalfBook hb = new HalfBook(Side.ASK);
        PriceLevel a = hb.getOrCreate(100);
        PriceLevel b = hb.getOrCreate(100);
        assertSame(a, b);
        assertEquals(1, hb.size());
    }

    @Test
    void removeLevelShiftsArray() {
        HalfBook hb = new HalfBook(Side.ASK);
        hb.getOrCreate(100);
        hb.getOrCreate(101);
        hb.getOrCreate(102);
        hb.removeLevel(101);
        assertEquals(2, hb.size());
        assertEquals(100, hb.at(0).price);
        assertEquals(102, hb.at(1).price);
        assertNull(hb.get(101));
    }

    @Test
    void growthBeyondInitialCapacity() {
        HalfBook hb = new HalfBook(Side.ASK);
        for (int p = 0; p < 100; p++) hb.getOrCreate(p);
        assertEquals(100, hb.size());
        assertEquals(0, hb.at(0).price);
        assertEquals(99, hb.at(99).price);
    }

    @Test
    void trimDiscardsWorstTailAndReturnsNodes() {
        HalfBook hb = new HalfBook(Side.BID); // best = highest
        for (long p = 100; p <= 104; p++) hb.getOrCreate(p).append(node(p, Side.BID, p, 1));
        // order best→worst: 104,103,102,101,100
        List<OrderNode> evicted = hb.trim(3); // keep 104,103,102; drop 101,100
        assertEquals(3, hb.size());
        assertEquals(102, hb.at(2).price);
        assertNull(hb.get(101));
        assertNull(hb.get(100));
        assertEquals(2, evicted.size());
        assertEquals(List.of(101L, 100L), evicted.stream().map(n -> n.orderId).toList());
    }

    @Test
    void trimNoOpWhenUnderLimit() {
        HalfBook hb = new HalfBook(Side.BID);
        hb.getOrCreate(100);
        hb.getOrCreate(101);
        assertTrue(hb.trim(5).isEmpty());
        assertEquals(2, hb.size());
    }
}
