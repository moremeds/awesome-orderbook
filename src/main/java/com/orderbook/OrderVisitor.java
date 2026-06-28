package com.orderbook;

/**
 * Receives one order during an allocation-free L3 walk ({@link OrderBook#forEachOrder}):
 * levels best→worst, FIFO (time priority) within each level. Primitives only — no snapshot object.
 */
@FunctionalInterface
public interface OrderVisitor {
    void accept(long price, long orderId, long qty);
}
