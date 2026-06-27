package com.orderbook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One side (bids or asks) of the order book.
 *
 * Bids: TreeMap with reverseOrder comparator — highest price = firstEntry() = best bid.
 * Asks: TreeMap with natural order — lowest price = firstEntry() = best ask.
 *
 * This ensures O(1) best-level access and O(log N) insert/delete without re-sorting.
 */
final class HalfBook {
    private final TreeMap<Long, PriceLevel> levels;

    HalfBook(Side side) {
        this.levels = side == Side.BID
            ? new TreeMap<>(Comparator.reverseOrder())
            : new TreeMap<>();
    }

    PriceLevel getOrCreateLevel(long price) {
        return levels.computeIfAbsent(price, PriceLevel::new);
    }

    PriceLevel getLevel(long price) {
        return levels.get(price);
    }

    void removeLevel(long price) {
        levels.remove(price);
    }

    /**
     * O(k) walk from the head of the sorted map, where k is the target index.
     * If callers subsequently iterate the returned level's orders, add O(M) for that level.
     * For typical book depths (≤200 levels) this is fast in practice;
     * an order-statistic tree would give O(log P) if needed.
     */
    PriceLevel getLevelByIndex(int index) {
        if (index < 0 || index >= levels.size()) return null;
        int i = 0;
        for (PriceLevel level : levels.values()) {
            if (i++ == index) return level;
        }
        return null;  // unreachable given size check above
    }

    /** Returns levels in best-to-worst order (iterator follows comparator order). */
    Collection<PriceLevel> getLevels() {
        return levels.values();
    }

    int getLevelCount() {
        return levels.size();
    }

    /**
     * Removes worst levels until at most maxLevels remain.
     * Returns evicted orders so the caller can clean up the global order index.
     *
     * lastEntry() on a reverseOrder TreeMap = lowest price (worst bid).
     * lastEntry() on a natural-order TreeMap = highest price (worst ask).
     *
     * Complexity: O(t log P + R) where t = levels removed, P = level count before trim,
     * R = total orders evicted. Each pollLastEntry() costs O(log P); iterating the
     * evicted orders costs O(R).
     */
    List<Order> trim(int maxLevels) {
        List<Order> evicted = new ArrayList<>();
        while (levels.size() > maxLevels) {
            Map.Entry<Long, PriceLevel> worst = levels.pollLastEntry();
            evicted.addAll(worst.getValue().getOrders());
        }
        return evicted;
    }
}
