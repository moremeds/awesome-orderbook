package com.orderbook;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PriceLevelTest {

    @Test
    void appendPreservesFifoAndTotals() {
        PriceLevel lvl = new PriceLevel(100);
        OrderNode a = new OrderNode(1, Side.BID, 100, 10);
        OrderNode b = new OrderNode(2, Side.BID, 100, 20);
        lvl.append(a);
        lvl.append(b);

        assertEquals(30, lvl.totalQty);
        assertEquals(2, lvl.orderCount);
        assertEquals(lvl, a.level);
        assertEquals(List.of(1L, 2L), lvl.toSnapshot().orders().stream().map(OrderSnapshot::orderId).toList());
    }

    @Test
    void unlinkMiddlePreservesLinks() {
        PriceLevel lvl = new PriceLevel(100);
        OrderNode a = new OrderNode(1, Side.BID, 100, 10);
        OrderNode b = new OrderNode(2, Side.BID, 100, 20);
        OrderNode c = new OrderNode(3, Side.BID, 100, 30);
        lvl.append(a); lvl.append(b); lvl.append(c);

        lvl.unlink(b);

        assertEquals(40, lvl.totalQty);
        assertEquals(2, lvl.orderCount);
        assertEquals(List.of(1L, 3L), lvl.toSnapshot().orders().stream().map(OrderSnapshot::orderId).toList());
        assertEquals(a, c.prev);
        assertEquals(c, a.next);
    }

    @Test
    void unlinkLastLeavesEmpty() {
        PriceLevel lvl = new PriceLevel(100);
        OrderNode a = new OrderNode(1, Side.BID, 100, 10);
        lvl.append(a);
        lvl.unlink(a);
        assertTrue(lvl.isEmpty());
        assertNull(lvl.head);
        assertNull(lvl.tail);
        assertEquals(0, lvl.totalQty);
    }

    @Test
    void changeQtyAdjustsTotal() {
        PriceLevel lvl = new PriceLevel(100);
        OrderNode a = new OrderNode(1, Side.BID, 100, 10);
        lvl.append(a);
        lvl.changeQty(a, 25);
        assertEquals(25, lvl.totalQty);
        assertEquals(25, a.qty);
    }
}
