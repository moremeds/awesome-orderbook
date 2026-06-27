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

    private HalfBook half(Side side) { return side == Side.BID ? bids : asks; }

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
}
