package com.orderbook;

/** Intrusive FIFO node. Lives in exactly one PriceLevel's doubly-linked list. */
final class OrderNode {
    final long orderId;
    final Side side;
    final long price;
    long qty;            // mutable
    OrderNode prev;
    OrderNode next;
    PriceLevel level;    // owning level, set on append

    OrderNode(long orderId, Side side, long price, long qty) {
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.qty = qty;
    }
}
