package com.orderbook.reference;

import com.orderbook.LevelSnapshot;
import com.orderbook.OrderSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/** One price point; LinkedHashMap preserves FIFO insertion order. */
final class PriceLevel {
    final long price;
    private final LinkedHashMap<Long, Order> orders = new LinkedHashMap<>();
    private long totalQty;

    PriceLevel(long price) { this.price = price; }

    void addOrder(Order order) { orders.put(order.orderId, order); totalQty += order.qty; }

    void applyQtyDelta(Order order, long newQty) { totalQty += (newQty - order.qty); order.qty = newQty; }

    void removeOrder(long orderId) {
        Order removed = orders.remove(orderId);
        if (removed != null) totalQty -= removed.qty;
    }

    Order getOrder(long orderId) { return orders.get(orderId); }
    Collection<Order> getOrders() { return orders.values(); }
    boolean isEmpty() { return orders.isEmpty(); }
    long getTotalQty() { return totalQty; }

    LevelSnapshot toSnapshot() {
        List<OrderSnapshot> os = new ArrayList<>(orders.size());
        for (Order o : orders.values()) os.add(new OrderSnapshot(o.orderId, o.qty));
        return new LevelSnapshot(price, totalQty, os);
    }
}
