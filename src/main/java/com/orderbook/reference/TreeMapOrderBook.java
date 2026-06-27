package com.orderbook.reference;

import com.orderbook.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Conventional ordered-map L3 book. Kept as the differential-test oracle and JMH baseline.
 * Intentionally simple — optimized for obvious correctness, not latency.
 */
public final class TreeMapOrderBook implements OrderBook {
    private final HalfBook bids = new HalfBook(Side.BID);
    private final HalfBook asks = new HalfBook(Side.ASK);
    private final HashMap<Long, Order> orderIndex = new HashMap<>();

    private HalfBook half(Side side) { return side == Side.BID ? bids : asks; }

    @Override public void add(long orderId, Side side, long price, long qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive for add: " + qty);
        if (side == null) throw new IllegalArgumentException("side must not be null");
        if (orderIndex.containsKey(orderId)) throw new IllegalArgumentException("Duplicate orderId: " + orderId);
        Order order = new Order(orderId, side, price, qty);
        half(side).getOrCreateLevel(price).addOrder(order);
        orderIndex.put(orderId, order);
    }

    @Override public void update(long orderId, long newQty) {
        Order order = orderIndex.get(orderId);
        if (order == null) throw new NoSuchElementException("Unknown orderId: " + orderId);
        if (newQty < 0) throw new IllegalArgumentException("qty must not be negative: " + newQty);
        if (newQty == 0) { remove(orderId); return; }
        half(order.side).getLevel(order.price).applyQtyDelta(order, newQty);
    }

    @Override public void remove(long orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null) throw new NoSuchElementException("Unknown orderId: " + orderId);
        HalfBook book = half(order.side);
        PriceLevel level = book.getLevel(order.price);
        level.removeOrder(orderId);
        if (level.isEmpty()) book.removeLevel(order.price);
    }

    @Override public LevelSnapshot getByLevel(Side side, int index) {
        PriceLevel lvl = half(side).getLevelByIndex(index);
        return lvl == null ? null : lvl.toSnapshot();
    }

    @Override public LevelSnapshot getByPrice(Side side, long price) {
        PriceLevel lvl = half(side).getLevel(price);
        return lvl == null ? null : lvl.toSnapshot();
    }

    @Override public BookSnapshot snapshot() {
        return new BookSnapshot(sideSnapshot(bids), sideSnapshot(asks));
    }

    private List<LevelSnapshot> sideSnapshot(HalfBook hb) {
        List<LevelSnapshot> out = new ArrayList<>(hb.getLevelCount());
        for (PriceLevel lvl : hb.getLevels()) out.add(lvl.toSnapshot());
        return out;
    }

    @Override public int levelCount(Side side) { return half(side).getLevelCount(); }
    @Override public int orderCount() { return orderIndex.size(); }

    @Override public void trim(Side side, int maxLevels) {
        for (Order o : half(side).trim(maxLevels)) orderIndex.remove(o.orderId);
    }

    /** Benchmark/diagnostic only, read-only. Not part of the OrderBook API. */
    public long priceAtLevel(Side side, int index) {
        PriceLevel lvl = half(side).getLevelByIndex(index);
        if (lvl == null) throw new IndexOutOfBoundsException("level " + index);
        return lvl.price;
    }
}
