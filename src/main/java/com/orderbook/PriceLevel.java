package com.orderbook;

import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * Holds all resting orders at a single price point, maintaining FIFO insertion order.
 * Uses LinkedHashMap for O(1) add/remove/lookup while preserving time priority.
 */
public final class PriceLevel {
    public final long price;
    private final LinkedHashMap<Long, Order> orders = new LinkedHashMap<>();
    private long totalQty;

    public PriceLevel(long price) {
        this.price = price;
    }

    public void addOrder(Order order) {
        orders.put(order.orderId, order);
        totalQty += order.qty;
    }

    /** Adjusts totalQty in-place; caller is responsible for setting order.qty after. */
    public void applyQtyDelta(Order order, long newQty) {
        totalQty += (newQty - order.qty);
        order.qty = newQty;
    }

    public void removeOrder(long orderId) {
        Order removed = orders.remove(orderId);
        if (removed != null) {
            totalQty -= removed.qty;
        }
    }

    public Order getOrder(long orderId) {
        return orders.get(orderId);
    }

    public Collection<Order> getOrders() {
        return orders.values();
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public int getOrderCount() {
        return orders.size();
    }

    public long getTotalQty() {
        return totalQty;
    }

    @Override
    public String toString() {
        return "PriceLevel{price=" + price + ", totalQty=" + totalQty + ", orders=" + orders.size() + "}";
    }
}
