package com.orderbook;

import java.util.ArrayList;
import java.util.List;

/** One price point. Orders held as an intrusive doubly-linked FIFO list (no per-level map). */
final class PriceLevel {
    final long price;
    long totalQty;
    int orderCount;
    OrderNode head;
    OrderNode tail;

    PriceLevel(long price) { this.price = price; }

    void append(OrderNode n) {
        n.level = this;
        if (tail == null) {
            head = tail = n;
        } else {
            tail.next = n;
            n.prev = tail;
            tail = n;
        }
        totalQty += n.qty;
        orderCount++;
    }

    void unlink(OrderNode n) {
        if (n.prev != null) n.prev.next = n.next; else head = n.next;
        if (n.next != null) n.next.prev = n.prev; else tail = n.prev;
        n.prev = null;
        n.next = null;
        totalQty -= n.qty;
        orderCount--;
    }

    void changeQty(OrderNode n, long newQty) {
        totalQty += (newQty - n.qty);
        n.qty = newQty;
    }

    boolean isEmpty() { return head == null; }

    LevelSnapshot toSnapshot() {
        List<OrderSnapshot> os = new ArrayList<>(orderCount);
        for (OrderNode n = head; n != null; n = n.next) os.add(new OrderSnapshot(n.orderId, n.qty));
        return new LevelSnapshot(price, totalQty, os);
    }
}
