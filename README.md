# Low-Latency L3 Order Book

Single-instrument Level-3 limit order book in Java, focused on correctness, determinism, and predictable low-latency performance.

## Build and Run

**Prerequisites:** JDK 21+, Maven 3.9+

```bash
# Install (macOS)
brew install openjdk@21 maven

# Build
mvn compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=OrderBookTest

# Run a single test method
mvn test -Dtest=OrderBookTest#trimBidsKeepsBestLevels
```

---

## Design Goals and Non-Goals

**Goals**
- Correct L3 semantics: multiple individual orders per price level, no aggregation
- O(log N) mutations, O(1) best-level access, O(1) order lookup by ID
- FIFO time priority within each price level
- Clean, readable code that clearly expresses intent

**Non-Goals**
- Thread safety — single-threaded model throughout
- Matching engine / order execution
- Market orders or time-in-force semantics
- Price-change updates — a price change requires `remove()` + `add()` because it resets time priority

---

## Data Structures

### Per-side: `TreeMap<Long, PriceLevel>`

Each side uses a `TreeMap` keyed by price:

| Side | Comparator | `firstEntry()` |
|------|-----------|---------------|
| Bids | `reverseOrder()` | Highest price = best bid |
| Asks | Natural order | Lowest price = best ask |

**Why TreeMap?**
- Guaranteed O(log N) insert, delete, and lookup — no worst-case spikes
- Sorted order is maintained automatically; best level is always O(1) via `firstEntry()`
- In-order iteration is O(N) with no additional sort step; the red-black tree maintains forward/back pointers so each iterator step is O(1)

**Trade-off:** Level-by-index access (`getByLevel(k)`) is O(k) — no random-access to position k. For typical exchange books (≤200 levels), O(k) is fine in practice. An order-statistic tree (augmented BST) would give O(log N) rank queries at the cost of significantly more implementation complexity.

### Per-level: `LinkedHashMap<Long, Order>`

Within each `PriceLevel`, orders are stored in a `LinkedHashMap<orderId, Order>`:
- O(1) add / remove / lookup by order ID
- Preserves insertion order = FIFO time priority (standard exchange matching rule)

### Global order index: `HashMap<Long, Order>`

`OrderBook` maintains a flat `HashMap<orderId, Order>` across all live orders:
- O(1) lookup for `update()` and `remove()` without knowing price or side upfront
- Market data feeds (CME, ITCH) deliver events keyed only by order ID — the index makes these O(1) without scanning either side's tree

---

## Complexity Analysis

| Operation | Time | Notes |
|-----------|------|-------|
| `add` | O(log N) | TreeMap insert + HashMap put |
| `update(qty)` | O(1) + O(log N) if level empties | HashMap O(1) lookup; level removal is O(log N) |
| `remove` | O(log N) | HashMap remove + conditional TreeMap remove |
| `getByLevel(k)` | O(k) | Iterator walk from head; k ≤ depth |
| `getByPrice` | O(log N) | TreeMap.get |
| `iterate` | O(N + M) | N = price levels, M = total orders |
| `trim(t)` | O(t × M_avg) | t worst levels removed, each clears its orders |

**Space:** O(N + M) where N = price levels, M = total orders.

---

## Testing Approach

`OrderBookTest` covers:
- **Correct ordering**: bids descend (highest first), asks ascend (lowest first)
- **FIFO within a level**: orders iterate in insertion order
- **Level lifecycle**: auto-creation on first order, auto-removal when last order leaves
- **qty=0 implies removal** (spec requirement)
- **Trim**: removes worst levels, cleans the global order index
- **Error conditions**: duplicate orderId, unknown orderId, non-positive qty on add
- **Side isolation**: bid and ask sides are independent

---

## What I Would Improve With More Time

1. **O(log N) level-by-index**: Augment the BST with subtree sizes (order-statistic tree) to answer rank queries in O(log N). Worthwhile if top-of-book snapshot queries are on the critical path.

2. **Object pooling**: Recycle `Order` and `PriceLevel` instances via a pool to eliminate GC pressure under high message rates (the main latency tail risk in Java).

3. **Off-heap storage**: For extreme HFT latency, store order data in a pre-allocated `ByteBuffer` array with fixed-width records. Eliminates GC entirely and improves cache-line utilization.

4. **Price-change updates**: Support cancel-replace — remove from old level, insert at new level with new time priority. Requires an extra O(log N) tree operation.

5. **Event listeners**: Add a change-listener interface (or ring buffer output) for downstream consumers such as a strategy engine or market data publisher.

6. **Benchmarks**: Add JMH microbenchmarks for add/update/remove at representative book depths to validate latency assumptions and catch regressions.
