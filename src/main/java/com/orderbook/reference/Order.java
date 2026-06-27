package com.orderbook.reference;

import com.orderbook.Side;

final class Order {
    final long orderId;
    final Side side;
    final long price;
    long qty;

    Order(long orderId, Side side, long price, long qty) {
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.qty = qty;
    }
}
