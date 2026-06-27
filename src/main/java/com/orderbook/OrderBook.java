package com.orderbook;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Single-instrument L3 limit order book.
 *
 * Threading: single-writer, thread-confined. All mutations must be serialized
 * through one writer thread (e.g. via MPSC queue). The concurrent collections
 * (ConcurrentHashMap, ConcurrentSkipListMap) are NOT used here — they cannot
 * preserve cross-structure invariants atomically.
 *
 * Prices are integer ticks (longs); callers handle any fixed-point scaling.
 *
 * "update" changes qty only. A price change requires remove() then add().
 * Rationale: a price change loses time priority, making it semantically a new
 * order. Forcing the caller to remove+add makes this loss of priority explicit.
 *
 * Invariants (bidirectional consistency):
 *   For every order O in orderIndex:
 *     1. O exists in halfBook(O.side).levels[O.price].orders[O.orderId]
 *     2. halfBook(O.side).levels[O.price].orders[O.orderId] == O (same reference)
 *     3. levels[O.price].totalQty == sum of all order.qty in that level
 *   For every order O reachable via any HalfBook:
 *     4. orderIndex.containsKey(O.orderId)
 *   All mutations must maintain these as one logical transition.
 *
 * Complexity summary (P = price level count, M = orders per level, R = evicted orders):
 *   add          O(log P)               — TreeMap.computeIfAbsent + HashMap.put
 *   update       O(1) avg               — HashMap lookup + in-place qty delta
 *                O(log P) if level empties — TreeMap.remove
 *   remove       O(1) avg               — HashMap.remove + LinkedHashMap.remove
 *                O(log P) if last order  — TreeMap.remove to prune empty level
 *   getByLevel   O(k)                   — iterator walk from TreeMap head
 *   getByPrice   O(log P)               — TreeMap.get
 *   iterate      O(P + M_total)         — all levels and all orders
 *   trim(t)      O(t log P + R)         — t × pollLastEntry O(log P) + R index removals
 *
 *   Space: O(P + M_total)
 */
public final class OrderBook {
    private final HalfBook bids = new HalfBook(Side.BID);
    private final HalfBook asks = new HalfBook(Side.ASK);

    // Global index: O(1) lookup by orderId for update/remove without scanning either side.
    private final HashMap<Long, Order> orderIndex = new HashMap<>();

    /**
     * Adds a new resting order. qty must be positive.
     *
     * @throws IllegalArgumentException if orderId already exists or qty <= 0
     */
    public void add(long orderId, Side side, long price, long qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive for add: " + qty);
        if (orderIndex.containsKey(orderId)) {
            throw new IllegalArgumentException("Duplicate orderId: " + orderId);
        }
        Order order = new Order(orderId, side, price, qty);
        halfBook(side).getOrCreateLevel(price).addOrder(order);
        orderIndex.put(orderId, order);
    }

    /**
     * Updates the qty of an existing order.
     * If newQty == 0, the order is removed (qty=0 implies removal per spec).
     *
     * @throws NoSuchElementException if orderId is unknown
     */
    public void update(long orderId, long newQty) {
        Order order = orderIndex.get(orderId);
        if (order == null) throw new NoSuchElementException("Unknown orderId: " + orderId);
        if (newQty == 0) {
            remove(orderId);
            return;
        }
        HalfBook book = halfBook(order.side);
        PriceLevel level = book.getLevel(order.price);
        level.applyQtyDelta(order, newQty);
    }

    /**
     * Removes an order. Automatically deletes the price level if it becomes empty.
     *
     * @throws NoSuchElementException if orderId is unknown
     */
    public void remove(long orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null) throw new NoSuchElementException("Unknown orderId: " + orderId);
        HalfBook book = halfBook(order.side);
        PriceLevel level = book.getLevel(order.price);
        level.removeOrder(orderId);
        if (level.isEmpty()) {
            book.removeLevel(order.price);
        }
    }

    /**
     * Returns the price level at the given depth index (0 = best).
     * Returns null if index is out of range.
     */
    public PriceLevel getByLevel(Side side, int index) {
        return halfBook(side).getLevelByIndex(index);
    }

    /**
     * Returns the price level at an exact price, or null if no such level exists.
     */
    public PriceLevel getByPrice(Side side, long price) {
        return halfBook(side).getLevel(price);
    }

    /**
     * Iterates all price levels from best to worst.
     * Each PriceLevel's getOrders() iterates individual orders in FIFO order.
     */
    public Collection<PriceLevel> getLevels(Side side) {
        return halfBook(side).getLevels();
    }

    /**
     * Trims the book to at most maxLevels on the given side, removing the worst (deepest) levels.
     * Evicted orders are removed from the global order index.
     */
    public void trim(Side side, int maxLevels) {
        List<Order> evicted = halfBook(side).trim(maxLevels);
        for (Order o : evicted) {
            orderIndex.remove(o.orderId);
        }
    }

    public int getLevelCount(Side side) {
        return halfBook(side).getLevelCount();
    }

    public int getTotalOrderCount() {
        return orderIndex.size();
    }

    private HalfBook halfBook(Side side) {
        return side == Side.BID ? bids : asks;
    }
}
