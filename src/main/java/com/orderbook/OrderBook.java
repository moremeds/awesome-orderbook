package com.orderbook;

/**
 * Public L3 order book API. Two implementations exist:
 * {@link ActiveArrayOrderBook} (primary) and reference.TreeMapOrderBook (oracle/baseline).
 * The interface shares signatures only — no algorithm crosses it.
 */
public interface OrderBook {
    /** @throws IllegalArgumentException duplicate id, qty&lt;=0, or null side */
    void add(long orderId, Side side, long price, long qty);

    /** qty==0 removes; qty&lt;0 rejected. @throws java.util.NoSuchElementException unknown id */
    void update(long orderId, long newQty);

    /** @throws java.util.NoSuchElementException unknown id */
    void remove(long orderId);

    /** @return the level at depth index (0 = best), or null if out of range. Materializes only this level. */
    LevelSnapshot getByLevel(Side side, int index);

    /** @return the level at an exact price, or null if absent. Materializes only this level. */
    LevelSnapshot getByPrice(Side side, long price);

    /**
     * Walk every price level best→worst, allocation-free (no snapshot objects).
     * This is the low-latency answer to the "iterate all price levels" requirement;
     * {@link #snapshot()} is the allocating, diagnostic alternative.
     * @throws IllegalArgumentException if side is null
     */
    void forEachLevel(Side side, LevelVisitor visitor);

    /**
     * Walk every order — levels best→worst, FIFO within each level — allocation-free.
     * Covers "within each level iterate the individual orders" without materializing the book.
     * @throws IllegalArgumentException if side is null
     */
    void forEachOrder(Side side, OrderVisitor visitor);

    /** Whole-book normalized view (best→worst, FIFO within). For diagnostics / differential testing. */
    BookSnapshot snapshot();

    int levelCount(Side side);

    int orderCount();

    /** Keep at most maxLevels best levels on the given side; discard the worst tail. */
    void trim(Side side, int maxLevels);
}
