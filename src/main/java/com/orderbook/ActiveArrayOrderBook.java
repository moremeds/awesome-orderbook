package com.orderbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Primary L3 order book: compact active-level arrays per side + intrusive FIFO per level
 * + global orderId→OrderNode index. Single-threaded, thread-confined.
 */
public final class ActiveArrayOrderBook implements OrderBook {
    private final HalfBook bids = new HalfBook(Side.BID);
    private final HalfBook asks = new HalfBook(Side.ASK);
    private final HashMap<Long, OrderNode> orderIndex;

    public ActiveArrayOrderBook() { this(1 << 12); }

    public ActiveArrayOrderBook(int expectedOrders) {
        this.orderIndex = new HashMap<>(Math.max(16, (int) (expectedOrders / 0.75f) + 1));
    }

    private HalfBook half(Side side) {
        if (side == null) throw new IllegalArgumentException("side must not be null");
        return side == Side.BID ? bids : asks;
    }

    @Override public void add(long orderId, Side side, long price, long qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive for add: " + qty);
        if (side == null) throw new IllegalArgumentException("side must not be null");
        if (orderIndex.containsKey(orderId)) throw new IllegalArgumentException("Duplicate orderId: " + orderId);
        OrderNode node = new OrderNode(orderId, side, price, qty);
        half(side).getOrCreate(price).append(node);
        orderIndex.put(orderId, node);
    }

    @Override public void update(long orderId, long newQty) {
        OrderNode node = orderIndex.get(orderId);
        if (node == null) throw new NoSuchElementException("Unknown orderId: " + orderId);
        if (newQty < 0) throw new IllegalArgumentException("qty must not be negative: " + newQty);
        if (newQty == 0) { remove(orderId); return; }
        node.level.changeQty(node, newQty);
    }

    @Override public void remove(long orderId) {
        OrderNode node = orderIndex.remove(orderId);
        if (node == null) throw new NoSuchElementException("Unknown orderId: " + orderId);
        PriceLevel level = node.level;
        level.unlink(node);
        if (level.isEmpty()) half(node.side).removeLevel(level.price);
    }

    @Override public LevelSnapshot getByLevel(Side side, int index) {
        PriceLevel lvl = half(side).at(index);
        return lvl == null ? null : lvl.toSnapshot();
    }

    @Override public LevelSnapshot getByPrice(Side side, long price) {
        PriceLevel lvl = half(side).get(price);
        return lvl == null ? null : lvl.toSnapshot();
    }

    @Override public void forEachLevel(Side side, LevelVisitor visitor) {
        HalfBook hb = half(side);
        for (int i = 0, n = hb.size(); i < n; i++) {
            PriceLevel lvl = hb.at(i);
            visitor.accept(lvl.price, lvl.totalQty, lvl.orderCount);
        }
    }

    @Override public void forEachOrder(Side side, OrderVisitor visitor) {
        HalfBook hb = half(side);
        for (int i = 0, n = hb.size(); i < n; i++) {
            PriceLevel lvl = hb.at(i);
            for (OrderNode node = lvl.head; node != null; node = node.next)
                visitor.accept(lvl.price, node.orderId, node.qty);
        }
    }

    @Override public BookSnapshot snapshot() {
        return new BookSnapshot(sideSnapshot(bids), sideSnapshot(asks));
    }

    private List<LevelSnapshot> sideSnapshot(HalfBook hb) {
        List<LevelSnapshot> out = new ArrayList<>(hb.size());
        for (int i = 0; i < hb.size(); i++) out.add(hb.at(i).toSnapshot());
        return out;
    }

    @Override public int levelCount(Side side) { return half(side).size(); }
    @Override public int orderCount() { return orderIndex.size(); }

    @Override public void trim(Side side, int maxLevels) {
        for (OrderNode n : half(side).trim(maxLevels)) orderIndex.remove(n.orderId);
    }

    /** Benchmark/diagnostic only, read-only. Not part of the OrderBook API. */
    public long priceAtLevel(Side side, int index) { return half(side).priceAtLevel(index); }

    /** Test-only. Verifies bidirectional consistency and structural integrity. O(N) — not for the hot path. */
    void validateInvariants() {
        int linked = validateSide(bids, Side.BID) + validateSide(asks, Side.ASK);
        if (linked != orderIndex.size())
            throw new IllegalStateException("index size " + orderIndex.size() + " != linked nodes " + linked);
        for (var e : orderIndex.entrySet()) {
            if (e.getKey() != e.getValue().orderId)
                throw new IllegalStateException("index key " + e.getKey() + " != node id " + e.getValue().orderId);
        }
    }

    private int validateSide(HalfBook hb, Side side) {
        int total = 0;
        long prevPrice = 0;
        boolean havePrev = false;
        for (int i = 0; i < hb.size(); i++) {
            PriceLevel lvl = hb.at(i);
            if (lvl == null) throw new IllegalStateException("null level in occupied prefix at " + i);
            if (lvl.isEmpty()) throw new IllegalStateException("empty level present at index " + i);
            if (havePrev) {
                boolean ordered = side == Side.BID ? lvl.price < prevPrice : lvl.price > prevPrice;
                if (!ordered) throw new IllegalStateException("levels not strictly best-to-worst near index " + i);
            }
            prevPrice = lvl.price;
            havePrev = true;
            if (lvl.head != null && lvl.head.prev != null) throw new IllegalStateException("head.prev != null at " + lvl.price);
            if (lvl.tail != null && lvl.tail.next != null) throw new IllegalStateException("tail.next != null at " + lvl.price);
            long sum = 0;
            int count = 0;
            OrderNode prev = null;
            for (OrderNode n = lvl.head; n != null; n = n.next) {
                if (n.prev != prev) throw new IllegalStateException("backward link broken at " + n.orderId);
                if (n.level != lvl) throw new IllegalStateException("node.level mismatch at " + n.orderId);
                if (n.side != side) throw new IllegalStateException("node.side mismatch at " + n.orderId);
                if (n.price != lvl.price) throw new IllegalStateException("node.price mismatch at " + n.orderId);
                if (n.qty <= 0) throw new IllegalStateException("non-positive qty at " + n.orderId);
                if (orderIndex.get(n.orderId) != n) throw new IllegalStateException("index does not point to node " + n.orderId);
                sum += n.qty;
                count++;
                prev = n;
                if (count > lvl.orderCount + 1) throw new IllegalStateException("cycle suspected at " + n.orderId);
            }
            if (sum != lvl.totalQty) throw new IllegalStateException("totalQty mismatch at price " + lvl.price);
            if (count != lvl.orderCount) throw new IllegalStateException("orderCount mismatch at price " + lvl.price);
            total += count;
        }
        return total;
    }
}
