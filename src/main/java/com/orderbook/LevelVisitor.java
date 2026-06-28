package com.orderbook;

/**
 * Receives one price level during an allocation-free best→worst walk ({@link OrderBook#forEachLevel}).
 * Primitives only — no snapshot object is created.
 */
@FunctionalInterface
public interface LevelVisitor {
    void accept(long price, long totalQty, int orderCount);
}
