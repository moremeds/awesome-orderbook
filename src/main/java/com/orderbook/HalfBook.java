package com.orderbook;

import java.util.Arrays;
import java.util.function.LongConsumer;

/**
 * One side, as a compact array of active price levels held physically best→worst.
 * BID: high→low. ASK: low→high. Best level = levels[0]. Worst = levels[size-1].
 * Insert/remove shift references via System.arraycopy; the array grows dynamically.
 */
final class HalfBook {
    private final Side side;
    private PriceLevel[] levels;
    private int size;

    HalfBook(Side side) { this(side, 16); }

    HalfBook(Side side, int initialCapacity) {
        this.side = side;
        this.levels = new PriceLevel[Math.max(1, initialCapacity)];
    }

    int size() { return size; }

    /** Negative if a sorts BEFORE b in best-first order (a is "better"). */
    private int compareBestFirst(long a, long b) {
        return side == Side.BID ? Long.compare(b, a) : Long.compare(a, b);
    }

    /** Found: index. Absent: -(insertionPoint) - 1. */
    int indexOf(long price) {
        int lo = 0, hi = size - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = compareBestFirst(levels[mid].price, price);
            if (c < 0) lo = mid + 1;
            else if (c > 0) hi = mid - 1;
            else return mid;
        }
        return -lo - 1;
    }

    PriceLevel get(long price) {
        int i = indexOf(price);
        return i >= 0 ? levels[i] : null;
    }

    PriceLevel at(int index) {
        return (index >= 0 && index < size) ? levels[index] : null;
    }

    PriceLevel getOrCreate(long price) {
        int i = indexOf(price);
        if (i >= 0) return levels[i];
        int ins = -i - 1;
        ensureCapacity(size + 1);
        System.arraycopy(levels, ins, levels, ins + 1, size - ins);
        PriceLevel lvl = new PriceLevel(price);
        levels[ins] = lvl;
        size++;
        return lvl;
    }

    void removeLevel(long price) {
        int i = indexOf(price);
        if (i < 0) return;
        System.arraycopy(levels, i + 1, levels, i, size - i - 1);
        levels[--size] = null;
    }

    /**
     * Keep best maxLevels; discard the worst contiguous tail. Reports each evicted order's id to the
     * callback so the caller can clean its global index — no temporary collection is allocated.
     */
    void trim(int maxLevels, LongConsumer onEvictedOrderId) {
        int keep = Math.max(0, maxLevels);
        if (keep >= size) return;
        for (int i = keep; i < size; i++) {
            for (OrderNode n = levels[i].head; n != null; n = n.next) onEvictedOrderId.accept(n.orderId);
            levels[i] = null;
        }
        size = keep;
    }

    long priceAtLevel(int index) { return levels[index].price; }

    private void ensureCapacity(int need) {
        if (need > levels.length) {
            int newCap = Math.max(need, levels.length + (levels.length >> 1));
            levels = Arrays.copyOf(levels, newCap);
        }
    }
}
