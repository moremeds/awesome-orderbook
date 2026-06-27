package com.orderbook.reference;

import com.orderbook.Side;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One side. Bids: reverseOrder so firstEntry()=best. Asks: natural order. */
final class HalfBook {
    private final TreeMap<Long, PriceLevel> levels;

    HalfBook(Side side) {
        this.levels = side == Side.BID ? new TreeMap<>(Comparator.reverseOrder()) : new TreeMap<>();
    }

    PriceLevel getOrCreateLevel(long price) { return levels.computeIfAbsent(price, PriceLevel::new); }
    PriceLevel getLevel(long price) { return levels.get(price); }
    void removeLevel(long price) { levels.remove(price); }

    PriceLevel getLevelByIndex(int index) {
        if (index < 0 || index >= levels.size()) return null;
        int i = 0;
        for (PriceLevel level : levels.values()) if (i++ == index) return level;
        return null;
    }

    Collection<PriceLevel> getLevels() { return levels.values(); }
    int getLevelCount() { return levels.size(); }

    List<Order> trim(int maxLevels) {
        List<Order> evicted = new ArrayList<>();
        int keep = Math.max(0, maxLevels);
        while (levels.size() > keep) {
            Map.Entry<Long, PriceLevel> worst = levels.pollLastEntry();
            evicted.addAll(worst.getValue().getOrders());
        }
        return evicted;
    }
}
