package com.orderbook;

public final class Order {
    public final long orderId;
    public final Side side;
    public final long price;
    public long qty;  // mutable; set to 0 on removal

    public Order(long orderId, Side side, long price, long qty) {
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.qty = qty;
    }

    @Override
    public String toString() {
        return "Order{id=" + orderId + ", side=" + side + ", price=" + price + ", qty=" + qty + "}";
    }
}
